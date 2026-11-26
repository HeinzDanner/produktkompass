package de.heinzdanner.produktkompass.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AccountRecoveryIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private AppUserService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private AccountRecoveryService recovery;
    @Autowired private PasswordEncoder passwords;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private JavaMailSender sender;
    private MockMvc mvc;
    private String username;
    private static final String ORIGINAL = "OriginalPassword123";
    private static final String NEW_PASSWORD = "a long unique passphrase";

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        username = "recovery-" + UUID.randomUUID();
        accounts.registerUser(username, ORIGINAL, Role.USER);
    }

    @Test
    void verifiedAddressEnablesSingleUseResetAndRevokesWebAndApiSessions() throws Exception {
        var login = mvc.perform(post("/login").with(csrf()).param("username", username).param("password", ORIGINAL))
                .andExpect(status().is3xxRedirection()).andReturn();
        var webSession = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/v1/users/me").with(httpBasic(username, ORIGINAL)))
                .andExpect(status().isOk());
        // API IF_REQUIRED sessions are created by the CSRF endpoint.
        var apiCsrf = mvc.perform(get("/api/v1/csrf").with(httpBasic(username, ORIGINAL)))
                .andExpect(status().isOk()).andReturn();
        var apiSession = (MockHttpSession) apiCsrf.getRequest().getSession(false);

        mvc.perform(post("/account/security/email").session(webSession).with(csrf())
                        .param("email", "person@example.org").param("password", ORIGINAL))
                .andExpect(redirectedUrl("/account/security?sent"));
        var pending = users.findByUsername(username).orElseThrow();
        assertThat(pending.isRecoveryEmailVerified()).isFalse();
        String verification = token(0);
        assertThat(pending.getVerificationTokenHash()).hasSize(64).isNotEqualTo(verification);
        recovery.requestPasswordReset(username);
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
        mvc.perform(get("/verify-email").param("token", verification))
                .andExpect(status().isOk()).andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(users.findByUsername(username).orElseThrow().isRecoveryEmailVerified()).isFalse();
        mvc.perform(post("/verify-email").with(csrf()).param("token", verification))
                .andExpect(redirectedUrl("/login?emailVerified"));
        mvc.perform(post("/verify-email").with(csrf()).param("token", verification))
                .andExpect(status().isBadRequest());
        recovery.requestPasswordReset(username);
        String reset = token(1);
        var resetHash = users.findByUsername(username).orElseThrow().getResetTokenHash();
        assertThat(resetHash).hasSize(64).isNotEqualTo(reset);
        recovery.requestPasswordReset(username);
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
        mvc.perform(post("/reset-password").with(csrf()).param("token", reset)
                        .param("password", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/login?passwordReset"));
        var changed = users.findByUsername(username).orElseThrow();
        assertThat(passwords.matches(NEW_PASSWORD, changed.getPassword())).isTrue();
        assertThat(passwords.matches(ORIGINAL, changed.getPassword())).isFalse();
        assertThat(changed.getResetTokenHash()).isNull();
        assertThat(changed.getCredentialVersion()).isEqualTo(1);
        verify(sender, times(3)).send(any(SimpleMailMessage.class));
        mvc.perform(post("/reset-password").with(csrf()).param("token", reset)
                        .param("password", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/favorites").session(webSession)).andExpect(redirectedUrl("/login?expired"));
        assertThat(webSession.isInvalid()).isTrue();
        if (apiSession != null) {
            mvc.perform(get("/api/v1/users/me").session(apiSession)).andExpect(status().isUnauthorized());
            assertThat(apiSession.isInvalid()).isTrue();
        }
        mvc.perform(get("/api/v1/users/me").with(httpBasic(username, ORIGINAL)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").with(httpBasic(username, NEW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void expiredAndReplacedVerificationLinksCannotVerifyTheWrongAddress() {
        recovery.requestVerification(username, ORIGINAL, "first@example.org");
        String old = token(0);
        transactions.executeWithoutResult(status -> {
            var user = users.findByUsernameForUpdate(username).orElseThrow();
            user.requestEmailVerification(user.getRecoveryEmail(), user.getVerificationTokenHash(),
                    LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusMinutes(31));
        });
        assertThatThrownBy(() -> recovery.verifyEmail(old)).hasMessageContaining("abgelaufen");
        recovery.requestVerification(username, ORIGINAL, "second@example.org");
        String current = token(1);
        assertThatThrownBy(() -> recovery.verifyEmail(old)).hasMessageContaining("ungültig");
        recovery.verifyEmail(current);
        assertThat(users.findByUsername(username).orElseThrow().getRecoveryEmail()).isEqualTo("second@example.org");
        assertThatThrownBy(() -> recovery.requestVerification(username, ORIGINAL, "third@example.org"))
                .hasMessageContaining("nicht geändert");
    }

    @Test
    void verificationCooldownDisabledAccountsAndDeliveryFailuresDoNotCreateRecoveryAccess() {
        doThrow(new org.springframework.mail.MailSendException("Private recipient"))
                .when(sender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> recovery.requestVerification(username, ORIGINAL, "person@example.org"))
                .isInstanceOf(RecoveryMailService.DeliveryException.class);
        assertThat(users.findByUsername(username).orElseThrow().getRecoveryEmail()).isNull();
        org.mockito.Mockito.reset(sender);
        recovery.requestVerification(username, ORIGINAL, "person@example.org");
        assertThatThrownBy(() -> recovery.requestVerification(username, ORIGINAL, "another@example.org"))
                .hasMessageContaining("Minute");
        recovery.verifyEmail(token(0));
        transactions.executeWithoutResult(status -> users.findByUsernameForUpdate(username).orElseThrow().setEnabled(false));
        recovery.requestPasswordReset(username);
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
        assertThat(users.findByUsername(username).orElseThrow().getResetTokenHash()).isNull();
    }

    @Test
    void expiredWrongPurposeAndInvalidTokensCannotResetPasswords() {
        String reset = verifiedReset();
        assertThatThrownBy(() -> recovery.verifyEmail(reset)).hasMessageContaining("ungültig");
        assertThatThrownBy(() -> recovery.resetPassword("invalid", NEW_PASSWORD)).hasMessageContaining("ungültig");
        transactions.executeWithoutResult(status -> {
            var user = users.findByUsernameForUpdate(username).orElseThrow();
            user.setResetToken(user.getResetTokenHash(), LocalDateTime.now().minusSeconds(1));
        });
        assertThatThrownBy(() -> recovery.resetPassword(reset, NEW_PASSWORD)).hasMessageContaining("abgelaufen");
        assertThat(passwords.matches(ORIGINAL, users.findByUsername(username).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void concurrentRedemptionsAllowOnlyOnePasswordChange() throws Exception {
        String reset = verifiedReset();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> redeem = () -> {
                try {
                    recovery.resetPassword(reset, NEW_PASSWORD);
                    return true;
                } catch (org.springframework.web.server.ResponseStatusException exception) {
                    assertThat(exception.getStatusCode().value()).isEqualTo(400);
                    return false;
                }
            };
            var results = executor.invokeAll(java.util.List.of(redeem, redeem));
            int successes = 0;
            for (var result : results) if (result.get()) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(users.findByUsername(username).orElseThrow().getCredentialVersion()).isEqualTo(1);
    }

    @Test
    void deliveryFailureRollsBackTokenAndPasswordChanges() {
        String reset = verifiedReset();
        doThrow(new org.springframework.mail.MailSendException("Sensitive SMTP detail"))
                .when(sender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> recovery.resetPassword(reset, NEW_PASSWORD))
                .isInstanceOf(RecoveryMailService.DeliveryException.class)
                .hasMessageNotContaining("Sensitive");
        var user = users.findByUsername(username).orElseThrow();
        assertThat(user.getResetTokenHash()).isNotNull();
        assertThat(user.getCredentialVersion()).isZero();
        assertThat(passwords.matches(ORIGINAL, user.getPassword())).isTrue();
    }

    @Test
    void csrfPasswordConfirmationAndByteLimitsAreEnforcedWithoutEchoingPasswords() throws Exception {
        mvc.perform(post("/forgot-password").param("username", username)).andExpect(status().isForbidden());
        mvc.perform(post("/reset-password").param("token", "invalid")).andExpect(status().isForbidden());
        mvc.perform(post("/verify-email").param("token", "invalid")).andExpect(status().isForbidden());
        mvc.perform(post("/account/security/email").with(user(username)).param("email", "person@example.org")
                .param("password", ORIGINAL)).andExpect(status().isForbidden());
        var wrong = mvc.perform(post("/account/security/email").with(user(username)).with(csrf())
                        .param("email", "person@example.org").param("password", "wrong-secret"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(wrong.getResponse().getContentAsString()).doesNotContain("wrong-secret");
        mvc.perform(get("/account/security")).andExpect(status().is3xxRedirection());
        String reset = verifiedReset();
        assertThatThrownBy(() -> recovery.resetPassword(reset, "ä".repeat(37))).hasMessageContaining("72");
        assertThatThrownBy(() -> recovery.resetPassword(reset, "short")).hasMessageContaining("15");
        mvc.perform(post("/reset-password").with(csrf()).param("token", reset)
                        .param("password", NEW_PASSWORD).param("confirmPassword", "different-secret"))
                .andExpect(status().isBadRequest());
        assertThat(users.findByUsername(username).orElseThrow().getResetTokenHash()).isNotNull();
    }

    @Test
    void publicRequestsAlwaysReturnSameResponseWithoutSendingMailForUnknownAccounts() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf()).param("username", username))
                .andExpect(redirectedUrl("/forgot-password?submitted"));
        mvc.perform(post("/forgot-password").with(csrf()).param("username", "no-such-user"))
                .andExpect(redirectedUrl("/forgot-password?submitted"));
        mvc.perform(get("/forgot-password")).andExpect(status().isOk());
        mvc.perform(get("/account/security").with(user(username))).andExpect(status().isOk());
        mvc.perform(get("/reset-password").param("token", "invalid")).andExpect(status().isOk());
    }

    private String verifiedReset() {
        recovery.requestVerification(username, ORIGINAL, "person@example.org");
        recovery.verifyEmail(token(0));
        recovery.requestPasswordReset(username);
        return token(1);
    }

    private String token(int index) {
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(index + 1)).send(messages.capture());
        String text = messages.getAllValues().get(index).getText();
        var matcher = java.util.regex.Pattern.compile("token=([A-Za-z0-9_-]{43})").matcher(text);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
