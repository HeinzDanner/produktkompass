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

## Open-Food-Facts-Datenmodell (Vorbereitung)

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
interpretiert. Produktbilder sind bewusst nicht Teil dieses ersten Datenmodells;
für sie gelten eigene Quellen- und Lizenzbedingungen.

Migrationen V13 bis V16 legen dieses getrennte Modell an. Es speichert außerdem
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
`nutritionGrade` und `madeInGermany`. Listen enthalten kompakte Produktdaten
und das Merkmal `openFoodFactsDataAvailable`; OFF-Quellenangaben bleiben über
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

## Standardlogin

- Benutzer: `admin`
- Passwort: `admin123`
- Gilt nur, solange das Profil `prod` **nicht** aktiv ist (siehe oben).

## Admin-Bereich

Der Admin-Bereich unter `/admin` (Rolle `ADMIN` oder `EDITOR`) bietet:

- Anlegen, Bearbeiten und Löschen von Produkten, Marken, Kategorien, Unternehmen und Quellen
- Feldbezogene Validierungsfehler direkt im jeweiligen Formular
- Automatische Datenqualitätswarnungen (z. B. Produkt ohne Marke, ohne Quelle oder Quelle ohne Prüfdatum)
- Ein Audit-Log unter `/admin/audit-log`, das jede Änderung (CREATE/UPDATE/DELETE) mit Zeitstempel protokolliert
- Eine JavaScript-Bestätigung ("Wirklich löschen?") vor jedem Löschvorgang, um versehentliches Löschen zu verhindern

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
