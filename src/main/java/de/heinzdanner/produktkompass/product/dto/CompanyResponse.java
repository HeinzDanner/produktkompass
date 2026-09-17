package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.product.Company;

public record CompanyResponse(
        Long id,
        String name,
        String website,
        String country,
        String description
) {
    public static CompanyResponse fromEntity(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getWebsite(),
                company.getCountry(),
                company.getDescription()
        );
    }
}
