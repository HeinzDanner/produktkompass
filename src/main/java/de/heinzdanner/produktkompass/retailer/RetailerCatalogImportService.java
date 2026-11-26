package de.heinzdanner.produktkompass.retailer;

import de.heinzdanner.produktkompass.validation.HttpUrls;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class RetailerCatalogImportService {
    private final RetailerCatalogImportAdapter adapter;
    private final RetailerCatalogRepository repository;

    public RetailerCatalogImportService(RetailerCatalogImportAdapter adapter, RetailerCatalogRepository repository) {
        this.adapter = adapter;
        this.repository = repository;
    }

    @Transactional
    public int importCsv(String code, String displayName, java.io.InputStream input) {
        String normalizedCode = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (!normalizedCode.matches("[A-Z0-9][A-Z0-9_-]{1,31}") || normalizedCode.equals("DEMO")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger oder reservierter Händlercode.");
        }
        if (displayName == null || displayName.isBlank() || displayName.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Händlername muss 1 bis 100 Zeichen lang sein.");
        }
        List<RetailerProductImportRow> products;
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            products = adapter.parse(reader);
            validateUrls(products);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Katalogimport fehlgeschlagen: " + exception.getMessage(), exception);
        }
        repository.replaceSnapshot(normalizedCode, displayName.strip(), LocalDateTime.now(), products);
        return products.size();
    }

    private void validateUrls(List<RetailerProductImportRow> products) {
        for (int index = 0; index < products.size(); index++) {
            var product = products.get(index);
            if (product.productUrl() != null && HttpUrls.normalize(product.productUrl()) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ungültige Produkt-URL bei Importzeile " + (index + 2) + ".");
            }
            if (product.imageUrl() != null && HttpUrls.normalize(product.imageUrl()) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ungültige Bild-URL bei Importzeile " + (index + 2) + ".");
            }
        }
    }
}
