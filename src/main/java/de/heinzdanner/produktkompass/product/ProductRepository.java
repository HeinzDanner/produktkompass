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
    @EntityGraph(attributePaths = {"brand", "company"})
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.categories WHERE p.id IN :ids")
    List<Product> findPageCategories(@Param("ids") List<Long> ids);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.sources WHERE p.id IN :ids")
    List<Product> findPageSources(@Param("ids") List<Long> ids);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.company c LEFT JOIN FETCH p.categories cat LEFT JOIN FETCH p.sources s LEFT JOIN FETCH p.labels")
    List<Product> findAllWithDetails();

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.company c LEFT JOIN FETCH p.categories cat LEFT JOIN FETCH p.sources s LEFT JOIN FETCH p.labels LEFT JOIN FETCH p.alternativeProducts alt LEFT JOIN FETCH alt.brand WHERE p.id = :id")
    Optional<Product> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.company c LEFT JOIN FETCH p.categories cat LEFT JOIN FETCH p.sources s LEFT JOIN FETCH p.labels LEFT JOIN FETCH p.alternativeProducts alt LEFT JOIN FETCH alt.brand WHERE p.alternativeProducts IS NOT EMPTY")
    List<Product> findAllWithAlternatives();

    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.id = :categoryId")
    List<Product> findByCategoryId(@Param("categoryId") Long categoryId);

    List<Product> findByBrandId(Long brandId);
}
