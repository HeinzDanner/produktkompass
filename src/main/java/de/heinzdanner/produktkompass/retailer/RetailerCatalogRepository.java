package de.heinzdanner.produktkompass.retailer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.LinkedHashMap;

@Repository
public class RetailerCatalogRepository {
    private final JdbcTemplate jdbc;

    public RetailerCatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RetailerCatalog> findAll() {
        return jdbc.query("""
                SELECT c.code, c.display_name, c.source_kind, c.imported_at, COUNT(p.id) AS product_count
                FROM retailer_catalogs c LEFT JOIN retailer_products p ON p.catalog_code = c.code
                GROUP BY c.code, c.display_name, c.source_kind, c.imported_at
                ORDER BY c.display_name, c.code
                """, (row, index) -> new RetailerCatalog(row.getString("code"), row.getString("display_name"),
                row.getString("source_kind"), row.getTimestamp("imported_at") == null ? null
                : row.getTimestamp("imported_at").toLocalDateTime(), row.getLong("product_count")));
    }

    public Optional<RetailerCatalog> findCatalog(String code) {
        return jdbc.query("""
                SELECT c.code, c.display_name, c.source_kind, c.imported_at, COUNT(p.id) AS product_count
                FROM retailer_catalogs c LEFT JOIN retailer_products p ON p.catalog_code = c.code
                WHERE c.code = ? GROUP BY c.code, c.display_name, c.source_kind, c.imported_at
                """, (row, index) -> new RetailerCatalog(row.getString("code"), row.getString("display_name"),
                row.getString("source_kind"), row.getTimestamp("imported_at") == null ? null
                : row.getTimestamp("imported_at").toLocalDateTime(), row.getLong("product_count")), code)
                .stream().findFirst();
    }

    public List<RetailerProduct> findProducts(String code) {
        return jdbc.query("""
                SELECT id, external_id, name, brand, category_path, package_size, price, previous_price,
                       discount_percent, product_url, image_url, description, preference_tags,
                       allergens, traces, availability
                FROM retailer_products WHERE catalog_code = ? ORDER BY id
                """, (row, index) -> new RetailerProduct(row.getLong("id"), row.getString("external_id"),
                row.getString("name"), row.getString("brand"), row.getString("category_path"),
                row.getString("package_size"), row.getBigDecimal("price"), row.getBigDecimal("previous_price"),
                row.getBigDecimal("discount_percent"), row.getString("product_url"), row.getString("image_url"),
                row.getString("description"), row.getString("preference_tags"), row.getString("allergens"),
                row.getString("traces"), row.getString("availability")), code);
    }

    public Optional<RetailerProduct> findProduct(String catalogCode, long productId) {
        return jdbc.query("""
                SELECT id, external_id, name, brand, category_path, package_size, price, previous_price,
                       discount_percent, product_url, image_url, description, preference_tags,
                       allergens, traces, availability
                FROM retailer_products WHERE catalog_code = ? AND id = ?
                """, (row, index) -> new RetailerProduct(row.getLong("id"), row.getString("external_id"),
                row.getString("name"), row.getString("brand"), row.getString("category_path"),
                row.getString("package_size"), row.getBigDecimal("price"), row.getBigDecimal("previous_price"),
                row.getBigDecimal("discount_percent"), row.getString("product_url"), row.getString("image_url"),
                row.getString("description"), row.getString("preference_tags"), row.getString("allergens"),
                row.getString("traces"), row.getString("availability")), catalogCode, productId)
                .stream().findFirst();
    }

    public Map<Long, Long> findCachedMatches(String username, String catalogCode) {
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT cache.source_product_id, cache.retailer_product_id
                FROM retailer_product_match_cache cache
                JOIN app_users owner ON owner.id = cache.owner_id
                WHERE owner.username = ? AND cache.catalog_code = ? AND cache.retailer_product_id IS NOT NULL
                """, (RowCallbackHandler) row -> result.put(row.getLong("source_product_id"),
                row.getLong("retailer_product_id")),
                username, catalogCode);
        return result;
    }

    public void replaceSnapshot(String code, String displayName, LocalDateTime importedAt,
                                List<RetailerProductImportRow> products) {
        int updated = jdbc.update("""
                UPDATE retailer_catalogs SET display_name = ?, source_kind = 'USER_SUPPLIED', imported_at = ?
                WHERE code = ?
                """, displayName, Timestamp.valueOf(importedAt), code);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO retailer_catalogs(code, display_name, source_kind, imported_at)
                    VALUES (?, ?, 'USER_SUPPLIED', ?)
                    """, code, displayName, Timestamp.valueOf(importedAt));
        }
        jdbc.update("DELETE FROM retailer_products WHERE catalog_code = ?", code);
        jdbc.batchUpdate("""
                INSERT INTO retailer_products(catalog_code, external_id, name, brand, category_path,
                    package_size, price, previous_price, discount_percent, product_url, image_url,
                    description, preference_tags, allergens, traces, availability, imported_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, products, 250, (PreparedStatement statement, RetailerProductImportRow product) -> {
            statement.setString(1, code);
            statement.setString(2, product.externalId());
            statement.setString(3, product.name());
            statement.setString(4, product.brand());
            statement.setString(5, product.categoryPath());
            statement.setString(6, product.packageSize());
            statement.setBigDecimal(7, product.price());
            statement.setBigDecimal(8, product.previousPrice());
            statement.setBigDecimal(9, product.discountPercent());
            statement.setString(10, product.productUrl());
            statement.setString(11, product.imageUrl());
            statement.setString(12, product.description());
            statement.setString(13, product.preferenceTags());
            statement.setString(14, product.allergens());
            statement.setString(15, product.traces());
            statement.setString(16, product.availability());
            statement.setTimestamp(17, Timestamp.valueOf(importedAt));
        });
    }

    public long createList(String username, String catalogCode, String name, LocalDateTime createdAt) {
        KeyHolder key = new GeneratedKeyHolder();
        int inserted = jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO retailer_shopping_lists(owner_id, catalog_code, name, created_at)
                    SELECT id, ?, ?, ? FROM app_users WHERE username = ?
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, catalogCode);
            statement.setString(2, name);
            statement.setTimestamp(3, Timestamp.valueOf(createdAt));
            statement.setString(4, username);
            return statement;
        }, key);
        if (inserted != 1 || key.getKey() == null) {
            throw new IllegalStateException("Einkaufsliste konnte nicht gespeichert werden.");
        }
        return key.getKey().longValue();
    }

    public void addListItem(long listId, Long sourceProductId, Long retailerProductId, String sourceName,
                            String productName, String brand, String packageSize, String selectionKind,
                            java.math.BigDecimal price, String productUrl, int quantity) {
        jdbc.update("""
                INSERT INTO retailer_shopping_list_items(list_id, source_product_id, retailer_product_id,
                    source_product_name, retailer_product_name, retailer_brand, package_size, selection_kind,
                    quantity, unit_price, product_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, listId, sourceProductId, retailerProductId, sourceName, productName, brand,
                packageSize, selectionKind, quantity, price, productUrl);
    }

    public void cacheMatch(String username, Long sourceProductId, String catalogCode, Long retailerProductId) {
        clearCachedMatch(username, sourceProductId, catalogCode);
        jdbc.update("""
                INSERT INTO retailer_product_match_cache(owner_id, source_product_id, catalog_code,
                    retailer_product_id, updated_at)
                SELECT owner.id, ?, ?, ?, CURRENT_TIMESTAMP FROM app_users owner WHERE owner.username = ?
                """, sourceProductId, catalogCode, retailerProductId, username);
    }

    public void clearCachedMatch(String username, Long sourceProductId, String catalogCode) {
        jdbc.update("""
                DELETE FROM retailer_product_match_cache cache
                WHERE cache.owner_id = (SELECT id FROM app_users WHERE username = ?)
                  AND cache.source_product_id = ? AND cache.catalog_code = ?
                """, username, sourceProductId, catalogCode);
    }

    public Optional<RetailerShoppingList> findList(String username, long id) {
        return jdbc.query("""
                SELECT l.id, l.catalog_code, c.display_name, l.name, l.created_at
                FROM retailer_shopping_lists l
                JOIN retailer_catalogs c ON c.code = l.catalog_code
                JOIN app_users u ON u.id = l.owner_id
                WHERE l.id = ? AND u.username = ?
                """, (row, index) -> new RetailerShoppingList(row.getLong("id"), row.getString("catalog_code"),
                row.getString("display_name"), row.getString("name"),
                row.getTimestamp("created_at").toLocalDateTime()), id, username).stream().findFirst();
    }

    public List<RetailerShoppingListItem> findListItems(long id) {
        return jdbc.query("""
                SELECT source_product_name, retailer_product_name, retailer_brand, package_size,
                       selection_kind, quantity,
                       unit_price, product_url
                FROM retailer_shopping_list_items WHERE list_id = ? ORDER BY id
                """, (row, index) -> new RetailerShoppingListItem(row.getString("source_product_name"),
                row.getString("retailer_product_name"), row.getString("retailer_brand"),
                row.getString("package_size"), row.getString("selection_kind"), row.getInt("quantity"),
                row.getBigDecimal("unit_price"), row.getString("product_url")), id);
    }

    public List<RetailerShoppingListSummary> findLists(String username) {
        return jdbc.query("""
                SELECT l.id, l.name, l.created_at, c.display_name, COUNT(i.id) AS item_count
                FROM retailer_shopping_lists l
                JOIN retailer_catalogs c ON c.code = l.catalog_code
                JOIN app_users u ON u.id = l.owner_id
                LEFT JOIN retailer_shopping_list_items i ON i.list_id = l.id
                WHERE u.username = ?
                GROUP BY l.id, l.name, l.created_at, c.display_name
                ORDER BY l.created_at DESC, l.id DESC
                """, (row, index) -> new RetailerShoppingListSummary(row.getLong("id"), row.getString("name"),
                row.getString("display_name"), row.getTimestamp("created_at").toLocalDateTime(),
                row.getLong("item_count")), username);
    }

    public record RetailerShoppingList(long id, String catalogCode, String catalogName,
                                       String name, LocalDateTime createdAt) {}
    public record RetailerShoppingListItem(String sourceName, String productName, String brand,
                                           String packageSize, String selectionKind, int quantity, java.math.BigDecimal unitPrice,
                                           String productUrl) {}
    public record RetailerShoppingListSummary(long id, String name, String catalogName,
                                               LocalDateTime createdAt, long itemCount) {}
}
