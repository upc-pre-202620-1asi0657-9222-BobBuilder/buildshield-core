package pe.buildshield.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.buildshield.core.support.CoreIntegrationTest;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** El rol de ejecución no puede alterar el historial ni las protecciones del dueño/migrador. */
@CoreIntegrationTest
class RuntimeRoleIT {
    @Autowired JdbcConnectionDetails details;
    @Autowired JdbcTemplate jdbc;
    @Test void runtime_role_has_insert_and_read_but_no_history_mutation_or_ddl() throws Exception {
        String role = "buildshield_runtime_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var owner = DriverManager.getConnection(details.getJdbcUrl(),details.getUsername(),details.getPassword());
             var statement = owner.createStatement()) {
            statement.execute("CREATE ROLE \"" + role + "\" NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT");
            try {
                String grants = Files.readString(Path.of("docs/database/runtime-role.sql"));
                grants = grants.substring(grants.indexOf("BEGIN;"),grants.indexOf("COMMIT;") + 7);
                for(String sql : grants.replace(":\"app_role\"", "\"" + role + "\"").split(";"))
                    if (!sql.isBlank()) statement.execute(sql);
                owner.setAutoCommit(false);
                statement.execute("SET LOCAL ROLE \"" + role + "\"");
                try (var privileges = statement.executeQuery("SELECT has_table_privilege(current_user,'audit.events','INSERT'), has_table_privilege(current_user,'audit.events','SELECT'), has_table_privilege(current_user,'audit.events','UPDATE'), has_table_privilege(current_user,'audit.events','DELETE'), has_table_privilege(current_user,'audit.events','TRUNCATE')")) {
                    privileges.next();
                    assertThat(privileges.getBoolean(1)).isTrue();assertThat(privileges.getBoolean(2)).isTrue();
                    for(int index=3;index<=5;index++) assertThat(privileges.getBoolean(index)).isFalse();
                }
                statement.executeQuery("SELECT count(*) FROM audit.events").close();
                for(String sql : new String[]{"UPDATE audit.events SET action = action","DELETE FROM audit.events",
                        "TRUNCATE audit.events","UPDATE inventory.stock_movements SET quantity = quantity",
                        "DELETE FROM inventory.stock_movements","TRUNCATE inventory.stock_movements",
                        "ALTER TABLE audit.events DISABLE TRIGGER USER","CREATE TABLE audit.forbidden_ddl(id int)"}) {
                    Savepoint point=owner.setSavepoint();
                    assertThatThrownBy(() -> statement.execute(sql)).isInstanceOf(SQLException.class)
                            .satisfies(error -> assertThat(((SQLException)error).getSQLState()).isEqualTo("42501"));
                    owner.rollback(point);
                }
                owner.rollback();
                owner.setAutoCommit(true);
            } finally {
                if (!owner.getAutoCommit()) { owner.rollback();owner.setAutoCommit(true); }
                statement.execute("DROP OWNED BY \"" + role + "\"");
                statement.execute("DROP ROLE \"" + role + "\"");
            }
        }
    }
}
