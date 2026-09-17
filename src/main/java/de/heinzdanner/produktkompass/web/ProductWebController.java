package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductService;
import de.heinzdanner.produktkompass.user.AppUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;
import java.util.List;

@Controller
public class ProductWebController {

    private final ProductService productService;
    private final AppUserService appUserService;

    public ProductWebController(ProductService productService, AppUserService appUserService) {
        this.productService = productService;
        this.appUserService = appUserService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("products", productService.getAllProducts());
        return "index";
    }

    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("products", productService.getAllProducts());
        return "products";
    }

    @GetMapping("/favorites")
    public String favorites(Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        List<Product> favoriteProducts = appUserService.getFavoriteProducts(principal.getName());
        model.addAttribute("products", favoriteProducts);
        return "favorites";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/favorites/add/{id}")
    public String addFavorite(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        appUserService.addFavoriteProduct(principal.getName(), id);
        return "redirect:/products";
    }

    @PostMapping("/favorites/remove/{id}")
    public String removeFavorite(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        appUserService.removeFavoriteProduct(principal.getName(), id);
        return "redirect:/favorites";
    }
}
