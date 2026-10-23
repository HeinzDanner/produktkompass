package de.heinzdanner.produktkompass.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OpenFoodFactsLabelRepository extends JpaRepository<OpenFoodFactsLabel, Long> {
    List<OpenFoodFactsLabel> findAllByCanonicalTagIn(Collection<String> tags);
}
