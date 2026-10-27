package de.heinzdanner.produktkompass.cart;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags;
import de.heinzdanner.produktkompass.product.OpenFoodFactsLabelRepository;
import de.heinzdanner.produktkompass.product.OpenFoodFactsLabel;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.product.ProductSearch;
import de.heinzdanner.produktkompass.product.ProductService;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.dto.ProductSummary;
import de.heinzdanner.produktkompass.usertag.UserTagService;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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
public class CartSearchService {
    private static final Set<String> MILK_LABELS = Set.of("en:milk", "en:contains-milk", "en:contains-milch",
            "de:milch", "de:enthalt-milch", "de:enthält-milch", "de:enthaelt-milch",
            "fr:enthalt-milch", "fr:enthält-milch", "fr:contains-milk", "fr:contains-milch");
    private static final Set<String> MILK_ALLERGENS = Set.of("en:milk", "de:milch", "fr:lait",
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
    public record SelectedTag(Tag tag, String state) {}
    public record Results(Page<ProductSummary> page, List<SelectedTag> tags,
                          List<String> preferred, List<String> excluded) {}

    private final ProductService products;
    private final OpenFoodFactsLabelRepository offLabels;
    private final UserTagService personalTags;

    public CartSearchService(ProductService products, OpenFoodFactsLabelRepository offLabels,
                             UserTagService personalTags) {
        this.products = products;
        this.offLabels = offLabels;
        this.personalTags = personalTags;
    }

    public Results search(String username, String text, List<String> preferred, List<String> excluded, int page) {
        return search(username, text, preferred, excluded, page, true);
    }

    public Results search(String username, String text, List<String> preferred, List<String> excluded, int page,
                          boolean includeTagOptions) {
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
        var relevantOffLabels = includeTagOptions ? offLabels.findAll()
                : selectedOffTags.isEmpty() ? List.<OpenFoodFactsLabel>of()
                : offLabels.findAllByNormalizedTagIn(selectedOffTags);
        for (var label : relevantOffLabels) {
            String raw = label.getCanonicalTag().toLowerCase(Locale.ROOT);
            if (aliases.containsKey("off:" + raw)) continue;
            ProductLabel equivalent = java.util.Arrays.stream(ProductLabel.values())
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
        String phrase = text == null ? "" : text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        List<String> words = phrase.isEmpty() ? List.of() : Arrays.stream(phrase.split(" ")).distinct().toList();
        Specification<Product> filter = (root, query, builder) -> builder.conjunction();
        for (String word : words) {
            filter = filter.and(ProductSearch.matching(word, null, null, null, null, null, null, username));
        }
        for (String key : excludedKeys) {
            filter = filter.and(Specification.not(matches(available.get(key), username)));
        }
        Specification<Product> ranking = (root, query, builder) -> {
            if (query.getResultType() != Long.class) {
                Expression<Integer> score = builder.literal(0);
                for (String key : preferredKeys) {
                    score = builder.sum(score, builder.<Integer>selectCase()
                            .when(matches(available.get(key), username).toPredicate(root, query, builder), 1)
                            .otherwise(0));
                }
                var order = new ArrayList<jakarta.persistence.criteria.Order>();
                if (!preferredKeys.isEmpty()) order.add(builder.desc(score));
                if (!words.isEmpty()) {
                    Expression<String> name = builder.lower(root.get("name"));
                    var off = root.join("openFoodFactsData", JoinType.LEFT);
                    Expression<String> offName = builder.lower(off.get("productName"));
                    String literal = ProductSearch.escapeLike(phrase);
                    Expression<Integer> phraseScore = builder.<Integer>selectCase()
                            .when(builder.or(builder.equal(name, phrase), builder.equal(offName, phrase)), 3)
                            .when(builder.or(builder.like(name, literal + "%", '\\'),
                                    builder.like(offName, literal + "%", '\\')), 2)
                            .when(builder.or(builder.like(name, "%" + literal + "%", '\\'),
                                    builder.like(offName, "%" + literal + "%", '\\')), 1)
                            .otherwise(0);
                    Expression<Integer> nameScore = builder.literal(0);
                    Expression<Integer> brandScore = builder.literal(0);
                    Expression<String> brand = builder.lower(root.join("brand", JoinType.LEFT).get("name"));
                    for (String word : words) {
                        String pattern = "%" + ProductSearch.escapeLike(word) + "%";
                        nameScore = builder.sum(nameScore, builder.<Integer>selectCase()
                                .when(builder.or(builder.like(name, pattern, '\\'),
                                        builder.like(offName, pattern, '\\')), 1).otherwise(0));
                        brandScore = builder.sum(brandScore, builder.<Integer>selectCase()
                                .when(builder.or(builder.like(brand, pattern, '\\'),
                                        builder.like(builder.lower(off.get("brands")), pattern, '\\')), 1).otherwise(0));
                    }
                    order.add(builder.desc(phraseScore));
                    order.add(builder.desc(nameScore));
                    order.add(builder.desc(brandScore));
                }
                order.add(builder.asc(root.get("name")));
                order.add(builder.asc(root.get("id")));
                query.orderBy(order);
            }
            return builder.conjunction();
        };
        var resultPage = products.searchRankedPage(filter.and(ranking), page, 12);
        var tags = includeTagOptions ? available.values().stream()
                .sorted(Comparator.comparing(Tag::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Tag::key))
                .map(tag -> new SelectedTag(tag, preferredKeys.contains(tag.key()) ? "preferred"
                        : excludedKeys.contains(tag.key()) ? "excluded" : "neutral")).toList() : List.<SelectedTag>of();
        return new Results(resultPage, tags, preferredKeys, excludedKeys);
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
        return new ArrayList<>(normalized);
    }

    private Specification<Product> matches(Tag tag, String username) {
        if (tag.personalId() != null) {
            return ProductSearch.matching(null, null, null, null, null, null, tag.personalId(), username);
        }
        return (root, query, builder) -> {
            // EXISTS is deliberately two-valued, including products without OFF data when negated.
            var subquery = query.subquery(Long.class);
            var candidate = subquery.from(Product.class);
            var off = candidate.join("openFoodFactsData", jakarta.persistence.criteria.JoinType.LEFT);
            var alternatives = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (tag.label() != null) {
                alternatives.add(builder.isMember(tag.label(), candidate.get("labels")));
                for (String raw : OpenFoodFactsTags.equivalentLabelTags(tag.label())) {
                    alternatives.add(ProductSearch.containsTag(builder, off.get("labelTags"), raw));
                }
            } else {
                if (tag.key().equals("content:milk")) {
                    for (String raw : MILK_LABELS) {
                        alternatives.add(ProductSearch.containsTag(builder, off.get("labelTags"), raw));
                    }
                    for (String field : List.of("allergenTags", "traceTags", "allergens", "traces")) {
                        for (String raw : MILK_ALLERGENS) {
                            alternatives.add(ProductSearch.containsTag(builder, off.get(field), raw));
                        }
                    }
                } else {
                    alternatives.add(ProductSearch.containsTag(builder, off.get("labelTags"), tag.offTag()));
                    if (tag.key().startsWith("nutrition:")) {
                        String grade = tag.key().substring("nutrition:".length());
                        alternatives.add(builder.equal(candidate.get("nutritionGrade"), NutritionGrade.valueOf(grade)));
                        alternatives.add(builder.equal(builder.upper(builder.trim(off.get("nutritionGrade"))), grade));
                    }
                }
            }
            subquery.select(candidate.get("id")).where(builder.equal(candidate.get("id"), root.get("id")),
                    builder.or(alternatives.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            return builder.exists(subquery);
        };
    }
}
