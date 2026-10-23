package de.heinzdanner.produktkompass.cart;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.stereotype.Controller;

import java.util.Map;

@ControllerAdvice(annotations = Controller.class)
public class CartModelAdvice {
    private final CartService service;

    public CartModelAdvice(CartService service) {
        this.service = service;
    }

    @ModelAttribute("cartQuantities")
    public Map<Long, Integer> cartQuantities(Authentication authentication) {
        return authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                ? Map.of() : service.quantities(authentication.getName());
    }
}
