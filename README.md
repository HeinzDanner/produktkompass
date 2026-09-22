# ProduktKompass

ProduktKompass ist eine Spring Boot-Anwendung für einen neutralen Produkt- und Markenkompass mit Katalog, Quellen, Favoriten und Admin-Verwaltung.

## Technologien

- Java 21
- Spring Boot 4.1.1
- Spring Data JPA
- Flyway
- PostgreSQL
- H2 für lokale Entwicklung
- Thymeleaf für eine einfache Weboberfläche

## Entwicklungsprofile

- `local`: Standardprofil mit H2-In-Memory-Datenbank
- `docker`: PostgreSQL-Setup für Container-basierte lokale Entwicklung
- `prod`: Muss bei einem echten Produktivbetrieb zusätzlich aktiviert werden
  (z. B. `SPRING_PROFILES_ACTIVE=docker,prod`). Dadurch werden die
  Demo-Zugänge (`admin`/`admin123`, `max`/`pw`) **nicht** angelegt.

## Lokale Entwicklung mit H2

```bash
./mvnw spring-boot:run
```

Die App startet mit dem Standardprofil `local` und nutzt H2.

## PostgreSQL mit Docker Compose

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

- App: http://localhost:8080
- H2 Console: http://localhost:8080/h2-console
- PostgreSQL: localhost:5432

## Standardlogin

- Benutzer: `admin`
- Passwort: `admin123`
- Gilt nur, solange das Profil `prod` **nicht** aktiv ist (siehe oben).

## Wichtige Hinweise

- Der lokale Standard bleibt H2, damit Entwicklung schnell und unkompliziert bleibt.
- PostgreSQL wird als echte, persistente Datenbank für die spätere Produktiv- bzw. Container-Umgebung vorbereitet.
- Flyway verwaltet die Schema-Änderungen und macht die Datenbankmigrationen reproduzierbar.
- Server-seitige Zugriffsregeln (`SecurityConfig`) und deren Absicherung für anonym/USER/ADMIN sind
  durch `SecurityAccessTest` automatisiert abgedeckt.
