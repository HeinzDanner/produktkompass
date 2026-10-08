package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.Brand;
import de.heinzdanner.produktkompass.product.BrandService;
import de.heinzdanner.produktkompass.product.Category;
import de.heinzdanner.produktkompass.product.CategoryService;
import de.heinzdanner.produktkompass.product.Company;
import de.heinzdanner.produktkompass.product.CompanyService;
import de.heinzdanner.produktkompass.product.IngredientStatus;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.PackagingType;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductService;
import de.heinzdanner.produktkompass.product.ProductLabel;
import de.heinzdanner.produktkompass.product.Source;
import de.heinzdanner.produktkompass.product.SourceService;
import de.heinzdanner.produktkompass.product.dto.BrandRequest;
import de.heinzdanner.produktkompass.product.dto.CategoryRequest;
import de.heinzdanner.produktkompass.product.dto.CompanyRequest;
import de.heinzdanner.produktkompass.product.dto.ProductRequest;
import de.heinzdanner.produktkompass.product.dto.SourceRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Controller
public class AdminController {

    private final ProductService productService;
    private final BrandService brandService;
    private final CategoryService categoryService;
    private final CompanyService companyService;
    private final SourceService sourceService;
    private final AuditLogService auditLogService;

    public AdminController(ProductService productService,
                          BrandService brandService,
                          CategoryService categoryService,
                          CompanyService companyService,
                          SourceService sourceService,
                          AuditLogService auditLogService) {
        this.productService = productService;
        this.brandService = brandService;
        this.categoryService = categoryService;
        this.companyService = companyService;
        this.sourceService = sourceService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/admin")
    public String adminPage(@RequestParam(defaultValue = "") String query,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size,
                            Model model) {
        populateAdminModel(model, query, page, size);
        return "admin";
    }

    @GetMapping("/admin/audit-log")
    public String auditLog(Model model) {
        model.addAttribute("auditLogs", auditLogService.getAllLogs());
        return "admin-audit-log";
    }

    @PostMapping("/admin/products")
    public String createProduct(@Valid @ModelAttribute("productRequest") ProductRequest productRequest,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateAdminModel(model);
            return "admin";
        }

        productService.createProduct(productRequest);
        redirectAttributes.addFlashAttribute("adminSuccess", "Produkt wurde angelegt.");
        return "redirect:/admin";
    }

    @GetMapping("/admin/products/{id}/edit")
    public String editProductForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Product product = productService.getProductById(id).orElse(null);
        if (product == null) {
            redirectAttributes.addFlashAttribute("adminError", "Produkt wurde nicht gefunden.");
            return "redirect:/admin";
        }

        if (!model.containsAttribute("productRequest")) {
            model.addAttribute("productRequest", toProductRequest(product));
        }
        model.addAttribute("productId", id);
        populateSharedLists(model);
        populateAlternativeOptions(model, id, (ProductRequest) model.getAttribute("productRequest"));
        return "admin-product-edit";
    }

    @PostMapping("/admin/products/{id}")
    public String updateProduct(@PathVariable Long id,
                               @Valid @ModelAttribute("productRequest") ProductRequest productRequest,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("productId", id);
            populateSharedLists(model);
            populateAlternativeOptions(model, id, productRequest);
            return "admin-product-edit";
        }

        if (productService.updateProduct(id, productRequest).isEmpty()) {
            redirectAttributes.addFlashAttribute("adminError", "Produkt wurde nicht gefunden.");
        } else {
            redirectAttributes.addFlashAttribute("adminSuccess", "Produkt wurde aktualisiert.");
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        productService.deleteProduct(id);
        redirectAttributes.addFlashAttribute("adminSuccess", "Produkt wurde gelöscht.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/brands")
    public String createBrand(@Valid @ModelAttribute("brandRequest") BrandRequest brandRequest,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateAdminModel(model);
            return "admin";
        }

        brandService.createBrand(brandRequest);
        redirectAttributes.addFlashAttribute("adminSuccess", "Marke wurde angelegt.");
        return "redirect:/admin";
    }

    @GetMapping("/admin/brands/{id}/edit")
    public String editBrandForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Brand brand = brandService.getBrandById(id).orElse(null);
        if (brand == null) {
            redirectAttributes.addFlashAttribute("adminError", "Marke wurde nicht gefunden.");
            return "redirect:/admin";
        }

        if (!model.containsAttribute("brandRequest")) {
            model.addAttribute("brandRequest", new BrandRequest(brand.getName(), brand.getDescription()));
        }
        model.addAttribute("brandId", id);
        return "admin-brand-edit";
    }

    @PostMapping("/admin/brands/{id}")
    public String updateBrand(@PathVariable Long id,
                             @Valid @ModelAttribute("brandRequest") BrandRequest brandRequest,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("brandId", id);
            return "admin-brand-edit";
        }

        if (brandService.updateBrand(id, brandRequest).isEmpty()) {
            redirectAttributes.addFlashAttribute("adminError", "Marke wurde nicht gefunden.");
        } else {
            redirectAttributes.addFlashAttribute("adminSuccess", "Marke wurde aktualisiert.");
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/brands/{id}/delete")
    public String deleteBrand(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        brandService.deleteBrand(id);
        redirectAttributes.addFlashAttribute("adminSuccess", "Marke wurde gelöscht.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/categories")
    public String createCategory(@Valid @ModelAttribute("categoryRequest") CategoryRequest categoryRequest,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateAdminModel(model);
            return "admin";
        }

        categoryService.createCategory(categoryRequest);
        redirectAttributes.addFlashAttribute("adminSuccess", "Kategorie wurde angelegt.");
        return "redirect:/admin";
    }

    @GetMapping("/admin/categories/{id}/edit")
    public String editCategoryForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Category category = categoryService.getCategoryById(id).orElse(null);
        if (category == null) {
            redirectAttributes.addFlashAttribute("adminError", "Kategorie wurde nicht gefunden.");
            return "redirect:/admin";
        }

        if (!model.containsAttribute("categoryRequest")) {
            model.addAttribute("categoryRequest", new CategoryRequest(category.getName(), category.getDescription()));
        }
        model.addAttribute("categoryId", id);
        return "admin-category-edit";
    }

    @PostMapping("/admin/categories/{id}")
    public String updateCategory(@PathVariable Long id,
                                @Valid @ModelAttribute("categoryRequest") CategoryRequest categoryRequest,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categoryId", id);
            return "admin-category-edit";
        }

        if (categoryService.updateCategory(id, categoryRequest).isEmpty()) {
            redirectAttributes.addFlashAttribute("adminError", "Kategorie wurde nicht gefunden.");
        } else {
            redirectAttributes.addFlashAttribute("adminSuccess", "Kategorie wurde aktualisiert.");
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        categoryService.deleteCategory(id);
        redirectAttributes.addFlashAttribute("adminSuccess", "Kategorie wurde gelöscht.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/companies")
    public String createCompany(@Valid @ModelAttribute("companyRequest") CompanyRequest companyRequest,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateAdminModel(model);
            return "admin";
        }

        companyService.createCompany(companyRequest);
        redirectAttributes.addFlashAttribute("adminSuccess", "Unternehmen wurde angelegt.");
        return "redirect:/admin";
    }

    @GetMapping("/admin/companies/{id}/edit")
    public String editCompanyForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Company company = companyService.getCompanyById(id).orElse(null);
        if (company == null) {
            redirectAttributes.addFlashAttribute("adminError", "Unternehmen wurde nicht gefunden.");
            return "redirect:/admin";
        }

        if (!model.containsAttribute("companyRequest")) {
            model.addAttribute("companyRequest", new CompanyRequest(
                    company.getName(), company.getWebsite(), company.getCountry(), company.getDescription()));
        }
        model.addAttribute("companyId", id);
        return "admin-company-edit";
    }

    @PostMapping("/admin/companies/{id}")
    public String updateCompany(@PathVariable Long id,
                               @Valid @ModelAttribute("companyRequest") CompanyRequest companyRequest,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("companyId", id);
            return "admin-company-edit";
        }

        if (companyService.updateCompany(id, companyRequest).isEmpty()) {
            redirectAttributes.addFlashAttribute("adminError", "Unternehmen wurde nicht gefunden.");
        } else {
            redirectAttributes.addFlashAttribute("adminSuccess", "Unternehmen wurde aktualisiert.");
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/companies/{id}/delete")
    public String deleteCompany(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        companyService.deleteCompany(id);
        redirectAttributes.addFlashAttribute("adminSuccess", "Unternehmen wurde gelöscht.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/sources")
    public String createSource(@Valid @ModelAttribute("sourceRequest") SourceRequest sourceRequest,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateAdminModel(model);
            return "admin";
        }

        sourceService.createSource(sourceRequest);
        redirectAttributes.addFlashAttribute("adminSuccess", "Quelle wurde angelegt.");
        return "redirect:/admin";
    }

    @GetMapping("/admin/sources/{id}/edit")
    public String editSourceForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Source source = sourceService.getSourceById(id).orElse(null);
        if (source == null) {
            redirectAttributes.addFlashAttribute("adminError", "Quelle wurde nicht gefunden.");
            return "redirect:/admin";
        }

        if (!model.containsAttribute("sourceRequest")) {
            model.addAttribute("sourceRequest", new SourceRequest(
                    source.getTitle(), source.getUrl(), source.getType(), source.getVerifiedAt()));
        }
        model.addAttribute("sourceId", id);
        return "admin-source-edit";
    }

    @PostMapping("/admin/sources/{id}")
    public String updateSource(@PathVariable Long id,
                              @Valid @ModelAttribute("sourceRequest") SourceRequest sourceRequest,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("sourceId", id);
            return "admin-source-edit";
        }

        if (sourceService.updateSource(id, sourceRequest).isEmpty()) {
            redirectAttributes.addFlashAttribute("adminError", "Quelle wurde nicht gefunden.");
        } else {
            redirectAttributes.addFlashAttribute("adminSuccess", "Quelle wurde aktualisiert.");
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/sources/{id}/delete")
    public String deleteSource(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        sourceService.deleteSource(id);
        redirectAttributes.addFlashAttribute("adminSuccess", "Quelle wurde gelöscht.");
        return "redirect:/admin";
    }

    private void populateAdminModel(Model model) {
        populateAdminModel(model, "", 0, 20);
    }

    private void populateAdminModel(Model model, String query, int page, int size) {
        var productPage = productService.getAdminProductPage(query, page, size);
        List<Product> products = productPage.getContent();
        List<Source> sources = sourceService.getAllSources();

        model.addAttribute("productPage", productPage);
        model.addAttribute("selectedQuery", query);
        model.addAttribute("products", products);
        model.addAttribute("sources", sources);
        model.addAttribute("dataQualityWarnings", buildDataQualityWarnings(products, sources));
        populateSharedLists(model);

        if (!model.containsAttribute("productRequest")) {
            model.addAttribute("productRequest", new ProductRequest(
                    "", "", null, null,
                    NutritionGrade.UNKNOWN, null, "", null,
                    IngredientStatus.UNKNOWN, IngredientStatus.UNKNOWN, IngredientStatus.UNKNOWN, IngredientStatus.UNKNOWN,
                    java.util.Set.<ProductLabel>of(),
                    List.of(), List.of(), List.of()));
        }
        if (!model.containsAttribute("brandRequest")) {
            model.addAttribute("brandRequest", new BrandRequest("", ""));
        }
        if (!model.containsAttribute("categoryRequest")) {
            model.addAttribute("categoryRequest", new CategoryRequest("", ""));
        }
        if (!model.containsAttribute("companyRequest")) {
            model.addAttribute("companyRequest", new CompanyRequest("", "", "", ""));
        }
        if (!model.containsAttribute("sourceRequest")) {
            model.addAttribute("sourceRequest", new SourceRequest("", "", "", null));
        }
    }

    /**
     * Stammdaten (Marken, Kategorien, Unternehmen, Quellen, Enum-Werte), die sowohl
     * die Admin-Übersicht als auch die einzelnen Bearbeiten-Formulare für Auswahllisten benötigen.
     */
    private void populateSharedLists(Model model) {
        model.addAttribute("brands", brandService.getAllBrands());
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("companies", companyService.getAllCompanies());
        if (!model.containsAttribute("sources")) {
            model.addAttribute("sources", sourceService.getAllSources());
        }
        model.addAttribute("nutritionGrades", NutritionGrade.values());
        model.addAttribute("packagingTypes", PackagingType.values());
        model.addAttribute("ingredientStatuses", IngredientStatus.values());
        model.addAttribute("productLabels", ProductLabel.values());
    }

    private void populateAlternativeOptions(Model model, Long productId, ProductRequest request) {
        var options = new java.util.LinkedHashMap<Long, ProductService.ProductOption>();
        productService.getProductOptions(request.alternativeProductIds())
                .forEach(option -> options.put(option.id(), option));
        productService.searchPage(null, null, null, null, null, null, null, null, 0, 20)
                .forEach(product -> options.putIfAbsent(product.id(),
                        new ProductService.ProductOption(product.id(), product.name())));
        options.remove(productId);
        model.addAttribute("allProducts", options.values());
    }

    private ProductRequest toProductRequest(Product product) {
        return new ProductRequest(
                product.getName(),
                product.getDescription(),
                product.getBrand() != null ? product.getBrand().getId() : null,
                product.getCompany() != null ? product.getCompany().getId() : null,
                product.getNutritionGrade(),
                product.getPackagingType(),
                product.getPackageSize(),
                product.getMadeInGermany(),
                product.getContainsLactose(),
                product.getContainsGluten(),
                product.getContainsAlcohol(),
                product.getContainsCaffeine(),
                new HashSet<>(product.getLabels()),
                product.getCategories().stream().map(Category::getId).toList(),
                product.getSources().stream().map(Source::getId).toList(),
                product.getAlternativeProducts().stream().map(Product::getId).toList());
    }

    /**
     * Ermittelt einfache Datenqualitätshinweise für die Admin-Übersicht:
     * fehlende Marke/Unternehmen/Kategorien/Quellen bei Produkten sowie
     * Quellen ohne Prüfdatum. Dient nur zur redaktionellen Orientierung,
     * verhindert aber keine Speicherung.
     */
    private List<String> buildDataQualityWarnings(List<Product> products, List<Source> sources) {
        List<String> warnings = new ArrayList<>();

        for (Product product : products) {
            if (product.getBrand() == null) {
                warnings.add("Produkt \"" + product.getName() + "\" hat keine Marke zugewiesen.");
            }
            if (product.getCompany() == null) {
                warnings.add("Produkt \"" + product.getName() + "\" hat kein Unternehmen zugewiesen.");
            }
            if (product.getCategories() == null || product.getCategories().isEmpty()) {
                warnings.add("Produkt \"" + product.getName() + "\" hat keine Kategorie zugewiesen.");
            }
            if (product.getSources() == null || product.getSources().isEmpty()) {
                warnings.add("Produkt \"" + product.getName() + "\" hat keine Quellen hinterlegt.");
            }
            if (product.getNutritionGrade() == null || product.getNutritionGrade() == NutritionGrade.UNKNOWN) {
                warnings.add("Produkt \"" + product.getName() + "\" hat keinen Nutri-Score hinterlegt.");
            }
        }

        for (Source source : sources) {
            if (source.getVerifiedAt() == null) {
                warnings.add("Quelle \"" + source.getTitle() + "\" wurde noch nicht mit einem Prüfdatum versehen.");
            }
        }

        return warnings;
    }
}
