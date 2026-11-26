package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.product.ProductService;
import de.heinzdanner.produktkompass.product.Source;
import de.heinzdanner.produktkompass.product.SourceRepository;
import de.heinzdanner.produktkompass.product.OpenFoodFactsData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true")
@Transactional
class AdminProductPaginationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ProductRepository products;
    @Autowired private SourceRepository sources;
    @MockitoSpyBean private ProductService service;

    private MockMvc mvc;
    private List<Product> fixtures;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        fixtures = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            fixtures.add(products.save(new Product("000 admin page %02d".formatted(i), "Test")));
        }
        products.flush();
        clearInvocations(service);
    }

    @Test
    void productEditorEntryIsAvailableToAdminsAndEditors() throws Exception {
        for (String role : List.of("ADMIN", "EDITOR")) {
            var result = mvc.perform(get("/admin").param("query", "000 admin page")
                            .param("size", "2").with(user("staff").roles(role)))
                    .andExpect(status().isOk()).andReturn();
            assertThat(result.getResponse().getContentAsString())
                    .contains("href=\"#product-editor\"", "Produkt suchen und editieren",
                            "id=\"product-editor\"", "action=\"/admin#product-editor\"",
                            "href=\"/admin/products/" + fixtures.getFirst().getId() + "/edit\"",
                            "#product-editor\">Weiter</a>");
            mvc.perform(get("/admin/products/" + fixtures.getFirst().getId() + "/edit")
                            .with(user("staff").roles(role)))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/admin").with(user("max").roles("USER"))).andExpect(status().isForbidden());
    }

    @Test
    void adminFiltersAndPaginatesWithWarningsOnlyForDisplayedProducts() throws Exception {
        var result = mvc.perform(get("/admin").param("query", "000 admin page")
                        .param("page", "1").param("size", "2")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn();
        var model = result.getModelAndView().getModel();
        assertThat(model.get("productPage")).isInstanceOfSatisfying(Page.class, page -> {
            assertThat(page.getTotalElements()).isEqualTo(25);
            assertThat(page.getNumber()).isEqualTo(1);
            assertThat(page.getContent()).hasSize(2);
        });
        assertThat(result.getResponse().getContentAsString())
                .contains("000 admin page 02", "000 admin page 03", "page=2", "query=000");
        assertThat(model.get("dataQualityWarnings").toString()).contains("000 admin page 02")
                .doesNotContain("000 admin page 00");
        assertThat(model).doesNotContainKey("allProducts");
        verify(service, never()).getAllProducts();
    }

    @Test
    void qualityFiltersCombineWithTextBeforePaginationWithoutDuplicateProducts() throws Exception {
        var reviewed = sources.save(new Source("Reviewed quality source", "https://example.org/reviewed",
                "Test", java.time.LocalDateTime.now().minusDays(1)));
        var pending = sources.save(new Source("Pending quality source", "https://example.org/pending", "Test", null));
        var anotherPending = sources.save(new Source("Second pending source", "https://example.org/pending-two", "Test", null));
        Product complete = new Product("000 admin page reviewed", null);
        complete.setSources(new HashSet<>(Set.of(reviewed)));
        products.save(complete);
        Product undated = new Product("000 admin page undated", null);
        undated.setSources(new HashSet<>(Set.of(pending)));
        products.save(undated);
        Product mixed = new Product("000 admin page mixed", null);
        mixed.setSources(new HashSet<>(Set.of(reviewed, pending, anotherPending)));
        products.save(mixed);
        Product imported = fixtures.getFirst();
        imported.setOpenFoodFactsData(new OpenFoodFactsData(imported, "9898989800012",
                imported.getName(), java.time.LocalDateTime.now()));
        products.flush();

        assertQualityCount("ALL", 28);
        assertQualityCount("MISSING_SOURCES", 25);
        assertQualityCount("MISSING_REVIEW_DATE", 2);
        assertQualityCount("ANY_GAP", 27);
        var first = mvc.perform(get("/admin").with(user("editor").roles("EDITOR"))
                        .param("query", "000 admin page").param("quality", "MISSING_REVIEW_DATE").param("size", "1")
                        .param("brandQuery", "kept-brand"))
                .andExpect(status().isOk()).andReturn();
        var firstPage = (Page<?>) first.getModelAndView().getModel().get("productPage");
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(first.getResponse().getContentAsString().replaceAll("\\s+", " "))
                .contains("000 admin page mixed", "quality=MISSING_REVIEW_DATE", "page=1", "brandQuery=kept-brand",
                        "value=\"MISSING_REVIEW_DATE\" selected=\"selected\"");
        var second = mvc.perform(get("/admin").with(user("admin").roles("ADMIN"))
                        .param("query", "000 admin page").param("quality", "MISSING_REVIEW_DATE")
                        .param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andReturn();
        assertThat(((Page<?>) second.getModelAndView().getModel().get("productPage")).getContent())
                .extracting("id").containsExactly(undated.getId());
        var empty = mvc.perform(get("/admin").with(user("admin").roles("ADMIN"))
                        .param("query", "NoQualityProductMatch").param("quality", "ANY_GAP"))
                .andExpect(status().isOk()).andReturn();
        assertThat(((Page<?>) empty.getModelAndView().getModel().get("productPage")).getTotalElements()).isZero();
        pending.setVerifiedAt(java.time.LocalDateTime.now().minusHours(1));
        anotherPending.setVerifiedAt(java.time.LocalDateTime.now().minusHours(1));
        sources.flush();
        assertQualityCount("MISSING_REVIEW_DATE", 0);
    }

    private void assertQualityCount(String filter, long expected) throws Exception {
        var result = mvc.perform(get("/admin").with(user("admin").roles("ADMIN"))
                        .param("query", "000 admin page").param("quality", filter).param("size", "2"))
                .andExpect(status().isOk()).andReturn();
        assertThat(((Page<?>) result.getModelAndView().getModel().get("productPage")).getTotalElements())
                .as(filter).isEqualTo(expected);
    }

    @Test
    void creationValidationKeepsQualityFilterAndOtherListState() throws Exception {
        var result = mvc.perform(post("/admin/products").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "").param("query", "000 admin page").param("quality", "MISSING_SOURCES")
                        .param("page", "1").param("size", "2").param("sourceQuery", "kept-source"))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getModelAndView().getViewName()).isEqualTo("admin");
        var page = (Page<?>) result.getModelAndView().getModel().get("productPage");
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(result.getResponse().getContentAsString().replaceAll("\\s+", " ")).contains(
                "value=\"MISSING_SOURCES\" selected=\"selected\"", "quality=MISSING_SOURCES", "sourceQuery=kept-source");
    }

    @Test
    void editLoadsOnlyInitialPageAndSelectedAlternativesOutsideThatPage() throws Exception {
        Product product = fixtures.getFirst();
        Product selected = fixtures.getLast();
        product.setAlternativeProducts(new HashSet<>(Set.of(selected)));
        products.saveAndFlush(product);
        var result = mvc.perform(get("/admin/products/" + product.getId() + "/edit")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn();
        var options = (java.util.Collection<?>) result.getModelAndView().getModel().get("allProducts");
        assertThat(options).hasSizeLessThanOrEqualTo(21);
        assertThat(options).extracting("id").contains(selected.getId()).doesNotContain(product.getId());
        assertThat(result.getResponse().getContentAsString()).contains("alternative-picker.js", selected.getName());
        verify(service, never()).getAllProducts();
    }

    @Test
    void validationErrorsKeepSubmittedAlternativeSelections() throws Exception {
        Product selected = fixtures.getLast();
        var result = mvc.perform(post("/admin/products/" + fixtures.getFirst().getId())
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "").param("alternativeProductIds", selected.getId().toString()))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getModelAndView().getViewName()).isEqualTo("admin-product-edit");
        assertThat(result.getResponse().getContentAsString())
                .contains("value=\"" + selected.getId() + "\" selected=\"selected\"");
        verify(service, never()).getAllProducts();
    }

    @Test
    void invalidAdminPageParametersAreRejected() throws Exception {
        mvc.perform(get("/admin").param("size", "51").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin").param("page", "-1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin").param("quality", "UNKNOWN").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin").param("quality", "ANY_GAP").with(user("max").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
