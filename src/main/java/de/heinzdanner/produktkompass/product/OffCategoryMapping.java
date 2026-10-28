package de.heinzdanner.produktkompass.product;

import jakarta.persistence.*;

@Entity
@Table(name = "off_category_mappings")
public class OffCategoryMapping {
    @Id
    @Column(name = "off_value", columnDefinition = "TEXT")
    private String offValue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    protected OffCategoryMapping() {
    }

    public OffCategoryMapping(String offValue, Category category) {
        this.offValue = offValue;
        this.category = category;
    }

    public Category getCategory() {
        return category;
    }
}
