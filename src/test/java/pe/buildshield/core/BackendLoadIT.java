package pe.buildshield.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pe.buildshield.core.support.*;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import static org.assertj.core.api.Assertions.*;

/** Medición opt-in por HTTP real; describe el entorno, no acredita QAS de Recepciones. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ContainersConfig.class)
class BackendLoadIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Test void fifty_users_query_stock_orders_and_audit_for_sixty_seconds() throws Exception {
        int users = Integer.getInteger("buildshield.load.users", 50);
        int seconds = Integer.getInteger("buildshield.load.seconds", 60);
        assertThat(users).isBetween(1,500);
        assertThat(seconds).isBetween(1,600);
        var api = new HttpTestSession(port, json);
        var fixture = api.fixture();
        assertThat(api.call("POST", "/api/v1/stock/entries", fixture.entry(100), fixture.tenant().token()).status()).isEqualTo(201);
        UUID order = api.create("/api/v1/orders", fixture.order(), fixture.siteToken());
        var paths = List.of("/api/v1/stock", "/api/v1/orders/" + order, "/api/v1/audit/events");
        for (String path : paths) assertThat(api.call("GET",path,null,fixture.tenant().token()).status()).isEqualTo(200);
        var latencies = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>>();
        paths.forEach(path -> latencies.put(path, new ConcurrentLinkedQueue<>()));
        LongAdder errors = new LongAdder();
        var pool = Executors.newFixedThreadPool(users);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> workers = new ArrayList<>();
        Instant started = Instant.now();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        for (int worker = 0; worker < users; worker++) {
            int initial = worker;
            workers.add(pool.submit(() -> {
                try {
                    start.await();int sequence = initial;
                    while (System.nanoTime() < deadline) {
                        String path = paths.get(sequence++ % paths.size());
                        long begin = System.nanoTime();
                        try {
                            if (api.call("GET",path,null,fixture.tenant().token()).status() != 200) errors.increment();
                        } catch (Exception ex) { errors.increment(); }
                        latencies.get(path).add(System.nanoTime()-begin);
                    }
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }));
        }
        start.countDown();
        try { for(var worker : workers) worker.get(seconds+30L,TimeUnit.SECONDS); }
        finally { pool.shutdownNow(); }
        Map<String,Object> endpoints = new LinkedHashMap<>();
        long total=0;
        for(var entry : latencies.entrySet()) {
            long[] values=entry.getValue().stream().mapToLong(Long::longValue).sorted().toArray();
            total += values.length;
            endpoints.put(entry.getKey(),Map.of("requests",values.length,"p50Ms",percentile(values,.50),
                    "p95Ms",percentile(values,.95),"maxMs",values.length==0?0:values[values.length-1]/1_000_000.0));
        }
        var report = new LinkedHashMap<String,Object>();
        report.put("startedAt",started.toString());report.put("finishedAt",Instant.now().toString());
        report.put("users",users);report.put("durationSeconds",seconds);report.put("requests",total);report.put("errors",errors.sum());
        report.put("postgresql",jdbc.queryForObject("SELECT version()",String.class));
        report.put("java",System.getProperty("java.version"));report.put("os",System.getProperty("os.name"));
        report.put("processors",Runtime.getRuntime().availableProcessors());report.put("maxHeapBytes",Runtime.getRuntime().maxMemory());
        report.put("clientMode","closed-loop HTTP, read-only, one shared administrator session");
        report.put("authenticatedSubjects",1);
        report.put("testConnectionPoolMaximum",6);
        report.put("dataset",Map.of("organizations",1,"warehouses",1,"materials",1,"orders",1,"stockItems",1));
        report.put("endpoints",endpoints);
        Files.createDirectories(Path.of("target/verification"));
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/verification/backend-load.json").toFile(),report);
        assertThat(total).isGreaterThan(users);
        assertThat(errors.sum()).as("Errores de la carga HTTP").isZero();
    }
    private double percentile(long[] values,double p) { return values.length==0?0:values[(int)Math.ceil(values.length*p)-1]/1_000_000.0; }
}
