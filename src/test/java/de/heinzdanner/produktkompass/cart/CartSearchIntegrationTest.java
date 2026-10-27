package de.heinzdanner.produktkompass.cart;

import de.heinzdanner.produktkompass.product.*;
import de.heinzdanner.produktkompass.user.AppUserRepository;
import de.heinzdanner.produktkompass.usertag.UserTag;
import de.heinzdanner.produktkompass.usertag.UserTagRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class CartSearchIntegrationTest {
    @Autowired CartSearchService search;
    @Autowired CartService cart;
    @Autowired CartSearchPreferences preferences;
    @Autowired ProductRepository products;
    @Autowired BrandRepository brands;
    @Autowired OpenFoodFactsLabelRepository offLabels;
    @Autowired UserTagRepository tags;
    @Autowired AppUserRepository users;
    @Autowired EntityManager em;
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    Product neutral;
    Product own;
    Product off;
    Product both;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        neutral = products.save(new Product("CartSearch A neutral", null));
        own = products.save(new Product("CartSearch B own", null));
        own.setLabels(Set.of(ProductLabel.BIO));
        off = products.saveAndFlush(new Product("CartSearch C OFF", null));
        var data = new OpenFoodFactsData(off, "9898989898911", off.getName(), LocalDateTime.now());
        data.setLabelTags("en:organic,en:halal");
        off.setOpenFoodFactsData(data);
        em.persist(data);
        both = products.save(new Product("CartSearch D two", null));
        both.setLabels(Set.of(ProductLabel.BIO, ProductLabel.VEGAN));
        for (String raw : List.of("en:organic", "en:halal")) {
            if (offLabels.findAllByCanonicalTagIn(List.of(raw)).isEmpty()) offLabels.save(new OpenFoodFactsLabel(raw));
        }
        em.flush();
    }

    @Test
    void boostsAreNotFiltersAndExclusionsNeverTouchCart() {
        cart.change("max", own.getId(), CartService.Action.INCREMENT);
        var ranked = search.search("max", "CartSearch", List.of("label:BIO", "label:VEGAN"), List.of(), 0);
        assertThat(ranked.page().getContent()).extracting("id")
                .containsExactly(both.getId(), own.getId(), off.getId(), neutral.getId());
        var excluded = search.search("max", "CartSearch", List.of(), List.of("label:BIO"), 0);
        assertThat(excluded.page().getContent()).extracting("id").containsExactly(neutral.getId());
        assertThat(cart.quantities("max")).containsEntry(own.getId(), 1);
        assertThat(search.search("max", "CartSearch", List.of(), List.of(), 0).page().getTotalElements()).isEqualTo(4);
    }

    @Test
    void offTagsAreExactCanonicalAndDoNotExcludeProductsWithoutOffData() {
        var result = search.search("max", "CartSearch", List.of("off:en:organic"), List.of("off:en:halal"), 0);
        assertThat(result.preferred()).containsExactly("label:BIO");
        assertThat(result.page().getContent()).extracting("id")
                .containsExactly(own.getId(), both.getId(), neutral.getId());
        assertThat(result.tags()).filteredOn(tag -> tag.tag().key().equals("label:BIO")).hasSize(1);
        off.getOpenFoodFactsData().setLabelTags("en:halal-extra");
        em.flush();
        assertThat(search.search("max", "CartSearch", List.of(), List.of("off:en:halal"), 0)
                .page().getTotalElements()).isEqualTo(4);
    }

    @Test
    void personalTagsIncludeBrandAssignmentsAndAreOwnerScoped() throws Exception {
        var brand = brands.save(new Brand("CartSearch brand", null));
        neutral.setBrand(brand);
        var ownTag = new UserTag("CartSearch personal", users.findByUsername("max").orElseThrow());
        ownTag.setBrands(Set.of(brand));
        ownTag.setProducts(Set.of(own));
        tags.saveAndFlush(ownTag);
        String key = "personal:" + ownTag.getId();
        assertThat(search.search("max", "CartSearch", List.of(), List.of(key), 0).page().getContent())
                .extracting("id").containsExactly(off.getId(), both.getId());
        assertThat(search.search("max", "CartSearch", List.of(key), List.of(), 0).page().getContent())
                .extracting("id").containsExactly(neutral.getId(), own.getId(), off.getId(), both.getId());
        mvc.perform(get("/cart").with(user("admin")).param("excluded", key)).andExpect(status().isBadRequest());
        mvc.perform(get("/cart").with(user("max")).param("preferred", key).param("excluded", key))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/cart").with(user("max")).param("excluded", "personal:9223372036854775807"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rendersSearchAndSupportsPaginationAndNonJavaScriptTagSelections() throws Exception {
        for (int i = 0; i < 13; i++) products.save(new Product("CartSearch extra " + i, null));
        em.flush();
        var first = search.search("max", "CartSearch", List.of("label:BIO"), List.of(), 0).page();
        var second = search.search("max", "CartSearch", List.of("label:BIO"), List.of(), 1).page();
        assertThat(first.getTotalElements()).isEqualTo(17);
        assertThat(first.getContent()).hasSize(12);
        assertThat(second.getContent()).hasSize(5);
        assertThat(first.getContent()).doesNotContainAnyElementsOf(second.getContent());
        mvc.perform(get("/cart").with(user("max")).param("query", "CartSearch")
                        .param("tag.label:BIO", "excluded"))
                .andExpect(status().isOk()).andExpect(model().attribute("excluded", List.of("label:BIO")))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s).*id=\"cart-search-form\".*?</form>\\s*<details class=\"cart-tag-search\".*")))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s).*</section>\\s*<section class=\"box cart-results\".*")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cart-tag-query")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/cart-search.js")));
        mvc.perform(get("/cart").with(user("max")).param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void wordsCanMatchDifferentFieldsAndEveryWordIsRequired() {
        var chocolate = products.save(new Product("Mehrwort Schokolade", null));
        chocolate.setLabels(Set.of(ProductLabel.BIO));
        var brand = brands.save(new Brand("Mehrwort Kakaohaus", null));
        chocolate.setBrand(brand);
        var notOrganic = products.save(new Product("Mehrwort Schokolade dunkel", null));
        notOrganic.setBrand(brand);
        em.flush();

        assertThat(search.search("max", "  BIO   Schokolade Mehrwort  ", List.of(), List.of(), 0)
                .page().getContent()).extracting("id").containsExactly(chocolate.getId());
        assertThat(search.search("max", "Schokolade Kakaohaus", List.of(), List.of(), 0)
                .page().getContent()).extracting("id").containsExactly(chocolate.getId(), notOrganic.getId());
        assertThat(search.search("max", "Schokolade nichtvorhanden", List.of(), List.of(), 0)
                .page().getContent()).isEmpty();
        assertThat(search.search("max", "organic CartSearch", List.of(), List.of(), 0)
                .page().getContent()).extracting("id").contains(off.getId());
    }

    @Test
    void ranksExactThenPrefixThenNameThenMetadataWithinTagPriority() {
        var metadata = products.save(new Product("A Relevanz", "Relevanz Schokolade"));
        var contained = products.save(new Product("B Relevanz Schokolade", null));
        var prefix = products.save(new Product("Relevanz Schokolade dunkel", null));
        var exact = products.save(new Product("Relevanz Schokolade", null));
        var split = products.save(new Product("C Relevanz", "Schokolade"));
        var imported = products.saveAndFlush(new Product("D importiert", null));
        var data = new OpenFoodFactsData(imported, "9898989898922",
                "Relevanz Schokolade", LocalDateTime.now());
        imported.setOpenFoodFactsData(data);
        em.persist(data);
        em.flush();

        assertThat(search.search("max", "Relevanz Schokolade", List.of(), List.of(), 0)
                .page().getContent()).extracting("id")
                .containsExactly(imported.getId(), exact.getId(), prefix.getId(), contained.getId(),
                        metadata.getId(), split.getId());
        metadata.setLabels(Set.of(ProductLabel.BIO));
        em.flush();
        assertThat(search.search("max", "Relevanz Schokolade", List.of("label:BIO"), List.of(), 0)
                .page().getContent()).extracting("id").startsWith(metadata.getId(), imported.getId(), exact.getId());
    }

    @Test
    void treatsSqlWildcardsLiterallyAndKeepsEmptySearchStable() {
        var literal = products.save(new Product("Relevanz 50% Kakao_A", null));
        products.save(new Product("Relevanz 500 KakaoBA", null));
        em.flush();
        assertThat(search.search("max", "Relevanz 50% Kakao_", List.of(), List.of(), 0)
                .page().getContent()).extracting("id").containsExactly(literal.getId());
        assertThat(search.search("max", "   ", List.of(), List.of("label:BIO"), 0).page()
                .getContent()).extracting("id").contains(neutral.getId()).doesNotContain(own.getId(), off.getId());
    }

    @Test
    void fragmentContainsOnlyResultsAndPreservesFiltersAndAuthorization() throws Exception {
        for (int i = 0; i < 13; i++) products.save(new Product("CartSearch fragment " + i, null));
        em.flush();
        var full = search.search("max", "CartSearch", List.of("off:en:organic"), List.of("off:en:halal"), 0);
        var fragment = search.search("max", "CartSearch", List.of("off:en:organic"), List.of("off:en:halal"), 0, false);
        assertThat(fragment.page().getContent()).isEqualTo(full.page().getContent());
        assertThat(fragment.page().getTotalElements()).isEqualTo(full.page().getTotalElements());
        assertThat(fragment.tags()).isEmpty();
        assertThat(fragment.preferred()).containsExactly("label:BIO");
        mvc.perform(get("/cart/results").with(user("max")).param("query", "CartSearch")
                        .param("preferred", "off:en:organic").param("excluded", "off:en:halal"))
                .andExpect(status().isOk()).andExpect(view().name("cart :: results"))
                .andExpect(model().attributeDoesNotExist("items"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cart-results-heading")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("preferred=label:BIO")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("cart-search-form"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<html"))));
        var foreign = tags.saveAndFlush(new UserTag("Foreign", users.findByUsername("admin").orElseThrow()));
        mvc.perform(get("/cart/results").with(user("max")).param("preferred", "personal:" + foreign.getId()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/cart/results").with(user("max")).param("excluded", "off:en:unknown-tag-fixture"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/cart/results").with(user("max")).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/cart/results")).andExpect(status().is3xxRedirection());
    }

    @Test
    void persistsCanonicalSelectionAcrossSessionsWithoutTouchingCartOrOtherUsers() throws Exception {
        cart.change("max", own.getId(), CartService.Action.INCREMENT);
        mvc.perform(post("/cart/preferences").with(user("max")).with(csrf())
                        .param("preferred", "off:en:organic").param("excluded", "off:en:halal"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferred[0]").value("label:BIO"));
        mvc.perform(get("/cart").with(user("max")).session(new org.springframework.mock.web.MockHttpSession()))
                .andExpect(status().isOk()).andExpect(model().attribute("preferred", List.of("label:BIO")))
                .andExpect(model().attribute("excluded", List.of("off:en:halal")));
        mvc.perform(get("/cart/results").with(user("max")).param("query", "CartSearch"))
                .andExpect(status().isOk()).andExpect(model().attribute("preferred", List.of("label:BIO")));
        assertThat(preferences.load("admin").preferred()).isEmpty();
        assertThat(cart.quantities("max")).containsEntry(own.getId(), 1);
        mvc.perform(get("/cart/results").with(user("max")).param("tagsExplicit", "true"))
                .andExpect(model().attribute("preferred", List.of()));
        assertThat(preferences.load("max").preferred()).containsExactly("label:BIO");
        mvc.perform(post("/cart/preferences").with(user("max")).with(csrf()))
                .andExpect(status().isOk());
        assertThat(preferences.load("max").preferred()).isEmpty();
        assertThat(preferences.load("max").excluded()).isEmpty();
        assertThat(cart.quantities("max")).containsEntry(own.getId(), 1);
    }

    @Test
    void preferenceWritesRequireCsrfOwnershipAndValidStates() throws Exception {
        mvc.perform(post("/cart/preferences").with(user("max")).param("preferred", "label:BIO"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/cart/preferences").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/cart/preferences").with(user("max")).with(csrf())
                        .param("preferred", "label:BIO").param("excluded", "label:BIO"))
                .andExpect(status().isBadRequest());
        var foreign = tags.saveAndFlush(new UserTag("Foreign saved", users.findByUsername("admin").orElseThrow()));
        mvc.perform(post("/cart/preferences").with(user("max")).with(csrf())
                        .param("preferred", "personal:" + foreign.getId()))
                .andExpect(status().isBadRequest());
        assertThat(preferences.load("max").preferred()).isEmpty();
    }

    @Test
    void supportsPersistentNonJavaScriptFormsResetAndDeletedPersonalTags() throws Exception {
        mvc.perform(post("/cart").with(user("max")).with(csrf())
                        .param("query", "Bio Schokolade").param("tagsExplicit", "true")
                        .param("tag.label:BIO", "preferred").param("tag.label:VEGAN", "excluded"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart?query=Bio%20Schokolade"));
        assertThat(preferences.load("max").preferred()).containsExactly("label:BIO");
        mvc.perform(post("/cart/preferences/reset").with(user("max")).with(csrf())
                        .param("query", "Bio Schokolade"))
                .andExpect(redirectedUrl("/cart?query=Bio%20Schokolade"));
        assertThat(preferences.load("max").preferred()).isEmpty();
        assertThat(preferences.load("max").excluded()).isEmpty();
        var personal = tags.saveAndFlush(new UserTag("Deleted saved", users.findByUsername("max").orElseThrow()));
        mvc.perform(post("/cart/preferences").with(user("max")).with(csrf())
                        .param("preferred", "personal:" + personal.getId()))
                .andExpect(status().isOk());
        tags.delete(personal);
        em.flush();
        mvc.perform(get("/cart").with(user("max"))).andExpect(status().isOk())
                .andExpect(model().attribute("preferred", List.of()));
    }

    @Test
    void keepsSearchTextDuringSessionAndHidesEmptyResults() throws Exception {
        var session = new org.springframework.mock.web.MockHttpSession();
        mvc.perform(get("/cart/results").with(user("max")).session(session)
                        .param("query", "nichtvorhanden-eindeutiger-test"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s)<section[^>]*class=\"box cart-results\"[^>]*hidden[^>]*>.*")));
        mvc.perform(get("/cart").with(user("max")).session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedQuery", "nichtvorhanden-eindeutiger-test"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Tags anwenden"))));
        mvc.perform(get("/cart").with(user("max")).session(session).param("query", ""))
                .andExpect(model().attribute("selectedQuery", ""));
        mvc.perform(get("/cart").with(user("max")).session(session))
                .andExpect(model().attribute("selectedQuery", ""));
        mvc.perform(get("/cart").with(user("max")).session(new org.springframework.mock.web.MockHttpSession()))
                .andExpect(model().attribute("selectedQuery", ""));
    }

    @Test
    void mixedCaseOffTagsWorkInFullSearchFragmentsAndSavedPreferences() throws Exception {
        offLabels.saveAndFlush(new OpenFoodFactsLabel("fr:EnThAlT-MiLcH"));
        off.getOpenFoodFactsData().setLabelTags("fr:EnThAlT-MiLcH");
        em.flush();
        String key = "off:fr:enthalt-milch";
        var full = search.search("max", "CartSearch", List.of(), List.of(key), 0);
        var fragment = search.search("max", "CartSearch", List.of(), List.of(key), 0, false);
        assertThat(full.page().getContent()).extracting("id")
                .containsExactly(neutral.getId(), own.getId(), both.getId());
        assertThat(fragment.page().getContent()).isEqualTo(full.page().getContent());
        assertThat(search.search("max", "CartSearch", List.of("off:fr:EnThAlT-MiLcH"), List.of(), 0, false)
                .page().getContent()).extracting("id").startsWith(off.getId());
        mvc.perform(post("/cart/preferences").with(user("max")).with(csrf()).param("excluded", key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.excluded[0]").value("content:milk"));
        mvc.perform(get("/cart/results").with(user("max")).param("query", "CartSearch"))
                .andExpect(status().isOk()).andExpect(model().attribute("excluded", List.of("content:milk")));
        mvc.perform(get("/cart").with(user("max")))
                .andExpect(status().isOk()).andExpect(model().attribute("excluded", List.of("content:milk")));
    }

    @Test
    void milkAliasesMatchAllergensTracesAndClaimsButNotLactoseFreeOrUnknown() throws Exception {
        off.getOpenFoodFactsData().setLabelTags("en:contains-milk");
        var allergen = imported("MilkSearch Allergen", "9898989898931");
        allergen.getOpenFoodFactsData().setAllergenTags("en:milk");
        var trace = imported("MilkSearch Trace", "9898989898932");
        trace.getOpenFoodFactsData().setTraceTags("en:milk");
        trace.setLabels(Set.of(ProductLabel.LACTOSE_FREE));
        var textTrace = imported("MilkSearch Text", "9898989898933");
        textTrace.getOpenFoodFactsData().setTraces("Milch");
        var free = imported("MilkSearch Free", "9898989898934");
        free.getOpenFoodFactsData().setLabelTags("en:no-milk,en:lactose-free");
        free.getOpenFoodFactsData().setTraceTags("en:milk-chocolate-extra");
        var unknown = products.save(new Product("MilkSearch Unknown", null));
        em.flush();
        cart.change("max", trace.getId(), CartService.Action.INCREMENT);

        for (String alias : List.of("content:milk", "off:en:contains-milk", "off:fr:enthalt-milch")) {
            var result = search.search("max", "MilkSearch", List.of(), List.of(alias), 0, false);
            assertThat(result.excluded()).containsExactly("content:milk");
            assertThat(result.page().getContent()).extracting("id").containsExactly(free.getId(), unknown.getId());
        }
        assertThat(search.search("max", "CartSearch", List.of(), List.of("content:milk"), 0).page().getContent())
                .extracting("id").doesNotContain(off.getId());
        assertThat(search.search("max", "MilkSearch", List.of("content:milk"), List.of(), 0).page().getContent())
                .extracting("id").startsWith(allergen.getId(), textTrace.getId(), trace.getId());
        assertThat(search.search("max", "", List.of("off:en:contains-milk", "off:fr:enthalt-milch"),
                List.of(), 0).preferred()).containsExactly("content:milk");
        assertThat(cart.quantities("max")).containsEntry(trace.getId(), 1);
        mvc.perform(post("/cart/preferences").with(user("max")).with(csrf())
                        .param("excluded", "off:fr:enthalt-milch"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.excluded[0]").value("content:milk"));
        mvc.perform(get("/cart/results").with(user("max")).param("query", "MilkSearch"))
                .andExpect(status().isOk()).andExpect(model().attribute("excluded", List.of("content:milk")));
    }

    @Test
    void nutritionAliasesMatchActualAppAndOffScoresForEveryGrade() {
        var app = products.save(new Product("ScoreSearch App", null));
        var community = imported("ScoreSearch OFF", "9898989898941");
        var claim = imported("ScoreSearch Claim", "9898989898942");
        var unknown = products.save(new Product("ScoreSearch Unknown", null));
        for (NutritionGrade grade : NutritionGrade.values()) {
            if (grade == NutritionGrade.UNKNOWN) continue;
            app.setNutritionGrade(grade);
            community.getOpenFoodFactsData().setNutritionGrade(" " + grade.name().toLowerCase() + " ");
            claim.getOpenFoodFactsData().setLabelTags("en:nutriscore-grade-" + grade.name().toLowerCase());
            em.flush();
            var result = search.search("max", "ScoreSearch", List.of(),
                    List.of("off:en:nutriscore-grade-" + grade.name().toLowerCase()), 0, false);
            assertThat(result.excluded()).containsExactly("nutrition:" + grade.name());
            assertThat(result.page().getContent()).extracting("id").containsExactly(unknown.getId());
        }
        app.setNutritionGrade(NutritionGrade.B);
        community.getOpenFoodFactsData().setNutritionGrade("c");
        community.setNutritionGrade(NutritionGrade.A);
        claim.getOpenFoodFactsData().setLabelTags("en:nutriscore-grade-c-extra");
        em.flush();
        assertThat(search.search("max", "ScoreSearch", List.of(), List.of("nutrition:C"), 0)
                .page().getContent()).extracting("id")
                .containsExactly(app.getId(), claim.getId(), unknown.getId());
    }

    private Product imported(String name, String barcode) {
        var product = products.saveAndFlush(new Product(name, null));
        var data = new OpenFoodFactsData(product, barcode, name, LocalDateTime.now());
        product.setOpenFoodFactsData(data);
        em.persist(data);
        return product;
    }
}
