package de.heinzdanner.produktkompass.recipe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class MealieClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Food(UUID id, String name) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recipe(String name) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Suggestion(Recipe recipe, List<Food> missingFoods) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FoodsPage(List<Food> items, @JsonAlias("total_pages") Integer totalPages) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Suggestions(List<Suggestion> items) {}

    private final String baseUrl;
    private final String token;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();

    public MealieClient(@Value("${produktkompass.mealie.base-url:http://localhost:9925}") String baseUrl,
                        @Value("${produktkompass.mealie.token:}") String token, ObjectMapper mapper) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
        this.mapper = mapper;
    }

    public boolean configured() {
        return !token.isBlank();
    }

    public List<Food> foods() {
        var result = new ArrayList<Food>();
        for (int page = 1; page <= 50; page++) {
            FoodsPage response = get("/api/foods?page=" + page + "&perPage=100&orderBy=id&orderDirection=asc", FoodsPage.class);
            if (response == null || response.items() == null || response.totalPages() == null
                    || response.totalPages() < 0 || response.totalPages() > 50
                    || response.items().stream().anyMatch(food -> food == null || food.id() == null
                    || food.name() == null || food.name().isBlank())) {
                throw failure("Mealie-Zutatenantwort ist ungültig oder überschreitet 5.000 Zutaten.");
            }
            result.addAll(response.items());
            if (page >= response.totalPages()) {
                return result.stream().sorted(java.util.Comparator
                        .comparing(Food::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Food::id)).toList();
            }
        }
        throw failure("Mealie-Zutaten konnten nicht vollständig geladen werden.");
    }

    public List<Suggestion> suggest(List<UUID> foods) {
        if (foods.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mindestens eine Zutat auswählen.");
        String query = foods.stream().distinct().map(id -> "foods=" + id).collect(Collectors.joining("&"));
        Suggestions response = get("/api/recipes/suggestions?limit=10&maxMissingFoods=5"
                + "&includeFoodsOnHand=false&includeToolsOnHand=false&includeSubstitutions=false&" + query,
                Suggestions.class);
        if (response == null || response.items() == null || response.items().stream()
                .anyMatch(item -> item == null || item.recipe() == null || item.recipe().name() == null
                        || item.recipe().name().isBlank() || item.missingFoods() == null
                        || item.missingFoods().stream().anyMatch(food -> food == null || food.name() == null))) {
            throw failure("Mealie-Rezeptantwort ist unvollständig.");
        }
        return response.items();
    }

    private <T> T get(String path, Class<T> type) {
        if (!configured()) throw failure("Mealie ist noch nicht eingerichtet. API-Token konfigurieren.");
        URI base;
        try {
            base = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw failure("Die konfigurierte Mealie-Adresse ist ungültig.");
        }
        if (!"http".equals(base.getScheme()) || base.getHost() == null
                || !Set.of("localhost", "127.0.0.1", "[::1]").contains(base.getHost())
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || (base.getPath() != null && !base.getPath().isEmpty())) {
            throw failure("Mealie muss für diese lokale Integration auf localhost per HTTP laufen.");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw failure("Mealie-Anfrage fehlgeschlagen (HTTP " + response.statusCode() + ").");
            }
            return mapper.readValue(response.body(), type);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("Mealie-Anfrage wurde unterbrochen.");
        } catch (IOException | tools.jackson.core.JacksonException exception) {
            throw failure("Mealie ist nicht erreichbar oder liefert keine gültigen JSON-Daten.");
        }
    }

    private ResponseStatusException failure(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }
}
