package de.heinzdanner.produktkompass.product;

public enum ProductLabel {
    VEGAN("Vegan"),
    VEGETARIAN("Vegetarisch"),
    LACTOSE_FREE("Laktosefrei"),
    GLUTEN_FREE("Glutenfrei"),
    ALCOHOL_FREE("Alkoholfrei"),
    CAFFEINE_FREE("Koffeinfrei"),
    PALM_OIL_FREE("Ohne Palmöl"),
    NO_ADDED_SUGAR("Ohne Zuckerzusatz"),
    BIO("Bio");

    private final String displayName;

    ProductLabel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}