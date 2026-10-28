package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.util.LinkedHashSet;

public class V25__backfill_off_categories extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT product_id, categories, category_tags FROM product_open_food_facts");
             var insert = connection.prepareStatement(
                     "INSERT INTO product_off_categories(product_id, category_value) VALUES (?, ?)")) {
            int pending = 0;
            while (rows.next()) {
                var categories = new LinkedHashSet<String>();
                for (String column : new String[]{"categories", "category_tags"}) {
                    String csv = rows.getString(column);
                    if (csv == null) continue;
                    for (String entry : csv.split(",")) {
                        String value = entry.trim();
                        if (!value.isEmpty()) categories.add(value);
                    }
                }
                for (String value : categories) {
                    insert.setLong(1, rows.getLong("product_id"));
                    insert.setString(2, value);
                    insert.addBatch();
                    if (++pending == 100) {
                        insert.executeBatch();
                        pending = 0;
                    }
                }
            }
            if (pending > 0) insert.executeBatch();
        }
    }
}
