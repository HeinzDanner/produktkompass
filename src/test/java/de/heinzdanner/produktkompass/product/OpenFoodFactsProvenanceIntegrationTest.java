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
        imported.setCategories("en:plant-milks, de:pflanzendrinks");
        imported.setCategoryTags("en:plant-milks,de:pflanzendrinks");
        imported.setQuantity("1 l");
        imported.setIngredientsText("Water, oats");
        imported.setNutritionGrade("c");
        imported.setLanguageCode("de");
        imported.setSourceRevision("1759321800");
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
                .andExpect(jsonPath("$.openFoodFactsData.nutritionGrade").value("c"))
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
                .contains("Open-Food-Facts-Community-Daten", "nicht automatisch",
                        "Importdatum ist kein Prüfdatum", "0000123456789",
                        "https://world.openfoodfacts.org/product/0000123456789",
                        "Zuletzt importiert", "Open Database License (ODbL) 1.0")
                .doesNotContain("allergenfrei");

        var list = mvc.perform(get("/products").param("query", "OFF community display"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("Open-Food-Facts-Community-Daten",
                "https://world.openfoodfacts.org/product/0000123456789");
    }
}
