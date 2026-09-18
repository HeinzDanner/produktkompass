package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.product.BrandService;
import de.heinzdanner.produktkompass.product.CategoryService;
import de.heinzdanner.produktkompass.product.CompanyService;
import de.heinzdanner.produktkompass.product.IngredientStatus;
import de.heinzdanner.produktkompass.product.NutritionGrade;
import de.heinzdanner.produktkompass.product.PackagingType;
import de.heinzdanner.produktkompass.product.ProductService;
import de.heinzdanner.produktkompass.product.ProductLabel;
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

import java.util.List;

@Controller
public class AdminController {

    private final ProductService productService;
    private final BrandService brandService;
    private final CategoryService categoryService;
    private final CompanyService companyService;
    private final SourceService sourceService;

    public AdminController(ProductService productService,
                          BrandService brandService,
                          CategoryService categoryService,
                          CompanyService companyService,
                          SourceService sourceService) {
        this.productService = productService;
        this.brandService = brandService;
        this.categoryService = categoryService;
        this.companyService = companyService;
        this.sourceService = sourceService;
    }

    @GetMapping("/admin")
    public String adminPage(Model model) {
        populateAdminModel(model);
        return "admin";
    }

    @PostMapping("/admin/products")
    public String createProduct(@Valid @ModelAttribute("productRequest") ProductRequest productRequest,
                               BindingResult bindingResult,
                               Model model) {
        if (bindingResult.hasErrors()) {
            populateAdminModel(model);
            return "admin";
        }

        productService.createProduct(productRequest);
        return "redirect:/admin";
    }

    @PostMapping("/admin/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return "redirect:/admin";
    }

    @PostMapping("/admin/brands")
    public String createBrand(@Valid @ModelAttribute("brandRequest") BrandRequest brandRequest,
                             BindingResult bindingResult,
                             Model model) {
        if (bindingResult.hasErrors()) {
            populateAdminModel(model);
            return "admin";
        }

        brandService.createBrand(brandRequest);
        return "redirect:/admin";
    }

    @PostMapping("/admin/brands/{id}/delete")
    public String deleteBrand(@PathVariable Long id) {
        brandService.deleteBrand(id);
        return "redirect:/admin";
    }

    @PostMapping("/admin/categories")
    public String createCategory(@Valid @ModelAttribute("categoryRequest") CategoryRequest categoryRequest,
                                BindingResult bindingResult,
                                Model model) {
        if (bindingResult.hasErrors()) {
            populateAdminModel(model);
            return "admin";
        }

        categoryService.createCategory(categoryRequest);
        return "redirect:/admin";
    }

    @PostMapping("/admin/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return "redirect:/admin";
    }

    @PostMapping("/admin/companies")
    public String createCompany(@Valid @ModelAttribute("companyRequest") CompanyRequest companyRequest,
                               BindingResult bindingResult,
                               Model model) {
        if (bindingResult.hasErrors()) {
            populateAdminModel(model);
            return "admin";
        }

        companyService.createCompany(companyRequest);
        return "redirect:/admin";
    }

    @PostMapping("/admin/companies/{id}/delete")
    public String deleteCompany(@PathVariable Long id) {
        companyService.deleteCompany(id);
        return "redirect:/admin";
    }

    @PostMapping("/admin/sources")
    public String createSource(@Valid @ModelAttribute("sourceRequest") SourceRequest sourceRequest,
                              BindingResult bindingResult,
                              Model model) {
        if (bindingResult.hasErrors()) {
            populateAdminModel(model);
            return "admin";
        }

        sourceService.createSource(sourceRequest);
        return "redirect:/admin";
    }

    @PostMapping("/admin/sources/{id}/delete")
    public String deleteSource(@PathVariable Long id) {
        sourceService.deleteSource(id);
        return "redirect:/admin";
    }

    private void populateAdminModel(Model model) {
        model.addAttribute("products", productService.getAllProducts());
        model.addAttribute("brands", brandService.getAllBrands());
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("companies", companyService.getAllCompanies());
        model.addAttribute("sources", sourceService.getAllSources());
        model.addAttribute("nutritionGrades", NutritionGrade.values());
        model.addAttribute("packagingTypes", PackagingType.values());
        model.addAttribute("ingredientStatuses", IngredientStatus.values());
        model.addAttribute("productLabels", ProductLabel.values());
        model.addAttribute("productRequest", new ProductRequest(
                "", "", null, null,
                NutritionGrade.UNKNOWN, null, "", null,
                IngredientStatus.UNKNOWN, IngredientStatus.UNKNOWN, IngredientStatus.UNKNOWN, IngredientStatus.UNKNOWN,
                java.util.Set.<ProductLabel>of(),
                List.of(), List.of(), List.of()));
        model.addAttribute("brandRequest", new BrandRequest("", ""));
        model.addAttribute("categoryRequest", new CategoryRequest("", ""));
        model.addAttribute("companyRequest", new CompanyRequest("", "", "", ""));
        model.addAttribute("sourceRequest", new SourceRequest("", "", "", null));
    }
}
