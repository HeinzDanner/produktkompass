package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags.Tag;
import de.heinzdanner.produktkompass.product.OpenFoodFactsData;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.product.dto.ProductDisplay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OpenFoodFactsCardPresentationTest {
    @ParameterizedTest
    @CsvSource({
            "A, Sehr gute Nährwertqualität",
            "B, Gute Nährwertqualität",
            "C, Mittlere Nährwertqualität",
            "D, Weniger gute Nährwertqualität",
            "E, Ungünstige Nährwertqualität",
            "nicht anwendbar, Keine A–E-Bewertung: nicht anwendbar"
    })
    void describesNutriScoresWithoutInventingRatings(String grade, String description) {
        assertThat(new OpenFoodFactsCardPresentation(3).nutriScoreDescription(grade)).isEqualTo(description);
    }

    @Test
    void selectsUsefulLabelsWithConfigurableCapWithoutChangingFullData() {
        Product product = new Product("Card", null);
        var off = new OpenFoodFactsData(product, "12", "Card", LocalDateTime.now());
        off.setBrandTags("xx:first,xx:second,xx:third");
        off.setLabelTags("fr:unknown-label,en:green-dot,en:organic,en:eu-organic,en:vegan,en:vegetarian,"
                + "en:no-gluten,en:gluten-free,en:no-lactose,en:no-added-sugar");
        product.setOpenFoodFactsData(off);
        var display = ProductDisplay.from(product);
        assertThat(new OpenFoodFactsCardPresentation(3).labels(display)).extracting(Tag::raw)
                .containsExactly("en:no-gluten", "en:no-lactose", "en:vegan");
        assertThat(new OpenFoodFactsCardPresentation(4).labels(display)).extracting(Tag::raw)
                .containsExactly("en:no-gluten", "en:no-lactose", "en:vegan", "en:organic");
        assertThat(display.offLabels()).hasSize(9);
        assertThat(new OpenFoodFactsCardPresentation(3).brands(display)).containsExactly("first", "second", "third");

        product.setLabels(Set.of(ProductLabel.GLUTEN_FREE, ProductLabel.BIO));
        assertThat(new OpenFoodFactsCardPresentation(4).labels(ProductDisplay.from(product))).extracting(Tag::raw)
                .containsExactly("en:no-lactose", "en:vegan", "en:eu-organic", "en:vegetarian");
        assertThat(product.getLabels()).containsExactlyInAnyOrder(ProductLabel.GLUTEN_FREE, ProductLabel.BIO);
    }

    @Test
    void selectsExplicitAlcoholAndCaffeineLabelsButNeverDerivesThemFromIngredients() {
        Product product = new Product("Card", null);
        var off = new OpenFoodFactsData(product, "12", "Card", LocalDateTime.now());
        off.setLabelTags("en:alcohol-free,en:caffeine-free");
        product.setOpenFoodFactsData(off);
        assertThat(new OpenFoodFactsCardPresentation(3).labels(ProductDisplay.from(product)))
                .extracting(Tag::display).containsExactly("Alkoholfrei", "Koffeinfrei");
        off.setLabelTags(null);
        off.setTraceTags("en:milk,en:gluten");
        assertThat(new OpenFoodFactsCardPresentation(3).labels(ProductDisplay.from(product))).isEmpty();
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OpenFoodFactsCardPresentation(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new OpenFoodFactsCardPresentation(5));
    }
}
