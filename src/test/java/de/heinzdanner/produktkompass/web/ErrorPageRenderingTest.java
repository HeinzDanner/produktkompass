package de.heinzdanner.produktkompass.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prüft, dass für unbekannte Ressourcen tatsächlich die eigene 404-Fehlerseite
 * ausgeliefert wird (statt der Spring-Boot-Whitelabel-Seite).
 *
 * <p>Dazu wird bewusst ein eingebetteter Servlet-Container gestartet
 * ({@code webEnvironment = RANDOM_PORT}), weil MockMvc keinen echten
 * Error-Dispatch an einen Servlet-Container durchführt und daher die
 * gerenderte HTML-Fehlerseite dort nicht sichtbar wäre.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ErrorPageRenderingTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void unknownStaticResourceRendersCustom404Page() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.TEXT_HTML));

        ResponseEntity<String> response = restTemplate.exchange(
                "/css/datei-existiert-nicht.css",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("nicht gefunden");
    }
}
