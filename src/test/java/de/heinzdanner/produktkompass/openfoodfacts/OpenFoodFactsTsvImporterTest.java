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
}
