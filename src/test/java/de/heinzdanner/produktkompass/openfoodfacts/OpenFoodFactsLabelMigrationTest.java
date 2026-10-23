package de.heinzdanner.produktkompass.openfoodfacts;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenFoodFactsLabelMigrationTest {
    @Test
    void backfillsExistingRowsWithExactUniqueTagsAndLinksAndEnforcesConstraints() throws Exception {
        String url = "jdbc:h2:mem:off-label-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("17").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO products(id, name) VALUES (501, 'First'), (502, 'Second'), (503, 'Empty')");
            statement.executeUpdate("""
                    INSERT INTO product_open_food_facts(product_id, barcode, last_imported_at, label_tags)
                    VALUES (501, '501', CURRENT_TIMESTAMP, ' en:organic, ,en:organic,de:organic,fr:bio '),
                           (502, '502', CURRENT_TIMESTAMP, 'en:organic,xx:custom'),
                           (503, '503', CURRENT_TIMESTAMP, NULL)
                    """);
            Flyway flyway = Flyway.configure().dataSource(url, "sa", "").target("19").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (var counts = statement.executeQuery("""
                    SELECT (SELECT COUNT(*) FROM off_labels) AS labels,
                           (SELECT COUNT(*) FROM product_off_labels) AS links
                    """)) {
                assertThat(counts.next()).isTrue();
                assertThat(counts.getInt("labels")).isEqualTo(4);
                assertThat(counts.getInt("links")).isEqualTo(5);
            }
            try (var links = statement.executeQuery("""
                    SELECT l.canonical_tag, l.locale_key FROM product_off_labels p
                    JOIN off_labels l ON l.id = p.label_id WHERE p.product_id = 501 ORDER BY l.canonical_tag
                    """)) {
                var tags = new java.util.ArrayList<String>();
                while (links.next()) tags.add(links.getString(1) + "|" + links.getString(2));
                assertThat(tags).containsExactly("de:organic|de", "en:organic|en", "fr:bio|fr");
            }
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO off_labels(canonical_tag, locale_key) VALUES ('en:organic', 'en')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO product_off_labels SELECT product_id, label_id FROM product_off_labels WHERE product_id = 501"))
                    .isInstanceOf(SQLException.class);
            statement.executeUpdate("DELETE FROM products WHERE id = 501");
            try (var remaining = statement.executeQuery("SELECT COUNT(*) FROM product_off_labels WHERE product_id = 501")) {
                remaining.next();
                assertThat(remaining.getInt(1)).isZero();
            }
        }
    }
}
