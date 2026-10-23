package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags;
import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags.Tag;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.product.dto.ProductDisplay;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component("offCardPresentation")
public class OpenFoodFactsCardPresentation {
    private static final List<ProductLabel> PRIORITY = List.of(ProductLabel.GLUTEN_FREE, ProductLabel.LACTOSE_FREE,
            ProductLabel.ALCOHOL_FREE, ProductLabel.CAFFEINE_FREE, ProductLabel.VEGAN,
            ProductLabel.BIO, ProductLabel.VEGETARIAN, ProductLabel.NO_ADDED_SUGAR);
    private final int labelLimit;

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
            Set<String> equivalents = OpenFoodFactsTags.equivalentLabelTags(label);
            display.offLabels().stream().filter(tag -> equivalents.contains(tag.raw()))
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
