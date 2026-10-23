package de.heinzdanner.produktkompass.openfoodfacts;

import de.heinzdanner.produktkompass.product.OpenFoodFactsData;
import de.heinzdanner.produktkompass.product.OpenFoodFactsDataRepository;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.product.OpenFoodFactsLabel;
import de.heinzdanner.produktkompass.product.OpenFoodFactsLabelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class OpenFoodFactsImportBatchService {

    private final OpenFoodFactsDataRepository dataRepository;
    private final ProductRepository productRepository;
    private final OpenFoodFactsLabelRepository labelRepository;

    public OpenFoodFactsImportBatchService(OpenFoodFactsDataRepository dataRepository,
                                           ProductRepository productRepository,
                                           OpenFoodFactsLabelRepository labelRepository) {
        this.dataRepository = dataRepository;
        this.productRepository = productRepository;
        this.labelRepository = labelRepository;
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
        List<OpenFoodFactsData> importedData = new ArrayList<>();
        long updated = 0;
        for (OpenFoodFactsRecord record : recordsByBarcode.values()) {
            OpenFoodFactsData prior = existing.get(record.barcode());
            if (prior == null) {
                Product product = record.newProduct(importedAt);
                newProducts.add(product);
                importedData.add(product.getOpenFoodFactsData());
            } else {
                record.update(prior, importedAt);
                importedData.add(prior);
                updated++;
            }
        }
        synchronizeLabels(importedData);
        productRepository.saveAll(newProducts);
        return new ImportCounts(newProducts.size(), updated, skipped);
    }

    private void synchronizeLabels(List<OpenFoodFactsData> data) {
        Set<String> tags = new LinkedHashSet<>();
        data.forEach(row -> tags.addAll(OpenFoodFactsTags.split(row.getLabelTags())));
        Map<String, OpenFoodFactsLabel> catalog = new LinkedHashMap<>();
        if (!tags.isEmpty()) {
            labelRepository.findAllByCanonicalTagIn(tags)
                    .forEach(label -> catalog.put(label.getCanonicalTag(), label));
            List<OpenFoodFactsLabel> missing = tags.stream().filter(tag -> !catalog.containsKey(tag))
                    .map(OpenFoodFactsLabel::new).toList();
            labelRepository.saveAll(missing).forEach(label -> catalog.put(label.getCanonicalTag(), label));
        }
        for (OpenFoodFactsData row : data) {
            Set<OpenFoodFactsLabel> labels = new LinkedHashSet<>();
            OpenFoodFactsTags.split(row.getLabelTags()).forEach(tag -> labels.add(catalog.get(tag)));
            row.replaceCatalogLabels(labels);
        }
    }

    public record ImportCounts(long inserted, long updated, long skipped) {
    }
}
