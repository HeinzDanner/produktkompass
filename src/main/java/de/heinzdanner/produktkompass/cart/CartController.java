package de.heinzdanner.produktkompass.cart;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

@Controller
@RequestMapping("/cart")
public class CartController {
    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping
    public String cart(Principal principal, Model model) {
        model.addAttribute("items", service.items(principal.getName()));
        return "cart";
    }

    @PostMapping(value = "/items/{id}", params = "ajax=true")
    @ResponseBody
    public CartService.Item change(@PathVariable long id, @RequestParam CartService.Action action, Principal principal) {
        return service.change(principal.getName(), id, action);
    }

    @PostMapping(value = "/items/{id}", params = "!ajax")
    public String changeWithRedirect(@PathVariable long id, @RequestParam CartService.Action action, Principal principal) {
        service.change(principal.getName(), id, action);
        return "redirect:/cart";
    }
}
