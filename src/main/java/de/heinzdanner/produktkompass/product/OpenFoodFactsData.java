package de.heinzdanner.produktkompass.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_open_food_facts")
public class OpenFoodFactsData {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;

    @NotBlank
    @Size(max = 32)
    @Column(nullable = false, length = 32, unique = true)
    private String barcode;

    @Size(max = 500)
    @Column(name = "product_name", length = 500)
    private String productName;

    @Column(columnDefinition = "TEXT")
    private String brands;

    @Column(columnDefinition = "TEXT")
    private String categories;

    @Column(name = "category_tags", columnDefinition = "TEXT")
    private String categoryTags;

    @Size(max = 255)
    @Column(length = 255)
    private String quantity;

    @Column(name = "ingredients_text", columnDefinition = "TEXT")
    private String ingredientsText;

    @Column(columnDefinition = "TEXT")
    private String allergens;

    @Column(name = "allergen_tags", columnDefinition = "TEXT")
    private String allergenTags;

    @Column(columnDefinition = "TEXT")
    private String traces;

    @Column(name = "trace_tags", columnDefinition = "TEXT")
    private String traceTags;

    @Column(columnDefinition = "TEXT")
    private String countries;

    @Column(name = "country_tags", columnDefinition = "TEXT")
    private String countryTags;

    @Size(max = 20)
    @Column(name = "nutrition_grade", length = 20)
    private String nutritionGrade;

    @Size(max = 35)
    @Column(name = "language_code", length = 35)
    private String languageCode;

    @Column(name = "source_modified_at")
    private LocalDateTime sourceModifiedAt;

    @NotNull
    @Column(name = "last_imported_at", nullable = false)
    private LocalDateTime lastImportedAt;

    @Size(max = 100)
    @Column(name = "source_revision", length = 100)
    private String sourceRevision;

    protected OpenFoodFactsData() {
    }

    public OpenFoodFactsData(Product product, String barcode, String productName, LocalDateTime lastImportedAt) {
        this.product = product;
        this.barcode = barcode;
        this.productName = productName;
        this.lastImportedAt = lastImportedAt;
    }

    public Long getProductId() {
        return productId;
    }

    public Product getProduct() {
        return product;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getBrands() {
        return brands;
    }

    public void setBrands(String brands) {
        this.brands = brands;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public String getCategoryTags() {
        return categoryTags;
    }

    public void setCategoryTags(String categoryTags) {
        this.categoryTags = categoryTags;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getIngredientsText() {
        return ingredientsText;
    }

    public void setIngredientsText(String ingredientsText) {
        this.ingredientsText = ingredientsText;
    }

    public String getAllergens() {
        return allergens;
    }

    public void setAllergens(String allergens) {
        this.allergens = allergens;
    }

    public String getAllergenTags() {
        return allergenTags;
    }

    public void setAllergenTags(String allergenTags) {
        this.allergenTags = allergenTags;
    }

    public String getTraces() {
        return traces;
    }

    public void setTraces(String traces) {
        this.traces = traces;
    }

    public String getTraceTags() {
        return traceTags;
    }

    public void setTraceTags(String traceTags) {
        this.traceTags = traceTags;
    }

    public String getCountries() {
        return countries;
    }

    public void setCountries(String countries) {
        this.countries = countries;
    }

    public String getCountryTags() {
        return countryTags;
    }

    public void setCountryTags(String countryTags) {
        this.countryTags = countryTags;
    }

    public String getNutritionGrade() {
        return nutritionGrade;
    }

    public void setNutritionGrade(String nutritionGrade) {
        this.nutritionGrade = nutritionGrade;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public LocalDateTime getSourceModifiedAt() {
        return sourceModifiedAt;
    }

    public void setSourceModifiedAt(LocalDateTime sourceModifiedAt) {
        this.sourceModifiedAt = sourceModifiedAt;
    }

    public LocalDateTime getLastImportedAt() {
        return lastImportedAt;
    }

    public void setLastImportedAt(LocalDateTime lastImportedAt) {
        this.lastImportedAt = lastImportedAt;
    }

    public String getSourceRevision() {
        return sourceRevision;
    }

    public void setSourceRevision(String sourceRevision) {
        this.sourceRevision = sourceRevision;
    }
}
