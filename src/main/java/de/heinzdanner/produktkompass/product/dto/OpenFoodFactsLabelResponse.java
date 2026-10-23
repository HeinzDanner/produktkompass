package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags;
import de.heinzdanner.produktkompass.product.OpenFoodFactsLabel;

public record OpenFoodFactsLabelResponse(Long id, String canonicalTag, String localeKey,
                                         String display, String provenance) {
    public static OpenFoodFactsLabelResponse from(OpenFoodFactsLabel label) {
        return new OpenFoodFactsLabelResponse(label.getId(), label.getCanonicalTag(), label.getLocaleKey(),
                OpenFoodFactsTags.labels(label.getCanonicalTag()).getFirst().display(), OpenFoodFactsView.PROVENANCE);
    }
}
