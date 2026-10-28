package de.heinzdanner.produktkompass.cart;

import de.heinzdanner.produktkompass.product.OpenFoodFactsLabelRepository;
import de.heinzdanner.produktkompass.usertag.UserTagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CartPreferencesFlowTest {
    private final CartSearchService search = mock(CartSearchService.class);
    private final CartSearchPreferencesRepository repository = mock(CartSearchPreferencesRepository.class);
    private CartController controller;
    private CartSearchPreferences preferences;

    @BeforeEach
    void setup() {
        var offLabels = mock(OpenFoodFactsLabelRepository.class);
        var personalTags = mock(UserTagService.class);
        when(repository.lockOwner("max")).thenReturn(Optional.of(7L));
        when(offLabels.findAllByNormalizedTagIn(any())).thenReturn(List.of());
        when(personalTags.getTagOptionsForUser("max")).thenReturn(List.of());
        var catalog = new CartTagCatalog(offLabels, personalTags);
        preferences = new CartSearchPreferences(repository, catalog);
        controller = new CartController(mock(CartService.class), search, preferences);
    }

    @Test
    void savesCanonicalPreferencesWithoutExecutingProductSearch() {
        var result = controller.savePreferences(() -> "max",
                List.of("off:fr:enthalt-milch"), List.of("off:en:nutriscore-grade-c"));

        assertThat(result.preferred()).containsExactly("content:milk");
        assertThat(result.excluded()).containsExactly("nutrition:C");
        verify(repository).deleteByOwner(7L);
        verify(repository).insert(7L, "content:milk", TagState.PREFERRED);
        verify(repository).insert(7L, "nutrition:C", TagState.EXCLUDED);
        verifyNoInteractions(search);
    }

    @Test
    void formPostSavesWithoutBuildingSearchModelOrQueryingProducts() {
        String redirect = controller.applyPreferences(() -> "max", "Bio Schokolade",
                Map.of("tagsExplicit", "true", "tag.label:BIO", "preferred",
                        "tag.nutrition:C", "excluded", "tag.label:VEGAN", "neutral"));

        assertThat(redirect).isEqualTo("redirect:/cart?query=Bio%20Schokolade");
        verify(repository).insert(7L, "label:BIO", TagState.PREFERRED);
        verify(repository).insert(7L, "nutrition:C", TagState.EXCLUDED);
        verifyNoInteractions(search);
    }

    @Test
    void invalidSelectionDoesNotDeleteSavedPreferences() {
        assertThatThrownBy(() -> preferences.save("max",
                List.of("content:milk"), List.of("off:en:contains-milk")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> controller.applyPreferences(() -> "max", "",
                Map.of("tag.label:BIO", "invalid")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(repository, search);
    }
}
