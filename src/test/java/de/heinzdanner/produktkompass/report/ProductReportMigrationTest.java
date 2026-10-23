package de.heinzdanner.produktkompass.report;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductReportMigrationTest {

    @Test
    void h2MigratesAndEnforcesReportConstraints() throws Exception {
        verify("jdbc:h2:mem:reports-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "PRODUKTKOMPASS_TEST_PG_URL", matches = ".+")
    void postgresqlMigratesAndEnforcesReportConstraints() throws Exception {
        verify(System.getenv("PRODUKTKOMPASS_TEST_PG_URL"),
                System.getenv("PRODUKTKOMPASS_TEST_PG_USER"),
                System.getenv("PRODUKTKOMPASS_TEST_PG_PASSWORD"));
    }

    private void verify(String url, String username, String password) throws Exception {
        try (var dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(url);
            dataSource.setUsername(username);
            dataSource.setPassword(password);
            verifyMigratedDatabase(dataSource);
        }
    }

    private void verifyMigratedDatabase(HikariDataSource dataSource) throws Exception {
        Flyway flyway = Flyway.configure().dataSource(dataSource).load();
        flyway.migrate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("20");
        flyway.validate();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long productId;
                try (var statement = connection.prepareStatement(
                        "INSERT INTO products(name, description, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, "Report migration fixture");
                    statement.setString(2, "Rolled back after validation");
                    statement.executeUpdate();
                    try (var keys = statement.getGeneratedKeys()) {
                        assertThat(keys.next()).isTrue();
                        productId = keys.getLong(1);
                    }
                }
                try (var statement = connection.prepareStatement(
                        "INSERT INTO product_reports(product_id, description, submitted_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                    statement.setLong(1, productId);
                    statement.setString(2, "Migration fixture");
                    assertThat(statement.executeUpdate()).isEqualTo(1);
                }
                try (var statement = connection.prepareStatement(
                        "SELECT status FROM product_reports WHERE product_id = ?")) {
                    statement.setLong(1, productId);
                    try (var result = statement.executeQuery()) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getString(1)).isEqualTo("OPEN");
                    }
                }
                for (String assignment : new String[]{"status = 'INVALID'", "target_field = 'INVALID'",
                        "provenance = 'INVALID'", "description = NULL",
                        "description = '" + "x".repeat(2001) + "'", "product_id = -1"}) {
                    var savepoint = connection.setSavepoint();
                    try (var statement = connection.createStatement()) {
                        assertThatThrownBy(() -> statement.executeUpdate(
                                "UPDATE product_reports SET " + assignment + " WHERE product_id = " + productId))
                                .isInstanceOf(SQLException.class);
                    } finally {
                        connection.rollback(savepoint);
                    }
                }
                try (var statement = connection.prepareStatement("DELETE FROM products WHERE id = ?")) {
                    statement.setLong(1, productId);
                    assertThat(statement.executeUpdate()).isEqualTo(1);
                }
                try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM product_reports WHERE product_id = ?")) {
                    statement.setLong(1, productId);
                    try (var result = statement.executeQuery()) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getLong(1)).isZero();
                    }
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
