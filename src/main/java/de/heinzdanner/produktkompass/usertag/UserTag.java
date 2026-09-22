package de.heinzdanner.produktkompass.usertag;

import de.heinzdanner.produktkompass.product.Brand;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Ein persönlicher Tag, den sich ein Benutzer selbst anlegt (z. B. "Lieblingsmarken",
 * "Für Einkaufsliste"), um Produkte und Marken für die eigene Suche zu gruppieren.
 * Jeder Tag gehört genau einem Benutzer ({@link #owner}); die maximale Anzahl pro
 * Benutzer wird zentral in {@link UserTagService} durchgesetzt.
 */
@Entity
@Table(name = "user_tags")
public class UserTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Der Tag-Name darf nicht leer sein.")
    @Size(max = 50, message = "Der Tag-Name darf maximal 50 Zeichen lang sein.")
    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @ManyToMany
    @JoinTable(
            name = "user_tag_products",
            joinColumns = @JoinColumn(name = "user_tag_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private Set<Product> products = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "user_tag_brands",
            joinColumns = @JoinColumn(name = "user_tag_id"),
            inverseJoinColumns = @JoinColumn(name = "brand_id")
    )
    private Set<Brand> brands = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserTag() {
    }

    public UserTag(String name, AppUser owner) {
        this.name = name;
        this.owner = owner;
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

    public AppUser getOwner() {
        return owner;
    }

    public void setOwner(AppUser owner) {
        this.owner = owner;
    }

    public Set<Product> getProducts() {
        return products;
    }

    public void setProducts(Set<Product> products) {
        this.products = products;
    }

    public Set<Brand> getBrands() {
        return brands;
    }

    public void setBrands(Set<Brand> brands) {
        this.brands = brands;
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
        if (!(o instanceof UserTag userTag)) return false;
        return Objects.equals(id, userTag.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
