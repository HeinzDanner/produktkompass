package de.heinzdanner.produktkompass.openfoodfacts;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record OpenFoodFactsMetadata(
        String genericName, String servingSize, String labels, String origins,
        String manufacturingPlaces, String packagingText, String stores, String purchasePlaces,
        String additiveTags, String ingredientAnalysisTags, String qualityTags, String dataSources,
        String creator, Integer novaGroup, BigDecimal nutriscoreScore, String environmentGrade,
        BigDecimal completeness, LocalDateTime sourceCreatedAt, LocalDateTime sourceCheckedAt,
        Map<String, String> imageUrls, Map<String, Nutrient> nutrition) {

    private static final Pattern MODERN = Pattern.compile(
            "nutrition\\.input_sets\\.([^.]+)\\.([^.]+)\\.([^.]+)\\.nutrients\\.([^.]+)\\.value");
    private static final Pattern CLASSIC = Pattern.compile("(.+)_(100g|serving)");
    private static final java.util.List<String> IMAGE_FIELDS = java.util.List.of(
            "imageUrl", "imageFrontUrl", "imageIngredientsUrl", "imageNutritionUrl", "imagePackagingUrl");

    public static OpenFoodFactsMetadata from(Map<String, String> row) {
        Map<String, String> images = new LinkedHashMap<>();
        for (String field : IMAGE_FIELDS) {
            String url = safeImageUrl(value(row, field));
            if (url != null) images.put(field, url);
        }
        row.forEach((column, raw) -> {
            if (isImageColumn(column)) {
                String url = safeImageUrl(raw);
                if (url != null && !images.containsValue(url)) images.put(column, url);
            }
        });
        Map<String, Nutrient> nutrients = new LinkedHashMap<>();
        row.forEach((column, raw) -> {
            if (!isNutritionColumn(column) || decimal(raw) == null) return;
            Matcher modern = MODERN.matcher(column);
            if (modern.matches()) {
                String prefix = column.substring(0, column.length() - "value".length());
                nutrients.put(column, new Nutrient(modern.group(4), decimal(raw),
                        row.get(prefix + "unit"), row.get(prefix + "modifier"),
                        modern.group(3), modern.group(2), modern.group(1)));
            } else {
                Matcher classic = CLASSIC.matcher(column);
                if (classic.matches()) {
                    String name = classic.group(1);
                    nutrients.put(column, new Nutrient(name, decimal(raw),
                            row.get(name + "_unit"), row.get(name + "_modifier"),
                            classic.group(2), "as_sold", "export"));
                }
            }
        });
        return new OpenFoodFactsMetadata(value(row, "genericName"), value(row, "servingSize"),
                value(row, "labels"), value(row, "origins"), value(row, "manufacturingPlaces"),
                value(row, "packagingText"), value(row, "stores"), value(row, "purchasePlaces"),
                value(row, "additiveTags"), value(row, "ingredientAnalysisTags"), value(row, "qualityTags"),
                value(row, "dataSources"), value(row, "creator"), integer(value(row, "novaGroup")),
                decimal(value(row, "nutriscoreScore")), value(row, "environmentGrade"),
                decimal(value(row, "completeness")), timestamp(value(row, "sourceCreatedAt")),
                timestamp(value(row, "sourceCheckedAt")), images, nutrients);
    }

    public static OpenFoodFactsMetadata merge(Map<String, String> row, OpenFoodFactsMetadata prior) {
        OpenFoodFactsMetadata current = from(row);
        java.util.function.Predicate<String> has = field -> OpenFoodFactsSchema.present(row, field);
        Map<String, String> images = new LinkedHashMap<>(prior.imageUrls());
        for (String field : IMAGE_FIELDS) {
            if (has.test(field)) images.remove(field);
        }
        row.keySet().forEach(images::remove);
        images.putAll(current.imageUrls());
        Map<String, Nutrient> nutrients = new LinkedHashMap<>();
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(prior.nutrition().keySet());
        keys.addAll(current.nutrition().keySet());
        for (String key : keys) {
            Nutrient previous = prior.nutrition().get(key);
            Nutrient next = row.containsKey(key) ? current.nutrition().get(key) : previous;
            if (next == null) continue;
            String prefix = key.endsWith(".value") ? key.substring(0, key.length() - 5) : next.name + "_";
            String unitColumn = prefix + "unit";
            String modifierColumn = prefix + "modifier";
            String unit = row.containsKey(unitColumn) ? normalize(row.get(unitColumn)) : previous == null ? null : previous.unit;
            String modifier = row.containsKey(modifierColumn) ? normalize(row.get(modifierColumn)) : previous == null ? null : previous.modifier;
            nutrients.put(key, new Nutrient(next.name, next.value, unit, modifier,
                    next.basis, next.preparation, next.source));
        }
        return new OpenFoodFactsMetadata(
                has.test("genericName") ? current.genericName : prior.genericName,
                has.test("servingSize") ? current.servingSize : prior.servingSize,
                has.test("labels") ? current.labels : prior.labels,
                has.test("origins") ? current.origins : prior.origins,
                has.test("manufacturingPlaces") ? current.manufacturingPlaces : prior.manufacturingPlaces,
                has.test("packagingText") ? current.packagingText : prior.packagingText,
                has.test("stores") ? current.stores : prior.stores,
                has.test("purchasePlaces") ? current.purchasePlaces : prior.purchasePlaces,
                has.test("additiveTags") ? current.additiveTags : prior.additiveTags,
                has.test("ingredientAnalysisTags") ? current.ingredientAnalysisTags : prior.ingredientAnalysisTags,
                has.test("qualityTags") ? current.qualityTags : prior.qualityTags,
                has.test("dataSources") ? current.dataSources : prior.dataSources,
                has.test("creator") ? current.creator : prior.creator,
                has.test("novaGroup") ? current.novaGroup : prior.novaGroup,
                has.test("nutriscoreScore") ? current.nutriscoreScore : prior.nutriscoreScore,
                has.test("environmentGrade") ? current.environmentGrade : prior.environmentGrade,
                has.test("completeness") ? current.completeness : prior.completeness,
                has.test("sourceCreatedAt") ? current.sourceCreatedAt : prior.sourceCreatedAt,
                has.test("sourceCheckedAt") ? current.sourceCheckedAt : prior.sourceCheckedAt,
                images, nutrients);
    }

    public static boolean isNutritionColumn(String column) {
        return MODERN.matcher(column).matches()
                || CLASSIC.matcher(column).matches() && !column.startsWith("nutrition-score");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String value(Map<String, String> row, String field) {
        return OpenFoodFactsSchema.value(row, field);
    }

    public static BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || value.length() > 100) return null;
        try {
            BigDecimal number = new BigDecimal(value.trim());
            return Math.abs((long) number.scale()) <= 100 && number.precision() <= 100 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer integer(String value) {
        BigDecimal number = decimal(value);
        try {
            return number == null ? null : number.intValueExact();
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    public static LocalDateTime timestamp(String value) {
        if (value == null) return null;
        try {
            return portableTimestamp(Instant.ofEpochSecond(Long.parseLong(value)));
        } catch (RuntimeException ignored) {
            try {
                return portableTimestamp(Instant.parse(value));
            } catch (RuntimeException invalid) {
                return null;
            }
        }
    }

    private static LocalDateTime portableTimestamp(Instant instant) {
        LocalDateTime value = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        return value.getYear() >= 1 && value.getYear() <= 9999 ? value : null;
    }

    public static String safeImageUrl(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null ? uri.toASCIIString() : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean isImageColumn(String column) {
        return column.matches("image_(?:front_|ingredients_|nutrition_|packaging_)?(?:small_|thumb_)?url")
                || column.matches("selected_images\\.(?:front|ingredients|nutrition|packaging)\\.(?:display|small|thumb)\\.[a-z]{2}");
    }

    public static boolean isDisplayImageUrl(String value) {
        String safe = safeImageUrl(value);
        if (safe == null) return false;
        URI uri = URI.create(safe);
        return "https".equalsIgnoreCase(uri.getScheme())
                && "images.openfoodfacts.org".equalsIgnoreCase(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && uri.getPath() != null && uri.getPath().startsWith("/images/products/");
    }

    public record Nutrient(String name, BigDecimal value, String unit, String modifier,
                           String basis, String preparation, String source) {}
}
