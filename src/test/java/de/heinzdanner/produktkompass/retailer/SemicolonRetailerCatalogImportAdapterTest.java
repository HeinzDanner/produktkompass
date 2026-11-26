package de.heinzdanner.produktkompass.retailer;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemicolonRetailerCatalogImportAdapterTest {
    private final SemicolonRetailerCatalogImportAdapter adapter = new SemicolonRetailerCatalogImportAdapter();

    @Test
    void parsesGermanPricesAndQuotedSemicolons() throws Exception {
        var rows = adapter.parse(new StringReader("""
                id;title;brand;category_path;package_size;price;previous_price;discount_percent;product_url;description;preference_tags;allergens;traces;availability
                p-1;Haferdrink;Marke;Drinks / Hafer;1 l;1,19;1,49;20;https://shop.example/p-1;"Ohne Zucker; gekühlt";vegan,bio;;;Online
                """));
        assertThat(rows).singleElement().satisfies(product -> {
            assertThat(product.externalId()).isEqualTo("p-1");
            assertThat(product.name()).isEqualTo("Haferdrink");
            assertThat(product.price()).isEqualByComparingTo("1.19");
            assertThat(product.previousPrice()).isEqualByComparingTo("1.49");
            assertThat(product.description()).isEqualTo("Ohne Zucker; gekühlt");
            assertThat(product.preferenceTags()).isEqualTo("vegan,bio");
        });
    }

    @Test
    void rejectsDuplicateIdentifiersAndMissingNames() {
        assertThatThrownBy(() -> adapter.parse(new StringReader("""
                id;title
                same;Erstes Produkt
                same;Zweites Produkt
                """))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Doppelte");
        assertThatThrownBy(() -> adapter.parse(new StringReader("id;brand\n1;Marke\n")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> adapter.parse(new StringReader("name;Name\nProdukt;Produkt\n")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Doppelte CSV-Spalte");
        assertThatThrownBy(() -> adapter.parse(new StringReader("name;title\nProdukt;Produkt\n")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Mehrere CSV-Spalten");
    }

    @Test
    void generatesStableFallbackIdsAndRejectsImpossibleDiscounts() throws Exception {
        var first = adapter.parse(new StringReader("name;brand\nHaferdrink;Marke\n")).getFirst();
        var second = adapter.parse(new StringReader("name;brand\nHaferdrink;Marke\n")).getFirst();
        assertThat(first.externalId()).hasSize(64).isEqualTo(second.externalId());
        assertThatThrownBy(() -> adapter.parse(new StringReader("name;discount_percent\nProdukt;150\n")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Rabatt");
    }
}
