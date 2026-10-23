package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags;
import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags.Tag;
import de.heinzdanner.produktkompass.product.OpenFoodFactsData;
import de.heinzdanner.produktkompass.product.ProductLabel;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Readable, normalized form of stored OFF community values. All values are unverified OFF claims;
 * {@code provenance} is always {@value #PROVENANCE}. Missing values never mean "absent" or "free from".
 */
public record OpenFoodFactsView(
        String provenance,
        List<String> brands,
        String brandsSource,
        String brandOwner,
        List<Tag> labels,
        Set<ProductLabel> labelEquivalents,
        String quantity,
        String quantityRaw,
        String nutriScore,
        String nutriScoreRaw,
        List<Tag> packaging,
        String packagingSource,
        List<Tag> allergens,
        List<Tag> traces,
        IngredientHint gluten,
        IngredientHint lactose
) {
    public static final String PROVENANCE = "OPEN_FOOD_FACTS_UNVERIFIED";

    /** OFF statements about one ingredient; {@code summary} is {@code null} when OFF says nothing about it. */
    public record IngredientHint(String freeFromClaim, boolean declaredAllergen, boolean declaredTrace,
                                 boolean conflict, String summary) {
        public String displaySummary(String ingredientName) {
            StringBuilder display = new StringBuilder();
            if (declaredAllergen) {
                display.append(ingredientName).append(" als Allergen angegeben");
            }
            if (declaredTrace) {
                if (!display.isEmpty()) display.append("; ");
                display.append("Spuren von ").append(ingredientName).append(" angegeben");
            }
            return display.toString();
        }
    }

    public static OpenFoodFactsView from(OpenFoodFactsData data) {
        if (data == null) return null;
        List<Tag> labels = OpenFoodFactsTags.labels(data.getLabelTags());
        List<Tag> packaging = OpenFoodFactsTags.packaging(data.getPackagingTags());
        String packagingSource = packaging.isEmpty() ? null : "packaging_tags";
        if (packaging.isEmpty()) {
            packaging = OpenFoodFactsTags.packaging(data.getPackaging());
            packagingSource = packaging.isEmpty() ? null : "packaging";
        }
        List<Tag> allergens = OpenFoodFactsTags.allergens(data.getAllergenTags());
        List<Tag> traces = OpenFoodFactsTags.allergens(data.getTraceTags());
        return new OpenFoodFactsView(PROVENANCE,
                OpenFoodFactsTags.brands(data.getBrands(), data.getBrandTags()),
                OpenFoodFactsTags.brandsSource(data.getBrands(), data.getBrandTags()),
                blankToNull(data.getBrandOwner()),
                labels,
                OpenFoodFactsTags.equivalentProductLabels(labels),
                OpenFoodFactsTags.quantity(data.getQuantity()),
                blankToNull(data.getQuantity()),
                OpenFoodFactsTags.nutriScoreText(data.getNutritionGrade()),
                blankToNull(data.getNutritionGrade()),
                packaging, packagingSource, allergens, traces,
                hint(labels, allergens, traces, OpenFoodFactsTags.glutenFreeClaimTags(), "en:gluten", "Gluten", true),
                hint(labels, allergens, traces, OpenFoodFactsTags.lactoseFreeClaimTags(), "en:milk", "Milch", false));
    }

    /**
     * Gluten: a free-from label together with a gluten allergen is reported as conflict.
     * Lactose: milk is reported separately but is no conflict, because lactose-free milk products exist.
     */
    private static IngredientHint hint(List<Tag> labels, List<Tag> allergens, List<Tag> traces,
                                       Set<String> claimTags, String allergenTag, String allergenName,
                                       boolean conflictWhenDeclared) {
        Tag claim = labels.stream().filter(tag -> claimTags.contains(tag.raw().toLowerCase(Locale.ROOT)))
                .findFirst().orElse(null);
        boolean declared = allergens.stream().anyMatch(tag -> allergenTag.equalsIgnoreCase(tag.raw()));
        boolean trace = traces.stream().anyMatch(tag -> allergenTag.equalsIgnoreCase(tag.raw()));
        if (claim == null && !declared && !trace) return null;
        boolean conflict = conflictWhenDeclared && claim != null && declared;
        StringBuilder summary = new StringBuilder();
        if (conflict) {
            summary.append("Widersprüchlich laut OFF: Label „").append(claim.display())
                    .append("“, aber Allergen „").append(allergenName).append("“ angegeben – Verpackung prüfen");
        } else {
            if (claim != null) summary.append("Label laut OFF: ").append(claim.display());
            if (declared) {
                if (!summary.isEmpty()) summary.append("; ");
                summary.append("Allergen laut OFF: ").append(allergenName);
            }
        }
        if (trace) {
            if (!summary.isEmpty()) summary.append("; ");
            summary.append("Spuren laut OFF: ").append(allergenName);
        }
        summary.append(" (ungeprüft)");
        return new IngredientHint(claim == null ? null : claim.raw(), declared, trace, conflict, summary.toString());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}