package de.heinzdanner.produktkompass.openfoodfacts;

import de.heinzdanner.produktkompass.product.OpenFoodFactsData;
import de.heinzdanner.produktkompass.product.Product;

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
        String sourceRevision,
        Map<String, String> source
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
        String nutritionGrade = OpenFoodFactsSchema.value(source, "nutritionGrade");
        String languageCode = OpenFoodFactsSchema.value(source, "languageCode");
        if (barcode == null || !barcode.matches("[0-9]{1,32}") || name == null || name.length() > 500
                || nutritionGrade != null && nutritionGrade.length() > 20
                || languageCode != null && languageCode.length() > 35) {
            return null;
        }
        String quantity = text(source, "quantity");
        LocalDateTime modified = OpenFoodFactsMetadata.timestamp(OpenFoodFactsSchema.value(source, "sourceModifiedAt"));
        return new OpenFoodFactsRecord(
                barcode, name, text(source, "brands"), text(source, "brands_tags"),
                text(source, "brand_owner"), text(source, "categories"),
                text(source, "categories_tags"), quantity, text(source, "packaging"),
                text(source, "packaging_tags"),
                OpenFoodFactsSchema.value(source, "ingredientsText"),
                text(source, "allergens"), text(source, "allergens_tags"),
                text(source, "traces"), text(source, "traces_tags"),
                text(source, "countries"), text(source, "countries_tags"),
                text(source, "labels_tags"), text(source, "origins_tags"),
                text(source, "manufacturing_places_tags"),
                nutritionGrade, languageCode,
                modified,
                modified == null ? null : Long.toString(modified.toEpochSecond(ZoneOffset.UTC)),
                java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(source))
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
        if (present("productName")) target.setProductName(productName);
        if (present("brands")) target.setBrands(brands);
        if (present("brandTags")) target.setBrandTags(brandTags);
        if (present("brandOwner")) target.setBrandOwner(brandOwner);
        if (present("categories")) target.setCategories(categories);
        if (present("categoryTags")) target.setCategoryTags(categoryTags);
        if (present("quantity")) target.setQuantity(quantity);
        if (present("packaging")) target.setPackaging(packaging);
        if (present("packagingTags")) target.setPackagingTags(packagingTags);
        if (present("ingredientsText")) target.setIngredientsText(ingredientsText);
        if (present("allergens")) target.setAllergens(allergens);
        if (present("allergenTags")) target.setAllergenTags(allergenTags);
        if (present("traces")) target.setTraces(traces);
        if (present("traceTags")) target.setTraceTags(traceTags);
        if (present("countries")) target.setCountries(countries);
        if (present("countryTags")) target.setCountryTags(countryTags);
        if (present("labelTags")) target.setLabelTags(labelTags);
        if (present("originTags")) target.setOriginTags(originTags);
        if (present("manufacturingPlaceTags")) target.setManufacturingPlaceTags(manufacturingPlaceTags);
        if (present("nutritionGrade")) target.setNutritionGrade(nutritionGrade);
        if (present("languageCode")) target.setLanguageCode(languageCode);
        if (present("sourceModifiedAt")) target.setSourceModifiedAt(sourceModifiedAt);
        target.setLastImportedAt(importedAt);
        if (present("sourceModifiedAt")) target.setSourceRevision(sourceRevision);
        OpenFoodFactsMetadata metadata = OpenFoodFactsMetadata.merge(source, target.getMetadata());
        target.setMetadataJson(OpenFoodFactsJson.write(metadata));
        target.setSourceRowJson(OpenFoodFactsJson.write(source));
        Map<String, String> merged = OpenFoodFactsJson.readRow(target.getMergedSourceJson());
        merged.putAll(source);
        target.setMergedSourceJson(OpenFoodFactsJson.write(merged));
        if (present("servingSize")) target.setServingSize(metadata.servingSize());
        if (present("novaGroup")) target.setNovaGroup(metadata.novaGroup());
        if (present("nutriscoreScore")) target.setNutriscoreScore(boundedDecimal(metadata.nutriscoreScore()));
        if (present("completeness")) target.setCompleteness(boundedDecimal(metadata.completeness()));
        if (present("sourceCreatedAt")) target.setSourceCreatedAt(metadata.sourceCreatedAt());
    }

    private boolean present(String field) {
        return OpenFoodFactsSchema.present(source, field);
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

    private static java.math.BigDecimal boundedDecimal(java.math.BigDecimal value) {
        if (value == null || value.abs().compareTo(new java.math.BigDecimal("100000000")) >= 0) return null;
        return value.setScale(4, java.math.RoundingMode.HALF_UP);
    }

}
