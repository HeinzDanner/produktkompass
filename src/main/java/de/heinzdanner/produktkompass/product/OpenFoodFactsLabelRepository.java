package de.heinzdanner.produktkompass.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OpenFoodFactsLabelRepository extends JpaRepository<OpenFoodFactsLabel, Long> {
    List<OpenFoodFactsLabel> findAllByCanonicalTagIn(Collection<String> tags);

    @Query("SELECT label FROM OpenFoodFactsLabel label WHERE LOWER(label.canonicalTag) IN :tags")
    List<OpenFoodFactsLabel> findAllByNormalizedTagIn(@Param("tags") Collection<String> tags);
}
