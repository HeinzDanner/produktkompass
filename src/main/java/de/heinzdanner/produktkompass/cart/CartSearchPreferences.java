package de.heinzdanner.produktkompass.cart;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class CartSearchPreferences {
    public record Selection(List<String> preferred, List<String> excluded) {}

    private final CartSearchPreferencesRepository repository;
    private final CartTagCatalog catalog;

    public CartSearchPreferences(CartSearchPreferencesRepository repository, CartTagCatalog catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public Selection load(String username) {
        var preferred = new ArrayList<String>();
        var excluded = new ArrayList<String>();
        for (var entry : repository.findByOwner(username)) {
            String key = entry.key();
            // Deleted personal tags no longer participate; their IDs are never reassigned.
            if (key.startsWith("personal:") && !repository.ownsPersonalTag(username,
                    Long.parseLong(key.substring(9)))) continue;
            (entry.state() == TagState.PREFERRED ? preferred : excluded).add(key);
        }
        return new Selection(preferred, excluded);
    }

    @Transactional
    public Selection save(String username, List<String> preferred, List<String> excluded) {
        var validated = catalog.resolve(username, preferred, excluded, false).selection();
        long id = repository.lockOwner(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Benutzer nicht gefunden."));
        repository.deleteByOwner(id);
        for (String key : validated.preferred()) repository.insert(id, key, TagState.PREFERRED);
        for (String key : validated.excluded()) repository.insert(id, key, TagState.EXCLUDED);
        return validated;
    }
}
