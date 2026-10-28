package de.heinzdanner.produktkompass.cart;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.OpenFoodFactsLabel;
import de.heinzdanner.produktkompass.product.OpenFoodFactsLabelRepository;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.usertag.UserTagService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class CartTagCatalog {
    static final Set<String> MILK_LABELS = Set.of("en:milk", "en:contains-milk", "en:contains-milch",
            "de:milch", "de:enthalt-milch", "de:enthält-milch", "de:enthaelt-milch",
            "fr:enthalt-milch", "fr:enthält-milch", "fr:contains-milk", "fr:contains-milch");
    static final Set<String> MILK_ALLERGENS = Set.of("en:milk", "de:milch", "fr:lait",
            "milk", "milch", "lait");

    public record Tag(String key, String name, String source, ProductLabel label, String offTag, Long personalId) {
        public String searchText() {
            String aliases = label == null ? "" : label.name() + " "
                    + String.join(" ", OpenFoodFactsTags.equivalentLabelTags(label));
            String terms = name + " " + source + " " + aliases + " " + (offTag == null ? "" : offTag);
            if (key.equals("content:milk")) terms += " " + String.join(" ", MILK_LABELS) + " Spuren traces";
            return terms + " " + terms.replace('-', ' ').replace('_', ' ').replace(':', ' ');
        }
    }

    public record SelectedTag(Tag tag, TagState state) {}

    public record Resolved(Map<String, Tag> available, CartSearchPreferences.Selection selection,
                           List<SelectedTag> options) {}

    private final OpenFoodFactsLabelRepository offLabels;
    private final UserTagService personalTags;

    public CartTagCatalog(OpenFoodFactsLabelRepository offLabels, UserTagService personalTags) {
        this.offLabels = offLabels;
        this.personalTags = personalTags;
    }

    public Resolved resolve(String username, List<String> preferred, List<String> excluded, boolean includeOptions) {
        Map<String, Tag> available = new LinkedHashMap<>();
        for (ProductLabel label : ProductLabel.values()) {
            available.put("label:" + label.name(), new Tag("label:" + label.name(), label.getDisplayName(),
                    OpenFoodFactsTags.equivalentLabelTags(label).isEmpty() ? "Produkt" : "Produkt / OFF",
                    label, null, null));
        }
        Map<String, String> aliases = new LinkedHashMap<>();
        available.put("content:milk", new Tag("content:milk", "Milch (einschließlich Spuren)",
                "Produkt / OFF", null, "en:milk", null));
        for (String raw : MILK_LABELS) aliases.put("off:" + raw, "content:milk");
        for (NutritionGrade grade : NutritionGrade.values()) {
            if (grade == NutritionGrade.UNKNOWN) continue;
            String key = "nutrition:" + grade.name();
            String raw = "en:nutriscore-grade-" + grade.name().toLowerCase(Locale.ROOT);
            available.put(key, new Tag(key, "Nutri-Score " + grade.name(), "Produkt / OFF", null, raw, null));
            aliases.put("off:" + raw, key);
        }
        var selectedOffTags = Stream.concat(preferred.stream(), excluded.stream())
                .filter(key -> key.startsWith("off:")).map(key -> key.substring(4).toLowerCase(Locale.ROOT))
                .distinct().toList();
        var relevantOffLabels = includeOptions ? offLabels.findAll()
                : selectedOffTags.isEmpty() ? List.<OpenFoodFactsLabel>of()
                : offLabels.findAllByNormalizedTagIn(selectedOffTags);
        for (var label : relevantOffLabels) {
            String raw = label.getCanonicalTag().toLowerCase(Locale.ROOT);
            if (aliases.containsKey("off:" + raw)) continue;
            ProductLabel equivalent = Arrays.stream(ProductLabel.values())
                    .filter(value -> OpenFoodFactsTags.equivalentLabelTags(value).contains(raw))
                    .findFirst().orElse(null);
            if (equivalent != null) {
                aliases.put("off:" + raw, "label:" + equivalent.name());
            } else {
                String key = "off:" + raw;
                available.put(key, new Tag(key, OpenFoodFactsTags.labels(raw).getFirst().display(),
                        "OFF", null, raw, null));
            }
        }
        for (var tag : personalTags.getTagOptionsForUser(username)) {
            String key = "personal:" + tag.getId();
            available.put(key, new Tag(key, tag.getName(), "Mein Tag", null, null, tag.getId()));
        }
        List<String> preferredKeys = normalize(preferred, available, aliases);
        List<String> excludedKeys = normalize(excluded, available, aliases);
        if (preferredKeys.stream().anyMatch(excludedKeys::contains)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ein Tag darf nur einen Zustand haben.");
        }
        var options = includeOptions ? available.values().stream()
                .sorted(Comparator.comparing(Tag::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Tag::key))
                .map(tag -> new SelectedTag(tag, preferredKeys.contains(tag.key()) ? TagState.PREFERRED
                        : excludedKeys.contains(tag.key()) ? TagState.EXCLUDED : TagState.NEUTRAL)).toList()
                : List.<SelectedTag>of();
        return new Resolved(Map.copyOf(available),
                new CartSearchPreferences.Selection(preferredKeys, excludedKeys), options);
    }

    private List<String> normalize(List<String> keys, Map<String, Tag> available, Map<String, String> aliases) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String key : keys) {
            if (key.startsWith("off:")) key = key.toLowerCase(Locale.ROOT);
            String canonical = aliases.getOrDefault(key, key);
            if (!available.containsKey(canonical)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekannter oder nicht eigener Tag.");
            }
            normalized.add(canonical);
        }
        return List.copyOf(normalized);
    }
}
