package de.heinzdanner.produktkompass.cart;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CartSearchPreferencesRepository {
    public record Entry(String key, TagState state) {}

    private final JdbcTemplate jdbc;

    public CartSearchPreferencesRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Entry> findByOwner(String username) {
        return jdbc.query("""
                SELECT p.tag_key, p.tag_state FROM cart_search_preferences p
                JOIN app_users u ON u.id = p.user_id
                WHERE u.username = ? ORDER BY p.tag_key
                """, (row, index) -> new Entry(row.getString("tag_key"),
                TagState.fromValue(row.getString("tag_state"))), username);
    }

    public boolean ownsPersonalTag(String username, long tagId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT COUNT(*) > 0 FROM user_tags t JOIN app_users u ON u.id = t.user_id
                WHERE u.username = ? AND t.id = ?
                """, Boolean.class, username, tagId));
    }

    public Optional<Long> lockOwner(String username) {
        return jdbc.queryForList("SELECT id FROM app_users WHERE username = ? FOR UPDATE", Long.class, username)
                .stream().findFirst();
    }

    public void deleteByOwner(long userId) {
        jdbc.update("DELETE FROM cart_search_preferences WHERE user_id = ?", userId);
    }

    public void insert(long userId, String key, TagState state) {
        jdbc.update("INSERT INTO cart_search_preferences(user_id, tag_key, tag_state) VALUES (?, ?, ?)",
                userId, key, state.value());
    }
}
