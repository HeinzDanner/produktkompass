package de.heinzdanner.produktkompass.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface OpenFoodFactsDataRepository extends JpaRepository<OpenFoodFactsData, Long> {
    @Query(value = """
            select distinct new de.heinzdanner.produktkompass.product.dto.OffCategoryOption(entry, mapping.category.id)
            from OpenFoodFactsData data join data.categoryEntries entry
            left join OffCategoryMapping mapping on mapping.offValue = entry
            where lower(entry) like :pattern escape '!'
              and (:unmappedOnly = false or mapping.offValue is null)
            order by entry
            """, countQuery = """
            select count(distinct entry) from OpenFoodFactsData data join data.categoryEntries entry
            left join OffCategoryMapping mapping on mapping.offValue = entry
            where lower(entry) like :pattern escape '!'
              and (:unmappedOnly = false or mapping.offValue is null)
            """)
    org.springframework.data.domain.Page<de.heinzdanner.produktkompass.product.dto.OffCategoryOption> categoryMappingPage(
            @org.springframework.data.repository.query.Param("pattern") String pattern,
            @org.springframework.data.repository.query.Param("unmappedOnly") boolean unmappedOnly,
            org.springframework.data.domain.Pageable pageable);

    @Query("select count(data) from OpenFoodFactsData data join data.categoryEntries entry where entry = :value")
    long countCategoryEntry(@org.springframework.data.repository.query.Param("value") String value);

    @Query("select distinct entry from OpenFoodFactsData data join data.categoryEntries entry order by entry")
    List<String> findCategoryEntries();

    @Query("select data.barcode from OpenFoodFactsData data")
    List<String> findAllBarcodes();

    @EntityGraph(attributePaths = "catalogLabels")
    List<OpenFoodFactsData> findAllByBarcodeIn(Collection<String> barcodes);
}
