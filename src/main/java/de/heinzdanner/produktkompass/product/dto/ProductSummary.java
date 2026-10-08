package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.Product;

import java.util.List;

public record ProductSummary(Long id, String name, String description, BrandResponse brand,
                             CompanyResponse company, NutritionGrade nutritionGrade,
                             List<CategoryResponse> categories) {
    public static ProductSummary fromEntity(Product product) {
        return new ProductSummary(product.getId(), product.getName(), product.getDescription(),
                product.getBrand() == null ? null : BrandResponse.fromEntity(product.getBrand()),
                product.getCompany() == null ? null : CompanyResponse.fromEntity(product.getCompany()),
                product.getNutritionGrade(),
                product.getCategories().stream().map(CategoryResponse::fromEntity).toList());
    }
}
