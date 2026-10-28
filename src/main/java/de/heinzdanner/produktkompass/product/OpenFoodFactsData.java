package de.heinzdanner.produktkompass.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import org.hibernate.annotations.BatchSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsJson;
import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsMetadata;

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

    @Column(name = "brand_tags", columnDefinition = "TEXT")
    private String brandTags;

    @Column(name = "brand_owner", columnDefinition = "TEXT")
    private String brandOwner;

    @Column(columnDefinition = "TEXT")
    private String categories;

    @Column(name = "category_tags", columnDefinition = "TEXT")
    private String categoryTags;

    @jakarta.persistence.ElementCollection
    @jakarta.persistence.CollectionTable(name = "product_off_categories",
            joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "category_value", nullable = false, columnDefinition = "TEXT")
    private Set<String> categoryEntries = new LinkedHashSet<>();

    @Column(columnDefinition = "TEXT")
    private String quantity;

    @Column(columnDefinition = "TEXT")
    private String packaging;

    @Column(name = "packaging_tags", columnDefinition = "TEXT")
    private String packagingTags;

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

    @Column(name = "label_tags", columnDefinition = "TEXT")
    private String labelTags;

    @ManyToMany
    @JoinTable(name = "product_off_labels",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "label_id"))
    @BatchSize(size = 100)
    private Set<OpenFoodFactsLabel> catalogLabels = new LinkedHashSet<>();

    public Set<OpenFoodFactsLabel> getCatalogLabels() {
        return catalogLabels;
    }

    public void replaceCatalogLabels(Set<OpenFoodFactsLabel> labels) {
        if (!catalogLabels.equals(labels)) {
            catalogLabels.clear();
            catalogLabels.addAll(labels);
        }
    }

    @Column(name = "origin_tags", columnDefinition = "TEXT")
    private String originTags;

    @Column(name = "manufacturing_place_tags", columnDefinition = "TEXT")
    private String manufacturingPlaceTags;

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

    @Column(name = "source_row_json", columnDefinition = "TEXT")
    private String sourceRowJson;

    @Column(name = "merged_source_json", columnDefinition = "TEXT")
    private String mergedSourceJson;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    @Column(name = "serving_size", columnDefinition = "TEXT")
    private String servingSize;

    @Column(name = "nova_group")
    private Integer novaGroup;

    @Column(name = "nutriscore_score", precision = 12, scale = 4)
    private BigDecimal nutriscoreScore;

    @Column(precision = 12, scale = 4)
    private BigDecimal completeness;

    @Column(name = "source_created_at")
    private LocalDateTime sourceCreatedAt;

    public String getServingSize() { return servingSize; }
    public void setServingSize(String value) { servingSize = value; }
    public Integer getNovaGroup() { return novaGroup; }
    public void setNovaGroup(Integer value) { novaGroup = value; }
    public BigDecimal getNutriscoreScore() { return nutriscoreScore; }
    public void setNutriscoreScore(BigDecimal value) { nutriscoreScore = value; }
    public BigDecimal getCompleteness() { return completeness; }
    public void setCompleteness(BigDecimal value) { completeness = value; }
    public LocalDateTime getSourceCreatedAt() { return sourceCreatedAt; }
    public void setSourceCreatedAt(LocalDateTime value) { sourceCreatedAt = value; }

    public String getSourceRowJson() {
        return sourceRowJson;
    }

    public void setSourceRowJson(String sourceRowJson) {
        this.sourceRowJson = sourceRowJson;
    }

    public String getMergedSourceJson() {
        return mergedSourceJson;
    }

    public void setMergedSourceJson(String mergedSourceJson) {
        this.mergedSourceJson = mergedSourceJson;
    }

    public OpenFoodFactsMetadata getMetadata() {
        return metadataJson == null ? OpenFoodFactsMetadata.from(OpenFoodFactsJson.readRow(mergedSourceJson))
                : OpenFoodFactsJson.readMetadata(metadataJson);
    }

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

    public String getBrandTags() {
        return brandTags;
    }

    public void setBrandTags(String brandTags) {
        this.brandTags = brandTags;
    }

    public String getBrandOwner() {
        return brandOwner;
    }

    public void setBrandOwner(String brandOwner) {
        this.brandOwner = brandOwner;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
        refreshCategoryEntries();
    }

    public String getCategoryTags() {
        return categoryTags;
    }

    public void setCategoryTags(String categoryTags) {
        this.categoryTags = categoryTags;
        refreshCategoryEntries();
    }

    private void refreshCategoryEntries() {
        var entries = new LinkedHashSet<>(de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags.split(categories));
        entries.addAll(de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags.split(categoryTags));
        if (!categoryEntries.equals(entries)) {
            categoryEntries.clear();
            categoryEntries.addAll(entries);
        }
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public String getPackagingTags() {
        return packagingTags;
    }

    public void setPackagingTags(String packagingTags) {
        this.packagingTags = packagingTags;
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

    public String getLabelTags() {
        return labelTags;
    }

    public void setLabelTags(String labelTags) {
        this.labelTags = labelTags;
    }

    public String getOriginTags() {
        return originTags;
    }

    public void setOriginTags(String originTags) {
        this.originTags = originTags;
    }

    public String getManufacturingPlaceTags() {
        return manufacturingPlaceTags;
    }

    public void setManufacturingPlaceTags(String manufacturingPlaceTags) {
        this.manufacturingPlaceTags = manufacturingPlaceTags;
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
