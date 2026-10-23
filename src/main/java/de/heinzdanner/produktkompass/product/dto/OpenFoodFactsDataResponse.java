package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.product.OpenFoodFactsData;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;
import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsMetadata;

public record OpenFoodFactsDataResponse(
        String barcode,
        String productName,
        String brands,
        String brandTags,
        String brandOwner,
        String categories,
        String categoryTags,
        String quantity,
        String packaging,
        String packagingTags,
        String ingredientsText,
        String allergens,
        String allergenTags,
        String traces,
        String traceTags,
        String countries,
        String countryTags,
        String labelTags,
        String originTags,
        String manufacturingPlaceTags,
        String nutritionGrade,
        String languageCode,
        LocalDateTime sourceModifiedAt,
        LocalDateTime lastImportedAt,
        String sourceRevision,
        String productUrl,
        OpenFoodFactsMetadata metadata,
        OpenFoodFactsView normalized,
        List<OpenFoodFactsLabelResponse> catalogLabels
) {
    public static OpenFoodFactsDataResponse fromEntity(OpenFoodFactsData data) {
        return new OpenFoodFactsDataResponse(
                data.getBarcode(),
                data.getProductName(),
                data.getBrands(),
                data.getBrandTags(),
                data.getBrandOwner(),
                data.getCategories(),
                data.getCategoryTags(),
                data.getQuantity(),
                data.getPackaging(),
                data.getPackagingTags(),
                data.getIngredientsText(),
                data.getAllergens(),
                data.getAllergenTags(),
                data.getTraces(),
                data.getTraceTags(),
                data.getCountries(),
                data.getCountryTags(),
                data.getLabelTags(),
                data.getOriginTags(),
                data.getManufacturingPlaceTags(),
                data.getNutritionGrade(),
                data.getLanguageCode(),
                data.getSourceModifiedAt(),
                data.getLastImportedAt(),
                data.getSourceRevision(),
                UriComponentsBuilder.fromUriString("https://world.openfoodfacts.org")
                        .pathSegment("product", data.getBarcode()).build().encode().toUriString(),
                data.getMetadata(),
                OpenFoodFactsView.from(data),
                data.getCatalogLabels().stream()
                        .sorted(Comparator.comparing(label -> label.getCanonicalTag()))
                        .map(OpenFoodFactsLabelResponse::from).toList()
        );
    }
}
