package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.product.IngredientStatus;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.PackagingType;
import de.heinzdanner.produktkompass.product.ProductLabel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record ProductRequest(
        @NotBlank(message = "Der Produktname darf nicht leer sein.")
        @Size(max = 255, message = "Der Produktname darf maximal 255 Zeichen lang sein.")
        String name,

        @Size(max = 1000, message = "Die Beschreibung darf maximal 1000 Zeichen lang sein.")
        String description,

        Long brandId,
        Long companyId,
        NutritionGrade nutritionGrade,
        PackagingType packagingType,
        String packageSize,
        Boolean madeInGermany,
        IngredientStatus containsLactose,
        IngredientStatus containsGluten,
        IngredientStatus containsAlcohol,
        IngredientStatus containsCaffeine,
        Set<ProductLabel> labels,
        List<Long> categoryIds,
        List<Long> sourceIds,
        List<Long> alternativeProductIds
) {
    public ProductRequest {
        if (labels == null) {
            labels = Set.of();
        }
        if (categoryIds == null) {
            categoryIds = List.of();
        }
        if (sourceIds == null) {
            sourceIds = List.of();
        }
        if (alternativeProductIds == null) {
            alternativeProductIds = List.of();
        }
    }
}
