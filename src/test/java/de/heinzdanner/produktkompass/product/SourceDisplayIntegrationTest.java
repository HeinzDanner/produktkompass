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
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class SourceDisplayIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ProductRepository products;
    @Autowired private SourceRepository sources;
    private MockMvc mvc;
    private Product product;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        product = products.saveAndFlush(new Product("Source display test product", null));
    }

    @Test
    void publicDetailShowsMetadataForEachSourceWithoutClaimingProductVerification() throws Exception {
        Source dated = source("Display dated source", "Studie", LocalDateTime.of(2025, 6, 12, 14, 35));
        Source undated = source("Display undated source", "  ", null);
        product.setSources(new HashSet<>(Set.of(dated, undated)));
        products.saveAndFlush(product);

        String html = detail();
        String datedItem = sourceItem(html, dated.getTitle());
        assertThat(datedItem).contains("https://example.org/source", "Studie",
                "Prüfdatum der Quelle", "12.06.2025 14:35", "datetime=\"2025-06-12T14:35\"")
                .doesNotContain("Kein Prüfdatum hinterlegt");
        assertThat(sourceItem(html, undated.getTitle()))
                .contains("Nicht angegeben", "Kein Prüfdatum hinterlegt")
                .doesNotContain("<time");
        assertThat(html).contains("nicht auf eine vollständige Prüfung aller Produktangaben")
                .doesNotContain("Keine Katalogquelle zugeordnet.");
        mvc.perform(get("/api/v1/sources/{id}", undated.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedAt").isEmpty());
    }

    @Test
    void publicDetailExplicitlyShowsMissingCatalogSources() throws Exception {
        assertThat(detail()).contains("Keine Katalogquelle zugeordnet.")
                .doesNotContain("Prüfdatum der Quelle", "Kein Prüfdatum hinterlegt");
    }

    @Test
    void missingCatalogSourceDoesNotHideOrRepurposeOffProvenance() throws Exception {
        product.setOpenFoodFactsData(new OpenFoodFactsData(product, "0000123456789",
                "OFF source display test", LocalDateTime.of(2025, 7, 3, 10, 0)));
        products.saveAndFlush(product);
        assertThat(detail()).contains("Keine Katalogquelle zugeordnet.", "0000123456789",
                "Produkt, Zutaten und Herkunft laut OFF", "Zuletzt importiert (UTC)", "03.07.2025 10:00")
                .doesNotContain("Prüfdatum der Quelle");
    }

    @Test
    void adminEditFlowsThroughToPublicMetadataAndCanClearOptionalDate() throws Exception {
        Source source = source("AAA Source display admin test", null, null);
        product.setSources(new HashSet<>(Set.of(source)));
        products.saveAndFlush(product);
        updateSource(source, "2025-08-04T09:15");
        assertThat(sourceItem(detail(), source.getTitle())).contains("04.08.2025 09:15", "Bericht");
        mvc.perform(get("/api/v1/sources/{id}", source.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedAt").value("2025-08-04T09:15:00"));
        String admin = mvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(admin).contains(source.getTitle(), "04.08.2025 09:15", "Prüfdatum der Quelle");

        updateSource(source, "");
        assertThat(sourceItem(detail(), source.getTitle())).contains("Kein Prüfdatum hinterlegt")
                .doesNotContain("<time");
        assertThat(sources.findById(source.getId()).orElseThrow().getVerifiedAt()).isNull();
    }

    private Source source(String title, String type, LocalDateTime verifiedAt) {
        return sources.saveAndFlush(new Source(title, "https://example.org/source", type, verifiedAt));
    }

    private void updateSource(Source source, String verifiedAt) throws Exception {
        mvc.perform(post("/admin/sources/{id}", source.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("title", source.getTitle()).param("url", source.getUrl())
                        .param("type", "Bericht").param("verifiedAt", verifiedAt))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    private String detail() throws Exception {
        return mvc.perform(get("/products/{id}", product.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String sourceItem(String html, String title) {
        int titleIndex = html.indexOf(">" + title + "</a>");
        assertThat(titleIndex).isGreaterThanOrEqualTo(0);
        return html.substring(html.lastIndexOf("<li", titleIndex), html.indexOf("</li>", titleIndex));
    }
}
