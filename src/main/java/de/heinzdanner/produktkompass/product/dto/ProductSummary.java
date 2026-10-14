package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.Product;

import java.util.List;

public record ProductSummary(Long id, String name, String description, BrandResponse brand,
                             CompanyResponse company, NutritionGrade nutritionGrade,
                             List<CategoryResponse> categories, boolean openFoodFactsDataAvailable,
                             String openFoodFactsProductUrl, String openFoodFactsBrands,
                             String openFoodFactsBrandTags, String openFoodFactsBrandOwner,
                             String openFoodFactsPackaging) {
    public static ProductSummary fromEntity(Product product) {
        var openFoodFactsData = product.getOpenFoodFactsData();
        return new ProductSummary(product.getId(), product.getName(), product.getDescription(),
                product.getBrand() == null ? null : BrandResponse.fromEntity(product.getBrand()),
                product.getCompany() == null ? null : CompanyResponse.fromEntity(product.getCompany()),
                product.getNutritionGrade(),
                product.getCategories().stream().map(CategoryResponse::fromEntity).toList(),
                openFoodFactsData != null,
                openFoodFactsData == null ? null
                        : "https://world.openfoodfacts.org/product/" + openFoodFactsData.getBarcode(),
                openFoodFactsData == null ? null : openFoodFactsData.getBrands(),
                openFoodFactsData == null ? null : openFoodFactsData.getBrandTags(),
                openFoodFactsData == null ? null : openFoodFactsData.getBrandOwner(),
                openFoodFactsData == null ? null : openFoodFactsData.getPackaging());
    }
}
