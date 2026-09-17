package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.product.Brand;

public record BrandResponse(
        Long id,
        String name,
        String description
) {
    public static BrandResponse fromEntity(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getDescription()
        );
    }
}
