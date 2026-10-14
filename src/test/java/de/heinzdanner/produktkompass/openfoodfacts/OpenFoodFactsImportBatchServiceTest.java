package de.heinzdanner.produktkompass.openfoodfacts;

import java.util.Map;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.OpenFoodFactsDataRepository;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.user.AppUserRepository;
import de.heinzdanner.produktkompass.usertag.UserTag;
import de.heinzdanner.produktkompass.usertag.UserTagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OpenFoodFactsImportBatchServiceTest {
    @Autowired private OpenFoodFactsImportBatchService importer;
    @Autowired private OpenFoodFactsDataRepository dataRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AppUserRepository users;
    @Autowired private UserTagRepository tags;
    @Test
    void importIsRepeatableAndOnlyRefreshesOpenFoodFactsFields() {
        OpenFoodFactsRecord first = OpenFoodFactsRecord.from(Map.of(
                "code", "0002223334445", "product_name", "OFF imported name", "brands", "First brand",
                "categories_tags", "en:plant-milks", "nutrition_grade_fr", "b", "lang", "de"));
        var initial = importer.importPage(List.of(first));
        assertThat(initial.inserted()).isEqualTo(1);
        assertThat(initial.updated()).isZero();

        var imported = dataRepository.findAllByBarcodeIn(List.of("0002223334445")).getFirst();
        var product = productRepository.findByIdWithDetails(imported.getProductId()).orElseThrow();
        Long productId = product.getId();
        product.setName("Editor reviewed product name");
        product.setNutritionGrade(NutritionGrade.A);
        product.setLabels(new java.util.HashSet<>(java.util.Set.of(ProductLabel.VEGAN)));
        productRepository.saveAndFlush(product);
        var owner = users.findByUsername("max").orElseThrow();
        owner.addFavoriteProduct(product);
        users.saveAndFlush(owner);
        UserTag tag = new UserTag("Importer preservation tag", owner);
        tag.getProducts().add(product);
        tags.saveAndFlush(tag);

        OpenFoodFactsRecord refreshed = OpenFoodFactsRecord.from(Map.of(
                "code", "0002223334445", "product_name", "Changed OFF name",
                "brands", "Updated brand", "categories_tags", "en:plant-milks,en:soy-milks",
                "nutrition_grade_fr", "c", "lang", "de"));
        var replayRecords = new java.util.ArrayList<OpenFoodFactsRecord>();
        replayRecords.add(refreshed);
        replayRecords.add(refreshed);
        replayRecords.add(null);
        var replay = importer.importPage(replayRecords);
        assertThat(replay.inserted()).isZero();
        assertThat(replay.updated()).isEqualTo(1);
        assertThat(replay.skipped()).isEqualTo(2);

        var refreshedData = dataRepository.findAllByBarcodeIn(List.of("0002223334445")).getFirst();
        var unchangedProduct = productRepository.findByIdWithDetails(productId).orElseThrow();
        assertThat(unchangedProduct.getName()).isEqualTo("Editor reviewed product name");
        assertThat(unchangedProduct.getNutritionGrade()).isEqualTo(NutritionGrade.A);
        assertThat(unchangedProduct.getLabels()).containsExactly(ProductLabel.VEGAN);
        assertThat(users.findByUsername("max").orElseThrow().getFavoriteProducts())
                .extracting("id").contains(productId);
        assertThat(tags.findByIdWithAssignmentsAndOwnerUsername(tag.getId(), "max").orElseThrow()
                .getProducts()).extracting("id").contains(productId);
        assertThat(refreshedData.getProductName()).isEqualTo("Changed OFF name");
        assertThat(refreshedData.getBrands()).isEqualTo("Updated brand");
        assertThat(refreshedData.getCategoryTags()).isEqualTo("en:plant-milks,en:soy-milks");
    }
}
