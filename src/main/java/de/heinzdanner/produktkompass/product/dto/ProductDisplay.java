package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags.Tag;
import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.product.dto.OpenFoodFactsView.IngredientHint;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Source-neutral presentation: distinct OFF values accompany app values; duplicates appear once.
 * The legacy {@code off*Fallback} names are retained for API compatibility, but no longer imply precedence.
 * Nothing here derives brand/company links or changes stored data or filter semantics.
 */
public record ProductDisplay(
        Set<ProductLabel> editorialLabels,
        List<Tag> offLabels,
        List<String> offBrandFallback,
        String offBrandsSource,
        String offBrandOwnerFallback,
        String offNutriScoreFallback,
        String offQuantityFallback,
        List<Tag> offPackagingFallback,
        IngredientHint offGlutenHint,
        IngredientHint offLactoseHint,
        boolean anyLabels,
        String offProductName,
        List<String> offCategories
) {
    public static ProductDisplay from(Product product) {
        return from(product, OpenFoodFactsView.from(product.getOpenFoodFactsData()));
    }

    public static ProductDisplay from(Product product, OpenFoodFactsView off) {
        Set<ProductLabel> editorialLabels = product.getLabels() == null ? Set.of()
                : java.util.Collections.unmodifiableSet(new TreeSet<>(product.getLabels()));
        if (off == null) {
            return new ProductDisplay(editorialLabels, List.of(), List.of(), null, null, null, null, List.of(),
                    null, null, !editorialLabels.isEmpty(), null, List.of());
        }
        boolean noGrade = product.getNutritionGrade() == null || product.getNutritionGrade() == NutritionGrade.UNKNOWN;
        Set<String> seenLabels = new LinkedHashSet<>();
        List<Tag> labels = off.labels().stream()
                .filter(tag -> editorialLabels.stream().noneMatch(label -> sameLabel(label, tag)))
                .filter(tag -> seenLabels.add(labelKey(tag)))
                .toList();
        return new ProductDisplay(editorialLabels, labels,
                off.brands().stream().filter(name -> product.getBrand() == null
                        || !same(name, product.getBrand().getName())).toList(),
                off.brandsSource(),
                product.getCompany() != null && same(off.brandOwner(), product.getCompany().getName())
                        ? null : off.brandOwner(),
                !noGrade && same(off.nutriScore(), product.getNutritionGrade().name()) ? null : off.nutriScore(),
                same(off.quantity(), OpenFoodFactsTags.quantity(product.getPackageSize())) ? null : off.quantity(),
                off.packaging().stream().filter(tag -> !samePackaging(product, tag)).toList(),
                off.gluten(),
                off.lactose(),
                !editorialLabels.isEmpty() || !off.labels().isEmpty(),
                same(product.getName(), product.getOpenFoodFactsData().getProductName()) ? null
                        : product.getOpenFoodFactsData().getProductName(),
                OpenFoodFactsTags.split(product.getOpenFoodFactsData().getCategories()).stream()
                        .filter(name -> product.getCategories().stream().noneMatch(category -> same(name, category.getName())))
                        .toList());
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.strip().equalsIgnoreCase(right.strip());
    }

    private static boolean sameLabel(ProductLabel label, Tag tag) {
        if (same(label.getDisplayName(), tag.display())) return true;
        return switch (label) {
            case GLUTEN_FREE -> Set.of("en:no-gluten", "en:gluten-free").contains(tag.raw());
            case LACTOSE_FREE -> Set.of("en:no-lactose", "en:lactose-free").contains(tag.raw());
            default -> false;
        };
    }

    private static String labelKey(Tag tag) {
        return switch (tag.raw().toLowerCase(Locale.ROOT)) {
            case "en:no-gluten", "en:gluten-free" -> "gluten-free";
            case "en:no-lactose", "en:lactose-free" -> "lactose-free";
            default -> tag.display().strip().toLowerCase(Locale.ROOT);
        };
    }

    private static boolean samePackaging(Product product, Tag tag) {
        if (product.getPackagingType() == null) return false;
        String equivalent = switch (product.getPackagingType()) {
            case GLASS -> "en:glass";
            case PLASTIC -> "en:plastic";
            case CAN -> "en:can";
            case PAPER -> "en:paper";
            case CARTON -> "en:cardboard";
            default -> null;
        };
        return same(equivalent, tag.raw()) || same(product.getPackagingType().name(), tag.display());
    }
}