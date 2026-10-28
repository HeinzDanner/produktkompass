package de.heinzdanner.produktkompass.openfoodfacts;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionalCategoryMappingMigrationTest {
    @Test
    void createsMissingGroupsPreservesEditorialMappingsAndAuditsChanges() throws Exception {
        String url = "jdbc:h2:mem:additional-categories-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("28").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO categories(name) VALUES ('Öle und Fette')");
            statement.executeUpdate("""
                    INSERT INTO off_category_mappings(off_value, category_id)
                    SELECT 'en:olive-oils', id FROM categories WHERE name = 'Saucen und Würzmittel'
                    """);
            long before;
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM off_category_mappings")) {
                rows.next();
                before = rows.getLong(1);
            }
            var flyway = Flyway.configure().dataSource(url, "sa", "").target("29").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM off_category_mappings")) {
                rows.next();
                assertThat(rows.getLong(1)).isEqualTo(before + 52);
            }
            try (var rows = statement.executeQuery("""
                    SELECT c.name FROM off_category_mappings m JOIN categories c ON c.id = m.category_id
                    WHERE m.off_value = 'en:olive-oils'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("Saucen und Würzmittel");
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) FROM categories WHERE name IN
                    ('Fleisch und Wurstwaren', 'Fisch und Meeresfrüchte', 'Öle und Fette', 'Aufstriche und Konfitüren')
                    """)) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(4);
            }
            try (var rows = statement.executeQuery("""
                    SELECT entity_name, COUNT(*) FROM audit_logs WHERE actor = 'SYSTEM' AND details LIKE 'V29:%'
                    GROUP BY entity_name ORDER BY entity_name
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("Category");
                assertThat(rows.getInt(2)).isEqualTo(3);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("OffCategoryMapping");
                assertThat(rows.getInt(2)).isEqualTo(4);
                assertThat(rows.next()).isFalse();
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) FROM off_category_mappings WHERE off_value IN
                    ('en:fruit-preserves', 'en:plant-based-foods', 'en:meat-alternatives', 'nl:Koeken/Chocolade/Snoep')
                    """)) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        }
    }
}
