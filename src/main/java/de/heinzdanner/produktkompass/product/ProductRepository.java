package de.heinzdanner.produktkompass.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Override
    @EntityGraph(attributePaths = {"brand", "company", "openFoodFactsData"})
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.categories WHERE p.id IN :ids")
    List<Product> findPageCategories(@Param("ids") List<Long> ids);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.sources WHERE p.id IN :ids")
    List<Product> findPageSources(@Param("ids") List<Long> ids);

    @Query("""
            SELECT candidate.id FROM Product candidate, Product base
            WHERE base.id = :productId AND candidate.id <> base.id
              AND EXISTS (
                SELECT category.id FROM Product categoryProduct JOIN categoryProduct.categories category
                WHERE categoryProduct.id = candidate.id AND category MEMBER OF base.categories
              )
            ORDER BY (
                CASE WHEN candidate.nutritionGrade = base.nutritionGrade
                  OR (candidate.nutritionGrade IS NULL AND base.nutritionGrade IS NULL) THEN 2 ELSE 0 END
                + CASE WHEN candidate.packagingType = base.packagingType
                  OR (candidate.packagingType IS NULL AND base.packagingType IS NULL) THEN 1 ELSE 0 END
                + CASE WHEN candidate.madeInGermany = base.madeInGermany
                  OR (candidate.madeInGermany IS NULL AND base.madeInGermany IS NULL) THEN 1 ELSE 0 END
                + (SELECT COUNT(DISTINCT matchingLabel) FROM Product labelProduct JOIN labelProduct.labels matchingLabel
                   WHERE labelProduct.id = candidate.id AND matchingLabel MEMBER OF base.labels)
            ) DESC, candidate.id ASC
            """)
    List<Long> findAutomaticAlternativeIds(@Param("productId") Long productId, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "company", "openFoodFactsData"})
    @Query("SELECT p FROM Product p WHERE p.id IN :ids")
    List<Product> findAlternativeProductsByIds(@Param("ids") List<Long> ids);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.labels WHERE p.id IN :ids")
    List<Product> findPageLabels(@Param("ids") List<Long> ids);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.alternativeProducts a LEFT JOIN FETCH a.brand WHERE p.id IN :ids")
    List<Product> findPageAlternatives(@Param("ids") List<Long> ids);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.company c LEFT JOIN FETCH p.categories cat LEFT JOIN FETCH p.sources s LEFT JOIN FETCH p.labels LEFT JOIN FETCH p.openFoodFactsData")
    List<Product> findAllWithDetails();

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.company c LEFT JOIN FETCH p.categories cat LEFT JOIN FETCH p.sources s LEFT JOIN FETCH p.labels LEFT JOIN FETCH p.alternativeProducts alt LEFT JOIN FETCH alt.brand LEFT JOIN FETCH p.openFoodFactsData WHERE p.id = :id")
    Optional<Product> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.company c LEFT JOIN FETCH p.categories cat LEFT JOIN FETCH p.sources s LEFT JOIN FETCH p.labels LEFT JOIN FETCH p.alternativeProducts alt LEFT JOIN FETCH alt.brand LEFT JOIN FETCH p.openFoodFactsData WHERE p.alternativeProducts IS NOT EMPTY")
    List<Product> findAllWithAlternatives();

    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.id = :categoryId")
    List<Product> findByCategoryId(@Param("categoryId") Long categoryId);

    List<Product> findByBrandId(Long brandId);
}
