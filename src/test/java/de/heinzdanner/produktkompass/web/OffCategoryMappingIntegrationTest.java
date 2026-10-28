package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.product.*;
import de.heinzdanner.produktkompass.audit.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class OffCategoryMappingIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private AuditLogRepository audit;
    private MockMvc mvc;
    private Category chocolate;
    private Product imported;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        chocolate = categories.findAll().stream().filter(c -> c.getName().equals("Süßwaren und Schokolade"))
                .findFirst().orElseThrow();
        imported = new Product("Mapping imported test", null);
        var data = new OpenFoodFactsData(imported, "89340000001", imported.getName(),
                LocalDateTime.of(2025, 1, 1, 0, 0));
        data.setCategories("Chocolats noirs");
        data.setCategoryTags("nl:Chocolade,mapping:custom");
        imported.setOpenFoodFactsData(data);
        products.saveAndFlush(imported);
    }

    @Test
    void germanCategoryMatchesOffMappingsAndDirectAssignmentsWithoutDuplicates() throws Exception {
        mvc.perform(get("/api/v1/products/search").param("categoryId", chocolate.getId().toString())
                        .param("name", imported.getName()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        imported.addCategory(chocolate);
        products.saveAndFlush(imported);
        mvc.perform(get("/api/v1/products/search").param("categoryId", chocolate.getId().toString())
                        .param("name", imported.getName()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        String html = mvc.perform(get("/products").param("categoryId", chocolate.getId().toString())
                        .param("query", imported.getName()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains(imported.getName()).doesNotContain("value=\"off:", "<optgroup");
    }

    @Test
    void editorCanAssignReassignAndRemoveWithAuditAndImmediateSearchEffect() throws Exception {
        var custom = categories.saveAndFlush(new Category("Mapping test category", null));
        var other = categories.saveAndFlush(new Category("Mapping other category", null));
        for (Long target : new Long[]{custom.getId(), other.getId(), null}) {
            mvc.perform(post("/admin/category-mappings").with(user("editor").roles("EDITOR")).with(csrf())
                            .param("value", "mapping:custom").param("categoryId", target == null ? "" : target.toString()))
                    .andExpect(status().is3xxRedirection());
            mvc.perform(get("/api/v1/products/search").param("categoryId", other.getId().toString())
                            .param("name", imported.getName()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(other.getId().equals(target) ? 1 : 0));
        }
        assertThat(audit.findAll().stream().filter(log -> log.getEntityName().equals("OffCategoryMapping")
                        && log.getDetails().startsWith("OFF: mapping:custom;"))
                .toList()).hasSize(3).allSatisfy(log -> assertThat(log.getActor()).isEqualTo("editor"));
        assertThat(imported.getCategories()).isEmpty();
        assertThat(imported.getOpenFoodFactsData().getCategoryTags()).contains("mapping:custom");
        String html = mvc.perform(get("/admin/category-mappings").with(user("editor").roles("EDITOR"))
                        .param("query", "mapping:custom"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("mapping:custom (OFF)", "Nicht zugeordnet", "_csrf");
    }

    @Test
    void mappingAdministrationRejectsUnauthorizedCsrfAndInvalidEntries() throws Exception {
        mvc.perform(get("/admin/category-mappings")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/category-mappings").with(user("max").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/category-mappings").with(user("editor").roles("EDITOR"))
                        .param("value", "mapping:custom").param("categoryId", chocolate.getId().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/category-mappings").with(user("max").roles("USER")).with(csrf())
                        .param("value", "mapping:custom").param("categoryId", chocolate.getId().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/category-mappings").with(user("editor").roles("EDITOR")).with(csrf())
                        .param("value", "not present").param("categoryId", chocolate.getId().toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/category-mappings").with(user("editor").roles("EDITOR")).with(csrf())
                        .param("value", "mapping:custom").param("categoryId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Chocolats noirs extra fin|Süßwaren und Schokolade",
            "de:Tomatensoßen|Saucen und Würzmittel",
            "en:rolled-oats|Reis und Getreideprodukte",
            "en:plain-oat-based-drinks|Milchprodukte und Milchalternativen",
            "Cafés solubles|Kaffee und Tee",
            "en:canned-tomatoes|Konserven und Fertiggerichte",
            "en:frozen-aromatic-plants|Tiefkühlprodukte",
            "en:brioches|Brot und Backwaren",
            "en:dry-durum-wheat-pasta|Nudeln und Teigwaren",
            "en:walnuts|Snacks und Knabberartikel",
            "Sanddornsäfte|Getränke",
            "en:fresh-vegetables|Obst und Gemüse",
            "en:bacon|Fleisch und Wurstwaren",
            "Tonijn|Fisch und Meeresfrüchte",
            "en:vegetable-oils|Öle und Fette",
            "Beurres de cacahuètes|Aufstriche und Konfitüren"
    })
    void researchedExactTermsMatchGermanCategoryWithoutChangingProduct(String value, String categoryName) throws Exception {
        var target = categories.findAll().stream().filter(c -> c.getName().equals(categoryName))
                .findFirst().orElseThrow();
        imported.getOpenFoodFactsData().setCategories(value);
        imported.getOpenFoodFactsData().setCategoryTags(null);
        products.saveAndFlush(imported);
        mvc.perform(get("/api/v1/products/search").param("categoryId", target.getId().toString())
                        .param("name", imported.getName()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertThat(imported.getCategories()).isEmpty();
        assertThat(imported.getOpenFoodFactsData().getCategories()).isEqualTo(value);
    }
}
