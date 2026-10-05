package pe.buildshield.core;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import pe.buildshield.core.support.CoreIntegrationTest;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Migraciones reales: base nueva y base V12 con datos que deben conservarse. */
@CoreIntegrationTest
class MigrationUpgradeIT {
    @Autowired JdbcConnectionDetails connection;
    @Test void fresh_database_and_v12_upgrade_preserve_data_and_reject_cross_tenant_children() throws Exception {
        for (boolean upgrade : new boolean[]{false, true}) {
            String name = "buildshield_migration_it_" + UUID.randomUUID().toString().replace("-", "");
            String url = connection.getJdbcUrl().replaceAll("/[^/]+$", "/" + name);
            UUID organization = UUID.randomUUID(), other = UUID.randomUUID(), item = UUID.randomUUID(),
                    warehouse = UUID.randomUUID(), material = UUID.randomUUID(), actor = UUID.randomUUID(), order = UUID.randomUUID();
            try (var admin = DriverManager.getConnection(connection.getJdbcUrl(), connection.getUsername(), connection.getPassword())) {
                String existing;
                try (var stmt = admin.createStatement(); var result = stmt.executeQuery("SELECT current_database()")) {
                    result.next(); existing = result.getString(1);
                }
                assertThat(existing).isIn("test", "buildshield_core_it");
                admin.createStatement().execute("CREATE DATABASE \"" + name + "\"");
                try {
                    if (upgrade) {
                        Flyway.configure().dataSource(url, connection.getUsername(), connection.getPassword())
                                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("12")).load().migrate();
                        try (var db = DriverManager.getConnection(url, connection.getUsername(), connection.getPassword())) {
                            try (var stock = db.prepareStatement("INSERT INTO inventory.stock_items (id,organization_id,location_id,material_id,available_qty,reserved_qty,version,created_at,created_by,updated_at,updated_by) VALUES (?,?,?,?,10,0,0,now(),?,now(),?)")) {
                                stock.setObject(1,item);stock.setObject(2,organization);stock.setObject(3,warehouse);stock.setObject(4,material);
                                stock.setObject(5,actor);stock.setObject(6,actor);stock.executeUpdate();
                            }
                            try (var movement = db.prepareStatement("INSERT INTO inventory.stock_movements VALUES (?,?,?,'ENTRY',10,10,'histórico',now(),?)")) {
                                movement.setObject(1,UUID.randomUUID());movement.setObject(2,organization);movement.setObject(3,item);movement.setObject(4,actor);movement.executeUpdate();
                            }
                            try (var key = db.prepareStatement("INSERT INTO idempotency_keys (organization_id,idempotency_key,request_method,request_path,response_status,created_at) VALUES (?,?,'POST','/api/v1/stock/entries',201,now())")) {
                                key.setObject(1,organization);key.setObject(2,UUID.randomUUID());key.executeUpdate();
                            }
                            try (var orders = db.prepareStatement("INSERT INTO ordering.orders (id,organization_id,worksite_id,warehouse_id,requested_by,placed_at,status,version,created_at,created_by,updated_at,updated_by) VALUES (?,?,?,?,?,now(),'REGISTERED',0,now(),?,now(),?)")) {
                                orders.setObject(1,order);orders.setObject(2,organization);orders.setObject(3,UUID.randomUUID());orders.setObject(4,warehouse);
                                orders.setObject(5,actor);orders.setObject(6,actor);orders.setObject(7,actor);orders.executeUpdate();
                            }
                        }
                    }
                    Flyway.configure().dataSource(url, connection.getUsername(), connection.getPassword())
                            .locations("classpath:db/migration").load().migrate();
                    try (var db = DriverManager.getConnection(url, connection.getUsername(), connection.getPassword());
                         var stmt = db.createStatement()) {
                        try (var result = stmt.executeQuery("SELECT count(*) FROM audit.events")) { result.next();assertThat(result.getLong(1)).isZero(); }
                        if (upgrade) {
                            try (var result = stmt.executeQuery("SELECT available_qty FROM inventory.stock_items")) { result.next();assertThat(result.getBigDecimal(1)).isEqualByComparingTo("10"); }
                            try (var result = stmt.executeQuery("SELECT count(*) FROM inventory.stock_movements")) { result.next();assertThat(result.getLong(1)).isEqualTo(1); }
                            try (var result = stmt.executeQuery("SELECT user_id,request_fingerprint FROM idempotency_keys")) { result.next();assertThat(result.getObject(1)).isNull();assertThat(result.getObject(2)).isNull(); }
                            assertThatThrownBy(() -> {
                                try(var bad = db.prepareStatement("INSERT INTO inventory.stock_movements VALUES (?,?,?,'ENTRY',1,11,NULL,now(),?)")) {
                                    bad.setObject(1,UUID.randomUUID());bad.setObject(2,other);bad.setObject(3,item);bad.setObject(4,actor);bad.executeUpdate();
                                }
                            }).isInstanceOf(SQLException.class).hasMessageContaining("fk_stock_movements_org_item");
                            assertThatThrownBy(() -> {
                                try(var bad = db.prepareStatement("INSERT INTO ordering.order_lines VALUES (?,?,?,?,'CEM-002','BAG',1,0,0,0)")) {
                                    bad.setObject(1,UUID.randomUUID());bad.setObject(2,other);bad.setObject(3,order);bad.setObject(4,material);bad.executeUpdate();
                                }
                            }).isInstanceOf(SQLException.class).hasMessageContaining("fk_order_lines_org_order");
                        }
                    }
                } finally {
                    try(var stmt = admin.createStatement()) { stmt.execute("DROP DATABASE \"" + name + "\""); }
                }
            }
        }
    }
}
