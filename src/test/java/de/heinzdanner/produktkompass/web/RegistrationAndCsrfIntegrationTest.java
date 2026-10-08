package de.heinzdanner.produktkompass.web;

import com.jayway.jsonpath.JsonPath;
import de.heinzdanner.produktkompass.user.AppUserRepository;
import de.heinzdanner.produktkompass.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RegistrationAndCsrfIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserRepository users;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void apiRegistrationCannotGrantAdministratorOrEditorRole() throws Exception {
        for (Role requestedRole : new Role[]{Role.ADMIN, Role.EDITOR}) {
            String username = "api-" + UUID.randomUUID();
            mockMvc.perform(post("/api/v1/users/register")
                            .with(csrf().asHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","password":"Sicheres1Passwort","role":"%s"}
                                    """.formatted(username, requestedRole)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.password").doesNotExist());

            assertThat(users.findByUsername(username).orElseThrow().getRole()).isEqualTo(Role.USER);
        }
    }

    @Test
    void apiRegistrationRejectsInvalidCredentialsWithoutCreatingAccount() throws Exception {
        String username = "api-" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/users/register")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"weak"}
                                """.formatted(username)))
                .andExpect(status().isBadRequest());
        assertThat(users.findByUsername(username)).isEmpty();
    }

    @Test
    void stateChangingWebRequestsRequireCsrfEvenForAdministrators() throws Exception {
        for (String path : new String[]{"/login", "/register", "/logout", "/favorites/add/1",
                "/tags", "/admin/products", "/admin/users/1/role", "/admin/users/1/unlock"}) {
            mockMvc.perform(post(path).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(path).with(user("admin").roles("ADMIN")).with(csrf().useInvalidToken()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void apiWritesRequireCsrfWithBasicAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/favorites/1").with(httpBasic("max", "pw")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/me/favorites/1").with(httpBasic("max", "pw")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/products/1").with(httpBasic("admin", "admin123")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"csrf-test\",\"password\":\"Sicheres1Passwort\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiTokenEndpointProvidesUsableTokenForRegistration() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(post("/api/v1/users/register")
                        .header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"api-%s","password":"Sicheres1Passwort"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users/register")
                        .session(session)
                        .header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"api-%s","password":"Sicheres1Passwort"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void apiTokenWorksForAuthenticatedFavoriteChanges() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/csrf").with(httpBasic("max", "pw")))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(post("/api/v1/users/me/favorites/1")
                        .session(session)
                        .with(httpBasic("max", "pw"))
                        .header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        mockMvc.perform(get("/api/v1/users/me/favorites")
                        .session(session)
                        .with(httpBasic("max", "pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 1)]").isNotEmpty());
    }

    @Test
    void renderedWebFormsIncludeCsrfTokens() throws Exception {
        for (String path : new String[]{"/register", "/products", "/products/1", "/favorites",
                "/tags", "/admin", "/admin/users", "/admin/products/1/edit"}) {
            MvcResult result = mockMvc.perform(get(path).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andReturn();
            String html = result.getResponse().getContentAsString();
            String[] forms = html.split("<form");
            for (int i = 1; i < forms.length; i++) {
                String form = forms[i].substring(0, forms[i].indexOf("</form>"));
                if (form.contains("method=\"post\"")) {
                    assertThat(form).as("POST form on %s", path).contains("name=\"_csrf\"");
                }
            }
        }
    }

    @Test
    void renderedLoginFormIncludesUsableCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        CsrfToken token = (CsrfToken) result.getRequest().getAttribute(CsrfToken.class.getName());
        assertThat(result.getResponse().getContentAsString()).contains("name=\"_csrf\"");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(post("/login")
                        .session(session)
                        .param(token.getParameterName(), token.getToken())
                        .param("username", "max")
                        .param("password", "pw"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void editorCannotChangeRolesWithValidCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/users/1/role")
                        .with(user("editor").roles("EDITOR"))
                        .with(csrf())
                        .param("role", "USER"))
                .andExpect(status().isForbidden());
    }
}
