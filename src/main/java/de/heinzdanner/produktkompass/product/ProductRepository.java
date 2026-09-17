package de.heinzdanner.produktkompass.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.company c LEFT JOIN FETCH p.categories cat LEFT JOIN FETCH p.sources s")
    List<Product> findAllWithDetails();

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.company c LEFT JOIN FETCH p.categories cat LEFT JOIN FETCH p.sources s LEFT JOIN FETCH p.alternativeProducts alt LEFT JOIN FETCH alt.brand WHERE p.id = :id")
    Optional<Product> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.company c LEFT JOIN FETCH p.categories cat LEFT JOIN FETCH p.sources s LEFT JOIN FETCH p.alternativeProducts alt LEFT JOIN FETCH alt.brand WHERE p.alternativeProducts IS NOT EMPTY")
    List<Product> findAllWithAlternatives();

    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.id = :categoryId")
    List<Product> findByCategoryId(@Param("categoryId") Long categoryId);
}
