package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.product.CategoryService;
import de.heinzdanner.produktkompass.product.OffCategoryMappingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OffCategoryMappingController {
    private final OffCategoryMappingService mappings;
    private final CategoryService categories;

    public OffCategoryMappingController(OffCategoryMappingService mappings, CategoryService categories) {
        this.mappings = mappings;
        this.categories = categories;
    }

    @GetMapping("/admin/category-mappings")
    public String page(@RequestParam(defaultValue = "") String query,
                       @RequestParam(defaultValue = "false") boolean unmappedOnly,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("mappingPage", mappings.page(query, unmappedOnly, page));
        model.addAttribute("categories", categories.getPublicCategories());
        model.addAttribute("query", query);
        model.addAttribute("unmappedOnly", unmappedOnly);
        return "admin-category-mappings";
    }

    @PostMapping("/admin/category-mappings")
    public String save(@RequestParam String value, @RequestParam(required = false) Long categoryId,
                       @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "false") boolean unmappedOnly,
                       RedirectAttributes redirect) {
        mappings.assign(value, categoryId);
        redirect.addAttribute("query", query);
        redirect.addAttribute("page", page);
        redirect.addAttribute("unmappedOnly", unmappedOnly);
        redirect.addFlashAttribute("mappingSuccess", "Zuordnung gespeichert.");
        return "redirect:/admin/category-mappings";
    }
}
