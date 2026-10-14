package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.usertag.UserTag;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

public final class ProductSearch {

    private ProductSearch() {
    }

    public static Specification<Product> matching(String text, Long brandId, Long categoryId,
                                                  ProductLabel label, NutritionGrade grade,
                                                  Boolean madeInGermany, Long tagId, String username) {
        return (product, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (text != null && !text.isBlank()) {
                String term = text.trim().toLowerCase(Locale.ROOT);
                String pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_") + "%";
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
                var openFoodFacts = product.join("openFoodFactsData", jakarta.persistence.criteria.JoinType.LEFT);
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("productName")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("brands")), pattern, '\\'));
                textMatches.add(builder.like(builder.lower(openFoodFacts.get("brandTags")), pattern, '\\'));
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
                    if (value.name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(term)) {
                        textMatches.add(builder.isMember(value, product.get("labels")));
                    }
                }
                predicates.add(builder.or(textMatches.toArray(Predicate[]::new)));
            }
            if (brandId != null) {
                predicates.add(builder.equal(product.get("brand").get("id"), brandId));
            }
            if (categoryId != null) {
                var categoryFilter = query.subquery(Long.class);
                var categoryProduct = categoryFilter.from(Product.class);
                categoryFilter.select(categoryProduct.get("id")).where(
                        builder.equal(categoryProduct.get("id"), product.get("id")),
                        builder.equal(categoryProduct.join("categories").get("id"), categoryId));
                predicates.add(builder.exists(categoryFilter));
            }
            if (label != null) {
                predicates.add(builder.isMember(label, product.get("labels")));
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
}
