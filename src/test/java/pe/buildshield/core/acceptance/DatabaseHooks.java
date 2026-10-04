package pe.buildshield.core.acceptance;

import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.buildshield.core.acceptance.CucumberSpringConfiguration.CapturingEmailPort;
import pe.buildshield.core.support.MutableClock;

import java.time.Instant;
import java.util.List;

/** Antes de cada escenario: base vacía (tablas de los módulos), sin correos capturados y reloj en la hora actual. */
public class DatabaseHooks {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    CapturingEmailPort emails;

    @Before(order = 0)
    public void resetState() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_schema || '.' || table_name FROM information_schema.tables
                WHERE table_schema IN ('iam', 'organization', 'inventory', 'ordering', 'dispatch',
                                       'subscription', 'notification', 'audit')
                  AND table_type = 'BASE TABLE'
                """, String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " CASCADE");
        }
        emails.clear();
        clock.setTo(Instant.now());
    }
}
