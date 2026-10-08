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
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           Principal principal,
                           Model model) {
        if (tagId != null) {
            userTagService.requireOwnedTag(principal == null ? null : principal.getName(), tagId);
        }
        var results = productService.searchPage(query, brandId, categoryId, label, nutritionGrade,
                madeInGermany, tagId, principal == null ? null : principal.getName(), page, size);
        model.addAttribute("products", results.getContent());
        model.addAttribute("productPage", results);
        model.addAttribute("brands", brandService.getBrandOptions(
                brandId == null ? List.of() : List.of(brandId)));
        model.addAttribute("categories", categoryService.getCategoryOptions(
                categoryId == null ? List.of() : List.of(categoryId)));
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
        model.addAttribute("favoriteProductIds", principal == null ? Set.of()
                : appUserService.getFavoriteIdsForProducts(principal.getName(),
                        results.getContent().stream().map(product -> product.id()).toList()));
        if (principal != null) {
            model.addAttribute("userTagOptions", userTagService.getTagOptionsForUser(principal.getName()));
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
        model.addAttribute("favoriteProductIds", principal == null ? Set.of()
                : appUserService.getFavoriteIdsForProducts(principal.getName(), List.of(id)));
        if (principal != null) {
            List<UserTag> userTags = userTagService.getTagOptionsForUser(principal.getName());
            model.addAttribute("userTags", userTags);
            model.addAttribute("assignedTagIds", userTagService.getAssignedTagIdsForProduct(principal.getName(), id));
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
    public String brandDetail(@PathVariable Long id,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size,
                              Principal principal, Model model) {
        Brand brand = brandService.getBrandById(id).orElse(null);
        if (brand == null) {
            return "redirect:/products";
        }

        model.addAttribute("brand", brand);
        var results = productService.searchPage(null, id, null, null, null, null, null, null, page, size);
        model.addAttribute("products", results.getContent());
        model.addAttribute("productPage", results);
        if (principal != null) {
            List<UserTag> userTags = userTagService.getTagOptionsForUser(principal.getName());
            model.addAttribute("userTags", userTags);
            model.addAttribute("assignedTagIds", userTagService.getAssignedTagIdsForBrand(principal.getName(), id));
        }
        return "brand-detail";
    }

    @GetMapping("/companies/{id}")
    public String companyDetail(@PathVariable Long id,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size, Model model) {
        Company company = companyService.getCompanyById(id).orElse(null);
        if (company == null) {
            return "redirect:/products";
        }

        model.addAttribute("company", company);
        var results = productService.getProductsForCompany(id, page, size);
        model.addAttribute("products", results.getContent());
        model.addAttribute("productPage", results);
        return "company-detail";
    }

    @GetMapping("/favorites")
    public String favorites(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size,
                            Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        var results = productService.getFavoriteProductPage(principal.getName(), page, size);
        model.addAttribute("products", results.getContent());
        model.addAttribute("productPage", results);
        model.addAttribute("favoriteProductIds", results.getContent().stream()
                .map(product -> product.id()).collect(Collectors.toSet()));
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

}
