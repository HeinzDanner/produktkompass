package de.heinzdanner.produktkompass.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByNameContainingIgnoreCase(String name);

    List<Product> findByBrandId(Long brandId);

    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.id = :categoryId")
    List<Product> findByCategoryId(@Param("categoryId") Long categoryId);
}
