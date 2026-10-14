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
                Map.entry("categories", "Haferdrinks"),
                Map.entry("categories_tags", "en:plant-milks,de:haferdrinks"),
                Map.entry("ingredients_text", "Water"),
                Map.entry("ingredients_text_de", "Wasser, Hafer"),
                Map.entry("countries", "Germany"),
                Map.entry("countries_tags", "en:germany"),
                Map.entry("nutrition_grade_fr", "b"),
                Map.entry("lang", "en"),
                Map.entry("last_modified_t", "1790264955")));
        assertThat(record).isNotNull();
        assertThat(record.barcode()).isEqualTo("0001234567890");
        assertThat(record.productName()).isEqualTo("Deutscher Name");
        assertThat(record.ingredientsText()).isEqualTo("Wasser, Hafer");
        assertThat(record.categoryTags()).isEqualTo("en:plant-milks,de:haferdrinks");
        assertThat(record.allergens()).isNull();
        assertThat(record.countryTags()).isEqualTo("en:germany");
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
}
