package de.heinzdanner.produktkompass.openfoodfacts;

import de.heinzdanner.produktkompass.openfoodfacts.OpenFoodFactsTags.Tag;
import de.heinzdanner.produktkompass.product.ProductLabel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFoodFactsTagsTest {

    @Test
    void stripsOnlyValidLanguagePrefixes() {
        assertThat(OpenFoodFactsTags.stripLanguagePrefix("xx:Barilla")).isEqualTo("Barilla");
        assertThat(OpenFoodFactsTags.stripLanguagePrefix("de:Glass widely recycled")).isEqualTo("Glass widely recycled");
        assertThat(OpenFoodFactsTags.stripLanguagePrefix("fw:Céleri")).isEqualTo("fw:Céleri");
        assertThat(OpenFoodFactsTags.stripLanguagePrefix("Brand: Special")).isEqualTo("Brand: Special");
        assertThat(OpenFoodFactsTags.stripLanguagePrefix("en:")).isEqualTo("en:");
    }

    @Test
    void prefersBrandTextAndOtherwiseShowsStoredTagsWithoutReconstructingSlugs() {
        assertThat(OpenFoodFactsTags.brands("Barilla, Barilla G. e R.", "xx:barilla"))
                .containsExactly("Barilla", "Barilla G. e R.");
        assertThat(OpenFoodFactsTags.brandsSource("Barilla", "xx:barilla")).isEqualTo("brands");
        assertThat(OpenFoodFactsTags.brands(null, "xx:hela,xx:HELA Gewürz Ketchup,xx:K-Classic,xx:Hela"))
                .containsExactly("hela", "HELA Gewürz Ketchup", "K-Classic");
        assertThat(OpenFoodFactsTags.brands(" ", "xx:la-baleine,xx:Leggo's")).containsExactly("la-baleine", "Leggo's");
        assertThat(OpenFoodFactsTags.brandsSource(null, "xx:la-baleine")).isEqualTo("brands_tags");
        assertThat(OpenFoodFactsTags.brands(null, null)).isEmpty();
        assertThat(OpenFoodFactsTags.brandsSource(null, " , ")).isNull();
    }

    @Test
    void translatesOnlyKnownExactLabelTagsAndKeepsUnknownReadable() {
        assertThat(OpenFoodFactsTags.labels("en:no-gluten,en:no-preservatives,en:organic,en:vegan,en:vegetarian,fr:Adapté au moulin,en:no-gluten-ish"))
                .containsExactly(
                        new Tag("en:no-gluten", "Ohne Gluten", true),
                        new Tag("en:no-preservatives", "Ohne Konservierungsstoffe", true),
                        new Tag("en:organic", "Bio", true),
                        new Tag("en:vegan", "Vegan", true),
                        new Tag("en:vegetarian", "Vegetarisch", true),
                        new Tag("fr:Adapté au moulin", "Adapté au moulin", false),
                        new Tag("en:no-gluten-ish", "no gluten ish", false));
        assertThat(OpenFoodFactsTags.labels(null)).isEmpty();
    }

    @Test
    void preservesExactMultilingualIdentitiesWhileTrimmingAndHumanizingDisplayOnly() {
        assertThat(OpenFoodFactsTags.split(" en:organic, ,en:organic,de:organic,fr:bio,EN:organic, "))
                .containsExactly("en:organic", "de:organic", "fr:bio", "EN:organic");
        assertThat(OpenFoodFactsTags.labels("zz:unknown-tag,fr:commerce_equitable"))
                .containsExactly(new Tag("zz:unknown-tag", "zz:unknown tag", false),
                        new Tag("fr:commerce_equitable", "commerce equitable", false));
    }

    @Test
    void keepsAmbiguousPackagingEntriesSeparate() {
        assertThat(OpenFoodFactsTags.packaging("en:glass,de:verre,en:jar,en:Paper or cardboard"))
                .extracting(Tag::display)
                .containsExactly("Glas (Material)", "verre", "jar", "Paper or cardboard");
    }

    @Test
    void normalizesQuantityAndNutriScoreConservatively() {
        assertThat(OpenFoodFactsTags.quantity("380ml")).isEqualTo("380 ml");
        assertThat(OpenFoodFactsTags.quantity("1L")).isEqualTo("1 l");
        assertThat(OpenFoodFactsTags.quantity("200.0 g")).isEqualTo("200.0 g");
        assertThat(OpenFoodFactsTags.quantity("500 g ℮")).isEqualTo("500 g ℮");
        assertThat(OpenFoodFactsTags.quantity("3pcs")).isEqualTo("3pcs");
        assertThat(OpenFoodFactsTags.quantity(" ")).isNull();
        assertThat(OpenFoodFactsTags.nutriScoreText("d")).isEqualTo("D");
        assertThat(OpenFoodFactsTags.nutriScoreText("not-applicable")).isEqualTo("nicht anwendbar");
        assertThat(OpenFoodFactsTags.nutriScoreText("unknown")).isNull();
        assertThat(OpenFoodFactsTags.nutriScoreGrade("not-applicable")).isNull();
    }

    @Test
    void importerReadsNutriScoreFromCurrentExportColumn() {
        var record = OpenFoodFactsRecord.from(java.util.Map.of("code", "123", "product_name", "X",
                "countries_tags", "en:germany", "off:nutriscore_grade", "d"));
        assertThat(record.nutritionGrade()).isEqualTo("d");
    }

    @Test
    void mapsHumanSearchTermsAndEditorialEquivalentsOnlyToExactKnownTags() {
        assertThat(OpenFoodFactsTags.labelTagsMatchingText("glutenfrei")).contains("en:no-gluten")
                .doesNotContain("en:vegan");
        assertThat(OpenFoodFactsTags.labelTagsMatchingText("Ohne Gluten")).contains("en:no-gluten");
        assertThat(OpenFoodFactsTags.labelTagsMatchingText("bio")).contains("en:organic", "en:eu-organic");
        assertThat(OpenFoodFactsTags.labelTagsMatchingText("no")).isEmpty();
        assertThat(OpenFoodFactsTags.equivalentLabelTags(ProductLabel.GLUTEN_FREE)).contains("en:no-gluten");
        assertThat(OpenFoodFactsTags.equivalentLabelTags(ProductLabel.CAFFEINE_FREE))
                .containsExactlyInAnyOrder("en:caffeine-free", "en:no-caffeine");
        assertThat(OpenFoodFactsTags.equivalentLabelTags(ProductLabel.ALCOHOL_FREE))
                .containsExactlyInAnyOrder("en:alcohol-free", "en:no-alcohol");
        assertThat(OpenFoodFactsTags.equivalentProductLabels(OpenFoodFactsTags.labels("en:vegan,en:organic,en:green-dot")))
                .containsExactlyInAnyOrder(ProductLabel.VEGAN, ProductLabel.BIO);
    }
}