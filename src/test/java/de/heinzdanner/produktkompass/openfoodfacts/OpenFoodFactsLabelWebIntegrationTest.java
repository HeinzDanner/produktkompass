package de.heinzdanner.produktkompass.openfoodfacts;

import de.heinzdanner.produktkompass.product.OpenFoodFactsDataRepository;
import de.heinzdanner.produktkompass.product.OpenFoodFactsLabelRepository;
import de.heinzdanner.produktkompass.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class OpenFoodFactsLabelWebIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private OpenFoodFactsTsvImporter importer;
    @Autowired private OpenFoodFactsDataRepository data;
    @Autowired private OpenFoodFactsLabelRepository labels;
    @Autowired private ProductService products;
    @TempDir Path temporaryDirectory;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void localReimportExposesPersistentCatalogAndCappedCardsWithAllLabelsInClosedDetails() throws Exception {
        Path file = temporaryDirectory.resolve("labels.tsv");
        String tagList = "en:no-gluten,en:no-lactose,en:vegan,en:organic,en:eu-organic,"
                + "en:vegetarian,en:no-added-sugar,fr:unknown-label";
        Files.writeString(file, "code\tproduct_name\tcountries_tags\tlabels_tags\tbrands_tags\n"
                + "0007090901\tLabelcatalog fixture\ten:germany\t" + tagList + "\txx:first,xx:second\n");
        Long productId = null;
        try {
            assertThat(importer.importFile(file, "en:germany", 100).inserted()).isEqualTo(1);
            productId = data.findAllByBarcodeIn(List.of("0007090901")).getFirst().getProductId();
            long count = labels.count();
            assertThat(importer.importFile(file, "en:germany", 100).updated()).isEqualTo(1);
            assertThat(labels.count()).isEqualTo(count);

            mvc.perform(get("/api/v1/products/{id}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.openFoodFactsData.labelTags").value(tagList))
                    .andExpect(jsonPath("$.openFoodFactsData.catalogLabels.length()").value(8))
                    .andExpect(jsonPath("$.openFoodFactsData.catalogLabels[*].canonicalTag", hasItem("fr:unknown-label")))
                    .andExpect(jsonPath("$.openFoodFactsData.catalogLabels[0].id").isNumber())
                    .andExpect(jsonPath("$.openFoodFactsData.catalogLabels[0].provenance")
                            .value("OPEN_FOOD_FACTS_UNVERIFIED"));
            mvc.perform(get("/api/v1/products/off-labels").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(count));
            mvc.perform(get("/api/v1/products/off-labels").param("size", "51"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/v1/products/off-labels").param("page", "-1"))
                    .andExpect(status().isBadRequest());

            String cards = mvc.perform(get("/products").param("query", "Labelcatalog fixture"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            // Public search now lists all available tags independently of the labels rendered on product cards.
            cards = cards.substring(cards.indexOf("<article "), cards.indexOf("</article>") + "</article>".length());
            assertThat(count(cards, "off-label-badge")).isEqualTo(3);
            assertThat(cards).contains("Ohne Gluten", "Ohne Laktose", "Vegan", "Quelle: Open Food Facts")
                    .contains("second")
                    .doesNotContain("unknown label", "EU-Bio-Logo", "ungeprüft");
            String detail = mvc.perform(get("/products/{id}", productId))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(count(detail, "off-label-badge")).isEqualTo(8);
            assertThat(detail).contains("unknown label", "fr:unknown-label", "EU-Bio-Logo",
                    "Open Database License (ODbL) 1.0")
                    .doesNotContain("Label-Angaben laut OFF", "Ungeprüfte Community-Angaben, keine redaktionelle Prüfung.");
            var details = java.util.regex.Pattern.compile("<details\\b[^>]*>").matcher(detail);
            int closedSections = 0;
            while (details.find()) {
                assertThat(details.group()).doesNotContain("open");
                closedSections++;
            }
            assertThat(closedSections).isEqualTo(5);
        } finally {
            if (productId != null) products.deleteProduct(productId);
            labels.deleteAll(labels.findAllByCanonicalTagIn(OpenFoodFactsTags.split(tagList)));
        }
    }

    private long count(String html, String token) {
        return java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(token)).matcher(html).results().count();
    }
}
