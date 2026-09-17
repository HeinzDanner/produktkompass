package de.heinzdanner.produktkompass.product;

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

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Der Produktname darf nicht leer sein.")
    @Size(max = 255, message = "Der Produktname darf maximal 255 Zeichen lang sein.")
    @Column(nullable = false, length = 255)
    private String name;

    @Size(max = 1000, message = "Die Beschreibung darf maximal 1000 Zeichen lang sein.")
    @Column(length = 1000)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToMany
    @JoinTable(
            name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "product_sources",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "source_id")
    )
    private Set<Source> sources = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "product_alternatives",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "alternative_product_id")
    )
    private Set<Product> alternativeProducts = new HashSet<>();

    protected Product() {
    }

    public Product(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Product(String name, String description, Brand brand) {
        this.name = name;
        this.description = description;
        this.brand = brand;
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

    public Brand getBrand() {
        return brand;
    }

    public void setBrand(Brand brand) {
        this.brand = brand;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public void setCategories(Set<Category> categories) {
        this.categories = categories;
    }

    public void addCategory(Category category) {
        this.categories.add(category);
    }

    public Set<Source> getSources() {
        return sources;
    }

    public void setSources(Set<Source> sources) {
        this.sources = sources;
    }

    public void addSource(Source source) {
        this.sources.add(source);
    }

    public Set<Product> getAlternativeProducts() {
        return alternativeProducts;
    }

    public void setAlternativeProducts(Set<Product> alternativeProducts) {
        this.alternativeProducts = alternativeProducts;
    }

    public void addAlternativeProduct(Product product) {
        this.alternativeProducts.add(product);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product product)) return false;
        return Objects.equals(id, product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}