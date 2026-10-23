package de.heinzdanner.produktkompass.openfoodfacts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "produktkompass.open-food-facts.import.enabled", havingValue = "true")
public class OpenFoodFactsImportService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsImportService.class);
    private static final int MAXIMUM_RECORDS = 10_000;
    private final OpenFoodFactsTsvImporter importer;
    private final String dataFile;
    private final String countryTag;
    private final int maximumRecords;
    private final boolean imagesOnly;
    private final OpenFoodFactsImageImportService imageImporter;

    public OpenFoodFactsImportService(
            OpenFoodFactsTsvImporter importer,
            OpenFoodFactsImageImportService imageImporter,
            @Value("${produktkompass.open-food-facts.import.file:}") String dataFile,
            @Value("${produktkompass.open-food-facts.import.country-tag:en:germany}") String countryTag,
            @Value("${produktkompass.open-food-facts.import.max-products:0}") int maximumRecords,
            @Value("${produktkompass.open-food-facts.import.images-only:false}") boolean imagesOnly) {
        this.importer = importer;
        this.dataFile = dataFile;
        this.countryTag = countryTag;
        this.maximumRecords = maximumRecords;
        this.imagesOnly = imagesOnly;
        this.imageImporter = imageImporter;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (dataFile.isBlank()) {
            throw new IllegalArgumentException("Für den lokalen OFF-Import muss --produktkompass.open-food-facts.import.file gesetzt sein.");
        }
        if (!imagesOnly && (maximumRecords < 1 || maximumRecords > MAXIMUM_RECORDS)) {
            throw new IllegalArgumentException("max-products muss explizit zwischen 1 und 10000 liegen.");
        }
        if (!imagesOnly && (countryTag == null || !countryTag.matches("[a-z]{2}:[a-z0-9-]+"))) {
            throw new IllegalArgumentException("country-tag muss ein gültiger OFF-Tag wie en:germany sein.");
        }
        Path path = Path.of(dataFile).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("OFF-Datendatei ist keine lesbare Datei: " + path);
        }
        if (imagesOnly) {
            var result = imageImporter.importFile(path);
            log.info("OFF-Bildabgleich abgeschlossen: {} Zeilen gelesen, {} bestehende Barcodes gefunden, {} Bilddatensätze geändert, {} mit Produktbild, {} ohne Produktbild, {} nicht gefunden. Keine Produkte angelegt.",
                    result.recordsRead(), result.matched(), result.updated(), result.withProductImage(),
                    result.matched() - result.withProductImage(), result.notFound());
            return;
        }

        OpenFoodFactsTsvImporter.ImportResult result =
                importer.importFile(path, countryTag, maximumRecords);
        log.info("OFF-Dateiimport abgeschlossen: {} Zeilen geprüft, {} Produkte neu, {} aktualisiert, {} übersprungen.",
                result.recordsRead(), result.inserted(), result.updated(), result.skipped());
    }
}
