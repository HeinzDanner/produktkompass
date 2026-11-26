package de.heinzdanner.produktkompass.retailer;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/admin/retailer-catalog")
public class RetailerCatalogAdminController {
    private static final long MAX_IMPORT_BYTES = 64L * 1024 * 1024;
    private final RetailerCatalogRepository catalogs;
    private final RetailerCatalogImportService imports;

    public RetailerCatalogAdminController(RetailerCatalogRepository catalogs, RetailerCatalogImportService imports) {
        this.catalogs = catalogs;
        this.imports = imports;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("retailerCatalogs", catalogs.findAll());
        return "admin-retailer-catalog";
    }

    @PostMapping("/import")
    public String importCatalog(@RequestParam String code, @RequestParam String displayName,
                                @RequestParam MultipartFile file, RedirectAttributes redirect) {
        if (file.isEmpty()) {
            redirect.addFlashAttribute("adminError", "Bitte eine CSV-Datei auswählen.");
            return "redirect:/admin/retailer-catalog";
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            redirect.addFlashAttribute("adminError", "Die CSV-Datei darf höchstens 64 MB groß sein.");
            return "redirect:/admin/retailer-catalog";
        }
        try {
            int count = imports.importCsv(code, displayName, file.getInputStream());
            redirect.addFlashAttribute("adminSuccess", count + " Produkte wurden als neuer Katalog-Snapshot importiert.");
        } catch (ResponseStatusException exception) {
            redirect.addFlashAttribute("adminError", exception.getReason());
        } catch (java.io.IOException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "CSV-Datei konnte nicht gelesen werden.", exception);
        }
        return "redirect:/admin/retailer-catalog";
    }

    @GetMapping("/template.csv")
    public ResponseEntity<byte[]> template() {
        String content = "\uFEFFexternal_id;name;brand;category_path;package_size;price;previous_price;discount_percent;"
                + "product_url;image_url;description;preference_tags;allergens;traces;availability\r\n"
                + "sample-001;Haferdrink Natur;Beispielmarke;Pflanzliche Drinks / Hafer;1 l;1,19;;;;;"
                + "Beispielbeschreibung;vegan,bio;;;Datenbestand des Händlers\r\n";
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("haendlerkatalog-vorlage.csv", StandardCharsets.UTF_8).build().toString())
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
