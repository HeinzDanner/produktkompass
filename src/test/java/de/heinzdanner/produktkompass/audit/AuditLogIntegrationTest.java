package de.heinzdanner.produktkompass.audit;

import de.heinzdanner.produktkompass.product.EditorialAssessment;
import de.heinzdanner.produktkompass.product.EditorialAssessmentService;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.product.SourceService;
import de.heinzdanner.produktkompass.product.dto.SourceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuditLogIntegrationTest {

    @Autowired private AuditLogService logs;
    @Autowired private AuditLogRepository repository;
    @Autowired private EditorialAssessmentService assessments;
    @Autowired private ProductRepository products;
    @Autowired private SourceService sources;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @Transactional
    @WithMockUser(username = "audit-editor", roles = "EDITOR")
    void sharedServiceRecordsAuthenticatedActorForCatalogChanges() throws Exception {
        var source = sources.createSource(new SourceRequest("Audit source", "https://example.org", "Web", null));
        var log = repository.findAll().stream()
                .filter(entry -> entry.getEntityName().equals("Source") && entry.getEntityId().equals(source.getId()))
                .findFirst().orElseThrow();
        assertThat(log.getActor()).isEqualTo("audit-editor");
        assertThat(log.getAction()).isEqualTo("CREATE");
        mvc.perform(get("/admin/audit-log"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("audit-editor")));
    }

    @Test
    @Transactional
    @WithMockUser(username = "audit-admin", roles = "ADMIN")
    void apiExposesActorAndLegacyEntriesRemainExplicitlyUnknown() throws Exception {
        logs.logAction("Source", 123L, "UPDATE", "Audit API test");
        jdbc.update("INSERT INTO audit_logs (entity_name, entity_id, action, details, created_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)", "Source", 123L, "CREATE", "Legacy audit test");
        assertThat(repository.findAll()).anySatisfy(log -> {
            assertThat(log.getDetails()).isEqualTo("Legacy audit test");
            assertThat(log.getActor()).isNull();
        });
        mvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"actor\":\"audit-admin\"")));
        mvc.perform(get("/admin/audit-log"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nicht erfasst (Altbestand)")));
    }

    @Test
    @Transactional
    @WithAnonymousUser
    void anonymousExecutionIsNotRecordedAsAnAuthenticatedUser() {
        assertThat(logs.logAction("Source", 123L, "CREATE", "System task").getActor()).isEqualTo("SYSTEM");
    }

    @Test
    @Transactional
    void executionWithoutSecurityContextIsMarkedAsSystem() {
        assertThat(logs.logAction("Source", 123L, "CREATE", "System task").getActor()).isEqualTo("SYSTEM");
    }

    @Test
    @Transactional
    @WithMockUser(username = "assessment-editor", roles = "EDITOR")
    void assessmentCreateUpdateAndDeleteAreAuditedButMissingIdsAreNot() {
        Product product = products.save(new Product("Audit product", "Audit test"));
        EditorialAssessment assessment = assessments.createAssessment(new EditorialAssessment(
                product, EditorialAssessment.AssessmentType.ENVIRONMENTAL, "Audit label", 3, "Test"));
        Long id = assessment.getId();
        EditorialAssessment update = new EditorialAssessment(product,
                EditorialAssessment.AssessmentType.ENVIRONMENTAL, "Updated label", 4, "Updated test");
        update.setStatus(EditorialAssessment.AssessmentStatus.PUBLISHED);
        assertThat(assessments.updateAssessment(id, update)).isPresent();
        assertThat(assessments.deleteAssessment(id)).isTrue();
        assertThat(assessments.deleteAssessment(Long.MAX_VALUE)).isFalse();
        assertThat(assessments.updateAssessment(Long.MAX_VALUE, update)).isEmpty();
        var entries = repository.findAll().stream()
                .filter(log -> log.getEntityName().equals("EditorialAssessment") && log.getEntityId().equals(id))
                .toList();
        assertThat(entries).extracting(AuditLog::getAction).containsExactlyInAnyOrder("CREATE", "UPDATE", "DELETE");
        assertThat(entries).allSatisfy(log -> assertThat(log.getActor()).isEqualTo("assessment-editor"));
        assertThat(entries).anySatisfy(log -> assertThat(log.getDetails()).contains("Status: PUBLISHED"));
    }

    @Test
    @WithMockUser(username = "rollback-editor", roles = "EDITOR")
    void catalogChangeAndAuditEntryRollBackTogether() {
        long before = repository.count();
        Long sourceId = new TransactionTemplate(transactions).execute(transaction -> {
            var source = sources.createSource(new SourceRequest(
                    "Rolled back source", "https://example.org", "Web", null));
            assertThat(repository.count()).isEqualTo(before + 1);
            transaction.setRollbackOnly();
            return source.getId();
        });
        assertThat(sources.getSourceById(sourceId)).isEmpty();
        assertThat(repository.count()).isEqualTo(before);
    }
}
