package de.heinzdanner.produktkompass.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Der Firmenname darf nicht leer sein.")
    @Size(max = 255, message = "Der Firmenname darf maximal 255 Zeichen lang sein.")
    @Column(nullable = false, unique = true, length = 255)
    private String name;

    @Size(max = 500, message = "Die Website darf maximal 500 Zeichen lang sein.")
    @Column(length = 500)
    private String website;

    @Size(max = 255, message = "Das Land darf maximal 255 Zeichen lang sein.")
    @Column(length = 255)
    private String country;

    @Size(max = 1000, message = "Die Beschreibung darf maximal 1000 Zeichen lang sein.")
    @Column(length = 1000)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Company() {
    }

    public Company(String name, String website, String country, String description) {
        this.name = name;
        this.website = website;
        this.country = country;
        this.description = description;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Company company)) return false;
        return Objects.equals(id, company.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
