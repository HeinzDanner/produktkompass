package de.heinzdanner.produktkompass.cart;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.List;

@Service
public class CartService {
    public enum Action { INCREMENT, DECREMENT, REMOVE }
    public record Item(long productId, int quantity) {}
    public record CartRow(long id, String name, int quantity) {}

    private final CartRepository repository;

    public CartService(CartRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CartRow> items(String username) {
        return repository.items(username);
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> quantities(String username) {
        return repository.quantities(username);
    }

    @Transactional
    public Item change(String username, long productId, Action action) {
        // Serialize changes per owner, including the first insert for an empty cart.
        long userId = repository.lockOwner(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Benutzer nicht gefunden."));
        if (!repository.productExists(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nicht gefunden.");
        }
        var existing = repository.quantity(userId, productId);
        int quantity = existing.orElse(0);
        if (action == Action.INCREMENT && quantity == Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die maximal speicherbare Menge ist erreicht.");
        }
        int next = switch (action) {
            case INCREMENT -> quantity + 1;
            case DECREMENT -> Math.max(0, quantity - 1);
            case REMOVE -> 0;
        };
        if (next == 0) {
            repository.delete(userId, productId);
        } else if (existing.isEmpty()) {
            repository.insert(userId, productId, next);
        } else {
            repository.update(userId, productId, next);
        }
        return new Item(productId, next);
    }
}
