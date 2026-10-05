package pe.buildshield.core.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import java.util.List;

/** Limpieza privilegiada SOLO del PostgreSQL efímero de pruebas, dentro de una transacción. */
public final class TestDatabase {
    private TestDatabase() { }
    public static void reset(JdbcTemplate jdbc, String truncate) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String database;
            try (var query = connection.createStatement(); var result = query.executeQuery("SELECT current_database()")) {
                result.next(); database = result.getString(1);
            }
            if (!(database.equals("test") || database.matches("buildshield_(core|kernel|migration)_it")))
                throw new IllegalStateException("La limpieza requiere una base temporal de pruebas");
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                for (String table : List.of("inventory.stock_movements", "audit.events"))
                    statement.execute("ALTER TABLE " + table + " DISABLE TRIGGER USER");
                statement.execute(truncate);
                for (String table : List.of("inventory.stock_movements", "audit.events"))
                    statement.execute("ALTER TABLE " + table + " ENABLE TRIGGER USER");
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally { connection.setAutoCommit(autoCommit); }
            return null;
        });
    }
}
