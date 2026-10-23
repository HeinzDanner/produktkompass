package de.heinzdanner.produktkompass.openfoodfacts;

import java.util.Map;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.OpenFoodFactsDataRepository;
import de.heinzdanner.produktkompass.product.OpenFoodFactsLabel;
import de.heinzdanner.produktkompass.product.OpenFoodFactsLabelRepository;
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
    @Autowired private OpenFoodFactsLabelRepository labels;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @Test
    void importsAHundredRowsWithOneBatchedCatalogLookup() {
        var statistics = entityManager.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            var records = java.util.stream.IntStream.range(0, 100).mapToObj(i ->
                    OpenFoodFactsRecord.from(Map.of("code", "00909" + i, "product_name", "Batch label " + i,
                            "labels_tags", "xx:batch-shared,en:batch-" + i))).toList();
            assertThat(importer.importPage(records).inserted()).isEqualTo(100);
            entityManager.flush();
            assertThat(statistics.getQueryExecutionCount()).isEqualTo(2);
            entityManager.clear();
            assertThat(labels.findAllByCanonicalTagIn(List.of("xx:batch-shared"))).hasSize(1);
            assertThat(dataRepository.findAllByBarcodeIn(records.stream().map(OpenFoodFactsRecord::barcode).toList()))
                    .hasSize(100).allSatisfy(row -> assertThat(row.getCatalogLabels()).hasSize(2));
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }

    @Test
    void catalogsEveryExactTagOnceAcrossRowsAndReimportsAndKeepsCurrentLinksExact() {
        long before = labels.count();
        var first = OpenFoodFactsRecord.from(Map.of("code", "00090901", "product_name", "Labels one",
                "labels_tags", " en:unique-label, ,de:unique-label,en:unique-label,fr:étiquette,EN:unique-label, "));
        var second = OpenFoodFactsRecord.from(Map.of("code", "00090902", "product_name", "Labels two",
                "labels_tags", "fr:étiquette,en:unique-label,xx:custom"));
        var records = List.of(first, second);
        assertThat(importer.importPage(records).inserted()).isEqualTo(2);
        entityManager.flush();
        entityManager.clear();
        var catalog = labels.findAll();
        assertThat(catalog).hasSize((int) before + 5);
        assertThat(catalog).extracting(OpenFoodFactsLabel::getCanonicalTag).doesNotHaveDuplicates();
        assertThat(catalog).filteredOn(label -> label.getCanonicalTag().equals("fr:étiquette"))
                .extracting(OpenFoodFactsLabel::getLocaleKey).containsExactly("fr");
        var ids = catalog.stream().collect(java.util.stream.Collectors.toMap(
                OpenFoodFactsLabel::getCanonicalTag, OpenFoodFactsLabel::getId));
        assertThat(importer.importPage(records).updated()).isEqualTo(2);
        entityManager.flush();
        entityManager.clear();
        assertThat(labels.count()).isEqualTo(before + 5);
        assertThat(labels.findAll()).allSatisfy(label ->
                assertThat(label.getId()).isEqualTo(ids.get(label.getCanonicalTag())));
        var rows = dataRepository.findAllByBarcodeIn(List.of("00090901", "00090902"));
        assertThat(rows).filteredOn(row -> row.getBarcode().equals("00090901")).singleElement()
                .satisfies(row -> assertThat(row.getCatalogLabels()).extracting(OpenFoodFactsLabel::getCanonicalTag)
                        .containsExactlyInAnyOrder("en:unique-label", "de:unique-label", "fr:étiquette", "EN:unique-label"));
        assertThat(rows).filteredOn(row -> row.getBarcode().equals("00090902")).singleElement()
                .satisfies(row -> assertThat(row.getCatalogLabels()).extracting(OpenFoodFactsLabel::getCanonicalTag)
                        .containsExactlyInAnyOrder("fr:étiquette", "en:unique-label", "xx:custom"));

        importer.importPage(List.of(OpenFoodFactsRecord.from(Map.of(
                "code", "00090901", "product_name", "Labels omitted"))));
        entityManager.flush();
        entityManager.clear();
        assertThat(dataRepository.findAllByBarcodeIn(List.of("00090901")).getFirst().getCatalogLabels()).hasSize(4);
        importer.importPage(List.of(OpenFoodFactsRecord.from(Map.of(
                "code", "00090901", "product_name", "Labels replaced", "labels_tags", "xx:custom"))));
        entityManager.flush();
        entityManager.clear();
        assertThat(dataRepository.findAllByBarcodeIn(List.of("00090901")).getFirst().getCatalogLabels())
                .extracting(OpenFoodFactsLabel::getCanonicalTag).containsExactly("xx:custom");
        importer.importPage(List.of(OpenFoodFactsRecord.from(Map.of(
                "code", "00090902", "product_name", "Labels cleared", "labels_tags", " , "))));
        entityManager.flush();
        entityManager.clear();
        assertThat(dataRepository.findAllByBarcodeIn(List.of("00090902")).getFirst().getCatalogLabels()).isEmpty();
        assertThat(labels.count()).isEqualTo(before + 5);
    }

    @Test
    void importIsRepeatableAndOnlyRefreshesOpenFoodFactsFields() {
        OpenFoodFactsRecord first = OpenFoodFactsRecord.from(Map.of(
                "code", "0002223334445", "product_name", "OFF imported name", "brands", "First brand",
                "brands_tags", "en:first-brand", "brand_owner", "First owner",
                "packaging", "Bottle", "packaging_tags", "en:bottle",
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
                "brands", "Updated brand", "brands_tags", "en:updated-brand",
                "brand_owner", "Updated owner", "packaging", "Jar", "packaging_tags", "en:jar",
                "categories_tags", "en:plant-milks,en:soy-milks",
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
        assertThat(refreshedData.getBrandTags()).isEqualTo("en:updated-brand");
        assertThat(refreshedData.getBrandOwner()).isEqualTo("Updated owner");
        assertThat(refreshedData.getPackaging()).isEqualTo("Jar");
        assertThat(refreshedData.getPackagingTags()).isEqualTo("en:jar");
        assertThat(refreshedData.getCategoryTags()).isEqualTo("en:plant-milks,en:soy-milks");
    }
}
