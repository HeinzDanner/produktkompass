package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags.Tag;
import de.heinzdanner.produktkompass.product.dto.OpenFoodFactsView;
import de.heinzdanner.produktkompass.product.dto.ProductDisplay;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDisplayTest {

    private static Product imported(String brandTags, String labelTags, String allergenTags) {
        Product product = new Product("Imported", null);
        OpenFoodFactsData data = new OpenFoodFactsData(product, "8076809513753", "Imported", LocalDateTime.now());
        data.setBrandTags(brandTags);
        data.setLabelTags(labelTags);
        data.setAllergenTags(allergenTags);
        data.setQuantity("380ml");
        data.setNutritionGrade("d");
        data.setPackagingTags("en:glass,de:verre");
        data.setBrandOwner("Owner from OFF");
        product.setOpenFoodFactsData(data);
        return product;
    }

    @Test
    void importedValuesRemainSourceLabelledWithoutMutatingData() {
        Product product = imported("xx:Barilla", "en:no-gluten,en:organic", null);
        ProductDisplay display = ProductDisplay.from(product);
        assertThat(display.offBrandFallback()).containsExactly("Barilla");
        assertThat(display.offBrandsSource()).isEqualTo("brands_tags");
        assertThat(display.offBrandOwnerFallback()).isEqualTo("Owner from OFF");
        assertThat(display.offNutriScoreFallback()).isEqualTo("D");
        assertThat(display.offQuantityFallback()).isEqualTo("380 ml");
        assertThat(display.offPackagingFallback()).extracting(Tag::raw).containsExactly("en:glass", "de:verre");
        assertThat(display.offLabels()).extracting(Tag::display).containsExactly("Ohne Gluten", "Bio");
        assertThat(display.editorialLabels()).isEmpty();
        assertThat(display.anyLabels()).isTrue();
        assertThat(display.offGlutenHint().conflict()).isFalse();
        assertThat(display.offGlutenHint().summary()).contains("Ohne Gluten", "ungeprüft");
        assertThat(OpenFoodFactsView.from(product.getOpenFoodFactsData()).provenance())
                .isEqualTo("OPEN_FOOD_FACTS_UNVERIFIED");
    }

    @Test
    void distinctValuesAppearTogetherAndNoBrandOrCompanyIsDerived() {
        Product product = imported("xx:Other", "en:vegan", null);
        product.setBrand(new Brand("Editorial", null));
        product.setCompany(new Company("Editorial company", null, null, null));
        product.setNutritionGrade(NutritionGrade.B);
        product.setPackageSize("400 g");
        product.setPackagingType(PackagingType.GLASS);
        product.setContainsGluten(IngredientStatus.CONTAINS);
        product.setLabels(Set.of(ProductLabel.VEGETARIAN));
        ProductDisplay display = ProductDisplay.from(product);
        assertThat(display.offBrandFallback()).containsExactly("Other");
        assertThat(display.offBrandOwnerFallback()).isEqualTo("Owner from OFF");
        assertThat(display.offNutriScoreFallback()).isEqualTo("D");
        assertThat(display.offQuantityFallback()).isEqualTo("380 ml");
        assertThat(display.offPackagingFallback()).extracting(Tag::raw).containsExactly("de:verre");
        assertThat(display.editorialLabels()).containsExactly(ProductLabel.VEGETARIAN);
        assertThat(display.offLabels()).extracting(Tag::raw).containsExactly("en:vegan");
        assertThat(product.getBrand().getName()).isEqualTo("Editorial");
        assertThat(product.getLabels()).containsExactly(ProductLabel.VEGETARIAN);
    }

    @Test
    void identicalValuesAndBasicLabelAliasesAreShownOnceButSpecificSealsStay() {
        Product product = imported("xx: Same ,xx:Other", "en:vegan,en:organic,en:eu-organic,en:no-gluten,en:gluten-free", null);
        product.setBrand(new Brand("same", null));
        product.setCompany(new Company("Owner from OFF", null, null, null));
        product.setNutritionGrade(NutritionGrade.D);
        product.setPackageSize("380 ml");
        product.setLabels(Set.of(ProductLabel.VEGAN, ProductLabel.BIO));
        var display = ProductDisplay.from(product);
        assertThat(display.offBrandFallback()).containsExactly("Other");
        assertThat(display.offBrandOwnerFallback()).isNull();
        assertThat(display.offNutriScoreFallback()).isNull();
        assertThat(display.offQuantityFallback()).isNull();
        assertThat(display.offProductName()).isNull();
        assertThat(display.offLabels()).extracting(Tag::raw).containsExactly("en:eu-organic", "en:no-gluten");
        assertThat(product.getOpenFoodFactsData().getLabelTags()).contains("en:vegan", "en:gluten-free");
    }

    @Test
    void distinctNamesAndCategoriesRemainVisibleWhileEqualCategoriesAreDeduplicated() {
        Product product = imported(null, null, null);
        product.setCategories(Set.of(new Category("Getränke", null)));
        product.getOpenFoodFactsData().setProductName("Other name");
        product.getOpenFoodFactsData().setCategories("getränke, Pflanzendrinks");
        var display = ProductDisplay.from(product);
        assertThat(display.offProductName()).isEqualTo("Other name");
        assertThat(display.offCategories()).containsExactly("Pflanzendrinks");
        assertThat(product.getName()).isEqualTo("Imported");
        assertThat(product.getCategories()).extracting(Category::getName).containsExactly("Getränke");
    }

    @Test
    void reportsGlutenConflictButNotMilkVersusLactoseFree() {
        Product product = imported("xx:A", "en:no-gluten,en:no-lactose", "en:gluten,en:milk");
        ProductDisplay display = ProductDisplay.from(product);
        assertThat(display.offGlutenHint().conflict()).isTrue();
        assertThat(display.offGlutenHint().summary()).startsWith("Widersprüchlich laut OFF");
        assertThat(display.offLactoseHint().conflict()).isFalse();
        assertThat(display.offLactoseHint().declaredAllergen()).isTrue();
        assertThat(display.offLactoseHint().summary()).contains("Ohne Laktose", "Allergen laut OFF: Milch");
    }

    @Test
    void missingValuesStayMissingAndAreNotTurnedIntoFreeFromClaims() {
        Product product = new Product("Empty import", null);
        product.setOpenFoodFactsData(new OpenFoodFactsData(product, "1", "Empty import", LocalDateTime.now()));
        ProductDisplay display = ProductDisplay.from(product);
        assertThat(display.offBrandFallback()).isEmpty();
        assertThat(display.offBrandsSource()).isNull();
        assertThat(display.offLabels()).isEmpty();
        assertThat(display.offNutriScoreFallback()).isNull();
        assertThat(display.offQuantityFallback()).isNull();
        assertThat(display.offGlutenHint()).isNull();
        assertThat(display.offLactoseHint()).isNull();
        assertThat(display.anyLabels()).isFalse();

        Product editorialOnly = new Product("Editorial only", null);
        assertThat(ProductDisplay.from(editorialOnly).offLabels()).isEmpty();
    }
}