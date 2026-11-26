# ProduktKompass

**Capstone-Projekt einer Fortbildung · In Entwicklung · Geplanter Abschluss: 15. Januar 2027**

ProduktKompass ist eine webbasierte Informationsplattform für Produkte, Marken und
Unternehmen. Sie soll Verbrauchern ermöglichen, Produktinformationen und
Unternehmenszuordnungen anhand ihrer Quellen nachzuvollziehen und Alternativen zu
entdecken – sachlich und ohne moralische oder politische Bewertung.

Das Capstone-Projekt startet offiziell am **26. November 2026** und baut auf bereits
entwickelten Prototypfunktionen auf. Das Repository dokumentiert den laufenden
Entwicklungsstand: Die Anwendung ist **noch nicht fertig** und nicht als
produktionsreifes oder vollständig redaktionell geprüftes Angebot zu verstehen.

## Das Projekt im Überblick

| Bereich | Bereits umgesetzt |
|---|---|
| **Produktkatalog** | Öffentliche Suche und Filter, Produktdetails mit Marken- und Unternehmensbezug, Quellen und Prüfdatum sowie redaktionelle und automatische Alternativen |
| **Persönlicher Bereich** | Registrierung, Login, Favoriten, persönliche Tags und persistenter Warenkorb mit bevorzugten oder ausgeschlossenen Merkmalen |
| **Redaktion** | Verwaltung von Produkten, Marken, Unternehmen, Kategorien und Quellen; Datenqualitätswarnungen, Produktmeldungen, Rollen und Audit-Log |
| **Einkaufslisten** | CSV-Import eines Händlerkatalog-Snapshots, Vorschläge für Ersatzprodukte, bewusste Auswahl und Export einer Einkaufsliste |
| **Optionale Integration** | Suche nach vorhandenen Rezepten einer selbst betriebenen Mealie-Instanz anhand explizit zugeordneter Zutaten |
| **Sicherheit** | BCrypt-Passworthashes, rollenbasierte Zugriffe, CSRF-Schutz, Kontosperren und Rate-Limits sowie E-Mail-basierte Passwortwiederherstellung |

Die Anwendung ist als Spring-Boot-Monolith mit serverseitig gerenderten
Thymeleaf-Seiten und REST-Endpunkten aufgebaut. Controller, Services und
Repositories trennen HTTP-Verarbeitung, Fachlogik und Datenzugriff. Flyway
versioniert Datenbankschema und Migrationen; automatisierte Unit- und
Integrationstests prüfen unter anderem Suche, Imports und Sicherheitsabläufe.

**Technologien:** Java 21, Spring Boot 4.1.1, Spring Security, Spring Data JPA,
Maven, PostgreSQL, Flyway, Thymeleaf, HTML/CSS/JavaScript und Docker Compose.
H2 wird für automatisierte Tests verwendet.

## Projektstand und Grenzen

- **Datenqualität:** Open Food Facts liefert Community-Daten, die separat von
  redaktionellen Angaben und Quellen gekennzeichnet sind. Ein Prüfdatum ist kein
  Nachweis, dass sämtliche Produktangaben vollständig geprüft wurden.
- **Alternativen und Händlerlisten:** Automatische Vorschläge sind keine
  redaktionelle Empfehlung. Händlerdaten sind importierte Momentaufnahmen;
  Preise, Sortiment und Verfügbarkeit einer bestimmten Filiale werden nicht garantiert.
  Der integrierte Händler-Demokatalog ist synthetisch.
- **Rezeptideen:** Mealie ist optional und sucht in selbst angelegten Rezepten;
  es generiert keine Rezepte und prüft weder Mengen noch Allergene zuverlässig.
- **Sicherheit und Betrieb:** Schutzmechanismen sind implementiert, ersetzen aber
  keine abschließende Sicherheitsprüfung. Für einen öffentlichen Betrieb sind
  insbesondere HTTPS, sichere Deployment-Konfiguration und ein geeigneter
  SMTP-Dienst erforderlich. MFA und ein sicherer Wechsel bestätigter
  Wiederherstellungsadressen fehlen noch.

Bis zum geplanten Abschluss stehen insbesondere die weitere fachliche und
redaktionelle Prüfung, Tests, Dokumentation und die Vorbereitung einer
nachvollziehbaren Präsentation im Fokus. Umfang und Prioritäten können sich während
des Capstones noch ändern.

## Lokal ausprobieren

**Voraussetzungen:** Java 21, Docker mit Compose und eine freie lokale Portnummer
`5432` für PostgreSQL. Der Maven Wrapper ist im Projekt enthalten; Maven muss nicht
separat installiert werden.

PostgreSQL über Docker starten und die Anwendung auf dem Host ausführen:

```powershell
$env:POSTGRES_PASSWORD = "produktkompass_local_pw"
docker compose up -d postgres
$env:RECOVERY_BASE_URL = "http://localhost:8082"
.\mvnw.cmd spring-boot:run
```

Danach ist die Anwendung unter **http://localhost:8082** erreichbar. Flyway
richtet das Schema beim Start ein. Die Befehle verwenden das lokale
Entwicklungsprofil und dessen Beispielzugangsdaten; sie sind nicht für den
Produktivbetrieb gedacht. Wird bereits ein PostgreSQL-Volume verwendet, ändert
eine neue Umgebungsvariable dessen bestehendes Passwort nicht automatisch.

Für einen ersten Rundgang: Im öffentlichen Katalog ein Produkt suchen und dessen
Details öffnen; anschließend als Demo-Benutzer `max` mit Passwort `pw` anmelden
und den Warenkorb ausprobieren. Der Demo-Admin `admin` / `admin123` öffnet den
Redaktionsbereich. Diese Zugänge sind ausschließlich für lokale Entwicklung und
Tests vorgesehen; bei aktiviertem Profil `prod` werden sie nicht angelegt.
Details zur optionalen Mailpit-Demo und zu anderen Profilen stehen unten.

Tests starten:

```powershell
.\mvnw.cmd test
```

Für macOS/Linux jeweils `./mvnw` statt `.\mvnw.cmd` verwenden.

## Weiterführende Dokumentation

- [Login-Schutz und Rate-Limits](#login-schutz-und-rate-limits)
- [Passwortwiederherstellung und Mailpit](#passwort-vergessen-und-bestätigte-wiederherstellungsadresse)
- [Händlerkataloge und Einkaufslisten](#händlerkataloge-und-einkaufslisten)
- [Mealie-Rezeptsuche](#rezeptideen-aus-dem-warenkorb-mit-mealie)
- [Open-Food-Facts-Daten und Import](#open-food-facts-datenmodell)
- [Entwicklungsprofile und Datenbank](#entwicklungsprofile)
- [Administration und MVP-Abgleich](#admin-bereich)
- [Hinweise zum Produktivbetrieb](#produktivbetrieb-prod-profil)

## Login-Schutz und Rate-Limits

Passwörter werden mit BCrypt (zufälliges Salt, Standard-Kostenfaktor 10) gespeichert.
Nach fünf fehlgeschlagenen Anmeldungen ist ein vorhandenes Konto 15 Minuten gesperrt.
Versuche während einer Sperre verlängern sie nicht. Nach Ablauf beginnt der
Fehlversuchszähler beim nächsten falschen Passwort wieder mit 1; eine erfolgreiche
Anmeldung setzt Zähler und Sperre zurück. Datenbank-Zeilensperren serialisieren
gleichzeitige Aktualisierungen des Login-Zählers.

Zusätzlich gelten folgende feste Zeitfenster je Client-IP und Anwendungsinstanz:

| Aktion | Limit |
|---|---|
| Formular-Login und API-Aufrufe mit HTTP Basic, gemeinsam | 20 Anfragen pro Minute |
| Web- und API-Registrierung, gemeinsam | 5 Anfragen pro 15 Minuten |
| Produktmeldungen, über alle Produkt-IDs hinweg | 10 Anfragen pro 15 Minuten |

Auch erfolgreiche, ungültige und CSRF-abgewiesene Anfragen verbrauchen das jeweilige
Kontingent. Unbekannte Benutzernamen werden ebenfalls vom IP-Limit erfasst.
Überschreitungen liefern HTTP 429, eine sichtbare Fehlermeldung und `Retry-After`
in Sekunden. Abgewiesene Anfragen verlängern das Zeitfenster nicht. Öffentliche
Katalogabfragen ohne Basic-Anmeldung bleiben davon unberührt.

Der Filter läuft in beiden Spring-Security-Filterketten vor CSRF und
Authentifizierung. Er hält höchstens 10.000 aktive IP-/Aktionsfenster im Speicher,
entfernt abgelaufene Einträge und verdrängt keine aktiven Limits. Bei ausgeschöpftem
Speicher werden neue IP-/Aktionskombinationen ebenfalls mit 429 abgewiesen.
Die Limits sind standardmäßig aktiv; für isolierte Tests kann
`produktkompass.security.rate-limiting.enabled=false` gesetzt werden.
Die dedizierten Rate-Limit-Integrationstests aktivieren sie ausdrücklich.

**Betriebsgrenzen:** Neustarts setzen die IP-Kontingente zurück. Mehrere Instanzen
teilen sie nicht; dafür ist ein gemeinsamer Limiter im Reverse Proxy/API-Gateway
oder z. B. mit Redis notwendig. Nutzer mit derselben öffentlichen IP teilen ein
Kontingent. Feste Zeitfenster erlauben kurze Bursts an der Fenstergrenze.
Verteilte Angriffe werden durch IP-Limits allein nicht verhindert.

Der Filter verwendet `request.getRemoteAddr()`, nicht direkt einen
`X-Forwarded-For`-Header. Im Profil `prod` verarbeitet Spring Forwarded-Header:
Der Reverse Proxy muss externe Forwarded-/X-Forwarded-Header entfernen und selbst
korrekt setzen; die Anwendung darf nicht direkt aus dem Internet erreichbar sein.
HTTPS/TLS muss dort eingerichtet werden. Die vorhandene separate Meldung für
gesperrte Konten bleibt bestehen; MFA und eine Content Security Policy sind noch
nicht global implementiert. Wiederherstellungsseiten haben eine eigene restriktive
Content Security Policy.

## Passwort vergessen und bestätigte Wiederherstellungsadresse

Auf der Login-Seite gibt es „Passwort vergessen?“. Neue und bestehende Benutzer
hinterlegen ihre E-Mail-Adresse nach der Anmeldung unter **Kontosicherheit**
(`/account/security`) und bestätigen dabei ihr aktuelles Passwort. Anschließend
muss der Link im Postfach per Button bestätigt werden. Die Registrierung bleibt
unverändert; ohne vorher bestätigte Adresse ist kein Self-Service-Reset möglich.
Ein Wechsel einer bereits bestätigten Adresse ist in dieser Version bewusst nicht
freigeschaltet und braucht einen gesonderten, abgesicherten Supportprozess.

Die öffentliche Wiederherstellung fragt den **Benutzernamen**, nicht eine neue
Zieladresse ab. Für vorhandene, unbekannte, deaktivierte und nicht bestätigte Konten
ist die Rückmeldung gleich. Jede syntaktisch gültige Anfrage wird in dieselbe
begrenzte Hintergrundwarteschlange gestellt, damit SMTP-Latenz keine Konten verrät.
Die Rückmeldung bestätigt ausdrücklich nicht, dass eine Nachricht zugestellt wurde.

| Schutz | Umsetzung |
|---|---|
| Token-Erzeugung | 32 zufällige Bytes mit `SecureRandom` (256 Bit), URL-safe Base64 |
| Speicherung | Nur SHA-256-Hash des Tokens; Passwörter weiterhin BCrypt |
| Ablauf | E-Mail-Bestätigung nach 30 Minuten, Passwort-Reset nach 15 Minuten |
| Einmaligkeit | Zeilensperre und atomarer Passwortwechsel mit Tokenlöschung; parallele Einlösungen erlauben nur einen Wechsel |
| Zweckbindung | Getrennte Hash-Felder für Bestätigung und Passwort-Reset |
| Erneute Anforderung | Ein gültiger Reset-Link wird nicht durch anonyme neue Anfragen ersetzt; Bestätigungsversand höchstens einmal pro Minute je Konto |
| Rate-Limits je IP | Reset-Anforderung 5/15 Minuten; Adresse hinterlegen 5/15 Minuten; Bestätigung und Reset-Einlösung gemeinsam 10/Minute |
| Webschutz | CSRF auf allen POSTs; GET-Aufrufe verändern nichts und verbrauchen keinen Token |
| Browser | `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, restriktive CSP auf Recovery-Seiten |
| Passwortwahl beim Reset | Mindestens 15 Zeichen, keine erzwungenen Zeichenklassen, höchstens 72 UTF-8-Bytes wegen BCrypt; keine leeren Passphrasen |
| Sitzungen | Credential-Version in der DB; ältere Web-/API-Sessions werden beim nächsten Zugriff abgewiesen und invalidiert |
| Benachrichtigung | E-Mail nach Passwortwechsel; keine automatische Anmeldung |
| Audit | Bestätigung und Passwortwechsel ohne Token, Passwort oder E-Mail-Adresse im Audit-Detail |

E-Mail-Fehler rollen Token-/Passwortänderungen zurück. Beim angemeldeten
Bestätigungsversand oder Reset erscheint HTTP 503; Hintergrundfehler werden ohne
Adressen, Token oder SMTP-Inhalte protokolliert. Die Warteschlange hält maximal
100 ausstehende Anfragen; bei Überlast erhält der Aufrufer HTTP 503.
Sie ist nicht persistent: Bei einem Prozessabbruch können noch nicht bearbeitete
Anfragen verloren gehen. Mailversand und Datenbank sind keine verteilte Transaktion;
ein seltenes Commit-Problem nach bereits versendeter Mail kann einen ungültigen
Link erzeugen. Für größere Deployments ist eine transaktionale Outbox sinnvoll.
Ausgehende SMTP-Verbindungen haben kurze Connect-/Read-/Write-Timeouts.

### Lokale Demo mit Mailpit

```powershell
docker compose --profile mail up -d mailpit
```

Starte ProduktKompass wie bisher lokal. Standardmäßig gehen Mails an
`localhost:1025`; das Testpostfach ist unter **http://localhost:8025** erreichbar.
Bei lokalem App-Start auf Port `8082` muss vor dem Start
`RECOVERY_BASE_URL=http://localhost:8082` gesetzt werden, damit die Links auf die
richtige Adresse zeigen (siehe [Lokal ausprobieren](#lokal-ausprobieren)).
Mailpit stellt keine Nachrichten ins Internet zu. Verwende erfundene Adressen,
z. B. `demo@example.org`. Beide veröffentlichten Mailpit-Ports sind nur an Loopback
gebunden; Postfach und SMTP dürfen nicht öffentlich zugänglich gemacht werden.

Demoablauf: anmelden → Kontosicherheit → Adresse mit aktuellem Passwort hinterlegen
→ Mailpit öffnen → Link öffnen und bestätigen → abmelden → Passwort vergessen
→ Benutzernamen eingeben → Reset-Mail öffnen → neue Passphrase setzen → neu anmelden.

Für die App im Compose-Netz:

```powershell
docker compose --profile mail up -d
```

Die App verwendet dann den SMTP-Host `mailpit` und Port `8080`. Das vollständige
Container-Setup ist ein Entwicklungsszenario und noch kein Produktionsdeployment.

### Produktion

Profil `prod` aktivieren und folgende Umgebungsvariablen bereitstellen:

| Variable | Bedeutung |
|---|---|
| `RECOVERY_BASE_URL` | Feste öffentliche HTTPS-Origin, z. B. `https://produktkompass.example.org` |
| `RECOVERY_MAIL_FROM` | Beim SMTP-Anbieter zugelassene Absenderadresse |
| `SMTP_HOST` / `SMTP_PORT` | SMTP-Server; Standardport im Produktionsprofil 587 |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | SMTP-Zugangsdaten aus Deployment-Secrets, niemals ins Repo |

Im Produktionsprofil sind SMTP-Authentifizierung und STARTTLS zwingend aktiviert;
die Serveridentität wird geprüft. Die Basis-URL wird validiert und **nicht** aus
Host-/Forwarded-Headern übernommen. HTTP ist ausschließlich lokal erlaubt, nicht
im Produktionsprofil. SPF/DKIM/DMARC beim Mailanbieter einrichten, Zustellfehler
überwachen und App/Proxy-Zugriffslogs so konfigurieren, dass Query-Strings mit
Recovery-Tokens nicht protokolliert werden. Links und Testpostfächer enthalten
Anmeldegeheimnisse und dürfen nicht geteilt werden.

Dies ist kein vollständiger Identity-Provider: MFA, Prüfung gegen bekannte
kompromittierte Passwörter und ein abgesicherter E-Mail-Wechsel fehlen weiterhin.
Die bisherige Registrierungs-Passwortregel ist unverändert; die neue
15-Zeichen-Regel gilt zunächst für Passwort-Resets.

## Händlerkataloge und Einkaufslisten

Im Warenkorb kann eine neue, händlerspezifische Einkaufsliste aus einem gespeicherten
Katalog erstellt werden. Ein Admin importiert dazu über `/admin/retailer-catalog`
eine UTF-8-CSV mit Semikolon-Trennung. Die herunterladbare Vorlage zeigt die
unterstützten Spalten: Produktname (Pflicht), externe ID, Marke, Kategoriepfad,
Packungsgröße, Preis, Produkt-/Bild-URL, Beschreibung, Vorlieben, Allergene,
Spuren und Verfügbarkeit. URLs müssen HTTP(S) sein. Je Händlercode wird der
Snapshot vollständig ersetzt; fehlerhafte Importe überschreiben den bisherigen
Bestand nicht. Der Import ruft keine Händlerseite oder URL ab.

Für jedes Produkt im Warenkorb muss der Nutzer einen vorgeschlagenen Katalogtreffer
bestätigen oder den Originalnamen als manuell zu prüfende Position übernehmen.
Vorschläge verwenden Namen, Kategorien und explizit vorhandene Vorlieben; sie
bestätigen weder das Sortiment einer bestimmten Filiale noch aktuelle Verfügbarkeit.
Zusätzlich werden die bekannten Katalog- und OFF-Labels des jeweiligen
Warenkorbprodukts mit den importierten Händler-Tags verglichen. Nach den explizit
bevorzugten Tags werden Kandidaten mit mehr passenden Ausgangslabels höher
sortiert, danach entscheidet die Text-/Kategorieähnlichkeit. Explizite Ausschlüsse
gelten unverändert. Gleichwertige Katalog-/OFF-Labels zählen nicht doppelt.
Die Vorschau zeigt passende und nicht bestätigte Ausgangslabels mit ihrer Herkunft.
Fehlende Händlerangaben bleiben unbestätigt; Labels werden weder kopiert noch
aus Zutatenlücken abgeleitet. Kandidaten ohne Labeltreffer bleiben auswählbar.
Bestätigte Treffer werden nutzerbezogen für spätere Vorschauen vorgemerkt. Die
gespeicherte Liste behält Namen, Mengen und Preise als Snapshot und kann als
Semikolon-CSV exportiert werden. Tabellenkalkulations-Formelpräfixe in Textfeldern
werden beim Export entschärft.

Der vorinstallierte `DEMO`-Katalog enthält ausschließlich synthetische Beispiele
und kann nicht durch einen Upload überschrieben werden. Keine Lidl-Daten und kein
Crawler oder automatischer Abruf sind Bestandteil des öffentlichen Projekts.
Echte lokale Importdaten müssen unabhängig von der technischen Importfunktion
rechtmäßig erhoben und genutzt werden. Fehlende Zutaten-, Allergen- und
Spurenangaben bleiben unbekannt und werden nicht als „frei davon“ interpretiert.

## Rezeptideen aus dem Warenkorb mit Mealie

Im Warenkorb führt „Was kann ich kochen? · Mealie“ zu `/cart/recipes`.
Die optionale Anbindung sucht **bestehende Rezepte in einer eigenen lokalen
Mealie-Instanz**, sie generiert keine Rezepte und nutzt keinen öffentlichen
Rezeptkatalog. Jedes Warenkorbprodukt muss bewusst einer strukturierten
Mealie-Zutat zugeordnet oder übersprungen werden. Es gibt keine automatische
Gleichsetzung von Produktname und Zutat.

Erst beim Absenden gehen die ausgewählten Zutaten-UUIDs an Mealie.
Benutzername, Produktnamen, Mengen und persönliche Vorlieben werden nicht
übermittelt; der Warenkorb bleibt unverändert. Gleiche Zutaten werden für die
Rezeptsuche zusammengefasst. Die Vorschau zeigt Rezeptnamen und fehlende Zutaten.
„Keine fehlende strukturierte Zutat“ ist **keine Aussage über ausreichende
Mengen, Vorräte, Allergene oder unstrukturierte Rezeptangaben**.
Vorlieben/Ausschlüsse werden nicht auf Rezepte übertragen. Geräte und
Zubereitung sind direkt in Mealie zu prüfen.

### Lokale Einrichtung

Mealie ist im Compose-Profil `recipes` enthalten, getrennt von der
ProduktKompass-Datenbank und nur auf `127.0.0.1:9925` veröffentlicht.
Zum Starten ausschließlich dieses optionalen Dienstes:

```powershell
docker compose --profile recipes up -d mealie
```

Unter `http://localhost:9925` die Mealie-Ersteinrichtung durchführen,
das anfängliche Passwort ändern und im eigenen Benutzerprofil einen API-Token
erstellen. Rezepte und strukturierte Zutaten müssen dort angelegt werden.
Der Token bestimmt den zugänglichen Mealie-Rezeptbestand; bei mehreren
ProduktKompass-Nutzern wird dieselbe konfigurierte Mealie-Instanz verwendet.
Tokens gehören nicht in Git, Rezeptdateien oder Logs.

ProduktKompass anschließend **lokal auf dem Host** mit diesen
Umgebungsvariablen starten (Token durch deinen eigenen ersetzen):

```powershell
$env:MEALIE_BASE_URL = "http://localhost:9925"
$env:MEALIE_API_TOKEN = "<eigener Mealie-API-Token>"
.\mvnw.cmd spring-boot:run
```

Auch für den IDE-Start lassen sich diese Variablen in der Run-Konfiguration
setzen. Diese erste Integration akzeptiert ausschließlich HTTP auf
`localhost`, `127.0.0.1` oder `::1`; entfernte Server und Weiterleitungen
werden abgewiesen. Der ProduktKompass-App-Container kann mit dieser
Loopback-Adresse nicht auf den separaten Mealie-Container zugreifen.
Eine netzwerkfähige oder nutzerindividuelle Anbindung ist nicht enthalten.
Ohne Token erscheint ein Einrichtungshinweis. Verbindungs-, Authentifizierungs-
und ungültige API-Antworten werden als Fehler gezeigt, nicht als leere Treffer.

### Kleine Demo ohne fremde Rezeptdaten

Lege beispielsweise diese selbst erstellten Testrezepte in Mealie an, mit
**strukturierten Food-Zuordnungen** statt ausschließlich Freitext-Zutaten:

| Testrezept | Strukturierte Zutaten |
| --- | --- |
| Demo-Tomatenreis | Reis, Tomaten, Olivenöl |
| Demo-Kichererbsenpfanne | Kichererbsen, Tomaten, Olivenöl |
| Demo-Haferbrei | Haferflocken, Haferdrink |

Fülle den ProduktKompass-Warenkorb etwa mit Reis und Tomaten, ordne die beiden
Mealie-Zutaten zu und starte die Suche. Olivenöl sollte beim entsprechenden
Testrezept als fehlende Zutat erscheinen. Ein Nonfood-Artikel kann bewusst
übersprungen werden. Mengenumrechnung und Ernährungs-/Allergenprüfung sind
ausdrücklich nicht Bestandteil dieses Demonstrationsablaufs.

### API-Vertrag und Abgrenzung

Compose pinnt Mealie auf `v3.29.0`. Die Anbindung nutzt
`GET /api/foods` mit Pagination (maximal 5.000 Zutaten) und
`GET /api/recipes/suggestions` mit wiederholten `foods=<UUID>`-Parametern,
`limit=10` und `maxMissingFoods=5`. Mealie-Vorratsdaten, Geräte-Vorräte und
automatische Zutatenersetzungen sind für diese Anfrage deaktiviert.
GET/Pagination und das Antwortfeld `missingFoods` wurden mit den offiziellen
[Vorschlagsschemas](https://github.com/mealie-recipes/mealie/blob/v3.29.0/mealie/schema/recipe/recipe_suggestion.py)
und [Zutatenschemas](https://github.com/mealie-recipes/mealie/blob/v3.29.0/mealie/schema/recipe/recipe_ingredient.py)
abgeglichen. Mealie ist ein separat betriebenes Drittprogramm mit eigener
Lizenz; die Rezeptinhalte brauchen ebenfalls eine geeignete Nutzungsgrundlage.
Die [Installationsdokumentation](https://mealie.io/documentation/getting-started/installation/sqlite/)
beschreibt dessen Betrieb.

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

Der öffentliche Produktkatalog bietet ohne Anmeldung ein Suchfeld und eine
Kategorieauswahl. „Suchen“ sendet die Auswahl per GET an `/products` und startet
auf Seite 1; Suchtext und Kategorie bleiben in Ergebnis- und Seitenlinks erhalten.
Die kompakte Suchzeile enthält Suchfeld, Kategorieauswahl und Suchbutton;
die zusätzliche Kategorie-Suchleiste und der Zurücksetzen-Button entfallen.
Die Auswahl enthält nur deutsche Katalogkategorien. Der Filter berücksichtigt
direkte Produktzuordnungen und explizit zugeordnete OFF-Kategorien gemeinsam.
Es werden ausschließlich Katalognamen/IDs geladen, nicht der Produktbestand.
Suchtext leeren und „Alle Kategorien“ auswählen hebt
die sichtbaren Suchbedingungen auf. Der frühere
umfangreiche Filterblock bleibt entfernt; zusätzliche URL- und API-Filter bleiben
verfügbar und werden beim Absenden der Suche beibehalten.
Bei angemeldeten Benutzern stehen
passende eigene Favoriten im Katalog und in der Warenkorb-Suche zuerst, bereits
vor der Seiteneinteilung. Beide Suchwege verwenden dieselbe Wortlogik und
Textrelevanz: Alle Suchwörter müssen vorkommen, dürfen aber unterschiedliche
Felder treffen (z. B. „Bio Schokolade“: Bio-Label und Produktname).
Groß-/Kleinschreibung, Wortreihenfolge und zusätzliche Leerzeichen verhindern
solche Treffer nicht; `%` und `_` werden als wörtliche Zeichen gesucht.
Nach den Favoriten entscheidet die Textrelevanz: exakter Produktname (App oder OFF),
Namensanfang, zusammenhängender Text im Namen, Anzahl der Suchwörter im Namen
und anschließend in der Marke. Produktname und ID lösen Gleichstände stabil auf.
Ohne Suchtext bleibt die alphabetische Reihenfolge innerhalb der Favoritenpriorität.
Nur im Warenkorb stehen persönliche Tag-Bevorzugungen und Ausschlüsse zur Verfügung;
der Tag-Boost liegt dort zwischen Favoritenpriorität und Textrelevanz.
Suchbedingungen und harte Ausschlüsse gelten weiterhin auch für Favoriten.

OFF-Kategorien bleiben wörtliche, ungeprüfte Herkunftsangaben.
Unter `/admin/category-mappings` können ADMIN und EDITOR einzelne Begriffe
einer Katalogkategorie zuordnen, ändern oder durch „Nicht zugeordnet“ entfernen.
Die Liste ist durchsuchbar und auf 20 Einträge pro Seite begrenzt.
„Nur nicht zugeordnet“ zeigt ausschließlich OFF-Begriffe ohne Zuordnung.
Der Filter ist mit der Textsuche kombinierbar und bleibt beim Blättern und
Speichern erhalten; neu zugeordnete Begriffe verschwinden aus dieser Auswahl.
Änderungen sind CSRF-geschützt, werden mit Bearbeiter auditiert und wirken
sofort auf Web- und API-Kategoriefilter. Produktdaten bleiben unverändert.
V26/V27 legen die Zuordnungstabelle und eine konservative Startauswahl an,
darunter `Chocolats noirs` und `nl:Chocolade` unter „Süßwaren und Schokolade“.
Fehlende deutsche Basiskategorien für diese Startauswahl werden angelegt.
Unklare Sammelbegriffe wie `nl:Koeken/Chocolade/Snoep` bleiben zunächst
unzugeordnet und können nach redaktioneller Entscheidung zugeordnet werden.
Keine automatische Übersetzung, Teilwortzuordnung oder Vererbungsannahme.

V28 erweitert diese Startliste um eine recherchierte, explizite Zuordnung
für **721 der 867** zum Recherchezeitpunkt vorhandenen OFF-Kategorieeinträge.
Im lokalen Bestand nach Anwendung: 721 zugeordnet (rund 83 %), 146 offen;
vorher waren 27 zugeordnet. Dies ist eine Bestandsaufnahme vom 28.10.2026,
kein dauerhaft garantiertes Verhältnis bei weiteren Importen.
Grundlage sind die mehrsprachigen Namen, Synonyme und Elternbeziehungen der
[offiziellen OFF-Kategorietaxonomie](https://github.com/openfoodfacts/openfoodfacts-server/blob/6c4d573a1cc6a28cacb07cfb674024bad28531e8/taxonomies/food/categories.txt)
(Revision `6c4d573a1cc6a28cacb07cfb674024bad28531e8`, Recherche 28.10.2026).
OFF beschreibt diese Hierarchie in seiner
[Taxonomie-Dokumentation](https://github.com/openfoodfacts/openfoodfacts-server/blob/6c4d573a1cc6a28cacb07cfb674024bad28531e8/taxonomies/README.md).
Die Liste liegt nachvollziehbar in
`src/main/resources/db/data/off-category-mappings-v28.tsv`: deutsche Zielkategorie,
exakter vorhandener OFF-Wert sowie Taxonomiebegriff bzw. Hinweis auf eine
zusätzlich geprüfte Schreibvariante. Die Taxonomie selbst wird nicht mitgeliefert.

Die Hierarchie diente nur als Recherchehilfe; zur Laufzeit werden weiterhin
exakte, explizit gespeicherte Zuordnungen verwendet. Bei mehreren möglichen
Gruppen wurden spezifische Begriffe bevorzugt (z. B. Haferdrink als
Milchalternative, Dosentomaten als Konserve, gefrorene Kräuter als Tiefkühlprodukt).
Generische Angaben wie „pflanzliche Lebensmittel“, „Lebensmittel“,
„Lebensmittelgeschäft“ oder „gesalzen“ erhalten keine beliebige Fachkategorie.
Für Fleisch, Fisch, Öle und manche Aufstriche fehlt eine passende vorhandene
Kataloggruppe; auch diese Begriffe bleiben zunächst offen.

V28 überschreibt keine bereits vorhandene Zuordnung und protokolliert je
Zielkategorie die tatsächlich ergänzte Anzahl als SYSTEM-Aktion im Audit-Log.
Die TSV-Datei ist Bestandteil der Flyway-Prüfsumme; nach Anwendung nicht ändern,
sondern weitere Datenänderungen im Admin-Bereich oder über neue Migrationen
vornehmen. Die Abdeckung bezieht sich auf Kategoriebegriffe, nicht auf den
Anteil korrekt eingeordneter Produkte oder eine redaktionelle Produktprüfung.

V29 ergänzt die vier bislang fehlenden deutschen Gruppen „Fleisch und
Wurstwaren“, „Fisch und Meeresfrüchte“, „Öle und Fette“ sowie „Aufstriche und
Konfitüren“. Die erneute Prüfung derselben offiziellen Taxonomierevision
ergab 53 weitere genaue Begriffe, dokumentiert in
`src/main/resources/db/data/off-category-mappings-v29.tsv`.
Nach Anwendung im lokalen Bestand: **774 von 867** Kategoriebegriffen
zugeordnet (rund 89 %), **93 offen**, Stand 28.10.2026.
Beispiele: `en:bacon`, `Tonijn`, `en:vegetable-oils` und
`Beurres de cacahuètes`. Vorhandene Kategorien und Zuordnungen bleiben
erhalten; neu angelegte Kategorien und tatsächlich ergänzte Zuordnungen
werden als SYSTEM-Aktionen auditiert. Auch diese Datei gehört zur
Flyway-Prüfsumme und wird nach Anwendung nicht verändert.
Breite Begriffe wie `en:fruit-preserves` sind nicht automatisch Konfitüren
(sie können auch andere Obstkonserven umfassen); Fleischalternativen
werden nicht als Fleisch eingeordnet. Solche offenen Begriffe erfordern
eine weitere fachliche Entscheidung, nicht bloß eine Übersetzung.
Ein Filter trifft einen vollständigen, getrimmten Eintrag, keine Teilwörter.
Die Webauswahl verwendet `categoryId=off:<Originaleintrag>`; numerische
`categoryId`-Links bleiben Katalogfilter. Die Such-API akzeptiert zusätzlich
`offCategory=<Originaleintrag>` (mit `categoryId` zusammen als UND-Filter).
V24/V25 legen den OFF-Kategorieindex an und befüllen ihn aus vorhandenen Daten.
Künftige Importe aktualisieren ihn beim Setzen der beiden OFF-Kategoriefelder,
auch wenn ein Feld geleert wird. Redaktionelle Zuordnungen bleiben unberührt.
Die öffentliche Auswahl lädt nur die Katalogkategorien; OFF-Begriffe
ohne Zuordnung bleiben über die Freitextsuche und den exakten OFF-Filter auffindbar.

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

Starte für die Host-Entwicklung ausschließlich den Datenbankdienst und anschließend
die App mit dem Standardprofil `local`, wie unter
[Lokal ausprobieren](#lokal-ausprobieren) beschrieben. Das Profil `docker`
verwendet den Hostnamen `postgres` innerhalb des Compose-Netzwerks und eignet
sich nicht unverändert für eine auf dem Host gestartete Anwendung.

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

### Historischer MVP-Abgleich (Oktober 2026)

Dieser Abgleich dokumentiert den damaligen Stand vor Beginn des offiziellen
Capstones. „Fertig“ bezeichnet hier nur den technischen MVP-Umfang des jeweiligen
Bereichs, nicht den Abschluss des Projekts oder die Vollständigkeit und
redaktionelle Prüfung des gesamten Datenbestands.

| Bereich | Stand | Einordnung |
|---|---|---|
| Katalog | Fertig | Produktdetails, Marken/Unternehmen, Kategorien, öffentliche Suche ohne Anmeldung und API vorhanden; zusätzliche Warenkorbsuche mit persönlichen Tags. |
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

Die Produktliste lässt sich zusätzlich über `quality` nach Quellenlücken filtern:
`ALL` (Standard), `MISSING_SOURCES` (keine zugeordneten Quellen),
`MISSING_REVIEW_DATE` (mindestens eine zugeordnete Quelle ohne Prüfdatum)
und `ANY_GAP` (eine der beiden Lücken). Textsuche und Qualitätsfilter wirken
gemeinsam in der Datenbank vor der Seiteneinteilung; Seitenlinks und
Validierungsfehler beim Anlegen erhalten den Filter. Eine Mischung aus Quellen
mit und ohne Prüfdatum zählt weiterhin als fehlendes Prüfdatum.
OFF-Importzeitpunkte ersetzen keine redaktionellen Quellen oder Prüfdaten.
Der Filter verändert keine Daten und erklärt ein Produkt nicht als vollständig geprüft.

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
RECOVERY_BASE_URL=https://<öffentlicher-host> \
RECOVERY_MAIL_FROM=<absenderadresse> \
SMTP_HOST=<smtp-host> \
SMTP_USERNAME=<smtp-benutzer> \
SMTP_PASSWORD=<smtp-passwort> \
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
