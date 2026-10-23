package de.heinzdanner.produktkompass.openfoodfacts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mapping shared by normalization, presence-aware updates and the header coverage report. */
public final class OpenFoodFactsSchema {
    public static final Map<String, List<String>> FIELDS;
    static {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        fields.put("productName", List.of("product_name_de", "product_name", "product_name_en", "product_name_*"));
        fields.put("brands", List.of("brands"));
        fields.put("brandTags", List.of("brands_tags"));
        fields.put("brandOwner", List.of("brand_owner"));
        fields.put("categories", List.of("categories"));
        fields.put("categoryTags", List.of("categories_tags"));
        fields.put("quantity", List.of("quantity"));
        fields.put("packaging", List.of("packaging"));
        fields.put("packagingTags", List.of("packaging_tags"));
        fields.put("ingredientsText", List.of("ingredients_text_de", "ingredients_text", "ingredients_text_en", "ingredients_text_*"));
        fields.put("allergens", List.of("allergens"));
        fields.put("allergenTags", List.of("allergens_tags"));
        fields.put("traces", List.of("traces"));
        fields.put("traceTags", List.of("traces_tags"));
        fields.put("countries", List.of("countries"));
        fields.put("countryTags", List.of("countries_tags"));
        fields.put("labelTags", List.of("labels_tags"));
        fields.put("originTags", List.of("origins_tags"));
        fields.put("manufacturingPlaceTags", List.of("manufacturing_places_tags"));
        fields.put("nutritionGrade", List.of("nutrition_grade_fr", "nutriscore_grade", "off:nutriscore_grade"));
        fields.put("languageCode", List.of("lang", "lc"));
        fields.put("sourceModifiedAt", List.of("last_modified_t", "last_modified_datetime"));
        fields.put("genericName", List.of("generic_name_de", "generic_name", "generic_name_en", "generic_name_*"));
        fields.put("servingSize", List.of("serving_size"));
        fields.put("labels", List.of("labels"));
        fields.put("origins", List.of("origins", "origin_de", "origin_en", "origin_*"));
        fields.put("manufacturingPlaces", List.of("manufacturing_places"));
        fields.put("packagingText", List.of("packaging_text_de", "packaging_text", "packaging_text_en", "packaging_text_*"));
        fields.put("stores", List.of("stores", "stores_tags"));
        fields.put("purchasePlaces", List.of("purchase_places"));
        fields.put("additiveTags", List.of("additives_tags"));
        fields.put("ingredientAnalysisTags", List.of("ingredients_analysis_tags"));
        fields.put("qualityTags", List.of("data_quality_tags", "data_quality_errors_tags", "data_quality_warnings_tags"));
        fields.put("dataSources", List.of("data_sources"));
        fields.put("creator", List.of("creator"));
        fields.put("novaGroup", List.of("nova_group"));
        fields.put("nutriscoreScore", List.of("nutriscore_score", "nutrition-score-fr_100g"));
        fields.put("environmentGrade", List.of("environmental_score_grade", "ecoscore_grade"));
        fields.put("completeness", List.of("completeness"));
        fields.put("sourceCreatedAt", List.of("created_t", "created_datetime"));
        fields.put("sourceCheckedAt", List.of("last_checked_t", "last_checked_datetime"));
        fields.put("imageUrl", List.of("image_url"));
        fields.put("imageFrontUrl", List.of("image_front_url"));
        fields.put("imageIngredientsUrl", List.of("image_ingredients_url"));
        fields.put("imageNutritionUrl", List.of("image_nutrition_url"));
        fields.put("imagePackagingUrl", List.of("image_packaging_url"));
        FIELDS = java.util.Collections.unmodifiableMap(fields);
    }

    private OpenFoodFactsSchema() {}

    public static List<String> columns(String field, Collection<String> headers) {
        List<String> matches = new ArrayList<>();
        for (String alias : FIELDS.get(field)) {
            if (alias.endsWith("*")) {
                String prefix = alias.substring(0, alias.length() - 1);
                headers.stream().filter(key -> key.startsWith(prefix)).sorted()
                        .filter(key -> !matches.contains(key)).forEach(matches::add);
            } else if (headers.contains(alias)) {
                matches.add(alias);
            }
        }
        return matches;
    }

    public static boolean present(Map<String, String> source, String field) {
        return !columns(field, source.keySet()).isEmpty();
    }

    public static String value(Map<String, String> source, String field) {
        return columns(field, source.keySet()).stream().map(source::get)
                .filter(value -> value != null && !value.isBlank()).map(String::trim).findFirst().orElse(null);
    }

    public static Coverage coverage(Collection<String> headers) {
        Map<String, List<String>> mapped = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        FIELDS.forEach((field, aliases) -> {
            List<String> columns = columns(field, headers);
            if (columns.isEmpty()) missing.add(field);
            else mapped.put(field, columns);
        });
        List<String> nutrition = headers.stream().filter(OpenFoodFactsMetadata::isNutritionColumn).sorted().toList();
        if (nutrition.isEmpty()) missing.add("nutrition");
        else mapped.put("nutrition", nutrition);
        return new Coverage(List.of("code", "countries_tags", "product_name or product_name_<language>"),
                mapped, missing, headers.size());
    }

    public record Coverage(List<String> criticalFields, Map<String, List<String>> mappedFields,
                           List<String> missingOptionalFields, int sourceColumnCount) {}
}
