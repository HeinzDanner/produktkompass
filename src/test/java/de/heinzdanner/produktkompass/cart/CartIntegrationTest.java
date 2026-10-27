package de.heinzdanner.produktkompass.cart;

import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@Transactional
class CartIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired CartService service;
    @Autowired ProductRepository products;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    Product product;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        product = products.saveAndFlush(new Product("Cart fixture", null));
    }

    private String url() { return "/cart/items/" + product.getId(); }

    @Test
    void panelsAreOnlyVisibleForSignedInUsersAndIncludePersistedQuantity() throws Exception {
        mvc.perform(get("/products").param("query", "Cart fixture"))
                .andExpect(status().isOk()).andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("data-cart-product"))));
        service.change("max", product.getId(), CartService.Action.INCREMENT);
        mvc.perform(get("/products").param("query", "Cart fixture").with(user("max")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-cart-product=\"" + product.getId() + "\"")))
                .andExpect(content().string(containsString("data-cart-quantity")))
                .andExpect(content().string(containsString("/js/cart.js")));
        mvc.perform(get("/cart").with(user("max")).session(new MockHttpSession()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Cart fixture")));
        mvc.perform(get("/cart").with(user("admin")))
                .andExpect(status().isOk()).andExpect(model().attribute("items", org.hamcrest.Matchers.empty()));
    }

    @Test
    void updatesQuantityAndRemovesAtOneWithoutAffectingOtherOwners() throws Exception {
        service.change("admin", product.getId(), CartService.Action.INCREMENT);
        for (int quantity : new int[]{1, 2}) {
            mvc.perform(post(url()).with(user("max")).with(csrf())
                            .param("ajax", "true").param("action", "INCREMENT"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(quantity))
                    .andExpect(jsonPath("$.productId").value(product.getId()))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(product.getId()))
                    .andExpect(jsonPath("$.items[0].quantity").value(quantity));
        }
        assertThat(service.quantities("max")).containsEntry(product.getId(), 2);
        mvc.perform(post(url()).with(user("max")).with(csrf())
                        .param("ajax", "true").param("action", "DECREMENT"))
                .andExpect(jsonPath("$.quantity").value(1));
        mvc.perform(post(url()).with(user("max")).with(csrf())
                        .param("ajax", "true").param("action", "DECREMENT"))
                .andExpect(jsonPath("$.quantity").value(0));
        assertThat(service.quantities("max")).isEmpty();
        assertThat(service.quantities("admin")).containsEntry(product.getId(), 1);
        assertThat(service.change("max", product.getId(), CartService.Action.DECREMENT).quantity()).isZero();
        service.change("max", product.getId(), CartService.Action.INCREMENT);
        assertThat(service.change("max", product.getId(), CartService.Action.REMOVE).quantity()).isZero();
    }

    @Test
    void requiresAuthenticationCsrfValidActionAndExistingProduct() throws Exception {
        mvc.perform(post(url()).with(csrf()).param("action", "INCREMENT"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/cart")).andExpect(status().is3xxRedirection());
        mvc.perform(post(url()).with(user("max")).param("action", "INCREMENT"))
                .andExpect(status().isForbidden());
        mvc.perform(post(url()).with(user("max")).with(csrf()).param("action", "INVALID"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/cart/items/9223372036854775807").with(user("max")).with(csrf())
                        .param("ajax", "true").param("action", "INCREMENT"))
                .andExpect(status().isNotFound());
        assertThat(service.quantities("max")).isEmpty();
    }

    @Test
    void supportsFormsWithoutJavaScriptAndSurvivesANewSession() throws Exception {
        mvc.perform(post(url()).with(user("max")).with(csrf()).param("action", "INCREMENT"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cart"));
        mvc.perform(get("/cart").session(new MockHttpSession()).with(user("max")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Cart fixture")));
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE product_id = ?",
                Integer.class, product.getId())).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousAddsDoNotLoseUpdates() throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 12; i++) {
                tasks.add(executor.submit(() -> service.change("max", product.getId(), CartService.Action.INCREMENT)));
            }
            for (var task : tasks) task.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(service.quantities("max")).containsEntry(product.getId(), 12);
        } finally {
            products.deleteById(product.getId());
        }
    }

    @Test
    void rejectsOverflowAndCascadesDeletedProducts() throws Exception {
        service.change("max", product.getId(), CartService.Action.INCREMENT);
        jdbc.update("UPDATE cart_items SET quantity = ? WHERE product_id = ?", Integer.MAX_VALUE, product.getId());
        mvc.perform(post(url()).with(user("max")).with(csrf())
                        .param("ajax", "true").param("action", "INCREMENT"))
                .andExpect(status().isConflict());
        jdbc.update("DELETE FROM products WHERE id = ?", product.getId());
        assertThat(service.quantities("max")).isEmpty();
    }
}
