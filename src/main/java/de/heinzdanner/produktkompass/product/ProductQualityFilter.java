package de.heinzdanner.produktkompass.product;

public enum ProductQualityFilter {
    ALL("Alle Produkte"),
    MISSING_SOURCES("Ohne Quellen"),
    MISSING_REVIEW_DATE("Quelle ohne Prüfdatum"),
    ANY_GAP("Ohne Quellen oder mit fehlendem Prüfdatum");

    private final String displayName;

    ProductQualityFilter(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
