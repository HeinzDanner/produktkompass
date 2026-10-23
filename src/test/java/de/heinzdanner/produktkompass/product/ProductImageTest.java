package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsJson;
import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsMetadata;
import de.heinzdanner.produktkompass.product.dto.ProductImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductImageTest {
    @ParameterizedTest
    @ValueSource(strings = {"http://images.openfoodfacts.org/images/products/a.jpg",
            "https://images.openfoodfacts.org.evil.com/images/products/a.jpg",
            "https://user@images.openfoodfacts.org/images/products/a.jpg",
            "https://images.openfoodfacts.org:8443/images/products/a.jpg",
            "//images.openfoodfacts.org/images/products/a.jpg", "javascript:alert(1)",
            "https://example.com/a.jpg", "https://images.openfoodfacts.org/not-a-product.jpg"})
    void rejectsUnsafeOrUnapprovedImageAddresses(String url) {
        assertThat(OpenFoodFactsMetadata.isDisplayImageUrl(url)).isFalse();
    }

    @Test
    void selectsFrontImagesInsteadOfIngredientImagesAndHandlesMissingSmallImage() {
        var data = new OpenFoodFactsData(new Product("Name", null), "000123", "Name",
                LocalDateTime.now());
        String front = "https://images.openfoodfacts.org/images/products/front.jpg";
        data.setMergedSourceJson(OpenFoodFactsJson.write(Map.of("image_front_url", front,
                "image_url", "https://images.openfoodfacts.org/images/products/other.jpg",
                "image_small_url", "https://images.openfoodfacts.org/images/products/other-small.jpg",
                "image_ingredients_url", "https://images.openfoodfacts.org/images/products/ingredients.jpg")));
        assertThat(ProductImage.from(data).detailUrl()).isEqualTo(front);
        assertThat(ProductImage.from(data).cardUrl()).isEqualTo(front);
        data.setMergedSourceJson(OpenFoodFactsJson.write(Map.of("image_ingredients_url", front)));
        assertThat(ProductImage.from(data)).isNull();
        assertThat(ProductImage.from(null)).isNull();
    }
}
