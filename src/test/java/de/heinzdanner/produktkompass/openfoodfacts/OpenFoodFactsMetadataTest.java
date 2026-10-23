package de.heinzdanner.produktkompass.openfoodfacts;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFoodFactsMetadataTest {
    @Test
    void preservesCompleteRowsAndAbsentColumnsButAppliesExplicitBlanks() {
        Map<String, String> rich = new LinkedHashMap<>(Map.of(
                "code", "000123", "product_name", "Original", "brands", "Real brand",
                "ingredients_text", "Water", "serving_size", "250 ml", "nova_group", "3",
                "unknown.future.column", "unmodified \t\n\"value\"",
                "image_front_url", "https://images.openfoodfacts.org/front.jpg",
                "fat_100g", "2.5", "fat_unit", "g"));
        var product = OpenFoodFactsRecord.from(rich).newProduct(LocalDateTime.now());
        var target = product.getOpenFoodFactsData();
        product.setName("Editorial name");
        product.setDescription("Editorial description");
        assertThat(OpenFoodFactsJson.readRow(target.getSourceRowJson())).isEqualTo(rich);
        assertThat(target.getServingSize()).isEqualTo("250 ml");
        assertThat(target.getNovaGroup()).isEqualTo(3);

        OpenFoodFactsRecord.from(Map.of("code", "000123", "product_name_en", "New name",
                "brands_tags", "xx:new")).update(target, LocalDateTime.now());
        assertThat(target.getBrands()).isEqualTo("Real brand");
        assertThat(target.getIngredientsText()).isEqualTo("Water");
        assertThat(target.getMetadata().imageUrls()).containsKey("imageFrontUrl");
        assertThat(target.getMetadata().nutrition().get("fat_100g").value()).isEqualByComparingTo("2.5");
        assertThat(OpenFoodFactsJson.readRow(target.getMergedSourceJson())).containsEntry("unknown.future.column", rich.get("unknown.future.column"));
        assertThat(OpenFoodFactsJson.readRow(target.getSourceRowJson())).doesNotContainKey("brands");
        assertThat(product.getName()).isEqualTo("Editorial name");
        assertThat(product.getDescription()).isEqualTo("Editorial description");

        OpenFoodFactsRecord.from(Map.of("code", "000123", "product_name", "New name",
                "brands", "", "serving_size", "", "image_front_url", "", "fat_100g", ""))
                .update(target, LocalDateTime.now());
        assertThat(target.getBrands()).isNull();
        assertThat(target.getServingSize()).isNull();
        assertThat(target.getMetadata().imageUrls()).isEmpty();
        assertThat(target.getMetadata().nutrition()).isEmpty();
        assertThat(target.getIngredientsText()).isEqualTo("Water");
    }

    @Test
    void keepsModernNutritionInputSetsBasisPreparationModifiersAndUnitsSeparate() {
        String sold = "nutrition.input_sets.packaging.as_sold.100ml.nutrients.sugars.";
        String prepared = "nutrition.input_sets.manufacturer.prepared.serving.nutrients.sugars.";
        String estimate = "nutrition.input_sets.estimate.as_sold.100g.nutrients.energy-kj.";
        var data = OpenFoodFactsMetadata.from(Map.of(
                sold + "value", "0.25", sold + "unit", "g", sold + "modifier", "<",
                prepared + "value", "5", prepared + "unit", "g",
                estimate + "value", "1700", estimate + "unit", "kJ"));
        assertThat(data.nutrition()).hasSize(3);
        var nutrient = data.nutrition().get(sold + "value");
        assertThat(nutrient.basis()).isEqualTo("100ml");
        assertThat(nutrient.modifier()).isEqualTo("<");
        assertThat(nutrient.value()).isEqualByComparingTo("0.25");
        assertThat(data.nutrition().get(prepared + "value").preparation()).isEqualTo("prepared");
        assertThat(data.nutrition().get(estimate + "value").source()).isEqualTo("estimate");
    }

    @Test
    void mapsCommonMetadataAndNeverTreatsProducerScoresAsComputedScores() {
        var data = OpenFoodFactsMetadata.from(Map.ofEntries(
                Map.entry("generic_name_en", "Oat drink"), Map.entry("origins", "France"),
                Map.entry("manufacturing_places", "Berlin"), Map.entry("labels", "Organic"),
                Map.entry("packaging_text_de", "Flasche"), Map.entry("nova_group", "4"),
                Map.entry("nutriscore_score_producer", "3"), Map.entry("nutriscore_grade_producer", "a"),
                Map.entry("completeness", "0.8"), Map.entry("created_t", "1790264955"),
                Map.entry("last_checked_datetime", "2026-10-01T12:30:00Z")));
        assertThat(data.genericName()).isEqualTo("Oat drink");
        assertThat(data.novaGroup()).isEqualTo(4);
        assertThat(data.origins()).isEqualTo("France");
        assertThat(data.packagingText()).isEqualTo("Flasche");
        assertThat(data.nutriscoreScore()).isNull();
        assertThat(data.completeness()).isEqualByComparingTo("0.8");
        assertThat(data.sourceCreatedAt()).isNotNull();
        assertThat(data.sourceCheckedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 12, 30));
    }

    @Test
    void rejectsUnsafeImageUrlsAndMalformedNumbersAndDatesWithoutNetworkAccess() {
        assertThat(OpenFoodFactsMetadata.safeImageUrl("javascript:alert(1)")).isNull();
        assertThat(OpenFoodFactsMetadata.safeImageUrl("file:///C:/image.jpg")).isNull();
        assertThat(OpenFoodFactsMetadata.safeImageUrl("//example.org/image.jpg")).isNull();
        assertThat(OpenFoodFactsMetadata.safeImageUrl("https://user:pass@example.org/image.jpg")).isNull();
        assertThat(OpenFoodFactsMetadata.safeImageUrl("https://example.org/a.jpg")).isEqualTo("https://example.org/a.jpg");
        assertThat(OpenFoodFactsMetadata.decimal("1e2147483647")).isNull();
        assertThat(OpenFoodFactsMetadata.timestamp("9223372036854775807")).isNull();
        assertThat(OpenFoodFactsMetadata.from(Map.of("fat_100g", "NaN", "nova_group", "4.5")).novaGroup()).isNull();
    }

    @Test
    void reportsSupportedAndMissingExportFieldsExplicitly() {
        var coverage = OpenFoodFactsSchema.coverage(java.util.List.of("code", "product_name_de",
                "countries_tags", "brands_tags", "nutrition.input_sets.packaging.as_sold.100g.nutrients.fat.value"));
        assertThat(coverage.sourceColumnCount()).isEqualTo(5);
        assertThat(coverage.mappedFields()).containsKeys("brandTags", "nutrition", "productName");
        assertThat(coverage.missingOptionalFields()).contains("brands", "imageFrontUrl", "sourceCreatedAt", "nutritionGrade");
    }

    @Test
    void metadataAliasesUseExplicitBlanksWithoutResurrectingOldLanguageValues() {
        var record = OpenFoodFactsRecord.from(Map.of("code", "123", "product_name", "Product",
                "generic_name_de", "Drink", "created_t", "1790264955"));
        var target = record.newProduct(LocalDateTime.now()).getOpenFoodFactsData();
        OpenFoodFactsRecord.from(Map.of("code", "123", "product_name", "Product", "generic_name_en", ""))
                .update(target, LocalDateTime.now());
        OpenFoodFactsRecord.from(Map.of("code", "123", "product_name", "Product"))
                .update(target, LocalDateTime.now());
        assertThat(target.getMetadata().genericName()).isNull();
        assertThat(target.getMetadata().sourceCreatedAt()).isNotNull();
        assertThat(OpenFoodFactsJson.readRow(target.getMergedSourceJson())).containsEntry("generic_name_de", "Drink");
    }

    @Test
    void narrowNutritionUpdatesPreserveAbsentUnitsButApplyBlankUnitsAndInvalidValues() {
        var initial = OpenFoodFactsMetadata.from(Map.of("fat_100g", "2.5", "fat_unit", "g", "fat_modifier", "<"));
        var narrower = OpenFoodFactsMetadata.merge(Map.of("fat_100g", "3"), initial);
        assertThat(narrower.nutrition().get("fat_100g").unit()).isEqualTo("g");
        assertThat(narrower.nutrition().get("fat_100g").modifier()).isEqualTo("<");
        var clearedUnit = OpenFoodFactsMetadata.merge(Map.of("fat_unit", ""), narrower);
        assertThat(clearedUnit.nutrition().get("fat_100g").unit()).isNull();
        assertThat(clearedUnit.nutrition().get("fat_100g").value()).isEqualByComparingTo("3");
        assertThat(OpenFoodFactsMetadata.merge(Map.of("fat_100g", "invalid"), clearedUnit).nutrition()).isEmpty();
    }
}
