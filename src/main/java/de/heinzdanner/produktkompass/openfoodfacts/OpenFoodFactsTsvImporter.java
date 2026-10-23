package de.heinzdanner.produktkompass.openfoodfacts;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Service
public class OpenFoodFactsTsvImporter {

    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsTsvImporter.class);
    private static final int BATCH_SIZE = 100;
    private static final List<String> REQUIRED_COLUMNS = List.of("code", "countries_tags");
    private final OpenFoodFactsImportBatchService batchService;

    public OpenFoodFactsTsvImporter(OpenFoodFactsImportBatchService batchService) {
        this.batchService = batchService;
    }

    public ImportResult importFile(Path path, String countryTag, int maximumRecords) throws Exception {
        List<String> missing = new ArrayList<>(REQUIRED_COLUMNS);
        long recordsRead = 0;
        long inserted = 0;
        long updated = 0;
        long skipped = 0;
        List<OpenFoodFactsRecord> batch = new ArrayList<>(BATCH_SIZE);
        OpenFoodFactsSchema.Coverage coverage;
        if (maximumRecords < 1 || maximumRecords > 10_000) {
            throw new IllegalArgumentException("maximumRecords muss zwischen 1 und 10000 liegen.");
        }

        try (InputStream input = open(path);
             Reader reader = utf8Reader(input);
             CSVParser parser = CSVFormat.TDF.builder()
                     .setHeader()
                     .setDuplicateHeaderMode(org.apache.commons.csv.DuplicateHeaderMode.DISALLOW)
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .get()
                     .parse(reader)) {
            Map<String, Integer> headers = parser.getHeaderMap();
            coverage = OpenFoodFactsSchema.coverage(headers.keySet());
            missing.removeIf(headers::containsKey);
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("OFF-TSV fehlt Pflichtspalten: " + String.join(", ", missing));
            }
            if (headers.keySet().stream().noneMatch(header ->
                    header.equals("product_name") || header.startsWith("product_name_"))) {
                throw new IllegalArgumentException(
                        "OFF-TSV fehlt eine Produktnamensspalte (product_name oder product_name_<Sprache>).");
            }
            log.info("OFF-Importschema: {} Quellspalten; abgedeckte Felder: {}.",
                    coverage.sourceColumnCount(), coverage.mappedFields().keySet());
            if (!coverage.missingOptionalFields().isEmpty()) {
                log.warn("OFF-Export enthält keine Spalten für: {}. Fehlende Angaben können nicht ergänzt werden; vorhandene Werte bleiben erhalten.",
                        coverage.missingOptionalFields());
            }

            java.util.Iterator<CSVRecord> rows = parser.iterator();
            while (recordsRead < maximumRecords && rows.hasNext()) {
                CSVRecord row = rows.next();
                recordsRead++;
                if (!row.isConsistent()) {
                    throw new IllegalArgumentException("OFF-TSV: abweichende Spaltenanzahl in Datensatz " + row.getRecordNumber());
                }
                OpenFoodFactsRecord record = OpenFoodFactsRecord.from(row.toMap());
                if (record == null || !record.isSoldIn(countryTag)) {
                    skipped++;
                    continue;
                }
                batch.add(record);
                if (batch.size() == BATCH_SIZE) {
                    OpenFoodFactsImportBatchService.ImportCounts counts = batchService.importPage(List.copyOf(batch));
                    inserted += counts.inserted();
                    updated += counts.updated();
                    skipped += counts.skipped();
                    batch.clear();
                    log.info("OFF-Dateiimport: {} Zeilen geprüft, {} neu, {} aktualisiert, {} übersprungen.",
                            recordsRead, inserted, updated, skipped);
                }
            }
        }

        if (!batch.isEmpty()) {
            OpenFoodFactsImportBatchService.ImportCounts counts = batchService.importPage(List.copyOf(batch));
            inserted += counts.inserted();
            updated += counts.updated();
            skipped += counts.skipped();
        }
        return new ImportResult(recordsRead, inserted, updated, skipped, coverage);
    }

    private Reader utf8Reader(InputStream input) throws IOException {
        java.io.PushbackReader reader = new java.io.PushbackReader(new InputStreamReader(input, StandardCharsets.UTF_8), 1);
        int first = reader.read();
        if (first != -1 && first != '\uFEFF') reader.unread(first);
        return reader;
    }

    private InputStream open(Path path) throws IOException {
        InputStream input = new BufferedInputStream(Files.newInputStream(path));
        if (path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".gz")) {
            try {
                return new GZIPInputStream(input, 64 * 1024);
            } catch (IOException exception) {
                input.close();
                throw exception;
            }
        }
        return input;
    }

    public record ImportResult(long recordsRead, long inserted, long updated, long skipped,
                               OpenFoodFactsSchema.Coverage coverage) {
    }
}
