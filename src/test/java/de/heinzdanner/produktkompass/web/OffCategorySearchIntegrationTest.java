package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.product.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class OffCategorySearchIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ProductRepository products;
    @Autowired private OpenFoodFactsDataRepository offData;
    @Autowired private EntityManager entityManager;
    private MockMvc mvc;
    private Product chocolate;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        chocolate = imported("OFF category chocolate", "89000001",
                " Chocolats noirs, nl:Chocolade, ,Chocolats noirs ",
                "nl:Koeken/Chocolade/Snoep,nl:Chocolade");
        imported("OFF category nonmatch", "89000002", "nl:Chocolade-extra", null);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void rawCategoriesAndTagsAreSelectableAndMatchExactlyWithoutEditorialAssignments() throws Exception {
        assertThat(offData.findCategoryEntries()).contains("Chocolats noirs", "nl:Chocolade",
                "nl:Koeken/Chocolade/Snoep");
        for (String category : new String[]{"Chocolats noirs", "nl:Chocolade", "nl:Koeken/Chocolade/Snoep"}) {
            String html = mvc.perform(get("/products").param("categoryId", "off:" + category))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains(chocolate.getName()).doesNotContain("OFF category nonmatch",
                    "value=\"off:" + category + "\"");
            mvc.perform(get("/api/v1/products/search").param("offCategory", category))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(chocolate.getId()));
        }
        assertThat(products.findByIdWithDetails(chocolate.getId()).orElseThrow().getCategories()).isEmpty();
    }

    @Test
    void categorySelectionSurvivesPaginationAndTextStillRestrictsResults() throws Exception {
        imported("OFF category second", "89000003", "nl:Chocolade", null);
        entityManager.flush();
        String html = mvc.perform(get("/products").param("categoryId", "off:nl:Chocolade").param("size", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("page=1", "categoryId=off:");
        mvc.perform(get("/api/v1/products/search").param("offCategory", "nl:Chocolade")
                        .param("name", "second"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("OFF category second"));
        mvc.perform(get("/products").param("categoryId", "off:nl:Chocolade")
                        .param("query", "No category text match"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Keine Produkte gefunden.")));
    }

    @Test
    void partialUpdatesRetainOtherFieldAndClearingBothRemovesStaleEntries() {
        var data = offData.findById(chocolate.getId()).orElseThrow();
        data.setCategories(null);
        entityManager.flush();
        entityManager.clear();
        assertThat(offData.findCategoryEntries()).doesNotContain("Chocolats noirs").contains("nl:Chocolade");
        data = offData.findById(chocolate.getId()).orElseThrow();
        data.setCategoryTags("");
        entityManager.flush();
        entityManager.clear();
        assertThat(offData.findCategoryEntries()).doesNotContain("nl:Chocolade", "nl:Koeken/Chocolade/Snoep");
    }

    private Product imported(String name, String barcode, String categories, String tags) {
        Product product = new Product(name, null);
        var data = new OpenFoodFactsData(product, barcode, name, LocalDateTime.of(2025, 1, 1, 0, 0));
        data.setCategories(categories);
        data.setCategoryTags(tags);
        product.setOpenFoodFactsData(data);
        return products.save(product);
    }
}
