package de.heinzdanner.produktkompass.cart;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class CartSearchPreferences {
    public record Selection(List<String> preferred, List<String> excluded) {}

    private final JdbcTemplate jdbc;

    public CartSearchPreferences(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Selection load(String username) {
        var preferred = new ArrayList<String>();
        var excluded = new ArrayList<String>();
        jdbc.query("""
                SELECT p.tag_key, p.tag_state FROM cart_search_preferences p
                JOIN app_users u ON u.id = p.user_id
                WHERE u.username = ? ORDER BY p.tag_key
                """, row -> {
            String key = row.getString("tag_key");
            // Deleted personal tags no longer participate; their IDs are never reassigned.
            if (key.startsWith("personal:") && !Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT COUNT(*) > 0 FROM user_tags t JOIN app_users u ON u.id = t.user_id
                    WHERE u.username = ? AND t.id = ?
                    """, Boolean.class, username, Long.parseLong(key.substring(9))))) return;
            (row.getString("tag_state").equals("preferred") ? preferred : excluded).add(key);
        }, username);
        return new Selection(preferred, excluded);
    }

    @Transactional
    public void save(String username, List<String> preferred, List<String> excluded) {
        var owners = jdbc.queryForList("SELECT id FROM app_users WHERE username = ? FOR UPDATE", Long.class, username);
        if (owners.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Benutzer nicht gefunden.");
        long id = owners.getFirst();
        jdbc.update("DELETE FROM cart_search_preferences WHERE user_id = ?", id);
        for (String key : preferred) insert(id, key, "preferred");
        for (String key : excluded) insert(id, key, "excluded");
    }

    private void insert(long userId, String key, String state) {
        jdbc.update("INSERT INTO cart_search_preferences(user_id, tag_key, tag_state) VALUES (?, ?, ?)",
                userId, key, state);
    }
}
