package de.heinzdanner.produktkompass.retailer;

import de.heinzdanner.produktkompass.cart.CartSearchPreferences;
import de.heinzdanner.produktkompass.cart.CartTagCatalog;
import de.heinzdanner.produktkompass.product.Category;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.product.OpenFoodFactsData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RetailerProductMatcherTest {
    private final RetailerProductMatcher matcher = new RetailerProductMatcher();
    private final CartTagCatalog.Resolved neutral = new CartTagCatalog.Resolved(Map.of(),
            new CartSearchPreferences.Selection(List.of(), List.of()), List.of());

    @ParameterizedTest
    @CsvSource({
            "BIO,bio", "VEGAN,vegan", "VEGETARIAN,vegetarisch", "LACTOSE_FREE,laktosefrei",
            "GLUTEN_FREE,glutenfrei", "ALCOHOL_FREE,alkoholfrei", "CAFFEINE_FREE,koffeinfrei",
            "PALM_OIL_FREE,ohne palmöl", "NO_ADDED_SUGAR,ohne zuckerzusatz"
    })
    void everyProductLabelIsPreferredWithoutExplicitPreferences(ProductLabel label, String tag) {
        Product source = new Product("Haferdrink Barista", null);
        source.setLabels(Set.of(label));
        var matching = candidate(2, "Haferdrink Natur", tag);
        var unknown = candidate(1, "Haferdrink Barista", null);
        var result = matcher.suggest(source, List.of(unknown, matching), neutral);
        assertThat(result).extracting(suggestion -> suggestion.product().id()).containsExactly(2L, 1L);
        assertThat(result.getFirst().matchedSourceLabels()).containsExactly(label.getDisplayName() + " (Katalog)");
        assertThat(result.getFirst().unconfirmedSourceLabels()).isEmpty();
        assertThat(result.getLast().unconfirmedSourceLabels()).containsExactly(label.getDisplayName() + " (Katalog)");
        assertThat(result.getLast().matchedSourceLabels()).isEmpty();
        assertThat(source.getLabels()).containsExactly(label);
        assertThat(unknown.preferenceTags()).isNull();
    }

    @Test
    void prefersMoreSourceLabelsAndDeduplicatesEquivalentOffLabels() {
        Product source = new Product("Haferdrink Barista", null);
        source.setLabels(Set.of(ProductLabel.BIO, ProductLabel.VEGAN));
        var off = new OpenFoodFactsData(source, "1234567890123", source.getName(), LocalDateTime.now());
        off.setLabelTags("en:organic,en:vegan,en:fair-trade");
        source.setOpenFoodFactsData(off);
        var result = matcher.suggest(source, List.of(
                candidate(1, "Haferdrink Barista", "bio"),
                candidate(2, "Haferdrink Natur", "bio,vegan,en:fair-trade"),
                candidate(3, "Haferdrink Natur", "bio,vegan")), neutral);
        assertThat(result).extracting(suggestion -> suggestion.product().id()).containsExactly(2L, 3L, 1L);
        assertThat(result.getFirst().matchedSourceLabels()).hasSize(3).contains("Bio (Katalog)", "Vegan (Katalog)");
        assertThat(result.get(1).unconfirmedSourceLabels()).singleElement()
                .asString().endsWith("(OFF)");
        assertThat(off.getLabelTags()).isEqualTo("en:organic,en:vegan,en:fair-trade");
    }

    @Test
    void offOnlyLabelsAlsoRankCandidatesAndUnrelatedLabelMatchesAreNotSuggestions() {
        Product source = new Product("Haferdrink", null);
        var off = new OpenFoodFactsData(source, "1234567890123", source.getName(), LocalDateTime.now());
        off.setLabelTags("en:organic");
        source.setOpenFoodFactsData(off);
        var result = matcher.suggest(source, List.of(candidate(1, "Haferdrink", null),
                candidate(2, "Haferdrink Natur", "bio"), candidate(3, "Schraubendreher", "bio")), neutral);
        assertThat(result).extracting(suggestion -> suggestion.product().id()).containsExactly(2L, 1L);
        assertThat(result.getFirst().matchedSourceLabels()).containsExactly("Bio (OFF)");
    }

    @Test
    void explicitPreferencesRankBeforeSourceLabels() {
        Product source = new Product("Haferdrink", null);
        source.setLabels(Set.of(ProductLabel.BIO));
        var preferred = new CartTagCatalog.Resolved(Map.of("label:VEGAN",
                new CartTagCatalog.Tag("label:VEGAN", "Vegan", "Produkt", ProductLabel.VEGAN, null, null)),
                new CartSearchPreferences.Selection(List.of("label:VEGAN"), List.of()), List.of());
        var result = matcher.suggest(source, List.of(candidate(1, "Haferdrink", "bio"),
                candidate(2, "Haferdrink", "vegan")), preferred);
        assertThat(result).extracting(suggestion -> suggestion.product().id()).containsExactly(2L, 1L);
        assertThat(result.getFirst().matchedPreferences()).containsExactly("Vegan");
        assertThat(result.getFirst().unconfirmedSourceLabels()).containsExactly("Bio (Katalog)");
    }

    @Test
    void explicitExclusionsOverrideSourceLabels() {
        Product source = new Product("Haferdrink", null);
        source.setLabels(Set.of(ProductLabel.BIO));
        var excluded = new CartTagCatalog.Resolved(Map.of(),
                new CartSearchPreferences.Selection(List.of(), List.of("label:BIO")), List.of());
        assertThat(matcher.suggest(source, List.of(candidate(1, "Haferdrink", "bio"),
                candidate(2, "Haferdrink", null)), excluded))
                .extracting(suggestion -> suggestion.product().id()).containsExactly(2L);
    }

    private RetailerProduct candidate(long id, String name, String tags) {
        return new RetailerProduct(id, "test-" + id, name, null, null, null,
                null, null, null, null, null, null, tags, null, null, null);
    }

    @Test
    void missingAllergenDataIsUnknownWhileAnExplicitExcludedAllergenIsFiltered() {
        Product source = new Product("Haferdrink Barista", null);
        source.addCategory(new Category("Pflanzliche Drinks", null));
        var unknown = new RetailerProduct(1, "unknown", "Haferdrink Natur", null,
                "Pflanzliche Drinks / Hafer", null, null, null, null, null, null,
                null, null, null, null, null);
        var containsMilk = new RetailerProduct(2, "milk", "Haferdrink mit Milch", null,
                "Pflanzliche Drinks / Hafer", null, null, null, null, null, null,
                null, null, "milch", null, null);
        var preferences = new CartTagCatalog.Resolved(Map.of(),
                new CartSearchPreferences.Selection(List.of(), List.of("content:milk")), List.of());

        assertThat(matcher.suggest(source, List.of(unknown, containsMilk), preferences))
                .extracting(suggestion -> suggestion.product().externalId())
                .containsExactly("unknown");
    }
}
