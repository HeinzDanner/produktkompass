package de.heinzdanner.produktkompass.recipe;

import de.heinzdanner.produktkompass.cart.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class RecipeController {
    private final CartService cart;
    private final MealieClient mealie;

    public RecipeController(CartService cart, MealieClient mealie) {
        this.cart = cart;
        this.mealie = mealie;
    }

    @GetMapping("/cart/recipes")
    public String page(Principal principal, Model model) {
        model.addAttribute("items", cart.items(principal.getName()));
        model.addAttribute("configured", mealie.configured());
        model.addAttribute("foods", mealie.configured() ? mealie.foods() : List.of());
        model.addAttribute("selected", Map.of());
        return "cart-recipes";
    }

    @PostMapping("/cart/recipes")
    public String suggest(Principal principal, @RequestParam Map<String, String> parameters, Model model) {
        var items = cart.items(principal.getName());
        Set<String> expected = items.stream().map(item -> "food_" + item.id()).collect(Collectors.toSet());
        Set<String> supplied = parameters.keySet().stream().filter(key -> key.startsWith("food_"))
                .collect(Collectors.toSet());
        if (items.isEmpty() || !expected.equals(supplied)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Auswahl passt nicht zum aktuellen Warenkorb.");
        }
        var available = mealie.foods();
        Set<UUID> ids = available.stream().map(MealieClient.Food::id).collect(Collectors.toSet());
        var selected = new java.util.LinkedHashMap<Long, String>();
        var foods = new ArrayList<UUID>();
        var ignored = new ArrayList<String>();
        for (var item : items) {
            String value = parameters.get("food_" + item.id());
            selected.put(item.id(), value);
            if ("skip".equals(value)) {
                ignored.add(item.name());
                continue;
            }
            try {
                UUID id = UUID.fromString(value);
                if (!ids.contains(id)) throw new IllegalArgumentException();
                foods.add(id);
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte eine gültige Mealie-Zutat auswählen.");
            }
        }
        if (foods.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mindestens eine Zutat auswählen.");
        model.addAttribute("items", items);
        model.addAttribute("configured", true);
        model.addAttribute("foods", available);
        model.addAttribute("selected", selected);
        model.addAttribute("ignored", ignored);
        model.addAttribute("suggestions", mealie.suggest(foods));
        return "cart-recipes";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String error(ResponseStatusException exception, Model model, HttpServletResponse response) {
        response.setStatus(exception.getStatusCode().value());
        model.addAttribute("recipeError", exception.getReason());
        model.addAttribute("items", List.of());
        model.addAttribute("configured", false);
        model.addAttribute("foods", List.of());
        model.addAttribute("selected", Map.of());
        return "cart-recipes";
    }
}
