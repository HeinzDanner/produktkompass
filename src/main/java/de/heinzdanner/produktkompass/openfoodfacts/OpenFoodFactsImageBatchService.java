package de.heinzdanner.produktkompass.openfoodfacts;

import de.heinzdanner.produktkompass.product.OpenFoodFactsDataRepository;
import de.heinzdanner.produktkompass.product.dto.ProductImage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OpenFoodFactsImageBatchService {
    private final OpenFoodFactsDataRepository repository;

    public OpenFoodFactsImageBatchService(OpenFoodFactsDataRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Counts updateImages(List<Map<String, String>> rows) {
        var byCode = rows.stream().collect(Collectors.toMap(row -> row.get("code"), Function.identity()));
        long updated = 0;
        long withProductImage = 0;
        for (var data : repository.findAllByBarcodeIn(byCode.keySet())) {
            var row = byCode.get(data.getBarcode());
            Map<String, String> safeImages = new LinkedHashMap<>();
            row.forEach((column, value) -> {
                if (OpenFoodFactsMetadata.isImageColumn(column)) {
                    safeImages.put(column, OpenFoodFactsMetadata.isDisplayImageUrl(value) ? value : "");
                }
            });
            var metadata = OpenFoodFactsMetadata.merge(safeImages, data.getMetadata());
            String json = OpenFoodFactsJson.write(metadata);
            if (!metadata.equals(data.getMetadata())) {
                data.setMetadataJson(json);
                updated++;
            }
            var source = OpenFoodFactsJson.readRow(data.getMergedSourceJson());
            row.forEach((column, value) -> {
                if (OpenFoodFactsMetadata.isImageColumn(column)) source.put(column, value);
            });
            data.setMergedSourceJson(OpenFoodFactsJson.write(source));
            if (ProductImage.from(data) != null) withProductImage++;
        }
        return new Counts(updated, withProductImage);
    }

    public record Counts(long updated, long withProductImage) {}
}
