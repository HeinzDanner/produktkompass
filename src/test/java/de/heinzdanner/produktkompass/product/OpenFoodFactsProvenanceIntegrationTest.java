package de.heinzdanner.produktkompass.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;
import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsJson;
import de.heinzdanner.produktkompass.user.AppUserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true")
@Transactional
class OpenFoodFactsProvenanceIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ProductRepository products;
    @Autowired private AppUserRepository users;
    private MockMvc mvc;
    private Product product;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        product = new Product("OFF community display name", "A community-submitted product.");
        product.setNutritionGrade(NutritionGrade.UNKNOWN);
        OpenFoodFactsData imported = new OpenFoodFactsData(product, "0000123456789",
                "Original name from OFF", LocalDateTime.of(2026, 10, 1, 12, 30));
        imported.setBrands("Community brand");
        imported.setBrandTags("xx:community-brand");
        imported.setBrandOwner("Community brand owner");
        imported.setPackaging("Glass, Bottle");
        imported.setPackagingTags("en:glass,en:bottle");
        imported.setLabelTags("en:organic");
        imported.setOriginTags("en:germany");
        imported.setManufacturingPlaceTags("en:germany");
        imported.setCategories("en:plant-milks, de:pflanzendrinks");
        imported.setCategoryTags("en:plant-milks,de:pflanzendrinks");
        imported.setQuantity("1 l");
        imported.setIngredientsText("Water, oats");
        imported.setNutritionGrade("c");
        imported.setLanguageCode("de");
        imported.setSourceRevision("1759321800");
        imported.setMergedSourceJson(OpenFoodFactsJson.write(Map.of(
                "serving_size", "250 ml", "nova_group", "3", "created_t", "1759321800",
                "image_front_url", "https://images.openfoodfacts.org/images/products/example.jpg",
                "image_ingredients_url", "javascript:alert(1)", "fat_100g", "2.5", "fat_unit", "g")));
        product.setOpenFoodFactsData(imported);
        product = products.saveAndFlush(product);
    }

    @Test
    void apiKeepsImportedValuesSeparateAndRetainsBarcodeLeadingZeros() throws Exception {
        mvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutritionGrade").value("UNKNOWN"))
                .andExpect(jsonPath("$.openFoodFactsData.barcode").value("0000123456789"))
                .andExpect(jsonPath("$.openFoodFactsData.productName").value("Original name from OFF"))
                .andExpect(jsonPath("$.openFoodFactsData.brandTags").value("xx:community-brand"))
                .andExpect(jsonPath("$.openFoodFactsData.brandOwner").value("Community brand owner"))
                .andExpect(jsonPath("$.openFoodFactsData.packagingTags").value("en:glass,en:bottle"))
                .andExpect(jsonPath("$.openFoodFactsData.labelTags").value("en:organic"))
                .andExpect(jsonPath("$.openFoodFactsData.nutritionGrade").value("c"))
                .andExpect(jsonPath("$.openFoodFactsData.metadata.servingSize").value("250 ml"))
                .andExpect(jsonPath("$.openFoodFactsData.metadata.novaGroup").value(3))
                .andExpect(jsonPath("$.openFoodFactsData.metadata.imageUrls.imageFrontUrl").value("https://images.openfoodfacts.org/images/products/example.jpg"))
                .andExpect(jsonPath("$.image.detailUrl").value("https://images.openfoodfacts.org/images/products/example.jpg"))
                .andExpect(jsonPath("$.openFoodFactsData.metadata.imageUrls.imageIngredientsUrl").doesNotExist())
                .andExpect(jsonPath("$.openFoodFactsData.metadata.nutrition.fat_100g.value").value(2.5))
                .andExpect(jsonPath("$.openFoodFactsData.allergens").doesNotExist())
                .andExpect(jsonPath("$.openFoodFactsData.productUrl")
                        .value("https://world.openfoodfacts.org/product/0000123456789"));

        mvc.perform(get("/api/v1/products/search").param("name", "OFF community display"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].openFoodFactsDataAvailable").value(true))
                .andExpect(jsonPath("$.content[0].openFoodFactsProductUrl")
                        .value("https://world.openfoodfacts.org/product/0000123456789"));
        mvc.perform(get("/api/v1/products/search").param("name", "Community brand"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(product.getId()));
        mvc.perform(get("/api/v1/products/search").param("name", "pflanzendrinks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(product.getId()));
        mvc.perform(get("/api/v1/products/search").param("name", "en:plant-milks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(product.getId()));
    }

    @Test
    void publicPagesAttributeCommunityDataAndDoNotSuggestMissingAllergensAreAbsent() throws Exception {
        var details = mvc.perform(get("/products/{id}", product.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(details)
                .contains("Datenquellen und Herkunft", "nicht unabhängig geprüft", "0000123456789",
                        "Marken-Tags laut Open Food Facts", "Verpackung laut Open Food Facts",
                        "Glass, Bottle", "Kein Unternehmen zugeordnet",
                        "https://world.openfoodfacts.org/product/0000123456789",
                        "Zuletzt importiert", "Open Database License (ODbL) 1.0")
                .contains("250 ml", "Nährwerte laut OFF", "https://images.openfoodfacts.org/images/products/example.jpg",
                        "<img", "referrerpolicy=\"no-referrer\"", "CC BY-SA 3.0", "/js/product-images.js")
                .doesNotContain("allergenfrei", "javascript:alert", "ungeprüft");

        var list = mvc.perform(get("/products").param("query", "OFF community display"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("Quelle: Open Food Facts", "Community brand", "/products/" + product.getId());
        String card = list.substring(list.indexOf("<article class=\"product-card\""));
        assertThat(card.indexOf("<h3")).isLessThan(card.indexOf("class=\"brand-row\""));
        assertThat(card.indexOf("class=\"brand-row\"")).isLessThan(card.indexOf("class=\"product-media\""));
        assertThat(card.indexOf("class=\"product-media\"")).isLessThan(card.indexOf("class=\"off-card-facts\""));
        assertThat(card).contains("brand-badge", "brand-name");
    }

    @Test
    void hidesUnknownOnlyWhenOffProvidesAnAllergenOrTraceAndKeepsCardsLabelOnly() throws Exception {
        product.getOpenFoodFactsData().setTraceTags("en:milk,en:gluten");
        product.setLabels(new java.util.HashSet<>(java.util.Set.of(ProductLabel.ALCOHOL_FREE, ProductLabel.CAFFEINE_FREE)));
        products.saveAndFlush(product);
        String detail = mvc.perform(get("/products/{id}", product.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String lactose = detail.substring(detail.indexOf("<strong>Laktose</strong>"), detail.indexOf("<strong>Gluten</strong>"));
        String gluten = detail.substring(detail.indexOf("<strong>Gluten</strong>"), detail.indexOf("<strong>Alkohol</strong>"));
        assertThat(lactose).contains("Spuren von Milch angegeben").doesNotContain("UNKNOWN");
        assertThat(gluten).contains("Spuren von Gluten angegeben").doesNotContain("UNKNOWN");
        assertThat(detail).contains("1 l", "Community brand owner");

        String list = mvc.perform(get("/products").param("query", "OFF community display"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String labels = list.substring(list.indexOf("class=\"off-card-facts\""), list.indexOf("class=\"card-details-row\""));
        assertThat(labels).contains("Bio", "Alkoholfrei", "Koffeinfrei", "badge")
                .doesNotContain("Spuren", "UNKNOWN", "Menge:", "Community brand owner", "Weitere Angaben",
                        "Original name from OFF", "1 l");

        product.setContainsLactose(IngredientStatus.CONTAINS);
        product.getOpenFoodFactsData().setTraceTags(null);
        product.getOpenFoodFactsData().setAllergenTags("en:milk");
        products.saveAndFlush(product);
        detail = mvc.perform(get("/products/{id}", product.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        lactose = detail.substring(detail.indexOf("<strong>Laktose</strong>"), detail.indexOf("<strong>Gluten</strong>"));
        gluten = detail.substring(detail.indexOf("<strong>Gluten</strong>"), detail.indexOf("<strong>Alkohol</strong>"));
        assertThat(lactose).contains("CONTAINS", "Milch als Allergen angegeben").doesNotContain("UNKNOWN");
        assertThat(gluten).contains("UNKNOWN");
    }

    @Test
    void longTitlesAndManyBrandsHaveCompactHeadersWithoutChangingDetailData() throws Exception {
        String name = "Sehr langer Produktname mit vielen zusätzlichen Angaben ".repeat(4).strip();
        product.setName(name);
        product.getOpenFoodFactsData().setBrands("Community brand, Zweite Marke, Dritte Marke, Vierte Marke");
        products.saveAndFlush(product);
        String html = mvc.perform(get("/products").param("query", "Sehr langer Produktname"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("title=\"" + name + "\"", "class=\"card-brands\"", "class=\"more-brands\"",
                ">+3</a>", "3 weitere Marken auf der Detailseite anzeigen",
                "Alle Marken anzeigen: Community brand, Zweite Marke, Dritte Marke, Vierte Marke");
        String detail = mvc.perform(get("/products/{id}", product.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(detail).contains(name, "Community brand", "Zweite Marke", "Dritte Marke", "Vierte Marke")
                .doesNotContain("class=\"more-brands\"");
    }

    @Test
    void cardsPlaceNutriScoresBesideDetailsWithAccessibleExplanationsAndKeepConflicts() throws Exception {
        product.setNutritionGrade(NutritionGrade.A);
        products.saveAndFlush(product);
        String html = mvc.perform(get("/products").param("query", "OFF community display"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String footer = html.substring(html.indexOf("class=\"card-details-row\""), html.indexOf("<div class=\"actions\""));
        assertThat(footer).contains(">Details</a>", "class=\"card-nutri-scores\"",
                "class=\"nutri-score-step active\" data-grade=\"A\"",
                "class=\"nutri-score-step active\" data-grade=\"C\"",
                "Nutri-Score: A – Sehr gute Nährwertqualität · Quelle: App-Katalog",
                "Nutri-Score: C – Mittlere Nährwertqualität · Quelle: Open Food Facts",
                "tabindex=\"0\"", "class=\"nutri-score-tooltip\"", "class=\"nutri-score-source\"");
        assertThat(footer.indexOf(">Details</a>")).isLessThan(footer.indexOf("class=\"card-nutri-scores\""));

        product.setNutritionGrade(NutritionGrade.C);
        products.saveAndFlush(product);
        html = mvc.perform(get("/products").param("query", "OFF community display"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html.split("class=\"nutri-score-step active\"", -1)).hasSize(2);

        product.setNutritionGrade(NutritionGrade.UNKNOWN);
        product.getOpenFoodFactsData().setNutritionGrade("not-applicable");
        products.saveAndFlush(product);
        html = mvc.perform(get("/products").param("query", "OFF community display"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("class=\"nutri-score-unavailable\"", "nicht anwendbar")
                .doesNotContain("class=\"nutri-score-step active\"");

        product.getOpenFoodFactsData().setNutritionGrade("unknown");
        products.saveAndFlush(product);
        html = mvc.perform(get("/products").param("query", "OFF community display"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("class=\"nutri-score\"", "class=\"nutri-score-step active\"");
    }

    @Test
    void favoritesUseTheSameAttributedDataAndApprovedImages() throws Exception {
        var user = users.findByUsername("max").orElseThrow();
        user.addFavoriteProduct(product);
        users.saveAndFlush(user);
        var favoritePage = mvc.perform(get("/favorites").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("max")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(favoritePage).contains("Quelle: Open Food Facts", "Community brand",
                "Nutri-Score: C", "Bio", "Favorit entfernen", "<img", "loading=\"lazy\"")
                .doesNotContain("ungeprüft", "Menge: 1 l", "Community brand owner", "Weitere Angaben");
        mvc.perform(get("/api/v1/users/me/favorites").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("max")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + product.getId() + ")].openFoodFactsData.metadata.servingSize")
                        .value(org.hamcrest.Matchers.hasItem("250 ml")));
    }
}
