package de.heinzdanner.produktkompass.openfoodfacts;

import de.heinzdanner.produktkompass.product.OpenFoodFactsDataRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Service
public class OpenFoodFactsImageImportService {
    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsImageImportService.class);
    private final OpenFoodFactsDataRepository repository;
    private final OpenFoodFactsImageBatchService batches;

    public OpenFoodFactsImageImportService(OpenFoodFactsDataRepository repository,
                                          OpenFoodFactsImageBatchService batches) {
        this.repository = repository;
        this.batches = batches;
    }

    public ImportResult importFile(Path path) throws Exception {
        var pending = new HashSet<>(repository.findAllBarcodes());
        long read = 0;
        long matched = 0;
        long updated = 0;
        long withProductImage = 0;
        List<Map<String, String>> batch = new ArrayList<>();
        try (InputStream file = new BufferedInputStream(Files.newInputStream(path));
             InputStream input = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".gz")
                     ? new GZIPInputStream(file, 131072) : file;
             var reader = new PushbackReader(new InputStreamReader(input, StandardCharsets.UTF_8), 1)) {
            int first = reader.read();
            if (first != -1 && first != '\uFEFF') reader.unread(first);
            try (var parser = CSVFormat.TDF.builder().setHeader().setSkipHeaderRecord(true)
                    .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).get().parse(reader)) {
                var headers = parser.getHeaderMap();
                var imageColumns = headers.keySet().stream()
                        .filter(OpenFoodFactsMetadata::isImageColumn).toList();
                if (!headers.containsKey("code") || imageColumns.isEmpty()) {
                    throw new IllegalArgumentException("OFF-Bildabgleich benötigt code und mindestens eine Bild-URL-Spalte.");
                }
                for (var row : parser) {
                    read++;
                    if (!row.isConsistent()) {
                        throw new IllegalArgumentException("OFF-Bildabgleich: abweichende Spaltenanzahl in Datensatz "
                                + row.getRecordNumber());
                    }
                    String code = row.get("code").trim();
                    if (pending.remove(code)) {
                        Map<String, String> images = new LinkedHashMap<>();
                        images.put("code", code);
                        for (String column : imageColumns) {
                            String value = row.get(column).trim();
                            if (!value.isEmpty() && !OpenFoodFactsMetadata.isDisplayImageUrl(value)) {
                                log.warn("OFF-Bildabgleich: ungültige oder nicht freigegebene Bildadresse für Barcode {} in {}.",
                                        code, column);
                            }
                            images.put(column, value);
                        }
                        batch.add(images);
                        matched++;
                        if (batch.size() == 100) {
                            var counts = batches.updateImages(List.copyOf(batch));
                            updated += counts.updated();
                            withProductImage += counts.withProductImage();
                            batch.clear();
                        }
                    }
                    if (read % 1_000_000 == 0) log.info("OFF-Bildabgleich: {} Zeilen gelesen, {} Barcodes gefunden.", read, matched);
                }
            }
        }
        if (!batch.isEmpty()) {
            var counts = batches.updateImages(batch);
            updated += counts.updated();
            withProductImage += counts.withProductImage();
        }
        return new ImportResult(read, matched, updated, withProductImage, pending.size());
    }

    public record ImportResult(long recordsRead, long matched, long updated, long withProductImage, long notFound) {}
}
