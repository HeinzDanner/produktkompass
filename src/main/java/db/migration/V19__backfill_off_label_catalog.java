package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class V19__backfill_off_label_catalog extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        Map<String, Long> labels = new LinkedHashMap<>();
        try (var rows = connection.createStatement();
             var products = rows.executeQuery("SELECT product_id, label_tags FROM product_open_food_facts");
             var insertLabel = connection.prepareStatement(
                     "INSERT INTO off_labels(canonical_tag, locale_key) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
             var insertLink = connection.prepareStatement(
                     "INSERT INTO product_off_labels(product_id, label_id) VALUES (?, ?)")) {
            int pending = 0;
            while (products.next()) {
                String csv = products.getString("label_tags");
                if (csv == null) continue;
                var tags = new LinkedHashSet<String>();
                for (String value : csv.split(",")) {
                    String tag = value.trim();
                    if (!tag.isEmpty()) tags.add(tag);
                }
                for (String tag : tags) {
                    Long id = labels.get(tag);
                    if (id == null) {
                        insertLabel.setString(1, tag);
                        int colon = tag.indexOf(':');
                        insertLabel.setString(2, colon > 0 && colon <= 35 ? tag.substring(0, colon) : "xx");
                        insertLabel.executeUpdate();
                        try (var keys = insertLabel.getGeneratedKeys()) {
                            if (!keys.next()) throw new IllegalStateException("Missing OFF label catalog ID");
                            id = keys.getLong(1);
                        }
                        labels.put(tag, id);
                    }
                    insertLink.setLong(1, products.getLong("product_id"));
                    insertLink.setLong(2, id);
                    insertLink.addBatch();
                    if (++pending == 100) {
                        insertLink.executeBatch();
                        pending = 0;
                    }
                }
            }
            if (pending > 0) insertLink.executeBatch();
        }
    }
}
