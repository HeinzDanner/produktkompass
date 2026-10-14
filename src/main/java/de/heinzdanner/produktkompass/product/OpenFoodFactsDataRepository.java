package de.heinzdanner.produktkompass.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OpenFoodFactsDataRepository extends JpaRepository<OpenFoodFactsData, Long> {
    List<OpenFoodFactsData> findAllByBarcodeIn(Collection<String> barcodes);
}
