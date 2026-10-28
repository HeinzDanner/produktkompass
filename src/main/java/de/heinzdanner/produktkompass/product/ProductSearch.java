package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.usertag.UserTag;
import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProductSearch {

    private ProductSearch() {
    }

    public static Specification<Product> quality(ProductQualityFilter filter) {
        return (product, query, builder) -> {
            if (filter == ProductQualityFilter.ALL) return builder.conjunction();
            var missingSources = builder.isEmpty(product.get("sources"));
            if (filter == ProductQualityFilter.MISSING_SOURCES) return missingSources;
            var unreviewed = query.subquery(Long.class);
            var candidate = unreviewed.from(Product.class);
            unreviewed.select(candidate.get("id")).where(
                    builder.equal(candidate.get("id"), product.get("id")),
                    builder.isNull(candidate.join("sources").get("verifiedAt")));
            var missingDate = builder.exists(unreviewed);
            return filter == ProductQualityFilter.MISSING_REVIEW_DATE
                    ? missingDate : builder.or(missingSources, missingDate);
        };
    }

    public static Specification<Product> offCategory(String category) {
        return (product, query, builder) -> {
            if (category == null) return builder.conjunction();
            var matches = query.subquery(Long.class);
            var data = matches.from(OpenFoodFactsData.class);
            matches.select(data.get("productId")).where(
                    builder.equal(data.get("productId"), product.get("id")),
                    builder.equal(data.join("categoryEntries"), category));
            return builder.exists(matches);
        };
    }

    public static Expression<Integer> favoritePriority(jakarta.persistence.criteria.Root<Product> product,
            jakarta.persistence.criteria.CriteriaQuery<?> query, CriteriaBuilder builder, String username) {
        var favorites = query.subquery(Long.class);
        var owner = favorites.from(de.heinzdanner.produktkompass.user.AppUser.class);
        favorites.select(owner.get("id")).where(builder.equal(owner.get("username"), username),
                builder.<Product, Set<Product>>isMember(product, owner.get("favoriteProducts")));
        return builder.<Integer>selectCase().when(builder.exists(favorites), 1).otherwise(0);
    }

    public static Specification<Product> ranking(String username, String text) {
        return (product, query, builder) -> {
            if (query.getResultType() != Long.class) {
                var order = new ArrayList<Order>();
                if (username != null) {
                    order.add(builder.desc(favoritePriority(product, query, builder, username)));
                }
                order.addAll(textOrder(product, builder, text));
                query.orderBy(order);
            }
            return builder.conjunction();
        };
    }

    public static List<Order> textOrder(Root<Product> product, CriteriaBuilder builder, String text) {
        String phrase = normalizeText(text);
        var order = new ArrayList<Order>();
        if (!phrase.isEmpty()) {
            Expression<String> name = builder.lower(product.get("name"));
            var off = product.join("openFoodFactsData", JoinType.LEFT);
            Expression<String> offName = builder.lower(off.get("productName"));
            String literal = escapeLike(phrase);
            Expression<Integer> phraseScore = builder.<Integer>selectCase()
                    .when(builder.or(builder.equal(name, phrase), builder.equal(offName, phrase)), 3)
                    .when(builder.or(builder.like(name, literal + "%", '\\'),
                            builder.like(offName, literal + "%", '\\')), 2)
                    .when(builder.or(builder.like(name, "%" + literal + "%", '\\'),
                            builder.like(offName, "%" + literal + "%", '\\')), 1)
                    .otherwise(0);
            Expression<Integer> nameScore = builder.literal(0);
            Expression<Integer> brandScore = builder.literal(0);
            Expression<String> brand = builder.lower(product.join("brand", JoinType.LEFT).get("name"));
            for (String word : words(phrase)) {
                String pattern = "%" + escapeLike(word) + "%";
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
        order.add(builder.asc(product.get("name")));
        order.add(builder.asc(product.get("id")));
        return order;
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static List<String> words(String phrase) {
        return phrase.isEmpty() ? List.of() : Arrays.stream(phrase.split(" ")).distinct().toList();
    }

    public static Specification<Product> matching(String text, Long brandId, Long categoryId,
                                                  ProductLabel label, NutritionGrade grade,
                                                  Boolean madeInGermany, Long tagId, String username) {
        return matching(text, brandId, categoryId, label, grade, madeInGermany, tagId, username, false);
    }

    /**
     * {@code brandId} matches app assignments and exact OFF brand names/tags. With
     * {@code includeOpenFoodFactsLabels} the label filter additionally matches exact equivalent OFF label tags
     * (unverified). Free text also matches stored OFF brand/label values and known human label terms.
     */
    public static Specification<Product> matching(String text, Long brandId, Long categoryId,
                                                  ProductLabel label, NutritionGrade grade,
                                                  Boolean madeInGermany, Long tagId, String username,
                                                  boolean includeOpenFoodFactsLabels) {
        return (product, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            Join<Product, OpenFoodFactsData> openFoodFacts = null;
            for (String term : words(normalizeText(text))) {
                String pattern = "%" + escapeLike(term) + "%";
                var categorySearch = query.subquery(Long.class);
                var categoryProduct = categorySearch.from(Product.class);
                categorySearch.select(categoryProduct.get("id")).where(
                        builder.equal(categoryProduct.get("id"), product.get("id")),
                        builder.like(builder.lower(categoryProduct.join("categories").get("name")), pattern, '\\'));
                var textMatches = new ArrayList<Predicate>();
                textMatches.add(builder.like(builder.lower(product.get("name")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(product.get("description")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(product.join("brand", jakarta.persistence.criteria.JoinType.LEFT)
                        .get("name")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(product.join("company", jakarta.persistence.criteria.JoinType.LEFT)
                        .get("name")), pattern, '\\'));
                if (openFoodFacts == null) {
                    openFoodFacts = product.join("openFoodFactsData", JoinType.LEFT);
                }
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("productName")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("brands")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("brandTags")), pattern, '\\'));
                for (String tag : OpenFoodFactsTags.labelTagsMatchingText(term)) {
                    textMatches.add(containsTag(builder, openFoodFacts.get("labelTags"), tag));
                }
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("brandOwner")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("categories")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("categoryTags")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("packaging")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("packagingTags")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("labelTags")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("originTags")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("manufacturingPlaceTags")), pattern, '\\'));
                textMatches.add(builder.exists(categorySearch));
                for (ProductLabel value : ProductLabel.values()) {
                    if (value.name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(term)
                            || value.getDisplayName().toLowerCase(Locale.ROOT).contains(term)) {
                        textMatches.add(builder.isMember(value, product.get("labels")));
                    }
                }
                predicates.add(builder.or(textMatches.toArray(Predicate[]::new)));
            }
            if (brandId != null) {
                if (openFoodFacts == null) {
                    openFoodFacts = product.join("openFoodFactsData", JoinType.LEFT);
                }
                var brandFilter = query.subquery(Long.class);
                var selectedBrand = brandFilter.from(Brand.class);
                Expression<String> name = builder.lower(builder.trim(selectedBrand.get("name")));
                Expression<String> slug = builder.function("REPLACE", String.class, name,
                        builder.literal(" "), builder.literal("-"));
                brandFilter.select(selectedBrand.get("id")).where(
                        builder.equal(selectedBrand.get("id"), brandId),
                        builder.or(containsBrand(builder, openFoodFacts.get("brands"), name, false),
                                containsBrand(builder, openFoodFacts.get("brandTags"), slug, false),
                                containsBrand(builder, openFoodFacts.get("brandTags"), slug, true)));
                predicates.add(builder.or(
                        builder.equal(product.join("brand", JoinType.LEFT).get("id"), brandId),
                        builder.exists(brandFilter)));
            }
            if (categoryId != null) {
                var categoryFilter = query.subquery(Long.class);
                var categoryProduct = categoryFilter.from(Product.class);
                categoryFilter.select(categoryProduct.get("id")).where(
                        builder.equal(categoryProduct.get("id"), product.get("id")),
                        builder.equal(categoryProduct.join("categories").get("id"), categoryId));
                var offFilter = query.subquery(Long.class);
                var offData = offFilter.from(OpenFoodFactsData.class);
                var mapping = offFilter.from(OffCategoryMapping.class);
                offFilter.select(offData.get("productId")).where(
                        builder.equal(offData.get("productId"), product.get("id")),
                        builder.equal(offData.join("categoryEntries"), mapping.get("offValue")),
                        builder.equal(mapping.get("category").get("id"), categoryId));
                predicates.add(builder.or(builder.exists(categoryFilter), builder.exists(offFilter)));
            }
            if (label != null) {
                var labelMatches = new ArrayList<Predicate>();
                labelMatches.add(builder.isMember(label, product.get("labels")));
                if (includeOpenFoodFactsLabels) {
                    if (openFoodFacts == null) {
                        openFoodFacts = product.join("openFoodFactsData", JoinType.LEFT);
                    }
                    for (String tag : OpenFoodFactsTags.equivalentLabelTags(label)) {
                        labelMatches.add(containsTag(builder, openFoodFacts.get("labelTags"), tag));
                    }
                }
                predicates.add(builder.or(labelMatches.toArray(Predicate[]::new)));
            }
            if (grade != null) {
                predicates.add(builder.equal(product.get("nutritionGrade"), grade));
            }
            if (madeInGermany != null) {
                predicates.add(builder.equal(product.get("madeInGermany"), madeInGermany));
            }
            if (tagId != null) {
                var tagFilter = query.subquery(Long.class);
                var tag = tagFilter.from(UserTag.class);
                tagFilter.select(tag.get("id")).where(
                        builder.equal(tag.get("id"), tagId),
                        builder.equal(tag.get("owner").get("username"), username),
                        builder.or(builder.<Product, Set<Product>>isMember(product, tag.get("products")),
                                builder.<Brand, Set<Brand>>isMember(product.get("brand"), tag.get("brands"))));
                predicates.add(builder.exists(tagFilter));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate containsBrand(CriteriaBuilder builder, Expression<String> column,
                                           Expression<String> name, boolean languagePrefix) {
        Expression<String> normalized = builder.function("REPLACE", String.class, builder.lower(builder.trim(column)),
                builder.literal(", "), builder.literal(","));
        normalized = builder.function("REPLACE", String.class, normalized,
                builder.literal(" ,"), builder.literal(","));
        Expression<String> escaped = name;
        for (String character : new String[]{"\\", "%", "_"}) {
            escaped = builder.function("REPLACE", String.class, escaped,
                    builder.literal(character), builder.literal("\\" + character));
        }
        Expression<String> pattern = builder.concat(
                builder.concat(languagePrefix ? "%,__:" : "%,", escaped), ",%");
        return builder.like(builder.concat(builder.concat(",", normalized), ","), pattern, '\\');
    }

    /** Exact, case-insensitive match of one entry in a comma-separated OFF tag column. */
    public static Predicate containsTag(CriteriaBuilder builder, Expression<String> column, String tag) {
        Expression<String> normalized = builder.function("REPLACE", String.class, builder.lower(column),
                builder.literal(", "), builder.literal(","));
        Expression<String> delimited = builder.concat(builder.concat(",", normalized), ",");
        String escaped = escapeLike(tag.toLowerCase(Locale.ROOT));
        return builder.like(delimited, "%," + escaped + ",%", '\\');
    }

    public static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
