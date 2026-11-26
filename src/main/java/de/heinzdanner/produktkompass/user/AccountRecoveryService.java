package de.heinzdanner.produktkompass.user;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@Transactional
public class AccountRecoveryService {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final RecoveryMailService mail;
    private final AuditLogService audit;
    private final SecureRandom random = new SecureRandom();

    public AccountRecoveryService(AppUserRepository users, PasswordEncoder passwords, RecoveryMailService mail,
                                  AuditLogService audit) {
        this.users = users;
        this.passwords = passwords;
        this.mail = mail;
        this.audit = audit;
    }

    public void requestVerification(String username, String rawPassword, String email) {
        var user = users.findByUsernameForUpdate(username).orElseThrow();
        if (!user.isEnabled() || user.isCurrentlyLocked() || !passwords.matches(rawPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwortbestätigung nicht erfolgreich.");
        }
        if (user.isRecoveryEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Die bestätigte Adresse kann hier nicht geändert werden. Kontaktiere den Administrator.");
        }
        var now = LocalDateTime.now();
        if (user.getVerificationSentAt() != null && user.getVerificationSentAt().plusMinutes(1).isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Bitte mindestens eine Minute bis zur nächsten Bestätigungsnachricht warten.");
        }
        String token = newToken();
        String normalized = email.trim();
        user.requestEmailVerification(normalized, hash(token), now.plusMinutes(30), now);
        mail.sendVerification(normalized, token);
    }

    public void verifyEmail(String token) {
        String hash = hashToken(token);
        var id = users.findIdByVerificationTokenHash(hash).orElseThrow(AccountRecoveryService::invalidToken);
        var user = users.findByIdForUpdate(id).orElseThrow(AccountRecoveryService::invalidToken);
        if (!user.isEnabled() || !hash.equals(user.getVerificationTokenHash())
                || user.getVerificationExpiresAt() == null
                || !user.getVerificationExpiresAt().isAfter(LocalDateTime.now())) {
            throw invalidToken();
        }
        user.confirmRecoveryEmail();
        audit.logAction("AppUser", user.getId(), "EMAIL_VERIFIED", "Wiederherstellungsadresse bestätigt.");
    }

    public void requestPasswordReset(String username) {
        users.findByUsernameForUpdate(username.trim().replaceAll("\\s+", " ")).ifPresent(user -> {
            var now = LocalDateTime.now();
            if (!user.isEnabled() || !user.isRecoveryEmailVerified()
                    || (user.getResetExpiresAt() != null && user.getResetExpiresAt().isAfter(now))) return;
            String token = newToken();
            user.setResetToken(hash(token), now.plusMinutes(15));
            mail.sendReset(user.getRecoveryEmail(), token);
        });
    }

    public void resetPassword(String token, String password) {
        requireResetPassword(password);
        String hash = hashToken(token);
        var id = users.findIdByResetTokenHash(hash).orElseThrow(AccountRecoveryService::invalidToken);
        var user = users.findByIdForUpdate(id).orElseThrow(AccountRecoveryService::invalidToken);
        if (!user.isEnabled() || !user.isRecoveryEmailVerified() || !hash.equals(user.getResetTokenHash())
                || user.getResetExpiresAt() == null || !user.getResetExpiresAt().isAfter(LocalDateTime.now())) {
            throw invalidToken();
        }
        user.completePasswordReset(passwords.encode(password));
        mail.sendPasswordChanged(user.getRecoveryEmail());
        audit.logAction("AppUser", user.getId(), "PASSWORD_RESET",
                "Passwort mit einmaligem E-Mail-Token zurückgesetzt; bisherige Sitzungen widerrufen.");
    }

    public static void requireResetPassword(String password) {
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 15
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Das neue Passwort braucht mindestens 15 Zeichen und darf höchstens 72 UTF-8-Bytes haben.");
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidToken();
        return hash(token);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar.", exception);
        }
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Link ist ungültig oder abgelaufen.");
    }
}
