package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true")
@Transactional
class OpenFoodFactsNormalizationIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ProductRepository products;
    @Autowired private BrandRepository brands;
    @Autowired private CompanyRepository companies;
    @Autowired private AppUserRepository users;
    private MockMvc mvc;
    private Product pasta;
    private Product editorial;
    private Product ketchup;
    private Product bare;
    private Brand editorialBrand;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        editorialBrand = brands.saveAndFlush(new Brand("Normtest Redaktionsmarke", null));

        pasta = imported("Normtest Sugo", "80768090", data -> {
            data.setBrandTags("xx:Barilla");
            data.setLabelTags("en:no-gluten,en:no-preservatives,en:organic");
            data.setAllergenTags("en:gluten");
            data.setQuantity("380ml");
            data.setNutritionGrade("d");
            data.setPackagingTags("en:glass,de:verre");
        });
        editorial = imported("Normtest Redaktion", "80768091", data -> {
            data.setBrandTags("xx:Fremdmarke");
            data.setLabelTags("en:no-gluten");
            data.setNutritionGrade("e");
        });
        editorial.setBrand(editorialBrand);
        editorial.setNutritionGrade(NutritionGrade.B);
        editorial.setLabels(new java.util.HashSet<>(Set.of(ProductLabel.VEGAN)));
        editorial = products.saveAndFlush(editorial);
        ketchup = imported("Normtest Ketchup", "80768092", data -> {
            data.setBrandTags("xx:hela,xx:K-Classic,xx:la-baleine");
            data.setLabelTags("en:vegan,fr:Adapté au moulin");
        });
        bare = imported("Normtest Leer", "80768093", data -> { });
    }

    private Product imported(String name, String barcode, java.util.function.Consumer<OpenFoodFactsData> values) {
        Product product = new Product(name, null);
        product.setNutritionGrade(NutritionGrade.UNKNOWN);
        OpenFoodFactsData data = new OpenFoodFactsData(product, barcode, name, LocalDateTime.of(2026, 10, 1, 12, 0));
        values.accept(data);
        product.setOpenFoodFactsData(data);
        return products.saveAndFlush(product);
    }

    @Test
    void paginatedApiExposesDistinctValuesWithoutChangingStoredProvenance() throws Exception {
        mvc.perform(get("/api/v1/products/search").param("name", "Normtest").param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));

        mvc.perform(get("/api/v1/products/search").param("name", "Barilla"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(pasta.getId()))
                .andExpect(jsonPath("$.content[0].openFoodFactsBrandTags").value("xx:Barilla"))
                .andExpect(jsonPath("$.content[0].openFoodFacts.provenance").value("OPEN_FOOD_FACTS_UNVERIFIED"))
                .andExpect(jsonPath("$.content[0].openFoodFacts.brands[0]").value("Barilla"))
                .andExpect(jsonPath("$.content[0].openFoodFacts.brandsSource").value("brands_tags"))
                .andExpect(jsonPath("$.content[0].openFoodFacts.labels[0].display").value("Ohne Gluten"))
                .andExpect(jsonPath("$.content[0].openFoodFacts.labels[0].raw").value("en:no-gluten"))
                .andExpect(jsonPath("$.content[0].openFoodFacts.labelEquivalents", hasItem("GLUTEN_FREE")))
                .andExpect(jsonPath("$.content[0].display.offBrandFallback[0]").value("Barilla"))
                .andExpect(jsonPath("$.content[0].display.offNutriScoreFallback").value("D"))
                .andExpect(jsonPath("$.content[0].display.offQuantityFallback").value("380 ml"))
                .andExpect(jsonPath("$.content[0].display.offGlutenHint.conflict").value(true))
                .andExpect(jsonPath("$.content[0].labels").isEmpty())
                .andExpect(jsonPath("$.content[0].brand").doesNotExist());

        mvc.perform(get("/api/v1/products/search").param("name", "Normtest Redaktion"))
                .andExpect(jsonPath("$.content[0].brand.name").value("Normtest Redaktionsmarke"))
                .andExpect(jsonPath("$.content[0].labels[0]").value("VEGAN"))
                .andExpect(jsonPath("$.content[0].display.offBrandFallback[0]").value("Fremdmarke"))
                .andExpect(jsonPath("$.content[0].display.offNutriScoreFallback").value("E"))
                .andExpect(jsonPath("$.content[0].openFoodFacts.brands[0]").value("Fremdmarke"));

        mvc.perform(get("/api/v1/products/{id}", pasta.getId()))
                .andExpect(jsonPath("$.openFoodFactsData.labelTags").value("en:no-gluten,en:no-preservatives,en:organic"))
                .andExpect(jsonPath("$.openFoodFactsData.normalized.packaging[1].display").value("verre"))
                .andExpect(jsonPath("$.display.offLabels[2].display").value("Bio"));
    }

    @Test
    void textSearchFindsHumanBrandAndLabelTerms() throws Exception {
        for (String term : new String[]{"barilla", "ohne gluten", "glutenfrei", "konservierungsstoffe"}) {
            mvc.perform(get("/api/v1/products/search").param("name", term).param("size", "50"))
                    .andExpect(jsonPath("$.content[*].id", hasItem(pasta.getId().intValue())));
        }
        mvc.perform(get("/api/v1/products/search").param("name", "la baleine"))
                .andExpect(jsonPath("$.content[*].id", hasItem(ketchup.getId().intValue())));
        mvc.perform(get("/api/v1/products/search").param("name", "K-Classic"))
                .andExpect(jsonPath("$.content[*].id", hasItem(ketchup.getId().intValue())));
        mvc.perform(get("/api/v1/products/search").param("name", "vegan").param("size", "50"))
                .andExpect(jsonPath("$.content[*].id", hasItem(ketchup.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id", hasItem(editorial.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(pasta.getId().intValue()))));
    }

    @Test
    void labelFiltersStayEditorialUnlessOffLabelsAreExplicitlyIncluded() throws Exception {
        mvc.perform(get("/api/v1/products/search").param("name", "Normtest").param("label", "GLUTEN_FREE"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/products/search").param("name", "Normtest").param("label", "GLUTEN_FREE")
                        .param("includeOpenFoodFactsLabels", "true"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", hasItem(pasta.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id", hasItem(editorial.getId().intValue())));
        mvc.perform(get("/api/v1/products/search").param("name", "Normtest").param("label", "VEGAN")
                        .param("includeOpenFoodFactsLabels", "true"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/products/search").param("name", "Normtest").param("label", "VEGAN"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(editorial.getId()));
        mvc.perform(get("/api/v1/products/search").param("brandId", editorialBrand.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/products").param("query", "Normtest").param("label", "GLUTEN_FREE")
                        .param("includeOpenFoodFactsLabels", "true").param("size", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void cardsAndDetailShowReadableSourceLabelledValuesAndKeepConflictsHonest() throws Exception {
        String list = mvc.perform(get("/products").param("query", "Normtest").param("size", "50"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("Barilla", "hela", "Fremdmarke", "K-Classic", "la-baleine",
                        "off-label-badge", "Quelle: Open Food Facts", "Ohne Gluten", "Bio",
                        "Nutri-Score: D", "Nutri-Score: E",
                        "Normtest Redaktionsmarke", "Nutri-Score: B", "Vegan")
                .doesNotContain("ungeprüft", "Widersprüchlich", "OFF-Marken-Tags",
                        "glutenfrei laut", "allergenfrei",
                        "Adapté au moulin", "Ohne Konservierungsstoffe", "Verpackung laut OFF:",
                        "Menge: 380 ml", "Gluten als Allergen angegeben", "Weitere Angaben");

        String detail = mvc.perform(get("/products/{id}", pasta.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("Barilla", ">D<",
                "380 ml", "Glas (Material)", "verre",
                "Gluten als Allergen angegeben", "off-label-badge", "Quelle: Open Food Facts",
                "Ohne Gluten",
                "Barilla (aus OFF-Feld brands_tags)", "xx:Barilla", "Gluten", "(en:gluten)",
                "Ohne Konservierungsstoffe", "<details class=\"off-details\">", "<summary>Nährwerte laut OFF</summary>",
                "<summary>Bild-URLs laut OFF</summary>", "Open Database License (ODbL) 1.0",
                "Datenquellen und Herkunft", "nicht unabhängig geprüft", "/report", "Fehlerhafte Daten melden")
                .doesNotContain("<details open", "class=\"off-details\" open", "Label-Angaben laut OFF",
                        "ungeprüft", "Widersprüchlich",
                        "Ungeprüfte Community-Angaben, keine redaktionelle Prüfung.",
                        "Label laut OFF: Ohne Gluten (ungeprüft)", "(en:no-gluten)");

        String unknownDetail = mvc.perform(get("/products/{id}", ketchup.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(unknownDetail).contains("Adapté au moulin", "la-baleine")
                .doesNotContain("(fr:Adapté au moulin; nicht übersetzt)");

        String bareDetail = mvc.perform(get("/products/{id}", bare.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(bareDetail).contains("Keine Marke angegeben", "Keine Angabe", "<strong>Palmöl</strong>")
                .doesNotContain("laut OFF (ungeprüft; redaktionell", "Widersprüchlich", "Label-Angaben laut OFF");

        String editorialDetail = mvc.perform(get("/products/{id}", editorial.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(editorialDetail).contains("Normtest Redaktionsmarke", "Fremdmarke", ">B<", ">E<")
                .doesNotContain("ungeprüft");
    }

    @Test
    void importedFavoriteCardsUseTheSameNormalizedPresentation() throws Exception {
        var max = users.findByUsername("max").orElseThrow();
        max.addFavoriteProduct(pasta);
        max.addFavoriteProduct(ketchup);
        users.saveAndFlush(max);
        String favorites = mvc.perform(get("/favorites").param("size", "50").with(user("max")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(favorites).contains("Barilla", "hela", "Ohne Gluten", "la-baleine")
                .doesNotContain("OFF-Marken-Tags", "ungeprüft", "Widersprüchlich", "Adapté au moulin",
                        "Menge: 380 ml", "Gluten als Allergen angegeben");
    }

    @Test
    void detailShowsBothDistinctSizesAndScoresButLabelsStayInlineAndDuplicatesDisappear() throws Exception {
        editorial.setPackageSize("500 ml");
        editorial.setPackagingType(PackagingType.GLASS);
        editorial.getOpenFoodFactsData().setQuantity("380ml");
        editorial.getOpenFoodFactsData().setPackagingTags("en:glass,en:bottle");
        editorial.getOpenFoodFactsData().setBrandTags("xx:Normtest Redaktionsmarke,xx:Other");
        editorial.getOpenFoodFactsData().setLabelTags("en:vegan,en:organic");
        products.saveAndFlush(editorial);
        String html = mvc.perform(get("/products/{id}", editorial.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String routine = html.substring(0, html.indexOf("<summary>Datenquellen und Herkunft"));
        assertThat(routine).contains("500 ml", "380 ml", ">B<", ">E<", "GLASS", "Flasche",
                        "Other", "Vegan", "Bio", "off-source-marker")
                .doesNotContain("Glas (Material)", "ungeprüft", "Widersprüchlich", "Label-Angaben laut OFF");
        assertThat(java.util.regex.Pattern.compile(">Vegan<").matcher(routine).results().count()).isEqualTo(1);
        assertThat(java.util.regex.Pattern.compile(">Normtest Redaktionsmarke<").matcher(routine).results().count()).isEqualTo(1);
        assertThat(routine.indexOf("off-label-badge")).isLessThan(routine.indexOf("Fehlerhafte Daten melden"));
        assertThat(routine).doesNotContain("Open Database License");
        assertThat(html).contains("Open Database License (ODbL) 1.0");
    }

    @Test
    void brandCompanyAndSimilarCardsUseTheSameCuratedLabels() throws Exception {
        var company = companies.saveAndFlush(new Company("Normtest company", null, null, null));
        pasta.setBrand(editorialBrand);
        pasta.setCompany(company);
        pasta.setAlternativeProducts(new java.util.HashSet<>(Set.of(ketchup)));
        products.saveAndFlush(pasta);
        for (String path : new String[]{"/brands/" + editorialBrand.getId(), "/companies/" + company.getId(),
                "/products/" + pasta.getId() + "/similar"}) {
            String html = mvc.perform(get(path)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).contains("Ohne Gluten", "Bio", "off-label-badge", "Quelle: Open Food Facts")
                    .doesNotContain("Ohne Konservierungsstoffe", "Adapté au moulin", "Labels laut OFF (ungeprüft):");
        }
    }
}