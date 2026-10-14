package de.heinzdanner.produktkompass.openfoodfacts;

import de.heinzdanner.produktkompass.product.OpenFoodFactsData;
import de.heinzdanner.produktkompass.product.Product;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

record OpenFoodFactsRecord(
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
        String sourceRevision
) {
    static OpenFoodFactsRecord from(Map<String, String> source) {
        String barcode = text(source, "code");
        String name = firstText(source, "product_name_de", "product_name", "product_name_en");
        if (name == null) {
            name = source.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("product_name_"))
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .map(Map.Entry::getValue)
                    .map(OpenFoodFactsRecord::normalizeText)
                    .filter(value -> value != null)
                    .findFirst()
                    .orElse(null);
        }
        String nutritionGrade = firstText(source, "nutrition_grade_fr", "nutriscore_grade");
        String languageCode = text(source, "lang");
        if (barcode == null || barcode.length() > 32 || name == null || name.length() > 500
                || nutritionGrade != null && nutritionGrade.length() > 20
                || languageCode != null && languageCode.length() > 35) {
            return null;
        }
        String quantity = text(source, "quantity");
        long revision = parseLong(text(source, "last_modified_t"));
        return new OpenFoodFactsRecord(
                barcode, name, text(source, "brands"), text(source, "brands_tags"),
                text(source, "brand_owner"), text(source, "categories"),
                text(source, "categories_tags"), quantity, text(source, "packaging"),
                text(source, "packaging_tags"),
                firstText(source, "ingredients_text_de", "ingredients_text"),
                text(source, "allergens"), text(source, "allergens_tags"),
                text(source, "traces"), text(source, "traces_tags"),
                text(source, "countries"), text(source, "countries_tags"),
                text(source, "labels_tags"), text(source, "origins_tags"),
                text(source, "manufacturing_places_tags"),
                nutritionGrade, languageCode,
                revision > 0 ? LocalDateTime.ofInstant(Instant.ofEpochSecond(revision), ZoneOffset.UTC) : null,
                revision > 0 ? Long.toString(revision) : null
        );
    }

    boolean isSoldIn(String countryTag) {
        String tags = countryTags == null ? "" : countryTags;
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .anyMatch(countryTag::equalsIgnoreCase);
    }

    void update(OpenFoodFactsData target, LocalDateTime importedAt) {
        target.setBarcode(barcode);
        target.setProductName(productName);
        target.setBrands(brands);
        target.setBrandTags(brandTags);
        target.setBrandOwner(brandOwner);
        target.setCategories(categories);
        target.setCategoryTags(categoryTags);
        target.setQuantity(quantity);
        target.setPackaging(packaging);
        target.setPackagingTags(packagingTags);
        target.setIngredientsText(ingredientsText);
        target.setAllergens(allergens);
        target.setAllergenTags(allergenTags);
        target.setTraces(traces);
        target.setTraceTags(traceTags);
        target.setCountries(countries);
        target.setCountryTags(countryTags);
        target.setLabelTags(labelTags);
        target.setOriginTags(originTags);
        target.setManufacturingPlaceTags(manufacturingPlaceTags);
        target.setNutritionGrade(nutritionGrade);
        target.setLanguageCode(languageCode);
        target.setSourceModifiedAt(sourceModifiedAt);
        target.setLastImportedAt(importedAt);
        target.setSourceRevision(sourceRevision);
    }

    Product newProduct(LocalDateTime importedAt) {
        String catalogName = productName.length() <= 255 ? productName : "OFF product " + barcode;
        Product product = new Product(catalogName, null);
        OpenFoodFactsData data = new OpenFoodFactsData(product, barcode, productName, importedAt);
        update(data, importedAt);
        product.setOpenFoodFactsData(data);
        return product;
    }

    private static String firstText(Map<String, String> source, String... fields) {
        for (String field : fields) {
            String value = text(source, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(Map<String, String> source, String field) {
        return normalizeText(source.get(field));
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long parseLong(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

}
