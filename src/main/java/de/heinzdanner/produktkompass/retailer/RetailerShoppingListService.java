package de.heinzdanner.produktkompass.retailer;

import de.heinzdanner.produktkompass.cart.CartRepository;
import de.heinzdanner.produktkompass.cart.CartSearchPreferences;
import de.heinzdanner.produktkompass.cart.CartTagCatalog;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RetailerShoppingListService {
    public record CartLine(long sourceProductId, String sourceName, int quantity, Long cachedProductId,
                           List<RetailerProductMatcher.Suggestion> suggestions) {}
    public record Preview(RetailerCatalog catalog, List<CartLine> items) {}
    public record SavedList(RetailerCatalogRepository.RetailerShoppingList header,
                            List<RetailerCatalogRepository.RetailerShoppingListItem> items) {}

    private final CartRepository cart;
    private final CartSearchPreferences preferences;
    private final CartTagCatalog tagCatalog;
    private final RetailerCatalogRepository catalogs;
    private final RetailerProductMatcher matcher;
    private final ProductRepository products;

    public RetailerShoppingListService(CartRepository cart, CartSearchPreferences preferences,
                                       CartTagCatalog tagCatalog, RetailerCatalogRepository catalogs,
                                       RetailerProductMatcher matcher, ProductRepository products) {
        this.cart = cart;
        this.preferences = preferences;
        this.tagCatalog = tagCatalog;
        this.catalogs = catalogs;
        this.matcher = matcher;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public Preview preview(String username, String catalogCode) {
        RetailerCatalog catalog = catalogs.findCatalog(catalogCode).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Händlerkatalog nicht gefunden."));
        if (catalog.productCount() == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Der Händlerkatalog enthält keine Produkte.");
        }
        var quantities = cart.quantities(username);
        if (quantities.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Der Warenkorb ist leer.");
        }
        var preferenceSelection = preferences.load(username);
        var resolved = tagCatalog.resolve(username, preferenceSelection.preferred(),
                preferenceSelection.excluded(), false);
        List<RetailerProduct> retailerProducts = catalogs.findProducts(catalogCode);
        Map<Long, Long> cachedMatches = catalogs.findCachedMatches(username, catalogCode);
        var items = new ArrayList<CartLine>();
        for (var entry : quantities.entrySet()) {
            Product source = products.findByIdWithDetails(entry.getKey()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Warenkorbprodukt wurde nicht gefunden."));
            List<RetailerProductMatcher.Suggestion> suggestions = matcher.suggest(source, retailerProducts, resolved);
            Long storedProductId = cachedMatches.get(source.getId());
            Long cachedProductId = storedProductId;
            if (storedProductId != null && suggestions.stream()
                    .noneMatch(suggestion -> suggestion.product().id() == storedProductId)) {
                cachedProductId = null;
            }
            items.add(new CartLine(source.getId(), source.getName(), entry.getValue(), cachedProductId, suggestions));
        }
        return new Preview(catalog, List.copyOf(items));
    }

    @Transactional
    public long create(String username, String catalogCode, String name, Map<Long, String> selections) {
        if (name == null || name.isBlank() || name.strip().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Listenname muss 1 bis 120 Zeichen lang sein.");
        }
        var quantities = cart.quantities(username);
        if (quantities.isEmpty() || !quantities.keySet().equals(selections.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Auswahl passt nicht zum aktuellen Warenkorb.");
        }
        if (catalogs.findCatalog(catalogCode).filter(catalog -> catalog.productCount() > 0).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Händlerkatalog nicht gefunden.");
        }
        List<RetailerProduct> retailerProducts = catalogs.findProducts(catalogCode);
        var byId = retailerProducts.stream().collect(java.util.stream.Collectors.toMap(RetailerProduct::id, item -> item));
        var savedPreferences = preferences.load(username);
        var resolved = tagCatalog.resolve(username, savedPreferences.preferred(),
                savedPreferences.excluded(), false);
        var sourceNames = new java.util.LinkedHashMap<Long, String>();
        for (Long sourceId : selections.keySet()) {
            Product source = products.findByIdWithDetails(sourceId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Warenkorbprodukt wurde nicht gefunden."));
            sourceNames.put(sourceId, source.getName());
            String selected = selections.get(sourceId);
            if (!selected.equals("manual")) {
                try {
                    long selectedId = Long.parseLong(selected);
                    boolean isSuggestion = matcher.suggest(source, retailerProducts, resolved).stream()
                            .anyMatch(suggestion -> suggestion.product().id() == selectedId);
                    if (!byId.containsKey(selectedId) || !isSuggestion) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException exception) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Ein Ersatzartikel gehört nicht zum gewählten Katalog.", exception);
                }
            }
        }
        long id = catalogs.createList(username, catalogCode, name.strip(), LocalDateTime.now());
        for (var selection : selections.entrySet()) {
            String selected = selection.getValue();
            if (selected.equals("manual")) {
                catalogs.clearCachedMatch(username, selection.getKey(), catalogCode);
                catalogs.addListItem(id, selection.getKey(), null, sourceNames.get(selection.getKey()),
                        sourceNames.get(selection.getKey()), null, null, "MANUAL", null, null,
                        quantities.get(selection.getKey()));
            } else {
                long retailerProductId = Long.parseLong(selected);
                var product = byId.get(retailerProductId);
                catalogs.addListItem(id, selection.getKey(), retailerProductId, sourceNames.get(selection.getKey()),
                        product.name(), product.brand(), product.packageSize(), "CATALOG",
                        product.price(), product.productUrl(), quantities.get(selection.getKey()));
                catalogs.cacheMatch(username, selection.getKey(), catalogCode, retailerProductId);
            }
        }
        return id;
    }

    @Transactional(readOnly = true)
    public SavedList get(String username, long id) {
        var header = catalogs.findList(username, id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Einkaufsliste nicht gefunden."));
        return new SavedList(header, catalogs.findListItems(id));
    }

    @Transactional(readOnly = true)
    public List<RetailerCatalogRepository.RetailerShoppingListSummary> lists(String username) {
        return catalogs.findLists(username);
    }
}
