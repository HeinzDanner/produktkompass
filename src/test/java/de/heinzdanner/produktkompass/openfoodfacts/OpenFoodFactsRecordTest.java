package de.heinzdanner.produktkompass.openfoodfacts;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFoodFactsRecordTest {

    @Test
    void mapsLocalizedValuesTagsAndKeepsLeadingZeroBarcode() throws Exception {
        OpenFoodFactsRecord record = OpenFoodFactsRecord.from(Map.ofEntries(
                Map.entry("code", "0001234567890"),
                Map.entry("product_name", "Community name"),
                Map.entry("product_name_de", "Deutscher Name"),
                Map.entry("brands", "Community Brand"),
                Map.entry("brands_tags", "xx:community-brand"),
                Map.entry("brand_owner", "Community Owner"),
                Map.entry("categories", "Haferdrinks"),
                Map.entry("categories_tags", "en:plant-milks,de:haferdrinks"),
                Map.entry("packaging", "Bottle"),
                Map.entry("packaging_tags", "en:bottle"),
                Map.entry("ingredients_text", "Water"),
                Map.entry("ingredients_text_de", "Wasser, Hafer"),
                Map.entry("countries", "Germany"),
                Map.entry("countries_tags", "en:germany"),
                Map.entry("labels_tags", "en:organic"),
                Map.entry("origins_tags", "en:germany"),
                Map.entry("manufacturing_places_tags", "en:germany"),
                Map.entry("nutriscore_grade", "b"),
                Map.entry("lang", "en"),
                Map.entry("last_modified_t", "1790264955")));
        assertThat(record).isNotNull();
        assertThat(record.barcode()).isEqualTo("0001234567890");
        assertThat(record.productName()).isEqualTo("Deutscher Name");
        assertThat(record.brands()).isEqualTo("Community Brand");
        assertThat(record.brandTags()).isEqualTo("xx:community-brand");
        assertThat(record.brandOwner()).isEqualTo("Community Owner");
        assertThat(record.packaging()).isEqualTo("Bottle");
        assertThat(record.packagingTags()).isEqualTo("en:bottle");
        assertThat(record.ingredientsText()).isEqualTo("Wasser, Hafer");
        assertThat(record.categoryTags()).isEqualTo("en:plant-milks,de:haferdrinks");
        assertThat(record.allergens()).isNull();
        assertThat(record.countryTags()).isEqualTo("en:germany");
        assertThat(record.labelTags()).isEqualTo("en:organic");
        assertThat(record.originTags()).isEqualTo("en:germany");
        assertThat(record.manufacturingPlaceTags()).isEqualTo("en:germany");
        assertThat(record.nutritionGrade()).isEqualTo("b");
        assertThat(record.sourceRevision()).isEqualTo("1790264955");
        assertThat(record.sourceModifiedAt()).isNotNull();
    }

    @Test
    void rejectsRecordsWithoutBarcodeOrNameAndOversizedBoundedFields() {
        assertThat(OpenFoodFactsRecord.from(Map.of("code", "123", "product_name", "   "))).isNull();
        assertThat(OpenFoodFactsRecord.from(Map.of("code", "123", "product_name", "Name",
                "lang", "x".repeat(36)))).isNull();
    }

    @Test
    void mapsTabularFieldsAndFiltersCountryTagsExactly() {
        OpenFoodFactsRecord german = OpenFoodFactsRecord.from(Map.of(
                "code", "0001000000001",
                "product_name", "Oat drink",
                "product_name_de", "Haferdrink",
                "countries_tags", "en:france,en:germany",
                "categories_tags", "en:plant-milks,de:haferdrinks",
                "nutrition_grade_fr", "a",
                "last_modified_t", "1790264955"));
        OpenFoodFactsRecord france = OpenFoodFactsRecord.from(Map.of(
                "code", "0001000000002",
                "product_name", "Drink",
                "countries_tags", "en:france"));

        assertThat(german.productName()).isEqualTo("Haferdrink");
        assertThat(german.categoryTags()).isEqualTo("en:plant-milks,de:haferdrinks");
        assertThat(german.nutritionGrade()).isEqualTo("a");
        assertThat(german.isSoldIn("en:germany")).isTrue();
        assertThat(france.isSoldIn("en:germany")).isFalse();
    }

    @Test
    void fallsBackToAnotherLocalizedNameWhenGermanNameIsBlank() {
        OpenFoodFactsRecord record = OpenFoodFactsRecord.from(Map.of(
                "code", "0001000000003",
                "product_name_de", " ",
                "product_name_en", "Oat drink",
                "countries_tags", "en:germany"));

        assertThat(record).isNotNull();
        assertThat(record.productName()).isEqualTo("Oat drink");
    }

    @Test
    void mapsTheFieldsPresentInTheFilteredCsvExport() {
        OpenFoodFactsRecord record = OpenFoodFactsRecord.from(Map.of(
                "code", "0001000000005",
                "product_name_en", "REAL MAYONNAISE",
                "brands_tags", "xx:hellmann-s",
                "packaging", "rinse-jar-recycle-lid-recycle",
                "packaging_tags", "en:rinse-jar-recycle-lid-recycle",
                "countries_tags", "en:germany"));

        assertThat(record).isNotNull();
        assertThat(record.brands()).isNull();
        assertThat(record.brandTags()).isEqualTo("xx:hellmann-s");
        assertThat(record.packaging()).isEqualTo("rinse-jar-recycle-lid-recycle");
        assertThat(record.packagingTags()).isEqualTo("en:rinse-jar-recycle-lid-recycle");
    }
}
