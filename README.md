# ProduktKompass

ProduktKompass ist eine Spring Boot-Anwendung für einen neutralen Produkt- und Markenkompass mit Katalog, Quellen, Favoriten und Admin-Verwaltung.

## Technologien

- Java 21
- Spring Boot 4.1.1
- Spring Data JPA
- Flyway
- PostgreSQL (lokale Entwicklung und Produktivbetrieb)
- H2 nur noch für automatisierte Tests
- Thymeleaf für eine einfache Weboberfläche

## Open-Food-Facts-Datenmodell

Open-Food-Facts-Datensätze werden an einen Katalogeintrag gekoppelt, aber in
`product_open_food_facts` getrennt von redaktionellen Produktfeldern gespeichert.
Der Barcode ist eine eindeutige Zeichenkette (keine Zahl, damit führende Nullen
erhalten bleiben); Name, Marken, Kategorien und OFF-Taxonomie-Tags, Menge,
Zutaten, Allergene/Spuren, Länder und Nutri-Score bleiben als getrennte
Quellenangaben erkennbar.
Diese Angaben werden nicht automatisch in redaktionelle Marke-, Kategorie-,
Zutaten- oder Nutri-Score-Felder kopiert.

Die Produktdetailseite kennzeichnet solche Angaben als Open-Food-Facts-
Community-Daten, verlinkt den Quelldatensatz und zeigt Importzeitpunkt und
Datenbanklizenz. **Importzeitpunkt bedeutet nicht redaktionelles Prüfdatum.**
Fehlende OFF-Angaben werden nicht als „frei von“ oder als verifizierte Aussage
interpretiert. Produktbilder werden direkt vom Browser über HTTPS von
`images.openfoodfacts.org` geladen, nicht vom Server heruntergeladen oder gespeichert.
Dabei erhält OFF die IP-Adresse des Browsers; `referrerpolicy="no-referrer"`
unterdrückt die Übermittlung der aufgerufenen ProduktKompass-Seite.
Andere Hosts, HTTP und URLs mit Zugangsdaten werden nicht als Bilder eingebettet.
Karten verwenden kleine Bilder, Details die größere Variante. Fehlende oder
nicht erreichbare Bilder erhalten einen lokalen Platzhalter.
Bildquelle und **CC BY-SA 3.0** stehen am Bild; diese Bildlizenz ist von der
Datenbanklizenz **ODbL** getrennt.

Migrationen V13 bis V19 legen dieses getrennte Modell an. Es speichert außerdem
OFF-Marken-Tags, Markeninhaber-Angaben, Verpackung, Labels, Herkunfts- und
Herstellungsort-Tags getrennt von redaktionellen Zuordnungen. Sobald ein Datensatz
mit einem Produkt verknüpft ist, wird er öffentlich mit dem Hinweis auf
Community-Daten angezeigt. Ein redaktioneller Prüfablauf ist noch nicht
implementiert. Vor einem größeren Import müssen außerdem Attribution und die
Bedingungen der OFF-Datenbanklizenz (ODbL) für die konkrete Bereitstellung
geprüft werden.

### Lokal begrenzten OFF-Testimport ausführen

Für lokale/offline Importe wird eine OFF-Exportdatei verwendet, nicht die
Such-API. OFF stellt gzip-komprimierte TSV-Exporte über die [Datenseite](https://world.openfoodfacts.org/data)
bereit; ein passender gefilterter CSV/TSV-Auszug kann dort über die erweiterte
Suche erstellt werden. Für den Import müssen mindestens `code`,
`countries_tags` und eine Namensspalte (`product_name` oder `product_name_<Sprache>`)
enthalten sein. Deutsche Produktnamen werden bevorzugt; fehlt einer, nutzt der
Importer einen vorhandenen englischen oder anderen lokalisierten Namen.
Zusätzliche OFF-Felder wie `brands_tags`, `brand_owner`, `packaging`,
`packaging_tags`, `labels_tags`, `origins_tags` und `manufacturing_places_tags`
werden übernommen, wenn die Exportdatei sie enthält. Die Datei darf als UTF-8
TSV oder gzip-komprimierte TSV vorliegen und wird gestreamt, nicht vollständig
in den Arbeitsspeicher geladen.

Der Importer prüft zusätzlich den Länder-Tag, verarbeitet höchstens die
explizit angegebene Zahl von 10.000 Eingabezeilen und speichert in
transaktionalen 100er-Batches. Die Demo-Dateninitialisierung ist in diesem
Kommando deaktiviert. Ein erneuter Lauf aktualisiert nur OFF-Rohdaten anhand
des Barcodes; redaktionelle Produktfelder, Favoriten und persönliche Tags
werden nicht überschrieben.

Beispiel für einen lokalen Lauf mit maximal 1.000 Zeilen eines für Deutschland
gefilterten Exports (PowerShell; Pfad zur heruntergeladenen Datei anpassen):

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.main-class=de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsImportCommand" "-Dspring-boot.run.arguments=--produktkompass.open-food-facts.import.enabled=true --produktkompass.open-food-facts.import.file=$env:USERPROFILE\Downloads\off-germany.tsv.gz --produktkompass.open-food-facts.import.max-products=1000 --produktkompass.open-food-facts.import.country-tag=en:germany"
```

Der Import läuft gegen das `local`-PostgreSQL-Profil und beendet sich danach.
Die Konsole zeigt die Anzahl neu angelegter, aktualisierter und übersprungener
Datensätze. `max-products` begrenzt die gelesenen Zeilen, nicht die Anzahl
erfolgreich importierter Produkte. Vor einem größeren Gesamtexport erst mit
einem kleinen gefilterten Testauszug beginnen und Datenbankgröße sowie
Suchverhalten prüfen.

### Bilder vorhandener Produkte aus dem Gesamtexport ergänzen

Der explizite Bildmodus liest auch den vollständigen OFF-Export bis zum Ende.
Er benötigt nur `code` und mindestens eine Bild-URL-Spalte, keinen Länderfilter.
Er legt **keine Produkte** an und aktualisiert ausschließlich Bildmetadaten und
deren Einträge im zusammengeführten Quell-JSON für bereits gespeicherte Barcodes.
Namen, Marken, Labels, Nährwerte, Favoriten und Zuordnungen bleiben unverändert.
Der Zeitpunkt des letzten vollständigen Imports und dessen Originalzeile bleiben
ebenfalls unverändert; der Bildlauf behauptet keinen vollständigen Datenabgleich.
Die ursprünglichen Bildadressen bleiben im Quell-JSON nachvollziehbar, nicht
freigegebene Adressen werden protokolliert und nicht als Bildmetadaten übernommen.
Fehlende Spalten erhalten vorherige Werte; vorhandene leere Spalten löschen sie.

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.main-class=de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsImportCommand" "-Dspring-boot.run.arguments=--produktkompass.open-food-facts.import.enabled=true --produktkompass.open-food-facts.import.images-only=true --produktkompass.open-food-facts.import.file=$env:USERPROFILE\Downloads\openfoodfacts-products.csv.gz"
```

`max-products` gilt hier ausdrücklich nicht: Die gesamte Datei wird gestreamt,
nur die vorhandenen Barcodes werden im Speicher gehalten. Beim ersten Vorkommen
eines Barcodes werden seine Bilder übernommen; weitere Vorkommen werden ignoriert.
Gespeichert wird in 100er-Transaktionen; bei einem späteren Dateifehler bleiben
bereits abgeschlossene Batches erhalten. Ein erneuter Lauf ist möglich.
Die Abschlussmeldung nennt gelesene Zeilen, gefundene Barcodes, Änderungen,
Treffer mit darstellbarem Produktbild und nicht gefundene bestehende Barcodes.
Der normale Katalogimport bleibt auf höchstens 10.000 Eingabezeilen begrenzt.

### Vollständigkeit, Schema und Update-Semantik

Der Importer kann nur Angaben übernehmen, die in der Datei enthalten sind.
Ein Export mit `brands_tags`, aber ohne `brands`, liefert beispielsweise
Taxonomie-Tags, **keinen erfundenen Markennamen**. `brand_owner` ist lediglich
eine unbestätigte OFF-Quellenangabe, keine redaktionelle Unternehmenszuordnung.
Verkaufsländer sind keine Herkunfts- oder Herstellerländer.

Vor dem ersten Batch protokolliert der Importer die Anzahl Quellspalten,
abgedeckte logische Felder und eine Warnung mit allen fehlenden optionalen
Feldern. `ImportResult.coverage` enthält zusätzlich die exakte Zuordnung zu
Quellspalten und die kritischen Felder. Kritisch sind `code`, `countries_tags`
und mindestens eine `product_name`-/`product_name_<Sprache>`-Spalte; fehlen sie,
wird vor dem Schreiben abgebrochen. Doppelte Spaltennamen werden abgelehnt.
UTF-8 mit/ohne BOM, zitierte Tabs/Zeilenumbrüche und `.tsv.gz` werden unterstützt.
Die Begrenzung zählt **logische Datensätze**, nicht physische Textzeilen.
Falsche Spaltenanzahlen brechen mit Datensatznummer ab. Bereits abgeschlossene
100er-Transaktionen bleiben bei einem späteren Dateifehler erhalten.

V17 ergänzt portable `TEXT`-JSON-Spalten:

- `source_row_json`: vollständige letzte Quellzeile, einschließlich unbekannter
  Spalten, Leerwerte und unveränderter Zeichenketten.
- `merged_source_json`: Union aller seit V17 gesehenen Quellspalten; nur
  tatsächlich gelieferte Spalten werden ersetzt.
- `metadata_json`: typisierte normalisierte Metadaten einschließlich
  `BigDecimal`-Nährwerten, Integer-NOVA, UTC-Zeitpunkten und sicheren Bild-URLs.
  Die API liefert sie unter `openFoodFactsData.metadata`; Rohzeilen werden nicht
  standardmäßig in öffentlichen Antworten mitgeliefert.

Häufige Felder (Portionsgröße, NOVA, Nutri-Score-Punktzahl, Vollständigkeit,
OFF-Anlagedatum) haben außerdem eigene typisierte Datenbankspalten. Die beiden
Dezimalspalten verwenden `NUMERIC(12,4)` (gerundet, zu große Werte dort `NULL`);
Originalgenauigkeit bleibt in Rohdaten und normalisierten Metadaten erhalten.

**Fehlende Spalte = vorhandenen Wert behalten. Vorhandene leere Spalte =
Angabe löschen.** Bei Alias-/Sprachgruppen gilt diese Regel für das logische
Feld: ist mindestens ein Alias vorhanden, wird dessen aktueller Wert verwendet
(auch wenn alle gelieferten Aliase leer sind). Andere Rohspalten bleiben in
der Union erhalten; ältere Aliaswerte werden nicht als neue Angabe ausgegeben.
Nährwerte werden je Quellspalte separat aktualisiert; keine Umrechnung oder
Mischung zwischen 100 g, 100 ml, Portionen, zubereitet/unzubereitet oder Quellen.
Ungültige numerische Werte/Datumswerte werden normalisiert `NULL`, bleiben aber
verlustfrei in den Rohdaten. Name/Barcode müssen pro importierter Zeile gültig
sein; unbrauchbare Zeilen werden übersprungen. Bestehende redaktionelle Namen,
Marken, Unternehmen, Kategorien, Bewertungen, Favoriten und Tags bleiben erhalten.
Neu angelegte Produkte bekommen nur einen Katalognamen und getrennte OFF-Daten.

### Exakt unterstützte OFF-Zuordnungen

Kommagetrennte Tagwerte bleiben als Rohwerte erhalten und werden nie zu
redaktionellen Stammdaten umgedeutet. OFF-Label-Tags haben zusätzlich einen
eigenen Quellenkatalog (siehe unten); die lesbare Anzeige entsteht erst beim Lesen.
Die folgenden Aliase sind in Prioritätsreihenfolge aufgeführt; verwendet wird
der erste nichtleere Wert. `_*` bedeutet alphabetisch sortierte Sprachvarianten.

| Normalisiertes Feld | OFF-Spalten |
| --- | --- |
| Produktname | `product_name_de`, `product_name`, `product_name_en`, `product_name_*` |
| Zutaten | `ingredients_text_de`, `ingredients_text`, `ingredients_text_en`, `ingredients_text_*` |
| Allgemeine Bezeichnung | `generic_name_de`, `generic_name`, `generic_name_en`, `generic_name_*` |
| Marken / Marken-Tags / Inhaber | `brands` / `brands_tags` / `brand_owner` |
| Kategorien / Kategorie-Tags | `categories` / `categories_tags` |
| Menge / Portionsgröße | `quantity` / `serving_size` |
| Verpackung / Verpackungs-Tags | `packaging` / `packaging_tags` |
| Verpackungstext | `packaging_text_de`, `packaging_text`, `packaging_text_en`, `packaging_text_*` |
| Allergene / Allergen-Tags | `allergens` / `allergens_tags` |
| Spuren / Spuren-Tags | `traces` / `traces_tags` |
| Länder / Länder-Tags | `countries` / `countries_tags` |
| Labels / Label-Tags | `labels` / `labels_tags` |
| Herkunft | `origins`, `origin_de`, `origin_en`, `origin_*` |
| Herkunfts-Tags / Herstellungsorte / deren Tags | `origins_tags` / `manufacturing_places` / `manufacturing_places_tags` |
| Geschäfte / Kauforte | `stores`, `stores_tags` / `purchase_places` |
| Zusatzstoffe / Zutatenanalyse | `additives_tags` / `ingredients_analysis_tags` |
| Qualität | `data_quality_tags`, `data_quality_errors_tags`, `data_quality_warnings_tags` |
| Datenquellen / Ersteller | `data_sources` / `creator` |
| Nutri-Score-Grad | `nutrition_grade_fr`, `nutriscore_grade`, `off:nutriscore_grade` |
| Nutri-Score-Punktzahl | `nutriscore_score`, `nutrition-score-fr_100g` |
| NOVA / Vollständigkeit | `nova_group` / `completeness` |
| Umweltgrad | `environmental_score_grade`, `ecoscore_grade` |
| Sprache | `lang`, `lc` |
| OFF geändert / angelegt / geprüft | `last_modified_t`, `last_modified_datetime` / `created_t`, `created_datetime` / `last_checked_t`, `last_checked_datetime` |
| Hauptbild / Front / Zutaten / Nährwerte / Verpackung | `image_url` / `image_front_url` / `image_ingredients_url` / `image_nutrition_url` / `image_packaging_url` |

Zeitpunkte akzeptieren Unix-Sekunden oder ISO-Instant im portablen Jahresbereich
1–9999 und werden als UTC geführt; OFF-Änderungszeit ergibt zusätzlich die Quellenrevision. Bild-URLs
akzeptieren ausschließlich absolute HTTP(S)-URLs mit Host ohne Zugangsdaten.
Zusätzlich werden `image_[front_|ingredients_|nutrition_|packaging_][small_|thumb_]url`
und flache `selected_images.<front|ingredients|nutrition|packaging>.<display|small|thumb>.<zweistellige Sprache>`
als Bildadressen unterstützt. Keine URL wird während des Imports aufgerufen.

Nährwerte: klassische `<Nährstoff>_100g` und `<Nährstoff>_serving` mit
`<Nährstoff>_unit`/`<Nährstoff>_modifier` sowie das neuere flache Format
`nutrition.input_sets.<Quelle>.<Zubereitung>.<Basis>.nutrients.<Nährstoff>.value`
mit benachbarten `.unit`/`.modifier`. Der Schlüssel der API-Map bleibt die
vollständige Wertspalte. Kein Raten fehlender Einheiten, keine Ableitung von
Makronährstoffen, Nutri-Score oder Herkunft. `nutrition-score*` wird nicht als
Nährstoff behandelt. Produzentenangaben wie `nutriscore_grade_producer` werden
nicht mit berechneten OFF-Bewertungen gleichgesetzt; sie bleiben in Rohdaten.
Andere Felder (z. B. detaillierte Verpackungskomponenten oder bisher unbekannte
Nährwertstrukturen) bleiben ebenfalls in Rohdaten, sind aber nicht automatisch
als typisierte UI-Felder verfügbar.

Der lokal untersuchte gefilterte Export mit 1113 Spalten enthält moderne
Nährwert-Eingabesätze und `brands_tags`, jedoch **keine Bild-URLs und keine
OFF-Anlage-/Änderungszeitpunkte**. Diese kann auch ein erneuter Import nicht
ergänzen. Ein entsprechend umfangreicherer OFF-Export ist dafür erforderlich.
Der aktuelle Export liefert den Nutri-Score-Grad in `off:nutriscore_grade`; nach
Ergänzung dieses Alias wurde der lokale Bestand einmal erneut importiert.

### Anzeige-Normalisierung von OFF-Werten

`OpenFoodFactsTags` (reine Funktionen), `OpenFoodFactsView` und `ProductDisplay`
bilden die gemeinsame Darstellungsschicht für Katalogkarten, Favoriten,
Detailseite und API. Gespeicherte OFF-Rohwerte bleiben unverändert und weiterhin
in den bisherigen Feldern sichtbar.

- **Herkunft:** alle normalisierten Werte tragen `provenance =
  OPEN_FOOD_FACTS_UNVERIFIED` (bestehender API-Wert). Die Oberfläche nutzt kleine
  „OFF“-Quellenmarker statt wiederholter Warntexte.
- **Gleichwertige Darstellung:** App- und OFF-Angaben sind nicht unabhängig
  geprüft; App-Autorschaft ist kein Prüfsiegel. Unterschiedliche Werte werden
  nebeneinander gezeigt, gleiche Werte nur einmal. `ProductDisplay` behält seine
  `off*Fallback`-Feldnamen aus Kompatibilitätsgründen, enthält darin nun aber
  auch abweichende OFF-Werte bei vorhandenen App-Werten. Rohdaten und bestehende
  Provenienz-/Prüfstatusfelder bleiben unverändert.
  Labels nutzen dieselbe Gestaltung; ein kleines „OFF“ mit zugänglicher
  Quellenangabe und Tooltip kennzeichnet ihre Herkunft. Einfache äquivalente
  Labels werden dedupliziert, spezifische Siegel bleiben erhalten.
  Es werden keine Marken-, Unternehmens- oder Inhaberverknüpfungen erzeugt.
- **Marken:** OFF-Feld `brands`, falls vorhanden; sonst `brands_tags` ohne
  gültiges Sprachpräfix (ISO-639-1 oder `xx:`), z. B. `xx:Barilla` → `Barilla`.
  Slugs wie `xx:la-baleine` bleiben `la-baleine`; Groß-/Kleinschreibung,
  Leerzeichen oder Apostrophe werden nicht rekonstruiert. Mehrere Marken bleiben
  getrennt; nur exakte Dubletten ohne Beachtung der Großschreibung entfallen.
- **Labels/Allergene/Verpackung:** nur bekannte, exakt gleiche Tags werden
  übersetzt (z. B. `en:no-gluten` → „Ohne Gluten“, `en:organic` → „Bio“).
  Unbekannte Label-Tags werden ohne gültiges Sprachpräfix und mit Leerzeichen
  statt Bindestrichen/Unterstrichen angezeigt; der Rohtag bleibt im
  `title` bzw. auf der Detailseite mit „nicht übersetzt“ sichtbar. Verpackungs-
  einträge werden nicht zusammengeführt (`en:glass` und `de:verre` bleiben zwei
  Einträge).
- **Menge/Nutri-Score:** einfache Mengen wie `380ml` werden als `380 ml`
  gezeigt, alles andere unverändert. OFF-Grade `a`–`e` werden `A`–`E`,
  `not-applicable` → „nicht anwendbar“, `unknown` → keine Angabe.
- **Gluten/Laktose-Hinweise:** nur aus vorhandenen OFF-Labels, Allergen- und
  Spuren-Tags. Ein Gluten-frei-Label zusammen mit dem Allergen `en:gluten` wird
  in der API weiterhin als Widerspruch geführt. Die Oberfläche zeigt beide
  Angaben mit Herkunft statt einer Alarmmeldung. Milch neben „ohne Laktose“
  gilt nicht als Widerspruch. Fehlende Allergenangaben werden nie als „frei von“
  gedeutet. Der gemeinsame Quellenbereich verweist auf die aktuelle Verpackung.

### OFF-Labelkatalog und kompakte Anzeige

V18 legt `off_labels` und `product_off_labels` an. V19 übernimmt alle bereits
gespeicherten `label_tags`, auch von Produkten außerhalb der aktuellen Importdatei.
`canonical_tag` ist global eindeutig und enthält den vollständigen OFF-Tag samt
Sprachpräfix; `locale_key` enthält das Präfix (ohne Präfix: `xx`). Nur umgebende
Leerzeichen, leere Listeneinträge und exakt gleiche Tags werden entfernt.
`en:organic`, `de:organic` und `EN:organic` bleiben verschiedene Identitäten;
Übersetzungen und redaktionelle `ProductLabel` werden nicht zusammengeführt.
Der Katalog bewahrt auch früher empfangene, inzwischen nicht mehr zugeordnete Tags.

Jeder Import lädt den benötigten Katalog einmal je 100er-Batch, legt nur fehlende
Tags an und synchronisiert die Produktverknüpfungen mit den aktuellen OFF-Tags.
Eine fehlende `labels_tags`-Spalte erhält Zuordnungen; eine explizit leere
Spalte entfernt sie. Wiederholte lokale Importe ergänzen den Katalog auch für
bereits vorhandene Produkte, ohne Dubletten oder Änderungen redaktioneller Daten.
Die Detail-API ergänzt `openFoodFactsData.catalogLabels` um stabile `id`,
`canonicalTag`, `localeKey`, Anzeigename und ungeprüfte OFF-Provenienz;
die bisherigen Rohfelder und normalisierten Listen bleiben kompatibel.
`GET /api/v1/products/off-labels?page=0&size=20` liefert den vollständigen
Quellenkatalog alphabetisch nach exaktem Tag paginiert (`size`: 1–50).

Katalog-, Favoriten-, Marken-, Unternehmens- und Ähnlichkeitskarten zeigen
standardmäßig höchstens drei ausgewählte
OFF-Labels: Gluten/Laktose, vegan, Bio, vegetarisch und ohne Zuckerzusatz in
dieser Priorität. Pro entsprechender Kategorie erscheint nur ein OFF-Tag; eine
bereits identisch angezeigte Angabe wird dort nicht wiederholt.
`produktkompass.open-food-facts.cards.max-labels` ist auf 1–4 konfigurierbar.
Unbekannte Tags und Recycling-/Nutri-Score-Labelketten werden nicht als zusätzliche
Kartenlisten ausgegeben. Distinkte Marken bleiben auch auf Karten sichtbar.
Alle Daten bleiben in Details/API erhalten. Die Detailseite zeigt OFF-Labels
weiterhin inline, nicht in einem separaten Label-Abschnitt. Umfangreiche Produkt-,
Quellen-, Nährwert- und Bildangaben sowie OFF-Attribution und ODbL-Link sind im
standardmäßig geschlossenen Bereich „Datenquellen und Herkunft“ gruppiert.

### Fehlerhafte Produktdaten melden

`GET /products/{id}/report` und `POST /products/{id}/report` sind ohne Anmeldung
zugänglich; POST benötigt weiterhin ein gültiges CSRF-Token. Der Link steht auf
der Produktdetailseite. Erforderlich ist eine Beschreibung (1–2000 Zeichen,
nicht nur Leerraum); betroffenes Feld und Datenbestand (`APP_CATALOG` /
`OPEN_FOOD_FACTS`) sind optionale, begrenzte Auswahllisten. Es werden keine
Kontaktdaten, Nutzerkennung oder IP-Adresse mit einer Meldung gespeichert.
Das Formular bittet ausdrücklich darum, keine personenbezogenen Daten einzugeben.
Es gibt keine automatische Produktänderung und keinen öffentlichen Melde-Feed.
Nach erfolgreichem POST erfolgt eine Weiterleitung mit Bestätigung.

V20 legt `product_reports` separat von redaktionellen Bewertungen an: Produkt,
Beschreibung, Feld, Datenbestand, Eingangszeit und Status (`OPEN`, `REVIEWED`,
`CLOSED`). Bei expliziter Produktlöschung werden zugehörige Meldungen wie andere
produktgebundene Daten mit gelöscht. `/admin/reports` ist für `ADMIN` und
`EDITOR` erreichbar, nach Status filterbar und paginiert (1–50 pro Seite).
CSRF-geschützte Statusänderungen speichern Bearbeiter und Bearbeitungszeit sowie
einen Audit-Eintrag ohne Meldungstext. Sie ändern keine Produktdaten.
Es gibt derzeit keine anonyme Ratenbegrenzung; eine spätere Begrenzung sollte
ohne dauerhafte Erfassung von Reporter-Identifikatoren erfolgen.

`ProductReportMigrationTest` prüft Migration und Constraints mit H2. Der optionale
PostgreSQL-Test verwendet `PRODUKTKOMPASS_TEST_PG_URL`, `PRODUKTKOMPASS_TEST_PG_USER`
und `PRODUKTKOMPASS_TEST_PG_PASSWORD`. Er wendet ausstehende Migrationen an und
validiert Checks, Fremdschlüssel und Löschkaskade mit anschließend zurückgerollten
Testdatensätzen; vorhandene Produktdaten werden nicht geändert.

### Filtersemantik

- `brandId` findet App-Markenzuordnungen und passende OFF-Marken: Der Name der
  ausgewählten Marke wird ohne Beachtung der Groß-/Kleinschreibung als ganzer
  Eintrag in `brands` oder als Bindestrich-Slug in `brands_tags` verglichen.
  Das erzeugt keine neuen Zuordnungen oder Unternehmensbeziehungen.
  Die Freitextsuche berücksichtigt ebenfalls beide Datenquellen (`name`/`query`; Teilwortsuche in
  `brands`, `brands_tags`, `brand_owner`; Leerzeichen treffen zusätzlich
  Bindestrich-Slugs, z. B. „la baleine“).

- `label` filtert standardmäßig ausschließlich redaktionelle Labels. Mit
  `includeOpenFoodFactsLabels=true` (Web-Checkbox und Such-API) treffen
  zusätzlich exakt äquivalente OFF-Tags, z. B. `GLUTEN_FREE` ↔ `en:no-gluten`,
  `en:certified-gluten-free`, `en:dzg-gluten-free`, `en:crossed-grain-trademark`.
  `ALCOHOL_FREE` entspricht den expliziten OFF-Labels `en:alcohol-free`/`en:no-alcohol`,
  `CAFFEINE_FREE` den Labels `en:caffeine-free`/`en:no-caffeine`.
- Freitext findet bekannte Labels auch über deutsche Anzeigenamen und
  Synonyme ab drei Zeichen („glutenfrei“, „ohne gluten“, „bio“, „vegan“).
- `nutritionGrade` filtert weiterhin nur den redaktionellen Nutri-Score.

Produktkarten haben eine einheitliche Höhe und feste Bereiche für Kopf, Bild,
Eigenschaftslabels, Details/Nutri-Score und Aktionen. Titel zeigen höchstens zwei Zeilen;
der vollständige Titel bleibt im Tooltip und auf der Detailseite sichtbar.
Eine Marke erscheint einzeilig, weitere sind über „+N“ mit Tooltip und Link
zu allen Marken erreichbar. Zwischen Bild und Details stehen ausschließlich Labels,
einschließlich vorhandener Glutenfrei-, Laktosefrei-, Alkoholfrei- und Koffeinfrei-Labels.
Mengen, weitere Namen, Unternehmen und Allergen-/Spurenhinweise stehen auf der
Detailseite. Spuren oder fehlende Zutaten erzeugen niemals ein „frei von“-Label.
Die Detailseite gruppiert Inhaltslabels in fünf Feldern: Gluten, Laktose,
Alkohol, Koffein und Palmöl. Passende App- und OFF-Labels stehen direkt im
jeweiligen Feld und nicht nochmals unter den allgemeinen Labels.
`PALM_OIL_FREE` („Ohne Palmöl“) ist auch als App-Label und Filter verfügbar;
das entsprechende OFF-Label ist `en:no-palm-oil`.
Unbekannte App-Werte werden nicht als `UNKNOWN` angezeigt: Bei vorhandenen
Labels oder Spuren stehen diese Angaben im Feld, sonst „Keine Angabe“.
Bekannte App-Werte und abweichende Allergen-/Spurenhinweise bleiben sichtbar.
Palmölfreiheit wird nicht aus fehlenden Zutaten abgeleitet.
Lange Labellisten scrollen innerhalb ihres Bereichs,
ohne die Kachel zu vergrößern. Fehlende Bilder, Bildnachweise oder Bewertungen
verschieben die übrigen Bereiche nicht. Bilder nutzen die feste Bildfläche proportional
mit `object-fit: contain`: vollständig sichtbar, unverzerrt und ohne Beschnitt.
Formatbedingte freie Ränder sind beabsichtigt.
Der Nutri-Score steht als kompakte A–E-Farbskala rechts neben „Details“.
Die aktive Stufe ist zusätzlich durch Größe und Umrandung hervorgehoben.
Hover und Tastaturfokus zeigen die deutsche Erklärung und Datenquelle;
Screenreader erhalten dieselben Angaben. Abweichende App-/OFF-Werte bleiben
als zwei Skalen sichtbar, gleiche Werte erscheinen einmal. Unbekannte Werte
erhalten keine erfundene Bewertung; „nicht anwendbar“ bleibt eine Textangabe.

### Persönlicher Warenkorb

Der bisherige Such-/Filterblock im Produktkatalog ist vorerst entfernt; die
Suchparameter und API-Filter bleiben verfügbar. Bei angemeldeten Benutzern stehen
passende eigene Favoriten im Katalog und in der Warenkorb-Suche zuerst, bereits
vor der Seiteneinteilung. In der Warenkorb-Suche folgen Tag-Boost und Textrelevanz.
Suchbedingungen und harte Ausschlüsse gelten weiterhin auch für Favoriten.

Der Warenkorbbereich trennt HTTP/Session-/Model-Aufgaben (`CartController`),
Anwendungsabläufe (`CartService`, `CartSearchService`, `CartSearchPreferences`)
und JDBC-Datenzugriff (`CartRepository`, `CartSearchPreferencesRepository`).
`CartTagCatalog` übernimmt Tag-Katalog, Aliasauflösung und Auswahlvalidierung;
`CartSearchSpecifications` kapselt Filter und Ranking. `TagState` definiert die
Zustände zentral; HTML, JavaScript und gespeicherte Werte bleiben kompatibel.
Das Speichern einer Tag-Auswahl validiert nur die Tags und führt keine Produktsuche
aus. Transaktionen und die benutzerbezogenen Sperren bleiben auf Service-Ebene wirksam.

Angemeldete Benutzer können Produkte dauerhaft in ihrem Warenkorb speichern.
Unter den Favoriten wechselt das Panel zwischen „In den Warenkorb“, einem
Entfernen-Symbol bei Menge 1 und Minus/Menge/Plus bei größeren Mengen.
Änderungen werden ohne Seitenwechsel gespeichert; Fehler und abgelaufene
Sitzungen werden im Panel angezeigt. Ohne JavaScript funktionieren dieselben
POST-Formulare mit Weiterleitung zur Warenkorbübersicht `/cart`.
Nicht angemeldete Benutzer sehen kein Panel und keinen reservierten Leerraum.

Migration V21 speichert je Benutzer/Produkt eine positive Ganzzahl mit eindeutigem
Schlüssel und Löschkaskaden. Änderungen sind authentifiziert, CSRF-geschützt und
werden pro Benutzer transaktional serialisiert, damit parallele Klicks keine
Mengen verlieren. Der Besitzer wird ausschließlich aus der Anmeldung ermittelt.
Menge 0 entfernt den Eintrag. Der Warenkorb bleibt nach Abmeldung erhalten.
Er ist eine Einkaufsliste, kein Bestell- oder Bezahlprozess.

Die Warenkorbübersicht enthält links die Einkaufsliste, in der Mitte das Suchfeld
mit der Tag-Suche direkt darunter und rechts die serverseitig paginierten
Produkttreffer (12 je Seite). Die Treffer scrollen eigenständig. Bei weniger Platz
stehen sie unter Warenkorb und Suchsteuerung; auf kleinen Bildschirmen stehen
alle drei Bereiche untereinander.
Mengenänderungen synchronisieren Einkaufsliste und Trefferkarten ohne Seitenwechsel.

Die einklappbare **Tag-Suche** unterscheidet bevorzugte, neutrale und ausgeschlossene
Tags. Produktlabels und gleichbedeutende OFF-Tags werden gemeinsam ausgewertet;
weitere OFF-Tags aus dem importierten Tag-Katalog und eigene persönliche Tags
(einschließlich Markenzuordnungen) bleiben eigenständig. Die Herkunft steht am Chip.
Ausgeschlossene Tags entfernen passende Produkte nur aus den Suchtreffern, niemals
aus dem Warenkorb. Jeder erfüllte bevorzugte Tag zählt einmal für die Rangfolge;
danach entscheidet die Textrelevanz: exakter Produktname (App oder OFF), Name
beginnt mit dem Suchtext, Name enthält den Suchtext, Anzahl der Suchwörter im
Namen und anschließend in der Marke. Produktname und ID lösen Gleichstände stabil auf.
Mehrere Suchwörter müssen alle vorkommen, dürfen aber unterschiedliche Felder
treffen (z. B. „Bio Schokolade“: Bio-Tag und Produktname). Groß-/Kleinschreibung,
Reihenfolge der Wörter und zusätzliche Leerzeichen verhindern solche Treffer nicht.

„Tags finden …“ filtert ausschließlich die verfügbare Tag-Liste nach Namen,
Herkunft und bekannten Aliasbegriffen, nicht nach den Texten der Bedienelemente.
Zustände werden
per Auswahl am Chip oder Drag-and-drop geändert. „×“ setzt nur den Suchzustand
auf neutral; „Alle zurücksetzen“ lässt Suchtext, Warenkorb und Mengen unverändert.
Suchzustände stehen in der URL und werden zusätzlich dauerhaft pro Benutzer
gespeichert (Migration V22). Beim Öffnen ohne explizite Tag-Auswahl werden die
gespeicherten Präferenzen geladen, auch nach Abmeldung oder auf anderen Geräten.
„×“ und „Alle zurücksetzen“ aktualisieren ebenfalls die gespeicherte Auswahl.
Produkte und Tag-Zuordnungen werden dabei nicht verändert. Schreibzugriffe sind
CSRF-geschützt; persönliche Tags werden auf Besitz geprüft. Gelöschte persönliche
Tags werden beim Laden ignoriert. Ohne JavaScript speichert „Suchen“ die
Auswahl ebenfalls; der Suchtext bleibt bei Anwendung und Zurücksetzen erhalten.
Die Produktsuche startet über „Suchen“ (auch per Enter),
nicht automatisch beim Tippen oder Ändern der Tags. Jeder Suchaufruf verwendet
den aktuellen Text und die ausgewählten Tags, auch bei unveränderter Eingabe.
Tag-Änderungen werden unabhängig davon automatisch gespeichert und bleiben
beim Navigieren zu Details, Katalog und zurück zum Warenkorb erhalten.
Interne Links warten nötigenfalls auf noch laufende Speichervorgänge.
Der zuletzt ausgeführte Suchtext bleibt in der Anmeldungssitzung erhalten;
noch nicht abgesendete Eingaben werden zusätzlich je Benutzer im Browser-Tab
zwischengespeichert. Bei null Treffern bleibt die Ergebnisfläche ausgeblendet.
Bevorzugte und ausgeschlossene Zonen sind durch Rahmen, Hintergrund, Symbole
und Erklärungstexte hervorgehoben.
Der gemeinsame Such-Tag „Milch (einschließlich Spuren)“ wertet bekannte
Milch-Labelvarianten sowie explizite Milch-Einträge in OFF-Allergen- und
Spurenfeldern aus. Bestehende Auswahlen wie „contains milk“ oder „enthält Milch“
werden diesem Tag zugeordnet und zählen nur einmal. Laktosefreiheit wird nicht
mit Milchfreiheit gleichgesetzt; fehlende Angaben bleiben unbekannt.
Die Such-Tags „Nutri-Score A“ bis „Nutri-Score E“ berücksichtigen sowohl den
App-Score als auch den OFF-Score und entsprechende Verpackungstags. Bei
widersprüchlichen Angaben genügt jeder passende Wert für einen Ausschluss.
Diese fachlichen Zuordnungen gelten gleichermaßen für Ausschluss und Boost;
persönliche Tags bleiben unabhängig davon.
Die Suche lädt ausschließlich das HTML-Trefferfragment über `/cart/results`.
Dabei wird nicht die ganze Seite gerendert oder der gesamte
OFF-Tag-Katalog geladen; ausgewählte Tags werden weiterhin serverseitig validiert.

## Entwicklungsprofile

- `local`: Standardprofil, verbindet sich mit einer lokal installierten
  PostgreSQL-Datenbank (siehe unten für die Einrichtung)
- `docker`: PostgreSQL-Setup für Container-basierte lokale Entwicklung
  (z. B. für einen Rechner ohne lokale PostgreSQL-Installation)
- `prod`: Muss bei einem echten Produktivbetrieb zusätzlich aktiviert werden
  (z. B. `SPRING_PROFILES_ACTIVE=docker,prod`). Dadurch werden die
  Demo-Zugänge (`admin`/`admin123`, `max`/`pw`) **nicht** angelegt.

Automatisierte Tests laufen unabhängig davon immer gegen eine isolierte
H2-In-Memory-Datenbank (`src/test/resources/application-local.yml`), damit
sie ohne lokale PostgreSQL-Installation und ohne gegenseitige Beeinflussung
funktionieren.

## Lokale Entwicklung mit PostgreSQL

Voraussetzung: Eine lokal laufende PostgreSQL-Instanz (z. B. Version 16+).

1. Rolle und Datenbank einmalig anlegen (als PostgreSQL-Superuser, z. B. `postgres`):

```sql
CREATE ROLE produktkompass LOGIN PASSWORD 'produktkompass_local_pw';
CREATE DATABASE produktkompass OWNER produktkompass;
GRANT ALL PRIVILEGES ON DATABASE produktkompass TO produktkompass;
```

2. App starten (Standardprofil `local`):

```bash
./mvnw spring-boot:run
```

Flyway führt beim Start automatisch alle Migrationen aus `src/main/resources/db/migration`
gegen die Datenbank `produktkompass` aus und legt Demo-Daten an (außerhalb des `prod`-Profils).

Falls andere Zugangsdaten verwendet werden sollen, können `spring.datasource.username`
und `spring.datasource.password` in `src/main/resources/application-local.yml`
angepasst werden (oder per Umgebungsvariable überschrieben werden).

## PostgreSQL mit Docker Compose

Alternative, falls keine lokale PostgreSQL-Installation vorhanden ist:

1. Kopiere die Beispiel-Umgebungsdatei:

```bash
cp .env.example .env
```

2. Starte die Infrastruktur:

```bash
docker compose up -d
```

3. Starte die App mit dem Docker-Profil:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=docker
```

Die App verbindet sich dann über den Compose-Service `postgres` mit PostgreSQL.

## Zugriff

- App: http://localhost:8082 (lokal) bzw. http://localhost:8080 (Docker-Profil)
- PostgreSQL: localhost:5432

## Paginierte Produktsuche

Die öffentliche Suche unter `/products` filtert direkt in der Datenbank und
zeigt standardmäßig 20 Produkte pro Seite. `page` beginnt bei 0; `size` muss
zwischen 1 und 50 liegen. Die Navigation erhält alle aktiven Filter. Eigene
Tags berücksichtigen sowohl direkt markierte Produkte als auch Produkte
markierter Marken; fremde oder unbekannte Tags liefern HTTP 404.

Die REST-Listen haben nun ein **geändertes Antwortformat**: statt eines
unbegrenzten JSON-Arrays liefern sie ein Objekt mit `content`, `page`, `size`,
`totalElements` und `totalPages`:

```text
GET /api/v1/products?page=0&size=20
GET /api/v1/products/search?name=hafer&page=0&size=20
```

Die Such-API unterstützt außerdem `brandId`, `categoryId`, `label`,
`includeOpenFoodFactsLabels`, `nutritionGrade` und `madeInGermany`
(Semantik siehe „Filtersemantik“). Listen enthalten kompakte Produktdaten,
das Merkmal `openFoodFactsDataAvailable` sowie additiv `labels` (redaktionell),
`openFoodFacts` (normalisierte OFF-Werte) und `display` (quellenneutrale Darstellung);
der Detail-Endpunkt ergänzt `display` und `openFoodFactsData.normalized`.
OFF-Quellenangaben bleiben über
`GET /api/v1/products/{id}` im separaten Feld `openFoodFactsData` verfügbar.
Quellen, Labels und Alternativen sind ebenfalls im Detail-Endpunkt enthalten.
Sortiert wird nach Name und ID.

Migration V12 ergänzt Indizes für Sortierung, Marken, Unternehmen, Nutri-Score
sowie Kategorie- und Labelverknüpfungen. Die Textsuche verwendet weiterhin
Teilwortsuche; für sehr große OFF-Bestände sind später PostgreSQL-Trigram-
oder Volltextindizes und Lastmessungen vorgesehen. Katalog- und Stammdatenlisten
laden inzwischen begrenzte Seiten. Migration V13 ergänzt ein getrenntes
Provenienzmodell für OFF-Angaben; große Importe erst nach Bau des Importers und
Messungen mit einem kleinen Testbestand starten.

### Automatische Alternativensuche

Redaktionell zugewiesene Alternativen haben Vorrang und bleiben vollständig
erhalten. Sind keine hinterlegt, sucht die Datenbank Produkte mit mindestens
einer gemeinsamen Kategorie und liefert höchstens sechs Vorschläge.
Das Ausgangsprodukt wird ausgeschlossen.

Die Rangfolge entspricht der bisherigen Ähnlichkeitsberechnung:
gleicher Nutri-Score zählt zwei Punkte, gleiche Verpackungsart und gleiche
Herstellerland-Angabe jeweils einen Punkt, jedes gemeinsame Label einen Punkt.
Wie bisher gelten dabei auch zwei fehlende Angaben als gleich; dies ist keine
Aussage über geprüfte Qualität. Bei Punktgleichheit entscheidet die Produkt-ID.
Die Ergebnisse sind Ähnlichkeitsvorschläge, keine automatisch redaktionell
verifizierten Alternativen.

Die Datenbank wählt zunächst nur die besten sechs IDs aus. Zugehörige Details
werden danach gezielt geladen, ohne den gesamten Katalog in Java einzulesen.
Webseite und `GET /api/v1/products/{id}/alternatives` verwenden dieselbe Logik.
Für sehr große Bestände stehen Messungen der Ranking-Abfrage noch aus.

Die Webseite kennzeichnet die Herkunft ausdrücklich als „Redaktionell
zugeordnete Alternativen“ oder „Automatische Ähnlichkeitsvorschläge“.
Eine redaktionelle Zuordnung allein ist kein Nachweis gleicher Eigenschaften
oder einer vollständigen Prüfung. Die API behält ihr JSON-Listenformat bei
und liefert zusätzlich `X-Alternative-Origin`: `EDITORIAL`, `AUTOMATIC`
oder `NONE` (keine Treffer). Der Service ermittelt Herkunft und Produkte
gemeinsam; die Controller leiten die Herkunft nicht aus den Treffern ab.

## Standardlogin

- Benutzer: `admin`
- Passwort: `admin123`
- Gilt nur, solange das Profil `prod` **nicht** aktiv ist (siehe oben).

## Admin-Bereich

Der Admin-Bereich unter `/admin` (Rolle `ADMIN` oder `EDITOR`) bietet:

- Anlegen, Bearbeiten und Löschen von Produkten, Marken, Kategorien, Unternehmen und Quellen
- Feldbezogene Validierungsfehler direkt im jeweiligen Formular
- Automatische Datenqualitätswarnungen (z. B. Produkt ohne Marke, ohne Quelle oder Quelle ohne Prüfdatum)
- Ein Audit-Log unter `/admin/audit-log`, das Änderungen (CREATE/UPDATE/DELETE) mit Zeitstempel und Bearbeiter protokolliert
- Eine JavaScript-Bestätigung ("Wirklich löschen?") vor jedem Löschvorgang, um versehentliches Löschen zu verhindern

Der gemeinsame `AuditLogService` ermittelt den Bearbeiter aus der authentifizierten
Spring-Security-Sitzung bzw. API-Anmeldung, nicht aus Formulareingaben. Ohne
authentifizierten Benutzer wird `SYSTEM` gespeichert. Migration V23 ergänzt die
optionale Spalte `actor`; vorhandene Einträge bleiben unverändert und werden als
„Nicht erfasst (Altbestand)“ angezeigt. Auch die Audit-API liefert `actor`.
Der `EditorialAssessmentService` protokolliert nun ebenfalls Anlegen, Ändern
und Löschen. Fachänderung und Audit-Eintrag gehören zur selben Transaktion.
Das Log enthält weiterhin Aktionsbeschreibungen, keine vollständigen
Vorher-/Nachher-Snapshots aller Felder.

### Quellen und Prüfdatum anzeigen

Auf der Produktdetailseite stehen unter „Datenquellen und Herkunft“ die
zugeordneten Katalogquellen mit Link, Quellentyp und Prüfdatum
(`dd.MM.yyyy HH:mm`, ohne zusätzliche Zeitzonenumrechnung).
Fehlender Typ wird als „Nicht angegeben“, fehlendes Prüfdatum als
„Kein Prüfdatum hinterlegt“ ausgewiesen. Ohne Quellenzuordnung erscheint
„Keine Katalogquelle zugeordnet.“; vorhandene OFF-Provenienz bleibt davon
unabhängig sichtbar. Ein Quellenprüfdatum ist kein Nachweis einer
vollständigen Prüfung aller Produktangaben.

Die Quellenliste in der Administration verwendet dieselbe Darstellung.
Prüfdaten bleiben optional und werden weder aus Importzeitpunkten noch aus
Produktänderungen abgeleitet. Die API liefert weiterhin das gespeicherte
`verifiedAt` bzw. `null`.

Beim Anlegen und Bearbeiten akzeptieren Webformular, API und Service nur
absolute HTTP(S)-Quellenadressen mit Host und ohne eingebettete Zugangsdaten.
Die Syntaxprüfung ruft die Adresse nicht auf und bestätigt weder
Erreichbarkeit noch inhaltliche Qualität. Prüfdaten dürfen nicht in der
Zukunft liegen (maßgeblich ist die Serverzeit). Ungültige Eingaben werden
abgelehnt; bestehende Quellen werden nicht automatisch verändert.

### MVP-Abgleich (Oktober 2026)

„Fertig“ bezeichnet den technischen MVP-Umfang, nicht die Vollständigkeit
oder redaktionelle Prüfung des gesamten Datenbestands.

| Bereich | Stand | Einordnung |
|---|---|---|
| Katalog | Fertig | Produktdetails, Marken/Unternehmen, Kategorien, Suche und API vorhanden; Suche derzeit im Warenkorb, URL-/API-Filter bleiben erhalten. |
| Quellen / Prüfdatum | Teilweise fertig | Quellenzuordnung, öffentlich sichtbares Prüfdatum und Qualitätswarnungen vorhanden; fehlende Belege oder Prüfdaten sind erkennbar, verhindern das Anzeigen eines Produkts aber nicht. Redaktionelle Datenpflege bleibt erforderlich. |
| Alternativen | Teilweise fertig | Redaktionelle Zuordnung und automatische Vorschläge vorhanden; automatische Ähnlichkeit ist keine redaktionelle Verifikation. Prüfung der zugewiesenen Alternativen bleibt redaktionelle Aufgabe. |
| Anmeldung | Fertig | Registrierung, Login/Logout, Rollen, CSRF und Kontosperren vorhanden. |
| Favoriten | Fertig | Benutzerbezogen, duplikatfrei; passende Favoriten vor anderen Suchtreffern, Ausschlüsse bleiben wirksam. |
| Administration | Fertig | CRUD der fünf Kernentitäten, Rollenverwaltung und Bearbeitung von Datenmeldungen vorhanden. |
| Audit-Log | Teilweise fertig | Kern-CRUD, Bewertungen, Rollen und Meldungsstatus protokolliert; neue Einträge mit Bearbeiter. Vollständige Feldhistorie und nachträgliche Zuordnung alter Einträge fehlen. |

Die Backend-Bereiche folgen überwiegend Controller → Service → Repository.
Im Warenkorb sind SQL, Tagkatalog, Suchprädikate und HTTP-Verarbeitung nun
getrennt. Außerhalb davon bleiben der große `AdminController` (mehrere
Verwaltungsbereiche und Qualitätswarnungen), ältere Vollkatalog-Suchmethoden
im `ProductService` und HTTP-Fehler im `ProductReportService` Aufräumkandidaten.
Das ist kein Anlass, funktionierende Bereiche ohne konkreten Bedarf umzubauen.

Die Produktliste unter `/admin` besitzt eine Datenbanksuche und Pagination
mit standardmäßig 20, maximal 50 Produkten pro Seite, z. B.
`/admin?query=hafer&page=0&size=20`. Produkthinweise zur Datenqualität beziehen
sich auf die aktuelle Ergebnisseite; Quellenhinweise beziehen sich auf die
aktuelle Ergebnisseite der Quellenliste.

Beim Bearbeiten eines Produkts lädt die Alternative-Auswahl nur die ersten
20 Produkte sowie bereits ausgewählte Alternativen. Mit JavaScript können
weitere Produkte gesucht und Ergebnisseiten geladen werden, ohne das
Bearbeitungsformular neu zu laden. Gewählte Alternativen bleiben während
der Suche und nach Formular-Validierungsfehlern erhalten. Ohne JavaScript
können die anfänglich geladenen Optionen weiterhin bearbeitet werden.
Die übrigen Stammdaten-Auswahllisten bieten ebenfalls Suche und Pagination
(siehe „Begrenztes Laden von Stammdaten“).

### Benutzer- und Rollenverwaltung

Nur Benutzer mit der Rolle `ADMIN` können unter `/admin/users`:

- alle registrierten Konten und deren Rolle anzeigen,
- Benutzer zwischen `USER`, `EDITOR` und `ADMIN` umstellen,
- gesperrte Konten entsperren und deren Fehlversuchszähler zurücksetzen.

`EDITOR` darf weiterhin redaktionelle Katalogdaten pflegen, hat aber keinen
Zugriff auf die Benutzerverwaltung. Als Schutz vor einem vollständigen
Administratorverlust kann die eigene Rolle nicht geändert und der letzte
verbliebene Administrator nicht herabgestuft werden. Rollenänderungen und
manuelle Entsperrungen erscheinen im Audit-Log. Eine geänderte Rolle wird für
den betroffenen Benutzer spätestens bei dessen nächstem Login wirksam.

## Registrierung und CSRF-Schutz

Die öffentliche Web- und API-Registrierung legt ausschließlich `USER`-Konten an.
Die API akzeptiert `username` (3 bis 50 Zeichen) und `password` (mindestens
8 Zeichen, mit Großbuchstaben, Kleinbuchstaben und Ziffer). Ein zusätzlich
mitgesendetes `role`-Feld wird ignoriert; Rollenänderungen erfolgen nur über
die geschützte Admin-Benutzerverwaltung.

Alle schreibenden Web- und API-Anfragen benötigen einen gültigen CSRF-Token.
Thymeleaf fügt ihn automatisch in POST-Formulare ein. Auch Login und Logout
sind geschützt; Logout erfolgt per POST, nicht per GET.

Für API-Clients mit der derzeitigen HTTP-Basic-Anmeldung gilt:

1. `GET /api/v1/csrf` aufrufen; bei geschützten Aktionen bereits dabei
   die Basic-Anmeldedaten mitsenden.
2. Den Session-Cookie behalten. Die JSON-Antwort enthält `headerName`,
   `parameterName` und `token`.
3. Bei POST/PUT/DELETE den Token im genannten Header (`X-CSRF-TOKEN`)
   zusammen mit dem Session-Cookie und gegebenenfalls Basic-Anmeldedaten senden.

Ohne gültigen Token antwortet der Server mit HTTP 403. Öffentliche
GET-Abfragen benötigen keinen Token. Nach einem Web-Login oder Logout muss
ein neuer Token geladen werden. HTTP Basic darf außerhalb der lokalen
Entwicklung nur über HTTPS verwendet werden; mobile Token-Anmeldung wird
in einem späteren Schritt separat eingeführt.

## Schutz gegen Brute-Force-Logins

Nach 5 aufeinanderfolgenden Fehlversuchen wird ein Benutzerkonto für 15 Minuten
automatisch gesperrt (`AppUserService.MAX_FAILED_LOGIN_ATTEMPTS` /
`LOCKOUT_DURATION`). Ein erfolgreicher Login setzt den Zähler zurück.

- Falsches Passwort/unbekannter Benutzername → `/login?error`
- Konto gesperrt → `/login?locked` mit eigener Meldung
- Selbst das korrekte Passwort wird während der Sperre abgelehnt
- Umgesetzt über einen `AuthenticationEventPublisher`-Listener
  (`config/LoginAttemptListener.java`) und zwei neue Spalten in `app_users`
  (`failed_login_attempts`, `locked_until`, Migration `V11`)
- Falls ein Testkonto (z. B. `max`) versehentlich gesperrt wird, kann die Sperre
  manuell per SQL aufgehoben werden:
  ```sql
  UPDATE app_users SET failed_login_attempts = 0, locked_until = NULL WHERE username = 'max';
  ```

## Fehlerseiten

Für die häufigsten Fehlerfälle gibt es eigene, gestaltete Seiten statt der
Spring-Boot-Whitelabel-Seite:

- `403` – Kein Zugriff (z. B. USER versucht, `/admin` aufzurufen)
- `404` – Seite nicht gefunden
- `500` – Unerwarteter Serverfehler
- Ein generischer Fallback für alle übrigen Fehlerstatuscodes

Die Vorlagen liegen unter `src/main/resources/templates/error/` und werden von
Spring Boots `BasicErrorController` automatisch anhand des HTTP-Status
ausgewählt. Sie zeigen weiterhin die normale Navigation inkl. Loginstatus an.

## Produktivbetrieb (`prod`-Profil)

Das Profil `application-prod.yml` ist für einen echten Serverbetrieb hinter
einem Reverse Proxy (z. B. nginx, Traefik) gedacht, der TLS terminiert:

- `server.forward-headers-strategy: framework` sorgt dafür, dass die von dem
  Proxy gesetzten `X-Forwarded-*`-Header korrekt ausgewertet werden.
- Session-Cookies werden mit `Secure`, `HttpOnly` und `SameSite=Lax` gesetzt,
  damit sie nur über HTTPS und nicht per JavaScript ausgelesen werden können.
- Die Datenbankverbindung wird ausschließlich über Umgebungsvariablen
  konfiguriert: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
  `SPRING_DATASOURCE_PASSWORD`.
- `ddl-auto: validate` – das Schema wird ausschließlich von Flyway verwaltet,
  Hibernate darf es im Produktivbetrieb nicht selbst verändern.
- Demo-Zugänge (`admin`/`admin123`, `max`/`pw`) werden nicht angelegt.

Aktivierung zusätzlich zu einem Datenbankprofil, z. B.:

```bash
SPRING_PROFILES_ACTIVE=docker,prod \
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/produktkompass \
SPRING_DATASOURCE_USERNAME=<user> \
SPRING_DATASOURCE_PASSWORD=<passwort> \
./mvnw spring-boot:run
```

**Wichtig:** Da Cookies mit `Secure` gesetzt werden, muss die App wirklich
hinter HTTPS erreichbar sein – über HTTP würde der Browser den Cookie sonst
verwerfen und Logins würden fehlschlagen.

## Wichtige Hinweise

### Begrenztes Laden von Stammdaten

Die Produktlisten unter `/brands/{id}` und `/companies/{id}` sind ebenfalls
paginiert: `page` standardmäßig 0 (nullbasiert), `size` standardmäßig 20,
maximal 50. Die stabile Sortierung erfolgt nach Produktname und danach ID.
Auch die HTML-Favoritenliste `/favorites` lädt Produkte seitenweise mit diesen
Standardwerten und derselben Sortierung. Die REST-Favoritenendpunkte und die
persönliche Tag-Zuordnungsübersicht sind davon unabhängig und bleiben
unpaginiert. Produkt- und Markendetails laden persönliche Tag-/Favoriteninfos
nur für die dort benötigten IDs, nicht sämtliche persönlichen Zuordnungen.

`GET /api/v1/brands`, `/api/v1/categories`, `/api/v1/companies` und
`/api/v1/sources` liefern
**statt eines JSON-Arrays jetzt ein stabiles Seitenobjekt**:
`{content: [...], page, size, totalElements, totalPages, hasNext, hasPrevious}`.
`page` ist nullbasiert (Standard 0), `size` standardmäßig 20 und auf 1–100
begrenzt; ungültige Seitennummern oder Seitengrößen liefern HTTP 400. `query` sucht ohne
Beachtung der Groß-/Kleinschreibung im Namen bzw. Quellentitel; `%`, `_`
und `!` sind dabei normale Suchzeichen. Sortierung: Name/Titel, danach ID.
GET-Einzelabfragen und Schreibendpunkte sowie deren Zugriffs-/CSRF-Regeln
bleiben unverändert.

Die Admin-Stammdatenlisten laden unabhängig je 20 Einträge. `brandQuery`/
`brandPage`, `categoryQuery`/`categoryPage`, `companyQuery`/`companyPage`
und `sourceQuery`/`sourcePage` steuern ihre Suche/Seite; Navigation und
Suchformulare behalten die Parameter der übrigen Listen bei.
Quellen-Datenqualitätswarnungen gelten nur für die aktuelle Quellenseite.

Auswahllisten für Marken, Kategorien, Unternehmen und Quellen laden
zunächst maximal 20 Einträge **plus alle bereits ausgewählten IDs**.
Suche und Vor-/Zurückblättern laden weitere Seiten über die genannten APIs,
ohne bestehende Auswahlen zu verlieren. Ohne JavaScript bleiben die initialen
Optionen und alle bestehenden Auswahlen verfügbar; weitere Optionen erfordern
JavaScript. Auch nach Formularvalidierungsfehlern bleiben übermittelte
Auswahlen außerhalb der initialen Seite erhalten. Nicht mehr vorhandene
ausgewählte IDs werden als „Nicht verfügbar“ sichtbar, nicht still entfernt.

- Lokale Entwicklung und Produktivbetrieb laufen beide gegen PostgreSQL,
  um Verhaltensunterschiede zwischen Testumgebung und echter Datenbank zu vermeiden.
- H2 wird ausschließlich noch von automatisierten Tests verwendet.
- Flyway verwaltet die Schema-Änderungen und macht die Datenbankmigrationen reproduzierbar.
- Server-seitige Zugriffsregeln (`SecurityConfig`) und deren Absicherung für anonym/USER/ADMIN sind
  durch `SecurityAccessTest` automatisiert abgedeckt.
- Ein vollständiger Web-Flow (Registrierung → Login → Favorit hinzufügen/entfernen → Logout)
  wird durch `WebFlowIntegrationTest` automatisiert geprüft.
- Die eigenen Fehlerseiten (403/404) werden durch `ErrorPageRenderingTest` gegen
  einen echten eingebetteten Server verifiziert.
