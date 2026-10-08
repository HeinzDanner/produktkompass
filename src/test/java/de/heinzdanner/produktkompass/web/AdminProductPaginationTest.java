package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.product.ProductService;
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
    }
}
