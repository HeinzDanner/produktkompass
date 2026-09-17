package de.heinzdanner.produktkompass.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandRequest(
        @NotBlank(message = "Der Markenname darf nicht leer sein.")
        @Size(max = 255, message = "Der Markenname darf maximal 255 Zeichen lang sein.")
        String name,

        @Size(max = 1000, message = "Die Beschreibung darf maximal 1000 Zeichen lang sein.")
        String description
) {
}
