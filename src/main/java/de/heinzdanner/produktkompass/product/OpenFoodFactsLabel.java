package de.heinzdanner.produktkompass.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "off_labels")
public class OpenFoodFactsLabel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_tag", nullable = false, unique = true, columnDefinition = "TEXT")
    private String canonicalTag;

    @Column(name = "locale_key", nullable = false, length = 35)
    private String localeKey;

    protected OpenFoodFactsLabel() {
    }

    public OpenFoodFactsLabel(String canonicalTag) {
        if (canonicalTag == null || canonicalTag.isBlank() || canonicalTag.contains(",")) {
            throw new IllegalArgumentException("A single nonblank OFF label tag is required");
        }
        this.canonicalTag = canonicalTag.trim();
        int colon = this.canonicalTag.indexOf(':');
        this.localeKey = colon > 0 && colon <= 35 ? this.canonicalTag.substring(0, colon) : "xx";
    }

    public Long getId() { return id; }
    public String getCanonicalTag() { return canonicalTag; }
    public String getLocaleKey() { return localeKey; }
}
