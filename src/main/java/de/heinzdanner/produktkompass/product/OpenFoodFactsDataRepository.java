package de.heinzdanner.produktkompass.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface OpenFoodFactsDataRepository extends JpaRepository<OpenFoodFactsData, Long> {
    @Query("select data.barcode from OpenFoodFactsData data")
    List<String> findAllBarcodes();

    @EntityGraph(attributePaths = "catalogLabels")
    List<OpenFoodFactsData> findAllByBarcodeIn(Collection<String> barcodes);
}
