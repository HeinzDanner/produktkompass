package de.heinzdanner.produktkompass.retailer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/cart")
public class RetailerShoppingListController {
    private final RetailerCatalogRepository catalogs;
    private final RetailerShoppingListService lists;

    public RetailerShoppingListController(RetailerCatalogRepository catalogs, RetailerShoppingListService lists) {
        this.catalogs = catalogs;
        this.lists = lists;
    }

    @GetMapping("/retailer-list")
    public String preview(Principal principal, @RequestParam(required = false) String catalogCode, Model model) {
        var available = catalogs.findAll().stream().filter(catalog -> catalog.productCount() > 0).toList();
        model.addAttribute("retailerCatalogs", available);
        if (catalogCode != null && !catalogCode.isBlank()) {
            var preview = lists.preview(principal.getName(), catalogCode);
            model.addAttribute("preview", preview);
            model.addAttribute("defaultListName", preview.catalog().displayName()
                    + " · " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        }
        return "retailer-list";
    }

    @PostMapping("/retailer-list")
    public String create(Principal principal, @RequestParam String catalogCode, @RequestParam String name,
                        @RequestParam Map<String, String> parameters, RedirectAttributes redirect) {
        Map<Long, String> selections = new LinkedHashMap<>();
        try {
            for (var entry : parameters.entrySet()) {
                if (!entry.getKey().startsWith("selection_") || entry.getValue().isBlank()) continue;
                long sourceId = Long.parseLong(entry.getKey().substring("selection_".length()));
                String selection = entry.getValue().strip();
                if (!selection.equals("manual")) Long.parseLong(selection);
                if (selections.put(sourceId, selection) != null) {
                    throw new NumberFormatException();
                }
            }
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Ungültige Produktauswahl.", exception);
        }
        long id = lists.create(principal.getName(), catalogCode, name, selections);
        redirect.addFlashAttribute("success", "Einkaufsliste wurde gespeichert.");
        return "redirect:/cart/retailer-lists/" + id;
    }

    @GetMapping("/retailer-lists")
    public String listIndex(Principal principal, Model model) {
        model.addAttribute("shoppingLists", lists.lists(principal.getName()));
        return "retailer-shopping-lists";
    }

    @GetMapping("/retailer-lists/{id}")
    public String getList(Principal principal, @PathVariable long id, Model model) {
        model.addAttribute("savedList", lists.get(principal.getName(), id));
        return "retailer-shopping-list";
    }

    @GetMapping("/retailer-lists/{id}/export.csv")
    public ResponseEntity<byte[]> export(Principal principal, @PathVariable long id) {
        var list = lists.get(principal.getName(), id);
        StringWriter writer = new StringWriter();
        try (var printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setDelimiter(';').get())) {
            printer.printRecord("Produkt", "Marke", "Menge", "Packungsgröße", "Einzelpreis", "Produktseite");
            for (var item : list.items()) {
                printer.printRecord(spreadsheetSafe(item.productName()), spreadsheetSafe(item.brand()),
                        item.quantity(), spreadsheetSafe(item.packageSize()), item.unitPrice(),
                        spreadsheetSafe(item.productUrl()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Einkaufsliste konnte nicht als CSV erstellt werden.", exception);
        }
        byte[] body = ("\uFEFF" + writer).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("einkaufsliste-" + id + ".csv").build().toString())
                .body(body);
    }

    private String spreadsheetSafe(String value) {
        String firstMeaningful = value == null ? "" : value.stripLeading();
        if (!firstMeaningful.isEmpty() && "=+-@".indexOf(firstMeaningful.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
