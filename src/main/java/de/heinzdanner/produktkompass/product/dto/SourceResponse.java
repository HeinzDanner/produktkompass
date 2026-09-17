package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.product.Source;

import java.time.LocalDateTime;

public record SourceResponse(
        Long id,
        String title,
        String url,
        String type,
        LocalDateTime verifiedAt
) {
    public static SourceResponse fromEntity(Source source) {
        return new SourceResponse(
                source.getId(),
                source.getTitle(),
                source.getUrl(),
                source.getType(),
                source.getVerifiedAt()
        );
    }
}
