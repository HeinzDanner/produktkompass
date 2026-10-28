package de.heinzdanner.produktkompass.cart;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CartRepository {
    private final JdbcTemplate jdbc;

    public CartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CartService.CartRow> items(String username) {
        return jdbc.query("""
                SELECT p.id, p.name, c.quantity FROM cart_items c
                JOIN app_users u ON u.id = c.user_id JOIN products p ON p.id = c.product_id
                WHERE u.username = ? ORDER BY p.name, p.id
                """, (row, index) -> new CartService.CartRow(row.getLong("id"), row.getString("name"),
                row.getInt("quantity")), username);
    }

    public Map<Long, Integer> quantities(String username) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT c.product_id, c.quantity FROM cart_items c
                JOIN app_users u ON u.id = c.user_id WHERE u.username = ? ORDER BY c.product_id
                """, rows -> { result.put(rows.getLong("product_id"), rows.getInt("quantity")); }, username);
        return result;
    }

    public Optional<Long> lockOwner(String username) {
        return jdbc.queryForList("SELECT id FROM app_users WHERE username = ? FOR UPDATE", Long.class, username)
                .stream().findFirst();
    }

    public boolean productExists(long productId) {
        return jdbc.queryForObject("SELECT count(*) FROM products WHERE id = ?", Integer.class, productId) > 0;
    }

    public Optional<Integer> quantity(long userId, long productId) {
        return jdbc.queryForList("SELECT quantity FROM cart_items WHERE user_id = ? AND product_id = ?",
                Integer.class, userId, productId).stream().findFirst();
    }

    public void delete(long userId, long productId) {
        jdbc.update("DELETE FROM cart_items WHERE user_id = ? AND product_id = ?", userId, productId);
    }

    public void insert(long userId, long productId, int quantity) {
        jdbc.update("INSERT INTO cart_items(user_id, product_id, quantity) VALUES (?, ?, ?)", userId, productId, quantity);
    }

    public void update(long userId, long productId, int quantity) {
        jdbc.update("UPDATE cart_items SET quantity = ? WHERE user_id = ? AND product_id = ?", quantity, userId, productId);
    }
}
