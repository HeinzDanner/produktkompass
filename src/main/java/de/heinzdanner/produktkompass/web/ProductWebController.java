package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.product.BrandService;
import de.heinzdanner.produktkompass.product.CategoryService;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductService;
import de.heinzdanner.produktkompass.user.AppUserService;
import de.heinzdanner.produktkompass.user.Role;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;

@Controller
public class ProductWebController {

    private final ProductService productService;
    private final AppUserService appUserService;
    private final BrandService brandService;
    private final CategoryService categoryService;

    public ProductWebController(ProductService productService,
                               AppUserService appUserService,
                               BrandService brandService,
                               CategoryService categoryService) {
        this.productService = productService;
        this.appUserService = appUserService;
        this.brandService = brandService;
        this.categoryService = categoryService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("products", productService.getAllProducts());
        return "index";
    }

    @GetMapping("/products")
    public String products(@RequestParam(required = false) String query,
                           @RequestParam(required = false) Long brandId,
                           @RequestParam(required = false) Long categoryId,
                           Model model) {
        model.addAttribute("products", productService.searchProducts(query, brandId, categoryId));
        model.addAttribute("brands", brandService.getAllBrands());
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("selectedQuery", query == null ? "" : query);
        model.addAttribute("selectedBrandId", brandId);
        model.addAttribute("selectedCategoryId", categoryId);
        return "products";
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        Product product = productService.getProductById(id)
                .orElse(null);

        if (product == null) {
            return "redirect:/products";
        }

        model.addAttribute("product", product);
        return "product-detail";
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

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("registrationRequest", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("registrationRequest") RegistrationForm registrationForm,
                              BindingResult bindingResult,
                              Model model) {
        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            appUserService.registerUser(registrationForm.username(), registrationForm.password(), Role.USER);
            return "redirect:/login?registered";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "register";
        }
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

    public record RegistrationForm(
            @jakarta.validation.constraints.NotBlank(message = "Der Benutzername darf nicht leer sein.")
            @jakarta.validation.constraints.Size(min = 3, max = 50, message = "Der Benutzername muss zwischen 3 und 50 Zeichen lang sein.")
            String username,

            @jakarta.validation.constraints.NotBlank(message = "Das Passwort darf nicht leer sein.")
            @jakarta.validation.constraints.Size(min = 6, message = "Das Passwort muss mindestens 6 Zeichen lang sein.")
            String password
    ) {
        public RegistrationForm() {
            this(null, null);
        }
    }
}
