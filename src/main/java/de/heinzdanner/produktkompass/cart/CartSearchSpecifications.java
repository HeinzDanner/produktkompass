package de.heinzdanner.produktkompass.cart;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductSearch;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class CartSearchSpecifications {
    private CartSearchSpecifications() {}

    public static Specification<Product> matching(String username, String text, CartTagCatalog.Resolved tags) {
        Specification<Product> filter = ProductSearch.matching(text, null, null, null, null, null, null, username);
        for (String key : tags.selection().excluded()) {
            filter = filter.and(Specification.not(matches(tags.available().get(key), username)));
        }
        return filter.and(ranking(username, text, tags));
    }

    private static Specification<Product> ranking(String username, String text,
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
                order.addAll(ProductSearch.textOrder(root, builder, text));
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
