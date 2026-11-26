package de.heinzdanner.produktkompass.recipe;

import de.heinzdanner.produktkompass.cart.CartService;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class RecipeIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired CartService cart;
    @Autowired ProductRepository products;
    @MockitoBean MealieClient mealie;
    private MockMvc mvc;
    private Product product;
    private final UUID foodId = UUID.fromString("f1234567-1234-4234-8234-123456789abc");

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        product = products.saveAndFlush(new Product("Bio Tomaten Test", null));
        cart.change("max", product.getId(), CartService.Action.INCREMENT);
        when(mealie.configured()).thenReturn(true);
        when(mealie.foods()).thenReturn(List.of(new MealieClient.Food(foodId, "Tomaten")));
    }

    @Test
    void explicitSelectionReturnsIdeasWithoutChangingCart() throws Exception {
        mvc.perform(get("/cart/recipes").with(user("max")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bio Tomaten Test")))
                .andExpect(content().string(containsString("Tomaten")));
        verify(mealie, never()).suggest(anyList());
        when(mealie.suggest(List.of(foodId))).thenReturn(List.of(new MealieClient.Suggestion(
                new MealieClient.Recipe("Synthetische Tomatenpfanne"),
                List.of(new MealieClient.Food(UUID.randomUUID(), "Reis")))));
        mvc.perform(post("/cart/recipes").with(user("max")).with(csrf())
                        .param("food_" + product.getId(), foodId.toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Synthetische Tomatenpfanne")))
                .andExpect(content().string(containsString("Reis")));
        verify(mealie).suggest(List.of(foodId));
        assertThat(cart.quantities("max")).containsEntry(product.getId(), 1);
    }

    @Test
    void requiresLoginCsrfOwnerCartAndValidFood() throws Exception {
        mvc.perform(get("/cart/recipes")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/cart/recipes").with(user("max"))
                        .param("food_" + product.getId(), foodId.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/cart/recipes").with(user("admin")).with(csrf())
                        .param("food_" + product.getId(), foodId.toString()))
                .andExpect(status().isBadRequest());
        for (String selection : List.of("skip", "not-a-uuid", UUID.randomUUID().toString())) {
            mvc.perform(post("/cart/recipes").with(user("max")).with(csrf())
                            .param("food_" + product.getId(), selection))
                    .andExpect(status().isBadRequest());
        }
        verify(mealie, never()).suggest(anyList());
    }

    @Test
    void missingConfigurationAndUpstreamFailuresAreVisible() throws Exception {
        when(mealie.configured()).thenReturn(false);
        mvc.perform(get("/cart/recipes").with(user("max")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("noch nicht eingerichtet")));
        verify(mealie, never()).foods();
        when(mealie.configured()).thenReturn(true);
        when(mealie.foods()).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "Mealie Testfehler"));
        mvc.perform(get("/cart/recipes").with(user("max")))
                .andExpect(status().isBadGateway()).andExpect(content().string(containsString("Mealie Testfehler")));
    }
}
