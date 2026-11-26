INSERT INTO retailer_catalogs(code, display_name, source_kind, imported_at)
VALUES ('DEMO', 'Synthetisches Demo-Sortiment', 'SYNTHETIC', NULL);

INSERT INTO retailer_products(
    catalog_code, external_id, name, brand, category_path, package_size, price,
    previous_price, discount_percent, product_url, image_url, description,
    preference_tags, allergens, traces, availability, imported_at
) VALUES
('DEMO', 'demo-oat-drink', 'Haferdrink Natur', 'Beispielhof', 'Pflanzliche Drinks / Hafer', '1 l', 1.19,
 NULL, NULL, NULL, NULL, 'Synthetisches Demo-Produkt.', 'vegan,bio', NULL, NULL, 'Demo-Datenbestand', CURRENT_TIMESTAMP),
('DEMO', 'demo-oat-barista', 'Haferdrink Barista', 'Beispielhof', 'Pflanzliche Drinks / Hafer', '1 l', 1.49,
 NULL, NULL, NULL, NULL, 'Synthetisches Demo-Produkt.', 'vegan', NULL, NULL, 'Demo-Datenbestand', CURRENT_TIMESTAMP),
('DEMO', 'demo-cow-milk', 'Haltbare Vollmilch', 'Beispielhof', 'Milch / H-Milch', '1 l', 1.09,
 NULL, NULL, NULL, NULL, 'Synthetisches Demo-Produkt.', 'vegetarisch', 'milch', NULL, 'Demo-Datenbestand', CURRENT_TIMESTAMP),
('DEMO', 'demo-chickpeas', 'Kichererbsen', 'Gartenküche', 'Konserven / Hülsenfrüchte', '400 g', 0.89,
 NULL, NULL, NULL, NULL, 'Synthetisches Demo-Produkt.', 'vegan,bio', NULL, NULL, 'Demo-Datenbestand', CURRENT_TIMESTAMP),
('DEMO', 'demo-tomatoes', 'Gehackte Tomaten', 'Gartenküche', 'Konserven / Gemüse', '400 g', 0.79,
 NULL, NULL, NULL, NULL, 'Synthetisches Demo-Produkt.', 'vegan', NULL, NULL, 'Demo-Datenbestand', CURRENT_TIMESTAMP),
('DEMO', 'demo-dark-chocolate', 'Zartbitterschokolade 70 %', 'Kakaowelt', 'Süßwaren / Schokolade', '100 g', 1.39,
 NULL, NULL, NULL, NULL, 'Synthetisches Demo-Produkt.', 'vegan,bio', 'soja', NULL, 'Demo-Datenbestand', CURRENT_TIMESTAMP),
('DEMO', 'demo-rice', 'Langkornreis', 'Gartenküche', 'Beilagen / Reis', '1 kg', 1.59,
 NULL, NULL, NULL, NULL, 'Synthetisches Demo-Produkt.', 'vegan', NULL, NULL, 'Demo-Datenbestand', CURRENT_TIMESTAMP),
('DEMO', 'demo-yogurt', 'Naturjoghurt', 'Beispielhof', 'Milchprodukte / Joghurt', '500 g', 0.99,
 NULL, NULL, NULL, NULL, 'Synthetisches Demo-Produkt.', 'vegetarisch', 'milch', NULL, 'Demo-Datenbestand', CURRENT_TIMESTAMP);
