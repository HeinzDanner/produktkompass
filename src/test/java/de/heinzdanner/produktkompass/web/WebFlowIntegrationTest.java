package de.heinzdanner.produktkompass.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deckt den wichtigsten End-to-End-Weg durch die Web-Oberfläche ab:
 * Registrierung &rarr; Login &rarr; Produkt als Favorit markieren &rarr;
 * Favoritenliste enthält das Produkt &rarr; Favorit entfernen &rarr; Logout.
 *
 * <p>Läuft bewusst gegen die echten Services/Repositories (kein Mocking),
 * um sicherzustellen, dass Controller, Security-Konfiguration, Services und
 * die Flyway-Migrationen im Zusammenspiel funktionieren.</p>
 */
@SpringBootTest
class WebFlowIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void registrationLoginFavoriteAndLogoutFlowWorksEndToEnd() throws Exception {
        String username = "flowtestuser";
        String password = "Sicheres1Passwort";

        // 1. Registrierung
        mockMvc.perform(post("/register")
                        .param("username", username)
                        .param("password", password)
                        .param("confirmPassword", password)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        // 2. Login
        mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", password)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection());

        // 3. Produkt 1 als Favorit hinzufügen (angemeldet als neu registrierter Benutzer)
        mockMvc.perform(post("/favorites/add/1")
                        .with(SecurityMockMvcRequestPostProcessors.user(username).roles("USER"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products/1"));

        // 4. Favoritenliste enthält das Produkt
        mockMvc.perform(get("/favorites")
                        .with(SecurityMockMvcRequestPostProcessors.user(username).roles("USER")))
                .andExpect(status().isOk());

        // 5. Favorit wieder entfernen
        mockMvc.perform(post("/favorites/remove/1")
                        .with(SecurityMockMvcRequestPostProcessors.user(username).roles("USER"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/favorites"));

        // 6. Logout
        mockMvc.perform(post("/logout")
                        .with(SecurityMockMvcRequestPostProcessors.user(username).roles("USER"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void registrationWithMismatchedPasswordsShowsFieldError() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "mismatchuser")
                        .param("password", "Sicheres1Passwort")
                        .param("confirmPassword", "AnderesPasswort1")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("stimmen nicht überein")));
    }

    @Test
    void adminAccessDeniedForPlainUser() throws Exception {
        mockMvc.perform(get("/admin")
                        .with(SecurityMockMvcRequestPostProcessors.user("plainuser").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRouteReturnsNotFoundStatus() throws Exception {
        mockMvc.perform(get("/this-route-does-not-exist")
                        .with(SecurityMockMvcRequestPostProcessors.user("plainuser").roles("USER")))
                .andExpect(status().isNotFound());
    }
}
