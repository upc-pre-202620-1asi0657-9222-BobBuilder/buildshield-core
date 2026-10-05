package pe.buildshield.testapp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import pe.buildshield.core.shared.testsupport.MutableClock;

import java.time.Instant;

/** Reemplaza el reloj del kernel compartido por uno controlable. */
@TestConfiguration(proxyBeanMethods = false)
public class ClockTestConfig {

    public static final Instant START = Instant.parse("2026-10-04T12:00:00Z");

    @Bean
    MutableClock clock() {
        return new MutableClock(START);
    }
}
