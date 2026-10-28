package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogRepository;
import de.heinzdanner.produktkompass.product.dto.SourceRequest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@Transactional
class SourceValidationIntegrationTest {
    @Autowired private SourceService service;
    @Autowired private SourceRepository sources;
    @Autowired private AuditLogRepository logs;
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;
    private Source existing;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        existing = sources.saveAndFlush(new Source("Validation original", "https://example.org", "Web", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "/relative", "//example.org", "ftp://example.org",
            "https://name:password@example.org", "https:///missing-host", "https://example.org/a b", "", "  "})
    void invalidUrlsAreRejectedByServiceWithoutChangesOrAuditEntries(String url) {
        long count = sources.count();
        long auditCount = logs.count();
        SourceRequest request = new SourceRequest("Changed", url, "Web", null);
        assertThatThrownBy(() -> service.createSource(request)).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.updateSource(existing.getId(), request))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(sources.count()).isEqualTo(count);
        assertThat(logs.count()).isEqualTo(auditCount);
        assertThat(sources.findById(existing.getId()).orElseThrow().getTitle()).isEqualTo("Validation original");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.org/path?q=one#part", "http://example.org", "HTTPS://example.org"})
    void httpUrlsAndOptionalOrPastReviewDatesAreAccepted(String url) {
        Source created = service.createSource(new SourceRequest("Accepted", url, "Web", null));
        assertThat(created.getUrl()).isEqualTo(url);
        assertThat(created.getVerifiedAt()).isNull();
        LocalDateTime past = LocalDateTime.of(2020, 1, 2, 10, 30);
        assertThat(service.updateSource(created.getId(), new SourceRequest("Updated", url, "Web", past)))
                .hasValueSatisfying(source -> assertThat(source.getVerifiedAt()).isEqualTo(past));
    }

    @Test
    void webCreateAndEditDisplayFieldErrorsAndPreserveSubmittedValues() throws Exception {
        for (String path : new String[]{"/admin/sources", "/admin/sources/" + existing.getId()}) {
            mvc.perform(post(path).with(user("admin").roles("ADMIN")).with(csrf())
                            .param("title", "Submitted title").param("url", "ftp://example.org")
                            .param("verifiedAt", "2999-01-01T10:00"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("sourceRequest", "url", "verifiedAt"))
                    .andExpect(model().attribute("sourceRequest", new SourceRequest("Submitted title",
                            "ftp://example.org", null, LocalDateTime.of(2999, 1, 1, 10, 0))))
                    .andExpect(view().name(path.equals("/admin/sources") ? "admin" : "admin-source-edit"));
        }
        assertThat(existing.getTitle()).isEqualTo("Validation original");
    }

    @Test
    void apiCreateAndUpdateRejectInvalidUrlAndFutureDate() throws Exception {
        long count = sources.count();
        for (String body : new String[]{
                "{\"title\":\"Invalid\",\"url\":\"ftp://example.org\"}",
                "{\"title\":\"Invalid\",\"url\":\"https://example.org\",\"verifiedAt\":\"2999-01-01T10:00:00\"}"}) {
            mvc.perform(post("/api/v1/sources").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/v1/sources/{id}", existing.getId())
                            .with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(sources.count()).isEqualTo(count);
        assertThat(existing.getTitle()).isEqualTo("Validation original");
    }

    @Test
    void serviceRejectsFutureDatesBeforeWriting() {
        long auditCount = logs.count();
        SourceRequest future = new SourceRequest("Future", "https://example.org", null,
                LocalDateTime.of(2999, 1, 1, 0, 0));
        assertThatThrownBy(() -> service.createSource(future)).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.updateSource(existing.getId(), future))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(logs.count()).isEqualTo(auditCount);
        assertThat(existing.getVerifiedAt()).isNull();
    }
}
