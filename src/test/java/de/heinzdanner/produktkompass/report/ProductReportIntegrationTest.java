package de.heinzdanner.produktkompass.report;

import de.heinzdanner.produktkompass.audit.AuditLogRepository;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import jakarta.persistence.EntityManager;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@Transactional
class ProductReportIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ProductRepository products;
    @Autowired private ProductReportRepository reports;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private ProductReportService service;
    @Autowired private EntityManager entityManager;

    private MockMvc mvc;
    private Product product;
    private String reportUrl;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        product = products.saveAndFlush(new Product("Report fixture", "Unverändert"));
        reportUrl = "/products/" + product.getId() + "/report";
    }

    @Test
    void anonymousFormRendersCsrfAndPrivacyNotice() throws Exception {
        mvc.perform(get(reportUrl))
                .andExpect(status().isOk())
                .andExpect(view().name("product-report"))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("maxlength=\"2000\"")))
                .andExpect(content().string(containsString("personenbezogene Daten")));
    }

    @Test
    void anonymousSubmissionIsPrgAndNeverChangesProductOrCollectsReporterIdentity() throws Exception {
        long count = reports.count();
        entityManager.refresh(product);
        LocalDateTime updatedAt = product.getUpdatedAt();
        var response = mvc.perform(post(reportUrl).with(csrf())
                        .param("description", "  Die Mengenangabe stimmt nicht.  ")
                        .param("targetField", "PACKAGING").param("provenance", "OPEN_FOOD_FACTS")
                        .param("status", "CLOSED").param("moderatedBy", "injected")
                        .param("product.name", "injected").param("email", "ignored@example.invalid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(reportUrl))
                .andExpect(flash().attributeExists("reportSuccess")).andReturn();
        mvc.perform(get(reportUrl).session((MockHttpSession) response.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vielen Dank!")))
                .andExpect(content().string(containsString("role=\"status\"")));
        reports.flush();
        assertThat(reports.count()).isEqualTo(count + 1);
        ProductReport report = reports.findAll().stream()
                .filter(it -> it.getProduct().getId().equals(product.getId())).findFirst().orElseThrow();
        assertThat(report.getDescription()).isEqualTo("Die Mengenangabe stimmt nicht.");
        assertThat(report.getTargetField()).isEqualTo(ReportTargetField.PACKAGING);
        assertThat(report.getProvenance()).isEqualTo(ReportProvenance.OPEN_FOOD_FACTS);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(report.getSubmittedAt()).isNotNull();
        assertThat(report.getModeratedAt()).isNull();
        assertThat(report.getModeratedBy()).isNull();
        entityManager.clear();
        Product unchanged = products.findById(product.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Report fixture");
        assertThat(unchanged.getDescription()).isEqualTo("Unverändert");
        assertThat(unchanged.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void optionalFieldsCanBeOmittedAndLimitIsAccepted() throws Exception {
        mvc.perform(post(reportUrl).with(csrf()).param("description", "a".repeat(2000)))
                .andExpect(status().is3xxRedirection());
        ProductReport saved = reports.findAll().stream()
                .filter(it -> it.getProduct().getId().equals(product.getId())).findFirst().orElseThrow();
        assertThat(saved.getDescription()).hasSize(2000);
        assertThat(saved.getTargetField()).isNull();
        assertThat(saved.getProvenance()).isNull();
    }

    @Test
    void csrfRemainsRequiredEvenForAnonymousSubmissions() throws Exception {
        long count = reports.count();
        mvc.perform(post(reportUrl).param("description", "Fehler"))
                .andExpect(status().isForbidden());
        mvc.perform(post(reportUrl).with(csrf().useInvalidToken()).param("description", "Fehler"))
                .andExpect(status().isForbidden());
        assertThat(reports.count()).isEqualTo(count);
    }

    @Test
    void invalidDescriptionsAndEnumsRedisplayWithoutWrites() throws Exception {
        long count = reports.count();
        long auditCount = auditLogs.count();
        for (String description : new String[]{"", "   ", "x".repeat(2001)}) {
            mvc.perform(post(reportUrl).with(csrf()).param("description", description))
                    .andExpect(status().isOk())
                    .andExpect(view().name("product-report"))
                    .andExpect(model().attributeHasFieldErrors("reportRequest", "description"));
        }
        for (String field : new String[]{"targetField", "provenance"}) {
            mvc.perform(post(reportUrl).with(csrf()).param("description", "Gültige Beschreibung")
                            .param(field, "NOT_ALLOWED"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("product-report"))
                    .andExpect(model().attributeHasFieldErrors("reportRequest", field))
                    .andExpect(content().string(containsString("G&uuml;ltige Beschreibung")));
        }
        assertThat(reports.count()).isEqualTo(count);
        assertThat(auditLogs.count()).isEqualTo(auditCount);
        assertThat(product.getDescription()).isEqualTo("Unverändert");
    }

    @Test
    void nonexistentProductIs404IncludingValidationFailures() throws Exception {
        mvc.perform(get("/products/9223372036854775807/report")).andExpect(status().isNotFound());
        mvc.perform(post("/products/9223372036854775807/report").with(csrf())
                        .param("description", "Fehler"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/products/9223372036854775807/report").with(csrf())
                        .param("description", ""))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceAlsoValidatesDescriptionsBeforePersistence() {
        long count = reports.count();
        ProductReportRequest request = new ProductReportRequest();
        request.setDescription("x".repeat(2001));
        assertThatThrownBy(() -> service.submit(product.getId(), request))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(reports.count()).isEqualTo(count);
    }

    @Test
    void queueAndStatusAreProtectedForAnonymousAndOrdinaryUsers() throws Exception {
        ProductReport report = createReport("Fehler");
        mvc.perform(get("/admin/reports"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/admin/reports/" + report.getId() + "/status").with(csrf())
                        .param("status", "CLOSED"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/reports").with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/reports/" + report.getId() + "/status").with(csrf())
                        .with(user("reader").roles("USER")).param("status", "CLOSED"))
                .andExpect(status().isForbidden());
        assertThat(report.getStatus()).isEqualTo(ReportStatus.OPEN);
    }

    @Test
    void editorCanModerateWithAuditAndAdminCanCloseAndReopen() throws Exception {
        ProductReport report = createReport("<script>alert(1)</script>");
        long auditCount = auditLogs.count();
        mvc.perform(get("/admin/reports").with(user("editor").roles("EDITOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post("/admin/reports/" + report.getId() + "/status").with(csrf())
                        .with(user("editor").roles("EDITOR")).param("status", "REVIEWED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/reports"))
                .andExpect(flash().attributeExists("adminSuccess"));
        assertThat(report.getStatus()).isEqualTo(ReportStatus.REVIEWED);
        assertThat(report.getModeratedBy()).isEqualTo("editor");
        assertThat(report.getModeratedAt()).isNotNull();
        assertThat(auditLogs.count()).isEqualTo(auditCount + 1);
        assertThat(auditLogs.findAll()).anySatisfy(log -> {
            assertThat(log.getEntityName()).isEqualTo("ProductReport");
            assertThat(log.getEntityId()).isEqualTo(report.getId());
            assertThat(log.getDetails()).contains("editor", "OPEN -> REVIEWED").doesNotContain("<script>");
        });
        for (String state : new String[]{"CLOSED", "OPEN"}) {
            mvc.perform(post("/admin/reports/" + report.getId() + "/status").with(csrf())
                            .with(user("admin").roles("ADMIN")).param("status", state))
                    .andExpect(status().is3xxRedirection());
            assertThat(report.getStatus().name()).isEqualTo(state);
        }
        reports.flush();
        assertThat(product.getDescription()).isEqualTo("Unverändert");
    }

    @Test
    void invalidModerationAndMissingCsrfNeverChangeStatus() throws Exception {
        ProductReport report = createReport("Fehler");
        String url = "/admin/reports/" + report.getId() + "/status";
        mvc.perform(post(url).with(user("admin").roles("ADMIN")).param("status", "CLOSED"))
                .andExpect(status().isForbidden());
        for (String value : new String[]{"NOT_ALLOWED", ""}) {
            mvc.perform(post(url).with(csrf()).with(user("admin").roles("ADMIN"))
                            .param("status", value))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/admin/reports/9223372036854775807/status").with(csrf())
                        .with(user("admin").roles("ADMIN")).param("status", "CLOSED"))
                .andExpect(status().isNotFound());
        assertThat(report.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(report.getModeratedAt()).isNull();
    }

    @Test
    void queueIsPaginatedCappedFilteredAndStable() throws Exception {
        for (int i = 0; i < 4; i++) {
            createReport("Meldung " + i);
        }
        var result = mvc.perform(get("/admin/reports").with(user("admin").roles("ADMIN"))
                        .param("page", "1").param("size", "2").param("status", "OPEN"))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getModelAndView().getModel().get("reportPage"))
                .isInstanceOfSatisfying(Page.class, page -> {
                    assertThat(page.getNumber()).isEqualTo(1);
                    assertThat(page.getContent()).hasSize(2);
                    assertThat(page.getSize()).isEqualTo(2);
                });
        mvc.perform(get("/admin/reports").with(user("editor").roles("EDITOR"))
                        .param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Keine Meldungen")));
        for (String size : new String[]{"0", "51", "2147483647"}) {
            mvc.perform(get("/admin/reports").with(user("admin").roles("ADMIN")).param("size", size))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/admin/reports").with(user("admin").roles("ADMIN")).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/reports").with(user("admin").roles("ADMIN")).param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    private ProductReport createReport(String description) {
        ProductReportRequest request = new ProductReportRequest();
        request.setDescription(description);
        ProductReport report = reports.saveAndFlush(new ProductReport(product, request));
        return report;
    }
}
