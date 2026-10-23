package de.heinzdanner.produktkompass.openfoodfacts;

import de.heinzdanner.produktkompass.product.OpenFoodFactsDataRepository;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.product.dto.ProductImage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class OpenFoodFactsImageImportTest {
    private static final String CODE = "0001000000999";
    private static final String IMAGE = "https://images.openfoodfacts.org/images/products/000/100/front_de.1.400.jpg";
    private static final String SMALL = "https://images.openfoodfacts.org/images/products/000/100/front_de.1.200.jpg";
    @Autowired OpenFoodFactsImageImportService images;
    @Autowired OpenFoodFactsTsvImporter catalog;
    @Autowired OpenFoodFactsDataRepository data;
    @Autowired ProductRepository products;
    @Autowired EntityManager entityManager;
    @TempDir Path temporaryDirectory;

    private void seed() throws Exception {
        Path file = temporaryDirectory.resolve("seed.tsv");
        Files.writeString(file, "code\tproduct_name\tcountries_tags\tbrands\tlabels_tags\tfat_100g\tfat_unit\n"
                + CODE + "\tOriginal name\ten:germany\tOriginal brand\ten:organic\t2.5\tg\n");
        catalog.importFile(file, "en:germany", 1);
        entityManager.flush();
    }

    private de.heinzdanner.produktkompass.product.OpenFoodFactsData reload() {
        entityManager.flush();
        entityManager.clear();
        return data.findAllByBarcodeIn(List.of(CODE)).getFirst();
    }

    @Test
    void streamsFullGzipBeyondCatalogLimitAndChangesOnlyImagesForExistingBarcodes() throws Exception {
        seed();
        var before = reload();
        var importedAt = before.getLastImportedAt();
        var source = before.getSourceRowJson();
        var labels = before.getCatalogLabels().stream().map(label -> label.getId()).toList();
        long count = products.count();
        Path file = temporaryDirectory.resolve("images.tsv.gz");
        StringBuilder tsv = new StringBuilder("\uFEFFcode\timage_url\timage_small_url\timage_ingredients_url\tproduct_name\n");
        for (int i = 0; i < 10001; i++) tsv.append("other").append(i).append("\t\t\t\t\"Quoted\tname\nline\"\n");
        tsv.append(CODE).append('\t').append(IMAGE).append('\t').append(SMALL)
                .append('\t').append(IMAGE.replace("front_de", "ingredients_de")).append("\tDo not change name\n");
        try (var gzip = new GZIPOutputStream(Files.newOutputStream(file))) {
            gzip.write(tsv.toString().getBytes(StandardCharsets.UTF_8));
        }
        var result = images.importFile(file);
        assertThat(result.recordsRead()).isEqualTo(10002);
        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.withProductImage()).isEqualTo(1);
        assertThat(products.count()).isEqualTo(count);
        var saved = reload();
        assertThat(saved.getProduct().getName()).isEqualTo("Original name");
        assertThat(saved.getBrands()).isEqualTo("Original brand");
        assertThat(saved.getLastImportedAt()).isEqualTo(importedAt);
        assertThat(saved.getSourceRowJson()).isEqualTo(source);
        assertThat(saved.getCatalogLabels()).extracting(label -> label.getId()).containsExactlyElementsOf(labels);
        assertThat(saved.getMetadata().nutrition().get("fat_100g").value()).isEqualByComparingTo("2.5");
        assertThat(ProductImage.from(saved)).isEqualTo(new ProductImage(SMALL, IMAGE,
                "https://world.openfoodfacts.org/product/" + CODE));
        assertThat(OpenFoodFactsJson.readRow(saved.getMergedSourceJson())).containsEntry("brands", "Original brand");
        assertThat(images.importFile(file).updated()).isZero();
    }

    @Test
    void blankColumnsClearImagesButAbsentColumnsPreserveOtherImagesAndMetadata() throws Exception {
        seed();
        Path file = temporaryDirectory.resolve("images.tsv");
        Files.writeString(file, "code\timage_url\timage_small_url\timage_ingredients_url\n"
                + CODE + "\t" + IMAGE + "\t" + SMALL + "\t" + IMAGE.replace("front_de", "ingredients_de") + "\n");
        images.importFile(file);
        reload();
        Files.writeString(file, "code\timage_url\timage_small_url\n" + CODE + "\t\t\n");
        var result = images.importFile(file);
        var saved = reload();
        assertThat(result.withProductImage()).isZero();
        assertThat(ProductImage.from(saved)).isNull();
        assertThat(saved.getMetadata().imageUrls()).containsKey("imageIngredientsUrl")
                .doesNotContainKeys("imageUrl", "image_small_url");
        assertThat(saved.getMetadata().nutrition()).containsKey("fat_100g");
    }

    @Test
    void rejectsUntrustedImagesWithoutCreatingProductsOrLosingSourceEvidence() throws Exception {
        seed();
        long count = products.count();
        Path file = temporaryDirectory.resolve("invalid-images.tsv");
        Files.writeString(file, "code\timage_url\n" + CODE + "\thttps://example.com/image.jpg\n");
        assertThat(images.importFile(file).withProductImage()).isZero();
        var saved = reload();
        assertThat(saved.getMetadata().imageUrls()).isEmpty();
        assertThat(OpenFoodFactsJson.readRow(saved.getMergedSourceJson()))
                .containsEntry("image_url", "https://example.com/image.jpg");
        assertThat(products.count()).isEqualTo(count);
    }

    @Test
    void rejectsMissingImageColumnsDuplicateHeadersAndInconsistentRows() throws Exception {
        Path file = temporaryDirectory.resolve("invalid.tsv");
        Files.writeString(file, "code\tproduct_name\n123\tName\n");
        assertThatThrownBy(() -> images.importFile(file)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bild-URL-Spalte");
        Files.writeString(file, "code\timage_url\timage_url\n123\t\t\n");
        assertThatThrownBy(() -> images.importFile(file)).isInstanceOf(IllegalArgumentException.class);
        Files.writeString(file, "code\timage_url\n123\t\textra\n");
        assertThatThrownBy(() -> images.importFile(file)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Datensatz 1");
    }
}
