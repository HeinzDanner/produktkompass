package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags;
import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags.Tag;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.IngredientStatus;
import de.heinzdanner.produktkompass.product.dto.ProductDisplay;
import de.heinzdanner.produktkompass.product.dto.OpenFoodFactsView.IngredientHint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component("offCardPresentation")
public class OpenFoodFactsCardPresentation {
    private static final List<ProductLabel> PRIORITY = List.of(ProductLabel.GLUTEN_FREE, ProductLabel.LACTOSE_FREE,
            ProductLabel.ALCOHOL_FREE, ProductLabel.CAFFEINE_FREE, ProductLabel.PALM_OIL_FREE, ProductLabel.VEGAN,
            ProductLabel.BIO, ProductLabel.VEGETARIAN, ProductLabel.NO_ADDED_SUGAR);
    private final int labelLimit;
    private static final Set<ProductLabel> CONTENT_LABELS = Set.of(ProductLabel.GLUTEN_FREE,
            ProductLabel.LACTOSE_FREE, ProductLabel.ALCOHOL_FREE, ProductLabel.CAFFEINE_FREE, ProductLabel.PALM_OIL_FREE);

    public record ContentSection(String title, IngredientStatus status, String hint,
                                 List<ProductLabel> appLabels, List<Tag> offLabels) {
        public boolean hasKnownStatus() {
            return status != null && status != IngredientStatus.UNKNOWN;
        }

        public boolean hasNoInformation() {
            return !hasKnownStatus() && hint == null && appLabels.isEmpty() && offLabels.isEmpty();
        }
    }

    public List<ContentSection> contentSections(Product product, ProductDisplay display) {
        return List.of(
                section("Laktose", ProductLabel.LACTOSE_FREE, product.getContainsLactose(), display.offLactoseHint(), "Milch", display),
                section("Gluten", ProductLabel.GLUTEN_FREE, product.getContainsGluten(), display.offGlutenHint(), "Gluten", display),
                section("Alkohol", ProductLabel.ALCOHOL_FREE, product.getContainsAlcohol(), null, null, display),
                section("Koffein", ProductLabel.CAFFEINE_FREE, product.getContainsCaffeine(), null, null, display),
                section("Palmöl", ProductLabel.PALM_OIL_FREE, null, null, null, display));
    }

    private ContentSection section(String title, ProductLabel label, IngredientStatus status,
                                   IngredientHint hint, String ingredient, ProductDisplay display) {
        String summary = hint != null && (hint.declaredAllergen() || hint.declaredTrace())
                ? hint.displaySummary(ingredient) : null;
        return new ContentSection(title, status, summary,
                display.editorialLabels().stream().filter(value -> value == label).toList(),
                display.offLabels().stream().filter(tag -> matches(label, tag)).toList());
    }

    private boolean matches(ProductLabel label, Tag tag) {
        return OpenFoodFactsTags.equivalentLabelTags(label).contains(tag.raw().toLowerCase(java.util.Locale.ROOT));
    }

    public List<ProductLabel> otherAppLabels(ProductDisplay display) {
        return display.editorialLabels().stream().filter(label -> !CONTENT_LABELS.contains(label)).toList();
    }

    public List<Tag> otherOffLabels(ProductDisplay display) {
        return display.offLabels().stream()
                .filter(tag -> CONTENT_LABELS.stream().noneMatch(label -> matches(label, tag))).toList();
    }

    public OpenFoodFactsCardPresentation(
            @Value("${produktkompass.open-food-facts.cards.max-labels:3}") int labelLimit) {
        if (labelLimit < 1 || labelLimit > 4) {
            throw new IllegalArgumentException("OFF card max-labels must be between 1 and 4");
        }
        this.labelLimit = labelLimit;
    }

    public List<Tag> labels(ProductDisplay display) {
        List<Tag> selected = new ArrayList<>();
        for (ProductLabel label : PRIORITY) {
            display.offLabels().stream().filter(tag -> matches(label, tag))
                    .findFirst().ifPresent(selected::add);
            if (selected.size() == labelLimit) break;
        }
        return List.copyOf(selected);
    }

    public List<String> brands(ProductDisplay display) {
        return display.offBrandFallback();
    }

    public String nutriScoreDescription(String grade) {
        return switch (grade) {
            case "A" -> "Sehr gute Nährwertqualität";
            case "B" -> "Gute Nährwertqualität";
            case "C" -> "Mittlere Nährwertqualität";
            case "D" -> "Weniger gute Nährwertqualität";
            case "E" -> "Ungünstige Nährwertqualität";
            default -> "Keine A–E-Bewertung: " + grade;
        };
    }
}
