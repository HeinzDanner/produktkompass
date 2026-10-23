package de.heinzdanner.produktkompass.product.dto;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsMetadata;
import de.heinzdanner.produktkompass.product.OpenFoodFactsData;

import java.util.List;
import java.util.Map;

public record ProductImage(String cardUrl, String detailUrl, String sourceUrl) {
    public static ProductImage from(OpenFoodFactsData data) {
        if (data == null) return null;
        Map<String, String> images = data.getMetadata().imageUrls();
        String front = first(images, List.of("imageFrontUrl", "image_front_url", "image_front_small_url"));
        String detail = front != null ? front : first(images, List.of("imageUrl", "image_url", "image_small_url"));
        String card = front != null ? first(images, List.of("image_front_small_url", "imageFrontUrl", "image_front_url"))
                : first(images, List.of("image_small_url", "imageUrl", "image_url"));
        if (detail == null) return null;
        return new ProductImage(card, detail, "https://world.openfoodfacts.org/product/" + data.getBarcode());
    }

    private static String first(Map<String, String> images, List<String> keys) {
        return keys.stream().map(images::get).filter(OpenFoodFactsMetadata::isDisplayImageUrl).findFirst().orElse(null);
    }
}
