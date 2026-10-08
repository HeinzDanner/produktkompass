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
`nutritionGrade` und `madeInGermany`. Listen enthalten kompakte Produktdaten;
Quellen, Labels und Alternativen bleiben über den Produktdetail-Endpunkt
`GET /api/v1/products/{id}` verfügbar. Sortiert wird nach Name und ID.

Migration V12 ergänzt Indizes für Sortierung, Marken, Unternehmen, Nutri-Score
sowie Kategorie- und Labelverknüpfungen. Die Textsuche verwendet weiterhin
Teilwortsuche; für sehr große OFF-Bestände sind später PostgreSQL-Trigram-
oder Volltextindizes und Lastmessungen vorgesehen. Stammdatenlisten
(z. B. Marken/Quellen) und die automatische Alternativensuche sind noch nicht
vollständig auf große Datenbestände umgestellt; große Importe daher noch
nicht starten.

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
sich auf die aktuelle Ergebnisseite; Quellenhinweise beziehen sich weiterhin
auf alle Quellen.

Beim Bearbeiten eines Produkts lädt die Alternative-Auswahl nur die ersten
20 Produkte sowie bereits ausgewählte Alternativen. Mit JavaScript können
weitere Produkte gesucht und Ergebnisseiten geladen werden, ohne das
Bearbeitungsformular neu zu laden. Gewählte Alternativen bleiben während
der Suche und nach Formular-Validierungsfehlern erhalten. Ohne JavaScript
können die anfänglich geladenen Optionen weiterhin bearbeitet werden.
Die übrigen Stammdaten-Auswahllisten sind noch nicht paginiert.

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
