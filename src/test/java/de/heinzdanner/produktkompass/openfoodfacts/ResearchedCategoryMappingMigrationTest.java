package de.heinzdanner.produktkompass.openfoodfacts;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchedCategoryMappingMigrationTest {
    @Test
    void researchedSeedExpandsAllGroupsPreservesExistingMappingsAndAuditsActualInserts() throws Exception {
        String url = "jdbc:h2:mem:researched-categories-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("27").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO off_category_mappings(off_value, category_id)
                    SELECT 'de:Soße', id FROM categories WHERE name = 'Getränke'
                    """);
            var flyway = Flyway.configure().dataSource(url, "sa", "").target("28").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*), COUNT(DISTINCT category_id) FROM off_category_mappings
                    """)) {
                rows.next();
                assertThat(rows.getInt(1)).isGreaterThanOrEqualTo(721);
                assertThat(rows.getInt(2)).isEqualTo(12);
            }
            try (var rows = statement.executeQuery("""
                    SELECT c.name FROM off_category_mappings m JOIN categories c ON c.id = m.category_id
                    WHERE m.off_value = 'de:Soße'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("Getränke");
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) FROM off_category_mappings WHERE off_value IN
                    ('en:plant-based-foods', 'en:fishes', 'nl:Koeken/Chocolade/Snoep', 'en:groceries')
                    """)) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
            try (var rows = statement.executeQuery("""
                    SELECT COUNT(*) FROM audit_logs WHERE entity_name = 'OffCategoryMapping'
                    AND actor = 'SYSTEM' AND details LIKE 'V28:%'
                    """)) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(12);
            }
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        }
    }
}
