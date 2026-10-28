package de.heinzdanner.produktkompass.cart;

import de.heinzdanner.produktkompass.product.ProductService;
import de.heinzdanner.produktkompass.product.dto.ProductSummary;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CartSearchService {
    public record Results(Page<ProductSummary> page, List<CartTagCatalog.SelectedTag> tags,
                          List<String> preferred, List<String> excluded) {}

    private final ProductService products;
    private final CartTagCatalog catalog;

    public CartSearchService(ProductService products, CartTagCatalog catalog) {
        this.products = products;
        this.catalog = catalog;
    }

    public Results search(String username, String text, List<String> preferred, List<String> excluded, int page) {
        return search(username, text, preferred, excluded, page, true);
    }

    public Results search(String username, String text, List<String> preferred, List<String> excluded, int page,
                          boolean includeTagOptions) {
        var tags = catalog.resolve(username, preferred, excluded, includeTagOptions);
        var results = products.searchRankedPage(CartSearchSpecifications.matching(username, text, tags), page, 12);
        return new Results(results, tags.options(), tags.selection().preferred(), tags.selection().excluded());
    }
}
