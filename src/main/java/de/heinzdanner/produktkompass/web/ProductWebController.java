package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.product.BrandService;
import de.heinzdanner.produktkompass.product.Brand;
import de.heinzdanner.produktkompass.product.CategoryService;
import de.heinzdanner.produktkompass.product.Company;
import de.heinzdanner.produktkompass.product.CompanyService;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.product.ProductService;
import de.heinzdanner.produktkompass.user.AppUserService;
import de.heinzdanner.produktkompass.user.Role;
import de.heinzdanner.produktkompass.usertag.UserTag;
import de.heinzdanner.produktkompass.usertag.UserTagService;
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
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class ProductWebController {

    private final ProductService productService;
    private final AppUserService appUserService;
    private final BrandService brandService;
    private final CategoryService categoryService;
    private final CompanyService companyService;
    private final UserTagService userTagService;

    public ProductWebController(ProductService productService,
                               AppUserService appUserService,
                               BrandService brandService,
                               CategoryService categoryService,
                               CompanyService companyService,
                               UserTagService userTagService) {
        this.productService = productService;
        this.appUserService = appUserService;
        this.brandService = brandService;
        this.categoryService = categoryService;
        this.companyService = companyService;
        this.userTagService = userTagService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/products")
    public String products(@RequestParam(required = false) String query,
                           @RequestParam(required = false) Long brandId,
                           @RequestParam(required = false) Long categoryId,
                           @RequestParam(required = false) ProductLabel label,
                           @RequestParam(required = false) NutritionGrade nutritionGrade,
                           @RequestParam(required = false) Boolean madeInGermany,
                           @RequestParam(required = false) Long tagId,
                           Principal principal,
                           Model model) {
        List<Product> products = productService.searchProducts(query, brandId, categoryId, label, nutritionGrade, madeInGermany);

        if (tagId != null && principal != null) {
            Set<Long> tagProductIds = userTagService.resolveProductIdsForTag(principal.getName(), tagId);
            products = products.stream()
                    .filter(product -> tagProductIds.contains(product.getId()))
                    .toList();
        }

        model.addAttribute("products", products);
        model.addAttribute("brands", brandService.getAllBrands());
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("productLabels", ProductLabel.values());
        model.addAttribute("nutritionGrades", NutritionGrade.values());
        model.addAttribute("selectedQuery", query == null ? "" : query);
        model.addAttribute("selectedBrandId", brandId);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedLabel", label);
        model.addAttribute("selectedNutritionGrade", nutritionGrade);
        model.addAttribute("selectedMadeInGermany", madeInGermany);
        model.addAttribute("selectedTagId", tagId);
        model.addAttribute("hasActiveFilter", (query != null && !query.isBlank()) || brandId != null || categoryId != null || label != null || nutritionGrade != null || madeInGermany != null || tagId != null);
        model.addAttribute("favoriteProductIds", resolveFavoriteProductIds(principal));
        if (principal != null) {
            model.addAttribute("userTagOptions", userTagService.getTagsForUser(principal.getName()));
        }
        return "products";
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Principal principal, Model model) {
        Product product = productService.getProductById(id)
                .orElse(null);

        if (product == null) {
            return "redirect:/products";
        }

        model.addAttribute("product", product);
        model.addAttribute("favoriteProductIds", resolveFavoriteProductIds(principal));
        if (principal != null) {
            List<UserTag> userTags = userTagService.getTagsForUser(principal.getName());
            model.addAttribute("userTags", userTags);
            model.addAttribute("assignedTagIds", userTags.stream()
                    .filter(tag -> tag.getProducts().contains(product))
                    .map(UserTag::getId)
                    .collect(Collectors.toSet()));
        }
        return "product-detail";
    }

    @GetMapping("/products/{id}/similar")
    public String similarProducts(@PathVariable Long id, Model model) {
        Product product = productService.getProductById(id).orElse(null);
        if (product == null) {
            return "redirect:/products";
        }

        model.addAttribute("product", product);
        model.addAttribute("similarProducts", productService.getAlternativeProducts(id));
        return "similar-products";
    }

    @GetMapping("/brands/{id}")
    public String brandDetail(@PathVariable Long id, Principal principal, Model model) {
        Brand brand = brandService.getBrandById(id).orElse(null);
        if (brand == null) {
            return "redirect:/products";
        }

        model.addAttribute("brand", brand);
        model.addAttribute("products", productService.getAllProducts().stream()
                .filter(product -> product.getBrand() != null && product.getBrand().getId().equals(id))
                .toList());
        if (principal != null) {
            List<UserTag> userTags = userTagService.getTagsForUser(principal.getName());
            model.addAttribute("userTags", userTags);
            model.addAttribute("assignedTagIds", userTags.stream()
                    .filter(tag -> tag.getBrands().contains(brand))
                    .map(UserTag::getId)
                    .collect(Collectors.toSet()));
        }
        return "brand-detail";
    }

    @GetMapping("/companies/{id}")
    public String companyDetail(@PathVariable Long id, Model model) {
        Company company = companyService.getCompanyById(id).orElse(null);
        if (company == null) {
            return "redirect:/products";
        }

        model.addAttribute("company", company);
        model.addAttribute("products", productService.getAllProducts().stream()
                .filter(product -> product.getCompany() != null && product.getCompany().getId().equals(id))
                .toList());
        return "company-detail";
    }

    @GetMapping("/favorites")
    public String favorites(Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        List<Product> favoriteProducts = appUserService.getFavoriteProducts(principal.getName());
        model.addAttribute("products", favoriteProducts);
        model.addAttribute("favoriteProductIds", appUserService.getFavoriteProductIds(principal.getName()));
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
                              BindingResult bindingResult) {
        if (registrationForm.password() != null
                && registrationForm.confirmPassword() != null
                && !registrationForm.password().equals(registrationForm.confirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch",
                    "Die Passwörter stimmen nicht überein.");
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            appUserService.registerUser(registrationForm.username(), registrationForm.password(), Role.USER);
            return "redirect:/login?registered";
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("username", "username.duplicate", ex.getMessage());
            return "register";
        }
    }

    @PostMapping("/favorites/add/{id}")
    public String addFavorite(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        appUserService.addFavoriteProduct(principal.getName(), id);
        return "redirect:/products/" + id;
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
            @jakarta.validation.constraints.Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                    message = "Das Passwort muss mindestens 8 Zeichen lang sein und Groß-, Kleinbuchstaben sowie eine Ziffer enthalten."
            )
            String password,

            @jakarta.validation.constraints.NotBlank(message = "Bitte bestätige das Passwort.")
            String confirmPassword
    ) {
        public RegistrationForm() {
            this(null, null, null);
        }
    }

    private Set<Long> resolveFavoriteProductIds(Principal principal) {
        if (principal == null) {
            return Set.of();
        }

        return appUserService.getFavoriteProductIds(principal.getName());
    }
}
