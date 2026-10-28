package de.heinzdanner.produktkompass.cart;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductSearch;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CartSearchSpecifications {
    private CartSearchSpecifications() {}

    public static Specification<Product> matching(String username, String text, CartTagCatalog.Resolved tags) {
        String phrase = text == null ? "" : text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        List<String> words = phrase.isEmpty() ? List.of() : Arrays.stream(phrase.split(" ")).distinct().toList();
        Specification<Product> filter = (root, query, builder) -> builder.conjunction();
        for (String word : words) {
            filter = filter.and(ProductSearch.matching(word, null, null, null, null, null, null, username));
        }
        for (String key : tags.selection().excluded()) {
            filter = filter.and(Specification.not(matches(tags.available().get(key), username)));
        }
        return filter.and(ranking(username, phrase, words, tags));
    }

    private static Specification<Product> ranking(String username, String phrase, List<String> words,
                                                   CartTagCatalog.Resolved tags) {
        return (root, query, builder) -> {
            if (query.getResultType() != Long.class) {
                Expression<Integer> score = builder.literal(0);
                for (String key : tags.selection().preferred()) {
                    score = builder.sum(score, builder.<Integer>selectCase()
                            .when(matches(tags.available().get(key), username).toPredicate(root, query, builder), 1)
                            .otherwise(0));
                }
                var order = new ArrayList<jakarta.persistence.criteria.Order>();
                if (username != null) {
                    order.add(builder.desc(ProductSearch.favoritePriority(root, query, builder, username)));
                }
                if (!tags.selection().preferred().isEmpty()) order.add(builder.desc(score));
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
    }

    private static Specification<Product> matches(CartTagCatalog.Tag tag, String username) {
        if (tag.personalId() != null) {
            return ProductSearch.matching(null, null, null, null, null, null, tag.personalId(), username);
        }
        return (root, query, builder) -> {
            // EXISTS remains two-valued when negated, even without OFF data.
            var subquery = query.subquery(Long.class);
            var candidate = subquery.from(Product.class);
            var off = candidate.join("openFoodFactsData", JoinType.LEFT);
            var alternatives = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (tag.label() != null) {
                alternatives.add(builder.isMember(tag.label(), candidate.get("labels")));
                for (String raw : OpenFoodFactsTags.equivalentLabelTags(tag.label())) {
                    alternatives.add(ProductSearch.containsTag(builder, off.get("labelTags"), raw));
                }
            } else if (tag.key().equals("content:milk")) {
                for (String raw : CartTagCatalog.MILK_LABELS) {
                    alternatives.add(ProductSearch.containsTag(builder, off.get("labelTags"), raw));
                }
                for (String field : List.of("allergenTags", "traceTags", "allergens", "traces")) {
                    for (String raw : CartTagCatalog.MILK_ALLERGENS) {
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
            subquery.select(candidate.get("id")).where(builder.equal(candidate.get("id"), root.get("id")),
                    builder.or(alternatives.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            return builder.exists(subquery);
        };
    }
}
