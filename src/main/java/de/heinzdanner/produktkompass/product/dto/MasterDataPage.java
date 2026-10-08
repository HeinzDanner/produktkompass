package de.heinzdanner.produktkompass.product.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record MasterDataPage<T>(List<T> content, int page, int size, long totalElements,
                                int totalPages, boolean hasNext, boolean hasPrevious) {
    public static <T> MasterDataPage<T> from(Page<T> page) {
        return new MasterDataPage<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext(), page.hasPrevious());
    }
}
