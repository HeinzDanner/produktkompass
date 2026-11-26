package de.heinzdanner.produktkompass.retailer;

import de.heinzdanner.produktkompass.cart.CartTagCatalog;
import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.product.dto.ProductDisplay;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RetailerProductMatcher {
    public record Suggestion(RetailerProduct product, int similarityScore, List<String> matchedPreferences,
                             boolean sameCategory, List<String> matchedSourceLabels,
                             List<String> unconfirmedSourceLabels) {}

    private record SourceLabel(String key, String name) {}

    public List<Suggestion> suggest(Product source, List<RetailerProduct> candidates,
                                    CartTagCatalog.Resolved preferences) {
        Set<String> sourceName = words(source.getName() + " " + (source.getBrand() == null ? "" : source.getBrand().getName()));
        Set<String> sourceCategories = source.getCategories().stream().map(category -> words(category.getName()))
                .flatMap(Set::stream).collect(Collectors.toSet());
        List<SourceLabel> sourceLabels = sourceLabels(source);
        var matches = new ArrayList<Suggestion>();
        for (RetailerProduct candidate : candidates) {
            Set<String> candidateName = words(candidate.name() + " " + nullToEmpty(candidate.brand()));
            Set<String> candidateCategories = words(nullToEmpty(candidate.categoryPath()));
            long nameOverlap = sourceName.stream().filter(candidateName::contains).count();
            long categoryOverlap = sourceCategories.stream().filter(candidateCategories::contains).count();
            if (nameOverlap == 0 && categoryOverlap == 0) continue;

            boolean excluded = preferences.selection().excluded().stream().anyMatch(key -> matches(key, candidate));
            if (excluded) continue;
            var matched = new ArrayList<String>();
            for (String key : preferences.selection().preferred()) {
                if (matches(key, candidate)) matched.add(preferences.available().get(key).name());
            }
            var matchedSource = new ArrayList<String>();
            var unconfirmedSource = new ArrayList<String>();
            for (SourceLabel label : sourceLabels) {
                (matches(label.key(), candidate) ? matchedSource : unconfirmedSource).add(label.name());
            }
            int score = (int) (nameOverlap * 3 + categoryOverlap * 4);
            matches.add(new Suggestion(candidate, score, List.copyOf(matched), categoryOverlap > 0,
                    List.copyOf(matchedSource), List.copyOf(unconfirmedSource)));
        }
        return matches.stream().sorted(Comparator
                        .comparingInt((Suggestion suggestion) -> suggestion.matchedPreferences().size()).reversed()
                        .thenComparing(Comparator.comparingInt(
                                (Suggestion suggestion) -> suggestion.matchedSourceLabels().size()).reversed())
                        .thenComparing(Comparator.comparingInt(Suggestion::similarityScore).reversed())
                        .thenComparing(suggestion -> suggestion.product().name(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(suggestion -> suggestion.product().id()))
                .limit(5).toList();
    }

    private boolean matches(String key, RetailerProduct product) {
        if (key.startsWith("label:")) {
            try {
                ProductLabel label = ProductLabel.valueOf(key.substring("label:".length()));
                List<String> aliases = switch (label) {
                    case BIO -> List.of("bio", "organic", "en:organic");
                    case VEGAN -> List.of("vegan", "en:vegan");
                    case VEGETARIAN -> List.of("vegetarian", "vegetarisch", "en:vegetarian");
                    case LACTOSE_FREE -> List.of("lactose-free", "laktosefrei", "lactose free");
                    case GLUTEN_FREE -> List.of("gluten-free", "glutenfrei", "gluten free");
                    case ALCOHOL_FREE -> List.of("alcohol-free", "alkoholfrei", "alcohol free");
                    case CAFFEINE_FREE -> List.of("caffeine-free", "koffeinfrei", "caffeine free");
                    case PALM_OIL_FREE -> List.of("palm-oil-free", "ohne palmöl", "palm oil free");
                    case NO_ADDED_SUGAR -> List.of("no-added-sugar", "ohne zuckerzusatz", "no added sugar");
                };
                return containsAny(product.preferenceTags(), aliases)
                        || containsAny(product.preferenceTags(), List.copyOf(OpenFoodFactsTags.equivalentLabelTags(label)));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        if (key.startsWith("off:")) {
            String tag = key.substring(4).toLowerCase(Locale.ROOT);
            String canonical = tag;
            int languageSeparator = tag.indexOf(':');
            if (languageSeparator >= 0) tag = tag.substring(languageSeparator + 1);
            return containsAny(product.preferenceTags(), List.of(canonical, tag));
        }
        if (key.equals("content:milk")) {
            Set<String> allergens = words(nullToEmpty(product.allergens()) + " " + nullToEmpty(product.traces()));
            return Set.of("milk", "milch", "lait").stream().anyMatch(allergens::contains);
        }
        return false;
    }

    private List<SourceLabel> sourceLabels(Product source) {
        var display = ProductDisplay.from(source);
        var labels = new java.util.LinkedHashMap<String, SourceLabel>();
        for (ProductLabel label : display.editorialLabels()) {
            String key = "label:" + label.name();
            labels.put(key, new SourceLabel(key, label.getDisplayName() + " (Katalog)"));
        }
        for (var tag : display.offLabels()) {
            String raw = tag.raw().toLowerCase(Locale.ROOT);
            String key = java.util.Arrays.stream(ProductLabel.values())
                    .filter(label -> OpenFoodFactsTags.equivalentLabelTags(label).contains(raw))
                    .map(label -> "label:" + label.name()).findFirst().orElse("off:" + raw);
            labels.putIfAbsent(key, new SourceLabel(key, tag.display() + " (OFF)"));
        }
        return List.copyOf(labels.values());
    }

    private boolean containsAny(String data, List<String> aliases) {
        Set<String> tags = java.util.Arrays.stream(nullToEmpty(data).split("[,;|]"))
                .map(String::strip).map(tag -> tag.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        return aliases.stream().map(tag -> tag.toLowerCase(Locale.ROOT)).anyMatch(tags::contains);
    }

    private Set<String> words(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(normalized.split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() > 2)
                .filter(word -> !Set.of("und", "mit", "ohne", "the", "product").contains(word))
                .collect(Collectors.toSet());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
