package de.heinzdanner.produktkompass.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank(message = "Der Firmenname darf nicht leer sein.")
        @Size(max = 255, message = "Der Firmenname darf maximal 255 Zeichen lang sein.")
        String name,

        @Size(max = 500, message = "Die Website darf maximal 500 Zeichen lang sein.")
        String website,

        @Size(max = 255, message = "Das Land darf maximal 255 Zeichen lang sein.")
        String country,

        @Size(max = 1000, message = "Die Beschreibung darf maximal 1000 Zeichen lang sein.")
        String description
) {
}
