package de.heinzdanner.produktkompass.openfoodfacts;

import de.heinzdanner.produktkompass.product.ProductLabel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, display-only normalization of stored Open Food Facts values.
 * Nothing here writes back to editorial data or derives editorial brand/company links.
 */
public final class OpenFoodFactsTags {

    /** A single OFF tag: the stored raw value, a readable display text and whether a known exact translation applied. */
    public record Tag(String raw, String display, boolean known) {
    }

    private static final Set<String> LANGUAGE_PREFIXES;
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final Map<String, List<String>> LABEL_SEARCH_TERMS = new LinkedHashMap<>();
    private static final Map<String, String> ALLERGENS = new LinkedHashMap<>();
    private static final Map<String, String> PACKAGING = new LinkedHashMap<>();
    private static final Map<ProductLabel, Set<String>> LABEL_EQUIVALENTS = new EnumMap<>(ProductLabel.class);
    private static final Pattern QUANTITY = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(mg|g|kg|ml|cl|dl|l)",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> GLUTEN_FREE_CLAIMS = Set.of("en:no-gluten", "en:gluten-free",
            "en:certified-gluten-free", "en:dzg-gluten-free", "en:crossed-grain-trademark");
    private static final Set<String> LACTOSE_FREE_CLAIMS = Set.of("en:no-lactose", "en:lactose-free");

    static {
        Set<String> prefixes = new LinkedHashSet<>(Arrays.asList(Locale.getISOLanguages()));
        prefixes.add("xx");
        LANGUAGE_PREFIXES = Collections.unmodifiableSet(prefixes);

        label("en:organic", "Bio", "bio", "öko", "organic");
        label("en:eu-organic", "EU-Bio-Logo", "bio", "öko", "eu organic");
        label("de:eg-öko-verordnung", "EG-Öko-Verordnung", "bio", "öko");
        label("en:usda-organic", "USDA Organic", "bio", "organic");
        label("fr:ab-agriculture-biologique", "AB Agriculture Biologique", "bio", "organic");
        label("en:eu-agriculture", "EU-Landwirtschaft");
        label("en:non-eu-agriculture", "Nicht-EU-Landwirtschaft");
        label("en:eu-non-eu-agriculture", "EU-/Nicht-EU-Landwirtschaft");
        label("en:vegan", "Vegan", "vegan");
        label("en:vegetarian", "Vegetarisch", "vegetarisch", "vegetarian");
        label("en:european-vegetarian-union", "V-Label (European Vegetarian Union)", "v-label");
        label("en:european-vegetarian-union-vegan", "V-Label vegan", "v-label", "vegan");
        label("en:european-vegetarian-union-vegetarian", "V-Label vegetarisch", "v-label", "vegetarisch");
        label("en:v-label-international", "V-Label International", "v-label");
        label("en:the-vegan-society", "The Vegan Society", "vegan");
        label("en:no-gluten", "Ohne Gluten", "glutenfrei", "gluten free", "gluten-free", "no gluten");
        label("en:gluten-free", "Glutenfrei", "glutenfrei", "gluten free", "gluten-free");
        label("en:certified-gluten-free", "Zertifiziert glutenfrei", "glutenfrei", "gluten free", "gluten-free");
        label("en:dzg-gluten-free", "DZG glutenfrei", "glutenfrei", "gluten free", "gluten-free");
        label("en:crossed-grain-trademark", "Durchgestrichene Ähre (glutenfrei)", "glutenfrei", "gluten free");
        label("en:no-lactose", "Ohne Laktose", "laktosefrei", "lactose free", "lactose-free", "no lactose");
        label("en:lactose-free", "Laktosefrei", "laktosefrei", "lactose free", "lactose-free");
        label("en:alcohol-free", "Alkoholfrei", "alkoholfrei", "alcohol free");
        label("en:no-alcohol", "Ohne Alkohol", "alkoholfrei", "ohne alkohol");
        label("en:caffeine-free", "Koffeinfrei", "koffeinfrei", "caffeine free");
        label("en:no-caffeine", "Ohne Koffein", "koffeinfrei", "ohne koffein");
        label("en:no-preservatives", "Ohne Konservierungsstoffe", "konservierungsstoffe", "no preservatives");
        label("en:no-colorings", "Ohne Farbstoffe", "farbstoffe");
        label("en:no-artificial-colors", "Ohne künstliche Farbstoffe", "farbstoffe");
        label("en:no-artificial-flavors", "Ohne künstliche Aromen", "aromen");
        label("en:natural-flavors", "Natürliche Aromen", "aromen");
        label("en:no-flavour-enhancer", "Ohne Geschmacksverstärker", "geschmacksverstärker");
        label("en:no-msg", "Ohne Mononatriumglutamat", "glutamat");
        label("en:no-added-msg", "Ohne zugesetztes Mononatriumglutamat", "glutamat");
        label("en:no-additives", "Ohne Zusatzstoffe", "zusatzstoffe");
        label("en:no-added-sugar", "Ohne Zuckerzusatz", "ohne zucker", "no added sugar", "zuckerzusatz");
        label("en:no-added-salt", "Ohne Salzzusatz", "salzzusatz");
        label("en:low-or-no-sugar", "Wenig oder kein Zucker");
        label("en:low-or-no-salt", "Wenig oder kein Salz");
        label("en:no-palm-oil", "Ohne Palmöl", "palmöl");
        label("en:no-gmos", "Ohne GVO", "gentechnik", "gvo", "gmo");
        label("en:non-gmo-project", "Non-GMO Project", "gentechnik", "gmo");
        label("en:green-dot", "Der Grüne Punkt", "grüner punkt");
        label("fr:triman", "Triman (französischer Recyclinghinweis)");
        label("en:fsc", "FSC");
        label("en:fsc-mix", "FSC Mix");
        label("en:fair-trade", "Fairer Handel", "fairtrade", "fair trade");
        label("en:fairtrade-international", "Fairtrade International", "fairtrade", "fair trade");
        label("en:rainforest-alliance", "Rainforest Alliance");
        label("en:halal", "Halal");
        label("en:kosher", "Koscher", "koscher", "kosher");
        label("en:orthodox-union-kosher", "OU Koscher", "koscher", "kosher");
        label("en:made-in-germany", "Hergestellt in Deutschland");
        label("en:made-in-italy", "Hergestellt in Italien");
        label("en:made-in-france", "Hergestellt in Frankreich");
        label("en:made-in-belgium", "Hergestellt in Belgien");
        label("en:nutriscore", "Nutri-Score auf der Verpackung");
        for (String grade : List.of("a", "b", "c", "d", "e")) {
            label("en:nutriscore-grade-" + grade,
                    "Nutri-Score " + grade.toUpperCase(Locale.ROOT) + " auf der Verpackung");
        }
        label("en:pgi", "g.g.A. (geschützte geografische Angabe)");
        label("en:pdo", "g.U. (geschützte Ursprungsbezeichnung)");
        label("en:pasteurized", "Pasteurisiert");
        label("en:german-agricultural-society", "DLG");
        label("en:gold-medal-of-the-german-agricultural-society", "DLG-Goldmedaille");

        LABEL_EQUIVALENTS.put(ProductLabel.VEGAN, Set.of("en:vegan", "en:european-vegetarian-union-vegan",
                "en:the-vegan-society"));
        LABEL_EQUIVALENTS.put(ProductLabel.VEGETARIAN, Set.of("en:vegetarian",
                "en:european-vegetarian-union-vegetarian"));
        LABEL_EQUIVALENTS.put(ProductLabel.GLUTEN_FREE, GLUTEN_FREE_CLAIMS);
        LABEL_EQUIVALENTS.put(ProductLabel.LACTOSE_FREE, LACTOSE_FREE_CLAIMS);
        LABEL_EQUIVALENTS.put(ProductLabel.ALCOHOL_FREE, Set.of("en:alcohol-free", "en:no-alcohol"));
        LABEL_EQUIVALENTS.put(ProductLabel.CAFFEINE_FREE, Set.of("en:caffeine-free", "en:no-caffeine"));
        LABEL_EQUIVALENTS.put(ProductLabel.NO_ADDED_SUGAR, Set.of("en:no-added-sugar"));
        LABEL_EQUIVALENTS.put(ProductLabel.BIO, Set.of("en:organic", "en:eu-organic", "de:eg-öko-verordnung",
                "en:usda-organic", "fr:ab-agriculture-biologique"));

        ALLERGENS.put("en:gluten", "Gluten");
        ALLERGENS.put("en:milk", "Milch");
        ALLERGENS.put("en:eggs", "Eier");
        ALLERGENS.put("en:nuts", "Schalenfrüchte");
        ALLERGENS.put("en:peanuts", "Erdnüsse");
        ALLERGENS.put("en:soybeans", "Soja");
        ALLERGENS.put("en:celery", "Sellerie");
        ALLERGENS.put("en:mustard", "Senf");
        ALLERGENS.put("en:sesame-seeds", "Sesam");
        ALLERGENS.put("en:fish", "Fisch");
        ALLERGENS.put("en:crustaceans", "Krebstiere");
        ALLERGENS.put("en:molluscs", "Weichtiere");
        ALLERGENS.put("en:lupin", "Lupinen");
        ALLERGENS.put("en:sulphur-dioxide-and-sulphites", "Schwefeldioxid und Sulfite");

        PACKAGING.put("en:plastic", "Kunststoff");
        PACKAGING.put("en:glass", "Glas (Material)");
        PACKAGING.put("en:metal", "Metall");
        PACKAGING.put("en:aluminium", "Aluminium");
        PACKAGING.put("en:paper", "Papier");
        PACKAGING.put("en:cardboard", "Karton");
        PACKAGING.put("en:paper-or-cardboard", "Papier oder Karton");
        PACKAGING.put("en:pet-polyethylene-terephthalate", "PET (Polyethylenterephthalat)");
        PACKAGING.put("en:bottle", "Flasche");
        PACKAGING.put("en:squeeze-bottle", "Quetschflasche");
        PACKAGING.put("en:can", "Dose");
        PACKAGING.put("en:canned", "Konserve");
        PACKAGING.put("en:bag", "Beutel");
        PACKAGING.put("en:box", "Schachtel");
        PACKAGING.put("en:tray", "Schale");
        PACKAGING.put("en:lid", "Deckel");
    }

    private OpenFoodFactsTags() {
    }

    private static void label(String tag, String display, String... searchTerms) {
        LABELS.put(tag, display);
        LABEL_SEARCH_TERMS.put(tag, List.of(searchTerms));
    }

    /** Splits a comma-separated OFF field, trimming entries and dropping blanks and exact duplicates. */
    public static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .distinct().toList();
    }

    /** Removes a leading ISO-639-1 language prefix or the OFF language-neutral {@code xx:} prefix; other text is kept. */
    public static String stripLanguagePrefix(String tag) {
        if (tag == null) return null;
        String value = tag.trim();
        int colon = value.indexOf(':');
        if (colon > 0 && colon < value.length() - 1
                && LANGUAGE_PREFIXES.contains(value.substring(0, colon))) {
            return value.substring(colon + 1).trim();
        }
        return value;
    }

    /** Readable brand names: the OFF {@code brands} text when present, otherwise prefix-stripped {@code brands_tags} as stored. */
    public static List<String> brands(String brandsText, String brandTags) {
        List<String> source = split(brandsText);
        boolean fromTags = source.isEmpty();
        if (fromTags) source = split(brandTags);
        Map<String, String> unique = new LinkedHashMap<>();
        for (String entry : source) {
            String display = fromTags ? stripLanguagePrefix(entry) : entry;
            if (!display.isEmpty()) unique.putIfAbsent(display.toLowerCase(Locale.ROOT), display);
        }
        return List.copyOf(unique.values());
    }

    /** OFF column the visible brand names were taken from: {@code brands}, {@code brands_tags} or {@code null}. */
    public static String brandsSource(String brandsText, String brandTags) {
        if (!split(brandsText).isEmpty()) return "brands";
        return split(brandTags).isEmpty() ? null : "brands_tags";
    }

    public static List<Tag> labels(String csv) {
        return tags(csv, LABELS).stream()
                .map(tag -> tag.known() ? tag : new Tag(tag.raw(),
                        tag.display().replace('-', ' ').replace('_', ' '), false)).toList();
    }

    public static List<Tag> allergens(String csv) {
        return tags(csv, ALLERGENS);
    }

    public static List<Tag> packaging(String csv) {
        return tags(csv, PACKAGING);
    }

    /** Each entry stays separate; similar materials in different languages are deliberately not merged. */
    private static List<Tag> tags(String csv, Map<String, String> dictionary) {
        List<Tag> result = new ArrayList<>();
        for (String raw : split(csv)) {
            String translation = dictionary.get(raw.toLowerCase(Locale.ROOT));
            result.add(translation == null ? new Tag(raw, stripLanguagePrefix(raw), false)
                    : new Tag(raw, translation, true));
        }
        return List.copyOf(result);
    }

    /** Adds a space between number and unit for simple quantities such as {@code 380ml}; anything else is returned trimmed as stored. */
    public static String quantity(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        Matcher matcher = QUANTITY.matcher(value);
        if (!matcher.matches()) return value;
        return matcher.group(1) + " " + matcher.group(2).toLowerCase(Locale.ROOT);
    }

    /** Nutri-Score letter A–E from an OFF value, or {@code null} for anything else. */
    public static String nutriScoreGrade(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.matches("[a-e]") ? value.toUpperCase(Locale.ROOT) : null;
    }

    /** Readable OFF Nutri-Score status; {@code unknown} and blanks yield {@code null}. */
    public static String nutriScoreText(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String grade = nutriScoreGrade(raw);
        if (grade != null) return grade;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "unknown" -> null;
            case "not-applicable" -> "nicht anwendbar";
            case "exempt" -> "ausgenommen";
            default -> raw.trim();
        };
    }

    /** OFF label tags that an editorial label filter may optionally also match. */
    public static Set<String> equivalentLabelTags(ProductLabel label) {
        return label == null ? Set.of() : LABEL_EQUIVALENTS.getOrDefault(label, Set.of());
    }

    /** Editorial label categories that known OFF label tags correspond to (still OFF claims, not editorial data). */
    public static Set<ProductLabel> equivalentProductLabels(List<Tag> labels) {
        Set<ProductLabel> result = EnumSet.noneOf(ProductLabel.class);
        for (Tag tag : labels) {
            String key = tag.raw().toLowerCase(Locale.ROOT);
            LABEL_EQUIVALENTS.forEach((label, tags) -> {
                if (tags.contains(key)) result.add(label);
            });
        }
        return Collections.unmodifiableSet(result);
    }

    /** Known label tags whose German display text, slug or search synonyms contain a human search term (min. 3 characters). */
    public static Set<String> labelTagsMatchingText(String term) {
        if (term == null) return Set.of();
        String needle = term.trim().toLowerCase(Locale.ROOT);
        if (needle.length() < 3) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        LABELS.forEach((tag, display) -> {
            String slug = stripLanguagePrefix(tag).replace('-', ' ');
            if (display.toLowerCase(Locale.ROOT).contains(needle) || slug.contains(needle)
                    || LABEL_SEARCH_TERMS.get(tag).stream().anyMatch(synonym -> synonym.contains(needle))) {
                result.add(tag);
            }
        });
        return result;
    }

    public static Set<String> glutenFreeClaimTags() {
        return GLUTEN_FREE_CLAIMS;
    }

    public static Set<String> lactoseFreeClaimTags() {
        return LACTOSE_FREE_CLAIMS;
    }
}