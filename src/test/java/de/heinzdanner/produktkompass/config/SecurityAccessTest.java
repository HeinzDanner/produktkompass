package de.heinzdanner.produktkompass.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prüft, dass die serverseitigen Zugriffsregeln aus {@link SecurityConfig}
 * für anonyme Besucher, normale Benutzer (ROLE_USER) und Administratoren
 * (ROLE_ADMIN) tatsächlich greifen.
 *
 * <p>MockMvc wird hier bewusst manuell über {@code webAppContextSetup(...).apply(springSecurity())}
 * aufgebaut statt per {@code @AutoConfigureMockMvc}, weil die automatische
 * Security-Integration in der aktuell verwendeten Spring-Boot-Version nicht
 * zuverlässig greift und sonst jede Anfrage als anonym behandelt würde.</p>
 */
@SpringBootTest
class SecurityAccessTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // --- Öffentliche Seiten: für jeden erreichbar, auch ohne Login ---

    @Test
    @WithAnonymousUser
    void publicPagesAreReachableAnonymously() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/products")).andExpect(status().isOk());
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/register")).andExpect(status().isOk());
    }

    // --- /favorites: nur für angemeldete Benutzer ---

    @Test
    @WithAnonymousUser
    void favoritesRedirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/favorites"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "max", roles = "USER")
    void favoritesIsReachableForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/favorites")).andExpect(status().isOk());
    }

    // --- /admin: nur für ADMIN oder EDITOR ---

    @Test
    @WithAnonymousUser
    void adminRedirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "max", roles = "USER")
    void adminIsForbiddenForPlainUser() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminIsReachableForAdmin() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void adminIsReachableForEditor() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isOk());
    }

    // --- Admin-Unterseiten (Bearbeiten, Audit-Log): gleiche Regeln wie /admin ---

    @Test
    @WithAnonymousUser
    void adminAuditLogRedirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/admin/audit-log"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "max", roles = "USER")
    void adminAuditLogIsForbiddenForPlainUser() throws Exception {
        mockMvc.perform(get("/admin/audit-log")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAuditLogIsReachableForAdmin() throws Exception {
        mockMvc.perform(get("/admin/audit-log")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "max", roles = "USER")
    void adminProductEditIsForbiddenForPlainUser() throws Exception {
        mockMvc.perform(get("/admin/products/1/edit")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void userManagementIsReachableForAdmin() throws Exception {
        mockMvc.perform(get("/admin/users")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    void userManagementIsForbiddenForEditor() throws Exception {
        mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "max", roles = "USER")
    void userManagementIsForbiddenForPlainUser() throws Exception {
        mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
    }

    // --- Zugriff auf 403-Seite liefert korrekten Statuscode ---
    // (Die tatsächliche HTML-Fehlerseite wird in ErrorPageRenderingTest mit
    // einem echten eingebetteten Server geprüft, da MockMvc keinen
    // Error-Dispatch an einen Servlet-Container durchführt.)

    @Test
    @WithMockUser(username = "max", roles = "USER")
    void forbiddenAdminAccessReturnsForbiddenStatus() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
    }
}
