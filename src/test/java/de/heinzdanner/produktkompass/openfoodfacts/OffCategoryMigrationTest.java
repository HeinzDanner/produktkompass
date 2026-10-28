package de.heinzdanner.produktkompass.openfoodfacts;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OffCategoryMigrationTest {
    @Test
    void backfillsBothFieldsDeduplicatesAndCascadesDeletion() throws Exception {
        String url = "jdbc:h2:mem:off-categories-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("23").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO products(id, name) VALUES (501, 'Chocolate'), (502, 'Empty')");
            statement.executeUpdate("""
                    INSERT INTO product_open_food_facts(product_id, barcode, last_imported_at, categories, category_tags)
                    VALUES (501, '501', CURRENT_TIMESTAMP, ' Chocolats noirs, nl:Chocolade, ,Chocolats noirs ',
                            'nl:Chocolade,nl:Koeken/Chocolade/Snoep'),
                           (502, '502', CURRENT_TIMESTAMP, NULL, NULL)
                    """);
            var flyway = Flyway.configure().dataSource(url, "sa", "").target("25").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (var rows = statement.executeQuery("SELECT category_value FROM product_off_categories ORDER BY category_value")) {
                var categories = new java.util.ArrayList<String>();
                while (rows.next()) categories.add(rows.getString(1));
                assertThat(categories).containsExactly("Chocolats noirs", "nl:Chocolade", "nl:Koeken/Chocolade/Snoep");
            }
            statement.executeUpdate("DELETE FROM products WHERE id = 501");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM product_off_categories")) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }
}
