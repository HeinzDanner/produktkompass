package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.product.Product;

import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BrandResponse brand,
        CompanyResponse company,
        List<CategoryResponse> categories,
        List<SourceResponse> sources,
        List<Long> alternativeProductIds
) {
    public static ProductResponse fromEntity(Product product) {
        BrandResponse brandResponse = product.getBrand() == null
                ? null
                : BrandResponse.fromEntity(product.getBrand());

        CompanyResponse companyResponse = product.getCompany() == null
                ? null
                : CompanyResponse.fromEntity(product.getCompany());

        List<CategoryResponse> categoryResponses = product.getCategories() == null
                ? List.of()
                : product.getCategories().stream()
                        .map(CategoryResponse::fromEntity)
                        .toList();

        List<SourceResponse> sourceResponses = product.getSources() == null
                ? List.of()
                : product.getSources().stream()
                        .map(SourceResponse::fromEntity)
                        .toList();

        List<Long> alternativeProductIds = product.getAlternativeProducts() == null
                ? List.of()
                : product.getAlternativeProducts().stream()
                        .map(Product::getId)
                        .toList();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                brandResponse,
                companyResponse,
                categoryResponses,
                sourceResponses,
                alternativeProductIds
        );
    }
}