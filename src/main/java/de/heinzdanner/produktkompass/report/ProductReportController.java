package de.heinzdanner.produktkompass.report;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class ProductReportController {

    private final ProductReportService reports;

    public ProductReportController(ProductReportService reports) {
        this.reports = reports;
    }

    @InitBinder("reportRequest")
    void reportBinding(WebDataBinder binder) {
        binder.setAllowedFields("description", "targetField", "provenance");
    }

    @GetMapping("/products/{id}/report")
    public String form(@PathVariable Long id, Model model) {
        model.addAttribute("reportRequest", new ProductReportRequest());
        populateForm(id, model);
        return "product-report";
    }

    @PostMapping("/products/{id}/report")
    public String submit(@PathVariable Long id,
                         @Valid @ModelAttribute("reportRequest") ProductReportRequest request,
                         BindingResult errors, Model model, RedirectAttributes redirect) {
        populateForm(id, model);
        if (errors.hasErrors()) {
            return "product-report";
        }
        reports.submit(id, request);
        redirect.addFlashAttribute("reportSuccess",
                "Vielen Dank! Deine Meldung wurde zur redaktionellen Prüfung gespeichert. Produktdaten wurden nicht verändert.");
        return "redirect:/products/" + id + "/report";
    }

    @GetMapping("/admin/reports")
    public String queue(@RequestParam(required = false) ReportStatus status,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size, Model model) {
        model.addAttribute("reportPage", reports.queue(status, page, size));
        model.addAttribute("selectedStatus", status);
        model.addAttribute("reportStatuses", ReportStatus.values());
        return "admin-reports";
    }

    @PostMapping("/admin/reports/{id}/status")
    public String moderate(@PathVariable Long id, @RequestParam ReportStatus status,
                           Principal principal, RedirectAttributes redirect) {
        reports.moderate(id, status, principal.getName());
        redirect.addFlashAttribute("adminSuccess", "Der Meldungsstatus wurde aktualisiert.");
        return "redirect:/admin/reports";
    }

    private void populateForm(Long id, Model model) {
        model.addAttribute("product", reports.getProduct(id));
        model.addAttribute("reportTargetFields", ReportTargetField.values());
        model.addAttribute("reportProvenances", ReportProvenance.values());
    }
}
