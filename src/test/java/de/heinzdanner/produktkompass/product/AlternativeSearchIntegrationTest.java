package de.heinzdanner.produktkompass.product;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true")
@Transactional
class AlternativeSearchIntegrationTest {

    @Autowired private ProductService service;
    @MockitoSpyBean private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private EntityManager entityManager;

    @Test
    void databaseRanksDistinctCandidatesAndLimitsToSixWithStableTies() {
        Category category = categories.save(new Category("Alternative ranking", "Test"));
        Category second = categories.save(new Category("Alternative ranking second", "Test"));
        Product base = save("Ranking base", category, NutritionGrade.A, PackagingType.GLASS, true,
                ProductLabel.VEGAN, ProductLabel.BIO);
        Product weak = save("Ranking weak", category, NutritionGrade.E, PackagingType.PLASTIC, false);
        Product medium = save("Ranking medium", category, NutritionGrade.A, PackagingType.GLASS, true);
        Product strong = save("Ranking strong", category, NutritionGrade.A, PackagingType.GLASS, true,
                ProductLabel.VEGAN, ProductLabel.BIO);
        strong.addCategory(second);
        base.addCategory(second);
        for (int i = 0; i < 8; i++) {
            save("Ranking tie " + i, category, NutritionGrade.A, PackagingType.GLASS, true, ProductLabel.VEGAN);
        }
        Product unrelated = products.save(new Product("Ranking unrelated", "Test"));
        entityManager.flush();
        entityManager.clear();
        clearInvocations(products);

        var result = service.getAlternativeProducts(base.getId());
        assertThat(result).hasSize(6);
        assertThat(result.getFirst().getId()).isEqualTo(strong.getId());
        assertThat(result).extracting(Product::getId).doesNotHaveDuplicates()
                .doesNotContain(base.getId(), weak.getId(), medium.getId(), unrelated.getId());
        assertThat(result.subList(1, 6)).extracting(Product::getId).isSorted();
        assertThat(result.getFirst().getCategories()).hasSize(2);
        assertThat(service.getAlternativeProductResponses(base.getId())).hasSize(6);
        verify(products, never()).findAllWithDetails();
    }

    @Test
    void matchingNullAttributesRetainPreviousScoringAndNoCategoryReturnsNoResults() {
        Category category = categories.save(new Category("Alternative nulls", "Test"));
        Product base = save("Null base", category, null, null, null);
        Product different = save("Null different", category, NutritionGrade.A, PackagingType.GLASS, false);
        Product match = save("Null match", category, null, null, null);
        entityManager.flush();
        entityManager.clear();
        assertThat(service.getAlternativeProducts(base.getId())).extracting(Product::getId)
                .containsExactly(match.getId(), different.getId());

        Product noCategory = products.saveAndFlush(new Product("No category", "Test"));
        assertThat(service.getAlternativeProducts(noCategory.getId())).isEmpty();
        assertThat(service.getAlternativeProducts(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void editorialAlternativesTakePrecedenceEvenWithoutSharedCategoryOrLimit() {
        Product base = products.save(new Product("Editorial base", "Test"));
        Set<Product> editorial = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            editorial.add(products.save(new Product("Editorial alternative " + i, "Test")));
        }
        base.setAlternativeProducts(editorial);
        products.saveAndFlush(base);
        entityManager.clear();
        clearInvocations(products);
        assertThat(service.getAlternativeProductResponses(base.getId())).hasSize(7);
        verify(products, never()).findAutomaticAlternativeIds(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(products, never()).findAllWithDetails();
    }

    private Product save(String name, Category category, NutritionGrade grade, PackagingType packaging,
                         Boolean germany, ProductLabel... labels) {
        Product product = new Product(name, "Test");
        product.addCategory(category);
        product.setNutritionGrade(grade);
        product.setPackagingType(packaging);
        product.setMadeInGermany(germany);
        product.setLabels(new HashSet<>(Set.of(labels)));
        return products.save(product);
    }
}
