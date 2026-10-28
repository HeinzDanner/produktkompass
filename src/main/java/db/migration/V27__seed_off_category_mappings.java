package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.util.Map;
import java.util.List;

public class V27__seed_off_category_mappings extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Map<String, List<String>> mappings = Map.of(
                "Süßwaren und Schokolade", List.of("Chocolats noirs", "nl:Chocolade",
                        "en:chocolates", "en:dark-chocolates", "en:milk-chocolates", "en:white-chocolates",
                        "Chocolats", "Chocolats au lait", "de:schokoladen", "de:bitterschokoladen"),
                "Getränke", List.of("en:beverages", "en:carbonated-drinks", "en:colas",
                        "en:fruit-juices", "en:waters", "Boissons", "Bebidas de cola"),
                "Kaffee und Tee", List.of("en:coffees", "en:teas", "Cafés", "Thés"),
                "Brot und Backwaren", List.of("en:breads", "en:pastries", "Pains"),
                "Nudeln und Teigwaren", List.of("en:pastas", "en:durum-wheat-pasta", "Pâtes alimentaires"),
                "Obst und Gemüse", List.of("en:fruits", "en:vegetables", "Fruits", "Légumes"),
                "Reis und Getreideprodukte", List.of("en:rices", "en:breakfast-cereals", "Riz"),
                "Saucen und Würzmittel", List.of("en:sauces", "en:mustards", "en:ketchups",
                        "en:spices", "Sauces", "Moutardes"),
                "Tiefkühlprodukte", List.of("en:frozen-foods", "Surgelés"),
                "Milchprodukte und Milchalternativen", List.of("en:dairies", "en:milks", "en:cheeses",
                        "en:yogurts", "en:plant-based-milks", "en:oat-milks", "en:almond-milks",
                        "Almond-based drinks", "Produits laitiers"));
        try (var ensure = context.getConnection().prepareStatement(
                     "INSERT INTO categories(name) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = ?)");
             var category = context.getConnection().prepareStatement("SELECT id FROM categories WHERE name = ?");
             var insert = context.getConnection().prepareStatement(
                     "INSERT INTO off_category_mappings(off_value, category_id) VALUES (?, ?)")) {
            for (var mapping : mappings.entrySet()) {
                ensure.setString(1, mapping.getKey());
                ensure.setString(2, mapping.getKey());
                ensure.executeUpdate();
                category.setString(1, mapping.getKey());
                try (var rows = category.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("Missing category: " + mapping.getKey());
                    long id = rows.getLong(1);
                    for (String value : mapping.getValue()) {
                        insert.setString(1, value);
                        insert.setLong(2, id);
                        insert.executeUpdate();
                    }
                }
            }
        }
    }
}
