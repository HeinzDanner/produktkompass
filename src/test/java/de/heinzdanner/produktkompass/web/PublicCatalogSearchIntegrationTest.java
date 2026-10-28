package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.product.Category;
import de.heinzdanner.produktkompass.product.CategoryRepository;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.product.dto.ProductSummary;
import de.heinzdanner.produktkompass.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@Transactional
class PublicCatalogSearchIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private AppUserRepository users;
    private MockMvc mvc;
    private Category category;
    private Product first;
    private Product second;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        category = categories.save(new Category("Public catalog test category", null));
        first = save("PublicCatalog Alpha", category);
        second = save("PublicCatalog Beta", category);
        save("PublicCatalog Wrong category", categories.save(new Category("Other public category", null)));
        save("Unrelated product", category);
        products.flush();
    }

    @Test
    void anonymousSearchCombinesTextAndCategoryAndPreservesThemOnNextPage() throws Exception {
        var result = mvc.perform(get("/products").param("query", "PublicCatalog")
                        .param("categoryId", category.getId().toString()).param("size", "1"))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(result).getTotalElements()).isEqualTo(2);
        assertThat(page(result).getContent()).extracting(ProductSummary::id).containsExactly(first.getId());
        String html = result.getResponse().getContentAsString().replaceAll("\\s+", " ");
        assertThat(html).contains("aria-label=\"Katalog durchsuchen\"", "method=\"get\"", "action=\"/products\"",
                "value=\"PublicCatalog\"", "value=\"" + category.getId() + "\" selected=\"selected\"",
                "query=PublicCatalog", "categoryId=" + category.getId(), "page=1", "size=1",
                "class=\"product-search catalog-search\"");
        String form = html.substring(html.indexOf("<form method=\"get\""), html.indexOf("</form>"));
        assertThat(form).doesNotContain("name=\"page\"", "<select id=\"brandId\"", "name=\"preferred\"",
                "Zurücksetzen", "data-lookup-url");
        assertThat(html).doesNotContain("lookup-picker.js", "Suche ohne Anmeldung.", "Kategorien suchen");

        var next = mvc.perform(get("/products").param("query", "PublicCatalog")
                        .param("categoryId", category.getId().toString()).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(next).getContent()).extracting(ProductSummary::id).containsExactly(second.getId());
        assertThat(next.getResponse().getContentAsString()).contains("value=\"PublicCatalog\"", "page=0");
    }

    @Test
    void anonymousEmptySearchKeepsInputsAndCategoryLookupRemainsPublic() throws Exception {
        var result = mvc.perform(get("/products").param("query", "NoPublicCatalogMatch")
                        .param("categoryId", category.getId().toString()))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(result)).isEmpty();
        assertThat(result.getResponse().getContentAsString().replaceAll("\\s+", " "))
                .contains("Keine Produkte gefunden.", "value=\"NoPublicCatalogMatch\"",
                        "value=\"" + category.getId() + "\" selected=\"selected\"");
        mvc.perform(get("/api/v1/categories").param("query", category.getName()))
                .andExpect(status().isOk());
    }

    @Test
    void webAndApiRequireAllWordsAcrossFieldsWhileKeepingCategoryFilter() throws Exception {
        second.setLabels(java.util.Set.of(ProductLabel.BIO));
        Product wrongCategory = save("PublicCatalog Beta Extra",
                categories.save(new Category("Multiword other category", null)));
        wrongCategory.setLabels(java.util.Set.of(ProductLabel.BIO));
        products.flush();
        String text = "  BIO \t Beta PublicCatalog ";
        var result = mvc.perform(get("/products").param("query", text)
                        .param("categoryId", category.getId().toString()).param("size", "1"))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(result).getTotalElements()).isEqualTo(1);
        assertThat(page(result).getContent()).extracting(ProductSummary::id).containsExactly(second.getId());
        mvc.perform(get("/api/v1/products/search").param("name", text)
                        .param("categoryId", category.getId().toString()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second.getId()));
        var missing = mvc.perform(get("/products").param("query", "BIO Beta MissingSearchWord"))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(missing)).isEmpty();
    }

    @Test
    void authenticatedCatalogPromotesOwnMatchingFavoritesBeforePagination() throws Exception {
        var owner = users.findByUsername("max").orElseThrow();
        owner.getFavoriteProducts().add(second);
        users.saveAndFlush(owner);
        var result = mvc.perform(get("/products").with(user("max"))
                        .param("query", "PublicCatalog").param("categoryId", category.getId().toString())
                        .param("size", "1"))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(result).getContent()).extracting(ProductSummary::id).containsExactly(second.getId());
        var constrained = mvc.perform(get("/products").with(user("max"))
                        .param("query", "PublicCatalog Alpha").param("categoryId", category.getId().toString()))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(constrained).getContent()).extracting(ProductSummary::id).containsExactly(first.getId());
    }

    private Product save(String name, Category assignedCategory) {
        Product product = new Product(name, null);
        product.addCategory(assignedCategory);
        return products.save(product);
    }

    @SuppressWarnings("unchecked")
    private Page<ProductSummary> page(MvcResult result) {
        return (Page<ProductSummary>) result.getModelAndView().getModel().get("productPage");
    }
}
