package de.heinzdanner.produktkompass.openfoodfacts;

import de.heinzdanner.produktkompass.ProduktkompassApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class OpenFoodFactsImportCommand {
    private OpenFoodFactsImportCommand() {
    }

    public static void main(String[] args) {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(ProduktkompassApplication.class)
                .properties("spring.jpa.show-sql=false")
                .profiles("local", "off-import")
                .web(WebApplicationType.NONE)
                .run(args)) {
        }
    }
}
