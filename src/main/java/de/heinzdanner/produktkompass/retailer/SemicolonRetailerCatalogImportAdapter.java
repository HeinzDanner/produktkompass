package de.heinzdanner.produktkompass.retailer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SemicolonRetailerCatalogImportAdapter implements RetailerCatalogImportAdapter {
    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("external_id", List.of("external_id", "id", "sku", "product_id", "code", "ean", "gtin")),
            Map.entry("name", List.of("name", "title", "product_name", "titel", "produktname")),
            Map.entry("brand", List.of("brand", "marke")),
            Map.entry("category_path", List.of("category_path", "category", "categories", "category_tree", "kategorie")),
            Map.entry("package_size", List.of("package_size", "quantity", "size", "packung", "menge")),
            Map.entry("price", List.of("price", "current_price", "preis", "aktueller_preis")),
            Map.entry("previous_price", List.of("previous_price", "old_price", "strike_price", "streichpreis")),
            Map.entry("discount_percent", List.of("discount_percent", "discount", "rabatt", "rabatt_prozent")),
            Map.entry("product_url", List.of("product_url", "url", "link", "produkt_url")),
            Map.entry("image_url", List.of("image_url", "image", "bild_url", "bild")),
            Map.entry("description", List.of("description", "bereinigte_beschreibung", "beschreibung")),
            Map.entry("preference_tags", List.of("preference_tags", "labels", "tags", "vorlieben")),
            Map.entry("allergens", List.of("allergens", "allergenes", "allergene")),
            Map.entry("traces", List.of("traces", "spuren")),
            Map.entry("availability", List.of("availability", "verfuegbarkeit", "verfügbarkeit", "status"))
    );

    @Override
    public List<RetailerProductImportRow> parse(Reader reader) throws Exception {
        var format = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true)
                .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).setIgnoreEmptyLines(true).get();
        try (var parser = format.parse(reader)) {
            var headers = new LinkedHashMap<String, String>();
            for (String source : parser.getHeaderMap().keySet()) {
                String normalized = normalizeHeader(source);
                if (headers.putIfAbsent(normalized, source) != null) {
                    throw new IllegalArgumentException("Doppelte CSV-Spalte nach Normalisierung: " + normalized + ".");
                }
            }
            for (var field : ALIASES.entrySet()) {
                long matches = field.getValue().stream().filter(headers::containsKey).count();
                if (matches > 1) {
                    throw new IllegalArgumentException("Mehrere CSV-Spalten für Feld " + field.getKey() + ".");
                }
            }
            String nameColumn = column(headers, "name");
            if (nameColumn == null) {
                throw new IllegalArgumentException("CSV benötigt eine Spalte name oder title.");
            }
            String idColumn = column(headers, "external_id");
            String urlColumn = column(headers, "product_url");
            var rows = new ArrayList<RetailerProductImportRow>();
            var ids = new java.util.HashSet<String>();
            for (CSVRecord record : parser) {
                if (!record.isConsistent()) {
                    throw new IllegalArgumentException("Abweichende Spaltenanzahl in CSV-Zeile " + (record.getRecordNumber() + 1) + ".");
                }
                String name = value(record, nameColumn);
                if (name.isBlank()) {
                    throw new IllegalArgumentException("Produktname fehlt in CSV-Zeile " + (record.getRecordNumber() + 1) + ".");
                }
                String productUrl = value(record, urlColumn);
                String externalId = value(record, idColumn);
                if (externalId.isBlank()) {
                    externalId = stableFallbackId(productUrl, name, value(record, column(headers, "brand")),
                            value(record, column(headers, "category_path")),
                            value(record, column(headers, "package_size")));
                }
                externalId = limit(externalId, 500, "Externe ID", record);
                if (!ids.add(externalId)) {
                    throw new IllegalArgumentException("Doppelte externe Produkt-ID in CSV-Zeile "
                            + (record.getRecordNumber() + 1) + ".");
                }
                rows.add(new RetailerProductImportRow(externalId, limit(name, 500, "Produktname", record),
                        limit(value(record, column(headers, "brand")), 255, "Marke", record),
                        limit(value(record, column(headers, "category_path")), 1000, "Kategoriepfad", record),
                        limit(value(record, column(headers, "package_size")), 100, "Packungsgröße", record),
                        decimal(record, column(headers, "price"), "Preis"),
                        decimal(record, column(headers, "previous_price"), "Streichpreis"),
                        decimal(record, column(headers, "discount_percent"), "Rabatt"),
                        limit(productUrl, 1000, "Produkt-URL", record),
                        limit(value(record, column(headers, "image_url")), 1000, "Bild-URL", record),
                        limit(value(record, column(headers, "description")), 4000, "Beschreibung", record),
                        limit(value(record, column(headers, "preference_tags")), 1000, "Vorlieben", record),
                        limit(value(record, column(headers, "allergens")), 1000, "Allergene", record),
                        limit(value(record, column(headers, "traces")), 1000, "Spuren", record),
                        limit(value(record, column(headers, "availability")), 255, "Verfügbarkeit", record)));
                if (rows.size() > 100_000) {
                    throw new IllegalArgumentException("Ein Katalog darf höchstens 100.000 Produkte enthalten.");
                }
            }
            if (rows.isEmpty()) throw new IllegalArgumentException("Die CSV enthält keine Produkte.");
            return List.copyOf(rows);
        }
    }

    private String normalizeHeader(String header) {
        return header.replace("\uFEFF", "").strip().toLowerCase(Locale.ROOT)
                .replace(' ', '_').replace('-', '_');
    }

    private String column(Map<String, String> headers, String field) {
        return ALIASES.get(field).stream().map(headers::get).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }

    private String value(CSVRecord record, String column) {
        return column == null ? "" : record.get(column).strip();
    }

    private String limit(String value, int maximum, String field, CSVRecord record) {
        if (value.length() > maximum) {
            throw new IllegalArgumentException(field + " ist zu lang in CSV-Zeile " + (record.getRecordNumber() + 1) + ".");
        }
        return value.isBlank() ? null : value;
    }

    private BigDecimal decimal(CSVRecord record, String column, String field) {
        String value = value(record, column).replace("€", "").replace(" ", "");
        if (value.isBlank()) return null;
        if (value.contains(",")) value = value.replace(".", "").replace(',', '.');
        try {
            BigDecimal number = new BigDecimal(value);
            if (number.signum() < 0 || number.scale() > 2
                    || number.precision() - number.scale() > (field.equals("Rabatt") ? 4 : 8)
                    || (field.equals("Rabatt") && number.compareTo(new BigDecimal("100")) > 0)) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " ist ungültig in CSV-Zeile "
                    + (record.getRecordNumber() + 1) + ".");
        }
    }

    private String stableFallbackId(String productUrl, String name, String brand,
                                    String category, String packageSize) {
        String identity = productUrl.isBlank() ? String.join("\u001F", name, brand, category, packageSize) : productUrl;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar.", exception);
        }
    }
}
