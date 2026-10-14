package de.heinzdanner.produktkompass.openfoodfacts;

import de.heinzdanner.produktkompass.product.OpenFoodFactsData;
import de.heinzdanner.produktkompass.product.OpenFoodFactsDataRepository;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenFoodFactsImportBatchService {

    private final OpenFoodFactsDataRepository dataRepository;
    private final ProductRepository productRepository;

    public OpenFoodFactsImportBatchService(OpenFoodFactsDataRepository dataRepository,
                                           ProductRepository productRepository) {
        this.dataRepository = dataRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public ImportCounts importPage(List<OpenFoodFactsRecord> sourceRecords) {
        LocalDateTime importedAt = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, OpenFoodFactsRecord> recordsByBarcode = new LinkedHashMap<>();
        long skipped = 0;
        for (OpenFoodFactsRecord record : sourceRecords) {
            if (record == null) {
                skipped++;
            } else if (recordsByBarcode.putIfAbsent(record.barcode(), record) != null) {
                skipped++;
            }
        }
        if (recordsByBarcode.isEmpty()) {
            return new ImportCounts(0, 0, skipped);
        }

        Map<String, OpenFoodFactsData> existing = new HashMap<>();
        dataRepository.findAllByBarcodeIn(recordsByBarcode.keySet())
                .forEach(data -> existing.put(data.getBarcode(), data));
        List<Product> newProducts = new ArrayList<>();
        long updated = 0;
        for (OpenFoodFactsRecord record : recordsByBarcode.values()) {
            OpenFoodFactsData prior = existing.get(record.barcode());
            if (prior == null) {
                newProducts.add(record.newProduct(importedAt));
            } else {
                record.update(prior, importedAt);
                updated++;
            }
        }
        productRepository.saveAll(newProducts);
        return new ImportCounts(newProducts.size(), updated, skipped);
    }

    public record ImportCounts(long inserted, long updated, long skipped) {
    }
}
