package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.product.IngredientStatus;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.PackagingType;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductLabel;

import java.util.List;
import java.util.Set;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BrandResponse brand,
        CompanyResponse company,
        NutritionGrade nutritionGrade,
        PackagingType packagingType,
        String packageSize,
        Boolean madeInGermany,
        IngredientStatus containsLactose,
        IngredientStatus containsGluten,
        IngredientStatus containsAlcohol,
        IngredientStatus containsCaffeine,
        Set<ProductLabel> labels,
        List<CategoryResponse> categories,
        List<SourceResponse> sources,
        List<Long> alternativeProductIds
) {
    public static ProductResponse fromEntity(Product product) {
        BrandResponse brandResponse = product.getBrand() == null
                ? null
                : BrandResponse.fromEntity(product.getBrand());

        CompanyResponse companyResponse = product.getCompany() == null
                ? null
                : CompanyResponse.fromEntity(product.getCompany());

        List<CategoryResponse> categoryResponses = product.getCategories() == null
                ? List.of()
                : product.getCategories().stream()
                        .map(CategoryResponse::fromEntity)
                        .toList();

        List<SourceResponse> sourceResponses = product.getSources() == null
                ? List.of()
                : product.getSources().stream()
                        .map(SourceResponse::fromEntity)
                        .toList();

        List<Long> alternativeProductIds = product.getAlternativeProducts() == null
                ? List.of()
                : product.getAlternativeProducts().stream()
                        .map(Product::getId)
                        .toList();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                brandResponse,
                companyResponse,
                product.getNutritionGrade(),
                product.getPackagingType(),
                product.getPackageSize(),
                product.getMadeInGermany(),
                product.getContainsLactose(),
                product.getContainsGluten(),
                product.getContainsAlcohol(),
                product.getContainsCaffeine(),
                product.getLabels(),
                categoryResponses,
                sourceResponses,
                alternativeProductIds
        );
    }
}