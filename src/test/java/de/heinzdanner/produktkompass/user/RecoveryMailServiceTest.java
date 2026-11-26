package de.heinzdanner.produktkompass.user;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.env.MockEnvironment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RecoveryMailServiceTest {
    @Test
    void smtpDeliveryUsesConfiguredOriginAndSendsAnActualMessage() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            var delivery = executor.submit(() -> {
                try (var socket = server.accept();
                     var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     var writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                    socket.setSoTimeout(5000);
                    writer.print("220 localhost test SMTP\r\n");
                    writer.flush();
                    var data = new StringBuilder();
                    boolean receiving = false;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (receiving) {
                            if (line.equals(".")) {
                                receiving = false;
                                writer.print("250 accepted\r\n");
                            } else {
                                data.append(line).append('\n');
                            }
                        } else if (line.equals("DATA")) {
                            receiving = true;
                            writer.print("354 send data\r\n");
                        } else if (line.equals("QUIT")) {
                            writer.print("221 bye\r\n");
                            writer.flush();
                            return data.toString();
                        } else {
                            writer.print("250 localhost\r\n");
                        }
                        writer.flush();
                    }
                    throw new IllegalStateException("SMTP client ended without QUIT.");
                }
            });
            var sender = new JavaMailSenderImpl();
            sender.setHost(server.getInetAddress().getHostAddress());
            sender.setPort(server.getLocalPort());
            sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "5000");
            sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "5000");
            var mail = new RecoveryMailService(sender, "https://demo.example.org",
                    "no-reply@example.org", new MockEnvironment());
            String token = "A".repeat(43);
            mail.sendReset("recipient@example.org", token);
            String raw = delivery.get(5, TimeUnit.SECONDS);
            assertThat(raw).contains("To: recipient@example.org", "From: no-reply@example.org");
            var message = new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()),
                    new java.io.ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
            assertThat(message.getContent().toString()).contains(
                    "https://demo.example.org/reset-password?token=" + token);
        }
    }

    @Test
    void unsafeOriginsAndPlainHttpInProductionAreRejectedAtStartup() {
        var sender = mock(JavaMailSender.class);
        for (String url : java.util.List.of("http://example.org", "https://user:password@example.org",
                "https://example.org?redirect=evil", "https://example.org/path", "https://example.org#fragment")) {
            assertThatThrownBy(() -> new RecoveryMailService(sender, url, "no-reply@example.org", new MockEnvironment()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new RecoveryMailService(sender, "http://localhost:8080",
                "no-reply@example.org", prod)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new RecoveryMailService(sender, "https://example.org", "no-reply@example.org", prod)).isNotNull();
        assertThatThrownBy(() -> new RecoveryMailService(sender, "https://example.org",
                "invalid address", prod)).isInstanceOf(IllegalArgumentException.class);
    }
}
