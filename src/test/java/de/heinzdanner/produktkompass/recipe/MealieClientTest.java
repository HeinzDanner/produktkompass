package de.heinzdanner.produktkompass.recipe;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MealieClientTest {
    private static final UUID FOOD = UUID.fromString("f1234567-1234-4234-8234-123456789abc");
    private HttpServer server;
    private MealieClient client;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> tokens = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String responseBody;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            tokens.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        client = new MealieClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-only-token", new ObjectMapper());
    }

    @AfterEach
    void cleanup() {
        server.stop(0);
    }

    @Test
    void readsMealieSnakeCaseFoodPaginationAndSendsDistinctIdsOnly() {
        responseBody = """
                {"items":[{"id":"%s","name":"Tomaten","extras":{}}],"total_pages":2}
                """.formatted(FOOD);
        assertThat(client.foods()).hasSize(2);
        assertThat(requests).hasSize(2);
        assertThat(requests.getLast()).contains("page=2", "perPage=100");
        responseBody = """
                {"items":[{"recipe":{"name":"Testpfanne","slug":"testpfanne"},
                "missingFoods":[{"id":"%s","name":"Reis"}],"missingTools":[]}]}
                """.formatted(FOOD);
        var suggestions = client.suggest(List.of(FOOD, FOOD));
        assertThat(suggestions).singleElement().satisfies(item -> {
            assertThat(item.recipe().name()).isEqualTo("Testpfanne");
            assertThat(item.missingFoods()).extracting(MealieClient.Food::name).containsExactly("Reis");
        });
        String request = requests.getLast();
        assertThat(request).startsWith("GET /api/recipes/suggestions?")
                .contains("includeFoodsOnHand=false", "includeToolsOnHand=false", "includeSubstitutions=false");
        assertThat(request.split("foods=", -1)).hasSize(2);
        assertThat(tokens).containsOnly("Bearer test-only-token");
    }

    @Test
    void errorsNeverLookLikeEmptySuccessAndRedirectsAreNotFollowed() {
        responseBody = "{}";
        assertThatThrownBy(client::foods).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> client.suggest(List.of(FOOD))).isInstanceOf(ResponseStatusException.class);
        responseBody = "invalid json";
        assertThatThrownBy(client::foods).isInstanceOf(ResponseStatusException.class);
        status = 401;
        assertThatThrownBy(client::foods).isInstanceOf(ResponseStatusException.class).hasMessageContaining("HTTP 401");
        status = 302;
        assertThatThrownBy(client::foods).isInstanceOf(ResponseStatusException.class).hasMessageContaining("HTTP 302");
    }

    @Test
    void rejectsRemoteTargetsAndMissingTokensBeforeSendingRequests() {
        var remote = new MealieClient("https://demo.mealie.io", "test-only-token", new ObjectMapper());
        assertThatThrownBy(remote::foods).isInstanceOf(ResponseStatusException.class).hasMessageContaining("localhost");
        var disabled = new MealieClient("http://localhost:9925", "", new ObjectMapper());
        assertThat(disabled.configured()).isFalse();
        assertThatThrownBy(disabled::foods).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Token");
        assertThat(requests).isEmpty();
    }
}
