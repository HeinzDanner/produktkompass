package de.heinzdanner.produktkompass.openfoodfacts;

import de.heinzdanner.produktkompass.product.OpenFoodFactsDataRepository;
import de.heinzdanner.produktkompass.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OpenFoodFactsTsvImporterTest {

    @Autowired private OpenFoodFactsTsvImporter importer;
    @Autowired private OpenFoodFactsDataRepository dataRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @TempDir Path temporaryDirectory;

    @Test
    void streamsGzipTsvFiltersCountryAndStopsAtExplicitRowLimit() throws Exception {
        Path file = temporaryDirectory.resolve("products.tsv.gz");
        String tsv = """
                code\tproduct_name\tproduct_name_de\tbrands\tcategories\tcategories_tags\tquantity\tingredients_text\tingredients_text_de\tallergens\tallergens_tags\ttraces\ttraces_tags\tcountries\tcountries_tags\tnutrition_grade_fr\tlast_modified_t\tlang
                0001000000001\tOat drink\tHaferdrink\t"Brand A, Brand B"\tHaferdrinks\ten:plant-milks,de:haferdrinks\t1 l\tWater, oats\tWasser, Hafer\t\t\t\t\tGermany\ten:germany\tb\t1790264955\tde
                0001000000002\tFruit bar\tFruchtriegel\tSnackCo\tSnacks\ten:snacks\t40 g\tDates\tDatteln\t\t\t\t\tFrance\ten:france\tc\t1790264956\tfr
                \tUnnamed\t\t\t\t\t\t\t\t\t\t\t\tGermany\ten:germany\t\t\t
                0001000000003\tYoghurt\tJoghurt\tDairyCo\tYoghurt\ten:yoghurts\t150 g\tMilk\tMilch\t\t\t\t\tGermany\ten:germany\ta\t1790264957\tde
                """;
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(file))) {
            output.write(tsv.getBytes(StandardCharsets.UTF_8));
        }

        var result = importer.importFile(file, "en:germany", 3);
        assertThat(result.recordsRead()).isEqualTo(3);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(dataRepository.findAllByBarcodeIn(java.util.List.of("0001000000001"))).hasSize(1);
        assertThat(dataRepository.findAllByBarcodeIn(java.util.List.of("0001000000003"))).isEmpty();
        assertThat(productRepository.findAll()).anySatisfy(product ->
                assertThat(product.getName()).isEqualTo("Haferdrink"));
    }

    @Test
    void rejectsFilesWithoutRequiredCountryFilterColumns() throws Exception {
        Path file = temporaryDirectory.resolve("missing-fields.tsv");
        Files.writeString(file, "code\tproduct_name\n123\tProduct\n");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> importer.importFile(file, "en:germany", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("countries_tags");
    }

    @Test
    void acceptsLocalizedProductNameColumnsFromTheOfficialExport() throws Exception {
        Path file = temporaryDirectory.resolve("localized-products.tsv");
        Files.writeString(file, """
                code\tproduct_name_de\tproduct_name_en\tcountries_tags
                0001000000004\t\tOat drink\ten:germany
                """);

        var result = importer.importFile(file, "en:germany", 10);

        assertThat(result.recordsRead()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(dataRepository.findAllByBarcodeIn(java.util.List.of("0001000000004"))).hasSize(1);
    }

    @Test
    void persistsCompleteSourceAndTypedMetadataAndPreservesAbsentFieldsAcrossNarrowExports() throws Exception {
        Path file = temporaryDirectory.resolve("rich.tsv");
        Files.writeString(file, "\uFEFFcode\tproduct_name\tcountries_tags\tbrands\tserving_size\tnova_group\tcreated_t\tfat_100g\tfat_unit\tfuture_field\n"
                + "0001000000006\tProduct\ten:germany\tReal brand\t250 ml\t3\t1790264955\t2.5\tg\t\"Quoted\tvalue\nsecond line\"\n");
        var result = importer.importFile(file, "en:germany", 10);
        assertThat(result.coverage().missingOptionalFields()).contains("imageFrontUrl");
        assertThat(result.recordsRead()).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();
        var saved = dataRepository.findAllByBarcodeIn(java.util.List.of("0001000000006")).getFirst();
        assertThat(saved.getNovaGroup()).isEqualTo(3);
        assertThat(saved.getServingSize()).isEqualTo("250 ml");
        assertThat(saved.getSourceCreatedAt()).isNotNull();
        assertThat(saved.getMetadata().sourceCreatedAt()).isEqualTo(saved.getSourceCreatedAt());
        assertThat(OpenFoodFactsJson.readRow(saved.getSourceRowJson()))
                .containsEntry("future_field", "Quoted\tvalue\nsecond line");

        Files.writeString(file, "code\tproduct_name_en\tcountries_tags\tserving_size\n0001000000006\tNew name\ten:germany\t\n");
        var replay = importer.importFile(file, "en:germany", 10);
        assertThat(replay.updated()).isEqualTo(1);
        assertThat(replay.inserted()).isZero();
        entityManager.flush();
        entityManager.clear();
        saved = dataRepository.findAllByBarcodeIn(java.util.List.of("0001000000006")).getFirst();
        assertThat(saved.getBrands()).isEqualTo("Real brand");
        assertThat(saved.getServingSize()).isNull();
        assertThat(saved.getMetadata().servingSize()).isNull();
        assertThat(saved.getMetadata().nutrition().get("fat_100g").value()).isEqualByComparingTo("2.5");
        assertThat(OpenFoodFactsJson.readRow(saved.getMergedSourceJson())).containsKey("future_field");
    }

    @Test
    void rejectsDuplicateHeadersAndInconsistentRows() throws Exception {
        Path file = temporaryDirectory.resolve("invalid.tsv");
        Files.writeString(file, "code\tcode\tproduct_name\tcountries_tags\n123\t123\tProduct\ten:germany\n");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> importer.importFile(file, "en:germany", 10))
                .isInstanceOf(IllegalArgumentException.class);
        Files.writeString(file, "code\tproduct_name\tcountries_tags\n123\tProduct\ten:germany\textra\n");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> importer.importFile(file, "en:germany", 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Datensatz 1");
    }

    @Test
    void doesNotParseAnotherRecordAfterTheConfiguredLimit() throws Exception {
        Path file = temporaryDirectory.resolve("bounded.tsv");
        Files.writeString(file, "code\tproduct_name\tcountries_tags\n0001000000007\tProduct\ten:germany\n\"unfinished");
        var result = importer.importFile(file, "en:germany", 1);
        assertThat(result.recordsRead()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(1);
    }
}
