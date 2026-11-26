package de.heinzdanner.produktkompass.user;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
public class RecoveryMailService {
    private final JavaMailSender sender;
    private final String baseUrl;
    private final String from;

    public RecoveryMailService(JavaMailSender sender,
                               @Value("${produktkompass.security.recovery.base-url}") String baseUrl,
                               @Value("${produktkompass.security.recovery.from}") String from,
                               Environment environment) {
        var uri = URI.create(baseUrl);
        boolean localHttp = "http".equals(uri.getScheme())
                && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty()
                && !uri.getPath().equals("/"))
                || (!"https".equals(uri.getScheme()) && !localHttp)
                || (environment.acceptsProfiles(Profiles.of("prod")) && !"https".equals(uri.getScheme()))) {
            throw new IllegalArgumentException("Recovery-Basis-URL muss HTTPS verwenden (lokal ist HTTP erlaubt).");
        }
        if (from.isBlank() || from.contains("\r") || from.contains("\n")) {
            throw new IllegalArgumentException("Ungültige Recovery-Absenderadresse.");
        }
        try {
            new InternetAddress(from, true).validate();
        } catch (AddressException exception) {
            throw new IllegalArgumentException("Ungültige Recovery-Absenderadresse.");
        }
        this.sender = sender;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.from = from;
    }

    public void sendVerification(String email, String token) {
        send(email, "ProduktKompass: E-Mail-Adresse bestätigen",
                "Bestätige deine Wiederherstellungsadresse innerhalb von 30 Minuten:\n"
                        + link("/verify-email", token)
                        + "\n\nWenn du dies nicht angefordert hast, ignoriere diese Nachricht.");
    }

    public void sendReset(String email, String token) {
        send(email, "ProduktKompass: Passwort zurücksetzen",
                "Setze dein Passwort innerhalb von 15 Minuten zurück:\n" + link("/reset-password", token)
                        + "\n\nDer Link ist einmalig nutzbar. Wenn du dies nicht angefordert hast, "
                        + "ignoriere diese Nachricht. Dein Passwort bleibt unverändert.");
    }

    public void sendPasswordChanged(String email) {
        send(email, "ProduktKompass: Passwort geändert",
                "Dein Passwort wurde geändert. Bisherige Sitzungen sind nicht mehr gültig.\n"
                        + "Wenn du dies nicht selbst vorgenommen hast, kontaktiere den Administrator "
                        + "und sichere dein E-Mail-Konto.");
    }

    private String link(String path, String token) {
        return UriComponentsBuilder.fromUriString(baseUrl).path(path).queryParam("token", token)
                .build().encode().toUriString();
    }

    private void send(String email, String subject, String text) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException exception) {
            // SMTP exceptions may include recipient addresses or message contents.
            throw new DeliveryException();
        }
    }

    public static class DeliveryException extends RuntimeException {
        public DeliveryException() {
            super("E-Mail-Versand ist derzeit nicht verfügbar.");
        }
    }
}
