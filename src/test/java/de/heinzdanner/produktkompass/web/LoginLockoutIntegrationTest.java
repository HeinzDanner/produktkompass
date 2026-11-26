package de.heinzdanner.produktkompass.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prüft den Schutz gegen Brute-Force-Login-Versuche: Nach mehreren
 * aufeinanderfolgenden falschen Passwörtern wird das Konto vorübergehend
 * gesperrt – selbst das korrekte Passwort wird dann abgelehnt, bis die
 * Sperre abgelaufen ist.
 *
 * <p>Arbeitet bewusst mit einem eigens registrierten Test-Benutzer statt mit
 * dem Demo-Konto "max", damit die Sperre keine anderen Tests beeinflusst,
 * die in derselben (von Spring Test zwischen Testklassen wiederverwendeten)
 * H2-Datenbank auf das Demo-Konto zugreifen könnten.</p>
 */
@SpringBootTest
class LoginLockoutIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    @Autowired
    private de.heinzdanner.produktkompass.user.AppUserRepository users;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void accountIsLockedAfterFiveFailedAttemptsAndCorrectPasswordIsThenRejected() throws Exception {
        String username = "lockouttestuser";
        String correctPassword = "Sicheres1Passwort";

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", username)
                        .param("password", correctPassword)
                        .param("confirmPassword", correctPassword))
                .andExpect(status().is3xxRedirection());

        // 5 Fehlversuche mit falschem Passwort
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("username", username)
                            .param("password", "falschesPasswort"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }

        var locked = users.findByUsername(username).orElseThrow();
        var originalExpiry = locked.getLockedUntil();
        // Selbst das korrekte Passwort wird jetzt abgelehnt, weil das Konto gesperrt ist
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", correctPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?locked"));
        assertThat(users.findByUsername(username).orElseThrow().getLockedUntil()).isEqualTo(originalExpiry);

        locked.setLockedUntil(java.time.LocalDateTime.now().minusMinutes(1));
        users.saveAndFlush(locked);
        mockMvc.perform(post("/login").with(csrf()).param("username", username).param("password", "invalid"))
                .andExpect(redirectedUrl("/login?error"));
        var expired = users.findByUsername(username).orElseThrow();
        assertThat(expired.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(expired.getLockedUntil()).isNull();
        mockMvc.perform(post("/login").with(csrf()).param("username", username).param("password", correctPassword))
                .andExpect(status().is3xxRedirection());
        assertThat(users.findByUsername(username).orElseThrow().getFailedLoginAttempts()).isZero();
    }
}
