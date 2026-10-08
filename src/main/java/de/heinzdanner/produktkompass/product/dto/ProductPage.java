package de.heinzdanner.produktkompass.product.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record ProductPage(List<ProductSummary> content, int page, int size,
                          long totalElements, int totalPages) {
    public static ProductPage from(Page<ProductSummary> results) {
        return new ProductPage(results.getContent(), results.getNumber(), results.getSize(),
                results.getTotalElements(), results.getTotalPages());
    }
}
