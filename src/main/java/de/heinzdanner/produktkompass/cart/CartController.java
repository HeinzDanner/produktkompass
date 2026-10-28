package de.heinzdanner.produktkompass.cart;

import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import jakarta.servlet.http.HttpSession;

import java.security.Principal;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@Controller
@RequestMapping("/cart")
public class CartController {
    private final CartService service;
    private final CartSearchService search;
    private final CartSearchPreferences preferences;

    public CartController(CartService service, CartSearchService search, CartSearchPreferences preferences) {
        this.service = service;
        this.search = search;
        this.preferences = preferences;
    }

    @GetMapping
    public String cart(Principal principal, Model model,
                       @RequestParam(defaultValue = "") String query,
                       @RequestParam(defaultValue = "") List<String> preferred,
                       @RequestParam(defaultValue = "") List<String> excluded,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam Map<String, String> parameters, HttpSession session) {
        query = sessionQuery(query, parameters, session);
        model.addAttribute("items", service.items(principal.getName()));
        addSearchModel(principal.getName(), model, query, preferred, excluded, page, parameters, true);
        return "cart";
    }

    @GetMapping("/results")
    public String results(Principal principal, Model model,
                          @RequestParam(defaultValue = "") String query,
                          @RequestParam(defaultValue = "") List<String> preferred,
                          @RequestParam(defaultValue = "") List<String> excluded,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam Map<String, String> parameters, HttpSession session) {
        query = sessionQuery(query, parameters, session);
        addSearchModel(principal.getName(), model, query, preferred, excluded, page, parameters, false);
        return "cart :: results";
    }

    private String sessionQuery(String query, Map<String, String> parameters, HttpSession session) {
        if (parameters.containsKey("query")) {
            session.setAttribute("cartSearchQuery", query);
            return query;
        }
        Object saved = session.getAttribute("cartSearchQuery");
        return saved instanceof String text ? text : "";
    }

    private void addSearchModel(String username, Model model, String query, List<String> preferred,
                                List<String> excluded, int page, Map<String, String> parameters,
                                boolean includeTagOptions) {
        var selection = requestedSelection(username, preferred, excluded, parameters);
        var result = search.search(username, query, selection.preferred(), selection.excluded(), page, includeTagOptions);
        model.addAttribute("productPage", result.page());
        model.addAttribute("searchTags", result.tags());
        model.addAttribute("preferred", result.preferred());
        model.addAttribute("excluded", result.excluded());
        model.addAttribute("selectedQuery", query);
    }

    private CartSearchPreferences.Selection requestedSelection(String username, List<String> preferred,
                                                               List<String> excluded, Map<String, String> parameters) {
        if (!parameters.containsKey("tagsExplicit") && !parameters.containsKey("preferred")
                && !parameters.containsKey("excluded")
                && parameters.keySet().stream().noneMatch(key -> key.startsWith("tag."))) {
            var saved = preferences.load(username);
            preferred = saved.preferred();
            excluded = saved.excluded();
        }
        if (parameters.keySet().stream().anyMatch(key -> key.startsWith("tag."))) {
            preferred = new ArrayList<>();
            excluded = new ArrayList<>();
            for (var entry : parameters.entrySet()) {
                if (!entry.getKey().startsWith("tag.")) continue;
                TagState state;
                try {
                    state = TagState.fromValue(entry.getValue());
                } catch (IllegalArgumentException exception) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Tag-Zustand.", exception);
                }
                switch (state) {
                    case PREFERRED -> preferred.add(entry.getKey().substring(4));
                    case EXCLUDED -> excluded.add(entry.getKey().substring(4));
                    case NEUTRAL -> { }
                }
            }
        }
        return new CartSearchPreferences.Selection(preferred, excluded);
    }

    @PostMapping
    public String applyPreferences(Principal principal,
            @RequestParam(defaultValue = "") String query, @RequestParam Map<String, String> parameters) {
        var selection = requestedSelection(principal.getName(), List.of(), List.of(), parameters);
        preferences.save(principal.getName(), selection.preferred(), selection.excluded());
        return cartRedirect(query);
    }

    @PostMapping("/preferences/reset")
    public String resetPreferences(Principal principal, @RequestParam(defaultValue = "") String query) {
        preferences.save(principal.getName(), List.of(), List.of());
        return cartRedirect(query);
    }

    private String cartRedirect(String query) {
        return "redirect:" + UriComponentsBuilder.fromPath("/cart").queryParam("query", query)
                .build().encode().toUriString();
    }

    @PostMapping("/preferences")
    @ResponseBody
    public CartSearchPreferences.Selection savePreferences(Principal principal,
            @RequestParam(defaultValue = "") List<String> preferred,
            @RequestParam(defaultValue = "") List<String> excluded) {
        return preferences.save(principal.getName(), preferred, excluded);
    }

    @PostMapping(value = "/items/{id}", params = "ajax=true")
    @ResponseBody
    public CartUpdate change(@PathVariable long id, @RequestParam CartService.Action action, Principal principal) {
        var item = service.change(principal.getName(), id, action);
        return new CartUpdate(item.productId(), item.quantity(), service.items(principal.getName()));
    }

    public record CartUpdate(long productId, int quantity, List<CartService.CartRow> items) {}

    @PostMapping(value = "/items/{id}", params = "!ajax")
    public String changeWithRedirect(@PathVariable long id, @RequestParam CartService.Action action, Principal principal) {
        service.change(principal.getName(), id, action);
        return "redirect:/cart";
    }
}
