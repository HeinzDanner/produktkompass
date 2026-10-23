package de.heinzdanner.produktkompass.cart;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@Service
public class CartService {
    public enum Action { INCREMENT, DECREMENT, REMOVE }
    public record Item(long productId, int quantity) {}
    public record CartRow(long id, String name, int quantity) {}

    private final JdbcTemplate jdbc;

    public CartService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<CartRow> items(String username) {
        return jdbc.query("""
                SELECT p.id, p.name, c.quantity FROM cart_items c
                JOIN app_users u ON u.id = c.user_id JOIN products p ON p.id = c.product_id
                WHERE u.username = ? ORDER BY p.name, p.id
                """, (row, index) -> new CartRow(row.getLong("id"), row.getString("name"), row.getInt("quantity")),
                username);
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> quantities(String username) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT c.product_id, c.quantity FROM cart_items c
                JOIN app_users u ON u.id = c.user_id WHERE u.username = ? ORDER BY c.product_id
                """, rows -> { result.put(rows.getLong("product_id"), rows.getInt("quantity")); }, username);
        return result;
    }

    @Transactional
    public Item change(String username, long productId, Action action) {
        // Serialize changes per owner, including the first insert for an empty cart.
        var users = jdbc.queryForList("SELECT id FROM app_users WHERE username = ? FOR UPDATE", Long.class, username);
        if (users.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Benutzer nicht gefunden.");
        long userId = users.getFirst();
        if (jdbc.queryForObject("SELECT count(*) FROM products WHERE id = ?", Integer.class, productId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nicht gefunden.");
        }
        var existing = jdbc.queryForList(
                "SELECT quantity FROM cart_items WHERE user_id = ? AND product_id = ?", Integer.class, userId, productId);
        int quantity = existing.isEmpty() ? 0 : existing.getFirst();
        if (action == Action.INCREMENT && quantity == Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die maximal speicherbare Menge ist erreicht.");
        }
        int next = switch (action) {
            case INCREMENT -> quantity + 1;
            case DECREMENT -> Math.max(0, quantity - 1);
            case REMOVE -> 0;
        };
        if (next == 0) {
            jdbc.update("DELETE FROM cart_items WHERE user_id = ? AND product_id = ?", userId, productId);
        } else if (existing.isEmpty()) {
            jdbc.update("INSERT INTO cart_items(user_id, product_id, quantity) VALUES (?, ?, ?)", userId, productId, next);
        } else {
            jdbc.update("UPDATE cart_items SET quantity = ? WHERE user_id = ? AND product_id = ?", next, userId, productId);
        }
        return new Item(productId, next);
    }
}
