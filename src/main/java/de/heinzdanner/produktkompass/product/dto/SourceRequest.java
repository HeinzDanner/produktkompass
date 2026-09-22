package de.heinzdanner.produktkompass.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public record SourceRequest(
        @NotBlank(message = "Der Quellenname darf nicht leer sein.")
        @Size(max = 255, message = "Der Quellenname darf maximal 255 Zeichen lang sein.")
        String title,

        @NotBlank(message = "Die URL darf nicht leer sein.")
        @Size(max = 1000, message = "Die URL darf maximal 1000 Zeichen lang sein.")
        String url,

        @Size(max = 255, message = "Der Typ darf maximal 255 Zeichen lang sein.")
        String type,

        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        LocalDateTime verifiedAt
) {
}
