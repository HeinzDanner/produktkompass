package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Objects;

public class V28__expand_researched_off_category_mappings extends BaseJavaMigration {
    public static final String RESOURCE = "/db/data/off-category-mappings-v28.tsv";

    @Override
    public void migrate(Context context) throws Exception {
        var counts = new LinkedHashMap<Long, Integer>();
        var categoryIds = new LinkedHashMap<String, Long>();
        var seen = new HashSet<String>();
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(RESOURCE), "Missing mapping seed");
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             var ensure = context.getConnection().prepareStatement("INSERT INTO categories(name) VALUES (?)",
                     java.sql.Statement.RETURN_GENERATED_KEYS);
             var category = context.getConnection().prepareStatement("SELECT id FROM categories WHERE name = ?");
             var insert = context.getConnection().prepareStatement("""
                     INSERT INTO off_category_mappings(off_value, category_id)
                     SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM off_category_mappings WHERE off_value = ?)
                     """);
             var audit = context.getConnection().prepareStatement("""
                     INSERT INTO audit_logs(entity_name, entity_id, action, details, actor, created_at)
                     VALUES ('OffCategoryMapping', ?, 'CREATE', ?, 'SYSTEM', CURRENT_TIMESTAMP)
                     """)) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 3 || fields[0].isBlank() || fields[1].isBlank()
                        || fields[2].isBlank() || !seen.add(fields[1])) {
                    throw new IllegalStateException("Invalid or duplicate OFF category seed: " + line);
                }
                Long categoryId = categoryIds.get(fields[0]);
                if (categoryId == null) {
                    category.setString(1, fields[0]);
                    try (var rows = category.executeQuery()) {
                        if (rows.next()) categoryId = rows.getLong(1);
                    }
                    if (categoryId == null) {
                        ensure.setString(1, fields[0]);
                        ensure.executeUpdate();
                        try (var keys = ensure.getGeneratedKeys()) {
                            if (!keys.next()) throw new IllegalStateException("Missing category ID: " + fields[0]);
                            categoryId = keys.getLong(1);
                        }
                    }
                    categoryIds.put(fields[0], categoryId);
                }
                insert.setString(1, fields[1]);
                insert.setLong(2, categoryId);
                insert.setString(3, fields[1]);
                int inserted = insert.executeUpdate();
                if (inserted > 0) counts.merge(categoryId, inserted, Integer::sum);
            }
            for (var entry : counts.entrySet()) {
                audit.setLong(1, entry.getKey());
                audit.setString(2, "V28: " + entry.getValue() + " recherchierte OFF-Kategoriezuordnungen; "
                        + "Taxonomie 6c4d573a1cc6a28cacb07cfb674024bad28531e8; Details: " + RESOURCE);
                audit.executeUpdate();
            }
        }
    }

    @Override
    public Integer getChecksum() {
        var checksum = new java.util.zip.CRC32();
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(RESOURCE), "Missing mapping seed")) {
            checksum.update(input.readAllBytes());
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
        return (int) checksum.getValue();
    }
}
