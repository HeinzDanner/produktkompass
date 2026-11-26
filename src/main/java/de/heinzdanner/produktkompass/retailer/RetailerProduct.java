package de.heinzdanner.produktkompass.retailer;

import java.math.BigDecimal;

public record RetailerProduct(long id, String externalId, String name, String brand, String categoryPath,
                              String packageSize, BigDecimal price, BigDecimal previousPrice,
                              BigDecimal discountPercent, String productUrl, String imageUrl,
                              String description, String preferenceTags, String allergens,
                              String traces, String availability) {
}
