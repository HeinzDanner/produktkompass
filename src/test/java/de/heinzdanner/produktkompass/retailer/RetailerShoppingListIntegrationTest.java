package de.heinzdanner.produktkompass.retailer;

import de.heinzdanner.produktkompass.cart.CartService;
import de.heinzdanner.produktkompass.cart.CartSearchPreferences;
import de.heinzdanner.produktkompass.product.Category;
import de.heinzdanner.produktkompass.product.CategoryRepository;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.product.ProductLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class RetailerShoppingListIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private CartService cart;
    @Autowired private CartSearchPreferences preferences;
    @Autowired private RetailerShoppingListService shoppingLists;
    @Autowired private RetailerCatalogRepository catalogs;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mvc;
    private Product source;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Category category = categories.save(new Category("Pflanzliche Drinks", null));
        source = new Product("Haferdrink Barista", null);
        source.addCategory(category);
        source.setLabels(Set.of(ProductLabel.BIO));
        products.saveAndFlush(source);
        cart.change("max", source.getId(), CartService.Action.INCREMENT);
        cart.change("max", source.getId(), CartService.Action.INCREMENT);
        preferences.save("max", java.util.List.of("label:BIO"), java.util.List.of());
    }

    @Test
    void sourceLabelsRankAndRenderWithoutPreferencesAndDoNotBecomeHardRequirements() throws Exception {
        preferences.save("max", java.util.List.of(), java.util.List.of());
        source.setLabels(new java.util.HashSet<>(Set.of(ProductLabel.VEGAN, ProductLabel.BIO)));
        products.saveAndFlush(source);
        var result = mvc.perform(get("/cart/retailer-list").with(user("max")).param("catalogCode", "DEMO"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Passende Ausgangslabels laut Händlerdaten")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ausgangslabels nicht bestätigt: Bio (Katalog)")))
                .andReturn();
        var preview = (RetailerShoppingListService.Preview) result.getModelAndView().getModel().get("preview");
        var suggestions = preview.items().getFirst().suggestions();
        assertThat(suggestions.getFirst().product().externalId()).isEqualTo("demo-oat-drink");
        assertThat(suggestions.getFirst().matchedPreferences()).isEmpty();
        assertThat(suggestions.getFirst().matchedSourceLabels()).containsExactly("Vegan (Katalog)", "Bio (Katalog)");
        var partial = suggestions.stream().filter(item -> item.product().externalId().equals("demo-oat-barista"))
                .findFirst().orElseThrow();
        mvc.perform(post("/cart/retailer-list").with(user("max")).with(csrf()).param("catalogCode", "DEMO")
                        .param("name", "Teilweise passende Labels")
                        .param("selection_" + source.getId(), Long.toString(partial.product().id())))
                .andExpect(status().is3xxRedirection());
        assertThat(catalogs.findProduct("DEMO", partial.product().id()).orElseThrow().preferenceTags())
                .isEqualTo("vegan");
        assertThat(source.getLabels()).containsExactlyInAnyOrder(ProductLabel.VEGAN, ProductLabel.BIO);
    }

    @Test
    void suggestsFromSyntheticCatalogPersistsConfirmedChoiceAndKeepsOriginalCart() throws Exception {
        var previewResult = mvc.perform(get("/cart/retailer-list").with(user("max"))
                        .param("catalogCode", "DEMO"))
                .andExpect(status().isOk()).andExpect(view().name("retailer-list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Fehlende Zutaten-, Allergen-")))
                .andReturn();
        var preview = (RetailerShoppingListService.Preview) previewResult.getModelAndView().getModel().get("preview");
        assertThat(preview.catalog().sourceKind()).isEqualTo("SYNTHETIC");
        assertThat(preview.items()).singleElement().satisfies(line -> {
            assertThat(line.sourceProductId()).isEqualTo(source.getId());
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.suggestions()).extracting(item -> item.product().externalId())
                    .contains("demo-oat-drink", "demo-oat-barista");
            assertThat(line.suggestions().getFirst().matchedPreferences()).contains("Bio");
        });
        long retailerProductId = jdbc.queryForObject(
                "SELECT id FROM retailer_products WHERE catalog_code = 'DEMO' AND external_id = 'demo-oat-drink'",
                Long.class);

        var response = mvc.perform(post("/cart/retailer-list").with(user("max")).with(csrf())
                        .param("catalogCode", "DEMO").param("name", "Meine Demo-Liste")
                        .param("selection_" + source.getId(), String.valueOf(retailerProductId)))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = response.getResponse().getRedirectedUrl();
        assertThat(location).matches("/cart/retailer-lists/\\d+");
        long listId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        var saved = shoppingLists.get("max", listId);
        assertThat(saved.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("Haferdrink Natur");
            assertThat(item.sourceName()).isEqualTo("Haferdrink Barista");
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.selectionKind()).isEqualTo("CATALOG");
        });
        assertThat(catalogs.findCachedMatches("max", "DEMO")).containsEntry(source.getId(), retailerProductId);
        assertThat(shoppingLists.preview("max", "DEMO").items().getFirst().cachedProductId())
                .isEqualTo(retailerProductId);
        assertThat(cart.quantities("max")).containsEntry(source.getId(), 2);

        mvc.perform(get(location).with(user("max"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Haferdrink Natur")));
        mvc.perform(get(location + "/export.csv").with(user("max")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Haferdrink Natur")));
        mvc.perform(get(location).with(user("admin"))).andExpect(status().isNotFound());
    }

    @Test
    void everyCartItemMustBeCoveredAndManualLinesAreExplicit() throws Exception {
        Product unmatched = products.saveAndFlush(new Product("Wunderzeug ohne Katalogtreffer", null));
        cart.change("max", unmatched.getId(), CartService.Action.INCREMENT);
        long retailerProductId = jdbc.queryForObject(
                "SELECT id FROM retailer_products WHERE catalog_code = 'DEMO' AND external_id = 'demo-oat-drink'",
                Long.class);
        mvc.perform(post("/cart/retailer-list").with(user("max")).with(csrf())
                        .param("catalogCode", "DEMO").param("name", "Unvollständige Liste")
                        .param("selection_" + source.getId(), String.valueOf(retailerProductId)))
                .andExpect(status().isBadRequest());

        var response = mvc.perform(post("/cart/retailer-list").with(user("max")).with(csrf())
                        .param("catalogCode", "DEMO").param("name", "Manuelle Liste")
                        .param("selection_" + source.getId(), String.valueOf(retailerProductId))
                        .param("selection_" + unmatched.getId(), "manual"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = response.getResponse().getRedirectedUrl();
        mvc.perform(get(location).with(user("max"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Manuell prüfen – kein Katalogtreffer")));
        assertThat(catalogs.findCachedMatches("max", "DEMO")).containsEntry(source.getId(), retailerProductId);
        mvc.perform(post("/cart/retailer-list").with(user("max")).with(csrf())
                        .param("catalogCode", "DEMO").param("name", "Manuelle Auswahl")
                        .param("selection_" + source.getId(), "manual")
                        .param("selection_" + unmatched.getId(), "manual"))
                .andExpect(status().is3xxRedirection());
        assertThat(catalogs.findCachedMatches("max", "DEMO")).doesNotContainKey(source.getId());

        preferences.save("max", java.util.List.of(), java.util.List.of("label:BIO"));
        mvc.perform(post("/cart/retailer-list").with(user("max")).with(csrf())
                        .param("catalogCode", "DEMO").param("name", "Ausgeschlossene Auswahl")
                        .param("selection_" + source.getId(), String.valueOf(retailerProductId))
                        .param("selection_" + unmatched.getId(), "manual"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanReplaceCatalogSnapshotWithNormalizedCsvAndInvalidImportPreservesOldSnapshot() throws Exception {
        byte[] validCsv = ("external_id;name;brand;category_path;package_size;price;product_url;preference_tags\r\n"
                + "lid-1;Haferdrink Barista;=HYPERLINK(\"\"https://example.com\"\");Getränke;1 l;1,29;"
                + "https://shop.example/product;vegan\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(multipart("/admin/retailer-catalog/import")
                        .file(new MockMultipartFile("file", "catalog.csv", "text/csv", validCsv))
                        .param("code", "LIDL").param("displayName", "Lidl Snapshot")
                        .with(user("editor").roles("EDITOR")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/retailer-catalog"));
        assertThat(catalogs.findCatalog("LIDL").orElseThrow().productCount()).isEqualTo(1);
        mvc.perform(get("/admin/retailer-catalog").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Lidl Snapshot")));
        mvc.perform(get("/admin/retailer-catalog/template.csv").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("external_id;name")));
        long importedProductId = jdbc.queryForObject(
                "SELECT id FROM retailer_products WHERE catalog_code = 'LIDL' AND external_id = 'lid-1'",
                Long.class);
        var listResponse = mvc.perform(post("/cart/retailer-list").with(user("max")).with(csrf())
                        .param("catalogCode", "LIDL").param("name", "Lidl Testliste")
                        .param("selection_" + source.getId(), String.valueOf(importedProductId)))
                .andExpect(status().is3xxRedirection()).andReturn();
        String listUrl = listResponse.getResponse().getRedirectedUrl();
        mvc.perform(get(listUrl + "/export.csv").with(user("max")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("'=HYPERLINK")));

        byte[] invalidCsv = ("external_id;name;product_url\r\nlid-2;Ungültige URL;ftp://shop.example/product\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(multipart("/admin/retailer-catalog/import")
                        .file(new MockMultipartFile("file", "invalid.csv", "text/csv", invalidCsv))
                        .param("code", "LIDL").param("displayName", "Lidl Snapshot")
                        .with(user("editor").roles("EDITOR")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("adminError"));
        assertThat(catalogs.findCatalog("LIDL").orElseThrow().productCount()).isEqualTo(1);
        mvc.perform(get("/admin/retailer-catalog").with(user("max").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
