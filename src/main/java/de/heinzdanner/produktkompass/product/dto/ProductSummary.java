package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductLabel;

import java.util.List;
import java.util.Set;

/**
 * Paginated list entry. The raw {@code openFoodFacts*} string fields are kept for API compatibility;
 * {@code openFoodFacts} holds the normalized OFF view and {@code display} the source-neutral presentation.
 */
public record ProductSummary(Long id, String name, String description, BrandResponse brand,
                             CompanyResponse company, NutritionGrade nutritionGrade,
                             List<CategoryResponse> categories, boolean openFoodFactsDataAvailable,
                             String openFoodFactsProductUrl, String openFoodFactsBrands,
                             String openFoodFactsBrandTags, String openFoodFactsBrandOwner,
                             String openFoodFactsPackaging,
                             String openFoodFactsQuantity, String openFoodFactsNutritionGrade,
                             Set<ProductLabel> labels, OpenFoodFactsView openFoodFacts,
                             ProductDisplay display, String packageSize, ProductImage image) {
    public static ProductSummary fromEntity(Product product) {
        var openFoodFactsData = product.getOpenFoodFactsData();
        OpenFoodFactsView view = OpenFoodFactsView.from(openFoodFactsData);
        ProductDisplay display = ProductDisplay.from(product, view);
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
                openFoodFactsData == null ? null : openFoodFactsData.getPackaging(),
                openFoodFactsData == null ? null : openFoodFactsData.getQuantity(),
                openFoodFactsData == null ? null : openFoodFactsData.getNutritionGrade(),
                display.editorialLabels(), view, display, product.getPackageSize(), ProductImage.from(openFoodFactsData));
    }
}