package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.user.AppUser;
import de.heinzdanner.produktkompass.user.AppUserRepository;
import de.heinzdanner.produktkompass.user.Role;
import de.heinzdanner.produktkompass.usertag.UserTag;
import de.heinzdanner.produktkompass.usertag.UserTagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true")
@Transactional
class ProductSearchIntegrationTest {

    @Autowired private ProductService service;
    @Autowired private ProductRepository products;
    @Autowired private BrandRepository brands;
    @Autowired private CategoryRepository categories;
    @Autowired private AppUserRepository users;
    @Autowired private UserTagRepository tags;
    @Autowired private WebApplicationContext context;

    @Test
    void databaseFiltersBeforePaginationAndReturnsStableDistinctPages() {
        Brand brand = brands.save(new Brand("Page test brand", "Test"));
        Category category = categories.save(new Category("Page test category", "Test"));
        Category secondCategory = categories.save(new Category("Page test category 2", "Test"));
        for (int i = 0; i < 5; i++) {
            Product product = new Product("Page test same name", "Test");
            product.setBrand(brand);
            product.setCategories(new HashSet<>(Set.of(category, secondCategory)));
            product.setNutritionGrade(NutritionGrade.A);
            product.setMadeInGermany(true);
            product.setLabels(new HashSet<>(Set.of(ProductLabel.VEGAN, ProductLabel.BIO)));
            products.save(product);
        }

        var first = service.searchPage("PAGE TEST", brand.getId(), category.getId(), ProductLabel.VEGAN,
                NutritionGrade.A, true, null, null, 0, 2);
        var second = service.searchPage("PAGE TEST", brand.getId(), category.getId(), ProductLabel.VEGAN,
                NutritionGrade.A, true, null, null, 1, 2);

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent()).hasSize(2);
        assertThat(first.getContent()).extracting("id").doesNotContainAnyElementsOf(
                second.getContent().stream().map(p -> p.id()).toList());
        assertThat(first.getContent().getFirst().categories()).hasSize(2);
        assertThat(service.searchPage(null, brand.getId(), null, null, null, false, null, null, 0, 20))
                .isEmpty();
    }

    @Test
    void textSearchIncludesCategoriesAndLabelsAndTreatsWildcardsLiterally() {
        Product product = new Product("Literal 100%_test", "Unique description");
        Category category = categories.save(new Category("Unique category text", "Test"));
        product.addCategory(category);
        product.setLabels(new HashSet<>(Set.of(ProductLabel.NO_ADDED_SUGAR)));
        products.save(product);

        assertThat(service.searchPage("100%_test", null, null, null, null, null, null, null, 0, 20)
                .getContent()).extracting("id").containsExactly(product.getId());
        assertThat(service.searchPage("unique category text", null, null, null, null, null, null, null, 0, 20)
                .getContent()).extracting("id").containsExactly(product.getId());
        assertThat(service.searchPage("no added sugar", null, null, null, null, null, null, null, 0, 20)
                .getContent()).extracting("id").contains(product.getId());
    }

    @Test
    void tagSearchIncludesTaggedBrandsAndProductsButNeverOtherOwnersTags() {
        AppUser owner = users.save(new AppUser("page-owner", "encoded", Role.USER));
        Brand brand = brands.save(new Brand("Tagged page brand", "Test"));
        Product branded = products.save(new Product("Tagged page product", "Test", brand));
        Product direct = products.save(new Product("Direct page product", "Test"));
        UserTag tag = new UserTag("Page tag", owner);
        tag.setProducts(new HashSet<>(Set.of(direct)));
        tag.setBrands(new HashSet<>(Set.of(brand)));
        tags.save(tag);

        var result = service.searchPage(null, null, null, null, null, null, tag.getId(),
                owner.getUsername(), 0, 1);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(service.searchPage(null, null, null, null, null, null, tag.getId(),
                owner.getUsername(), 0, 20).getContent()).extracting("id")
                .containsExactlyInAnyOrder(branded.getId(), direct.getId());
        assertThat(service.searchPage(null, null, null, null, null, null, tag.getId(),
                "different-owner", 0, 20)).isEmpty();
    }

    @Test
    void invalidPageSizesAreRejectedAndOutOfRangePagesAreEmpty() {
        for (int size : List.of(0, -1, 51)) {
            assertThatThrownBy(() -> service.searchPage(null, null, null, null, null, null,
                    null, null, 0, size)).isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> service.searchPage(null, null, null, null, null, null,
                null, null, -1, 20)).isInstanceOf(ResponseStatusException.class);
        assertThat(service.searchPage(null, null, null, null, null, null, null, null, 100, 20)).isEmpty();
    }

    @Test
    void apiAndWebExposePagesAndPreserveFiltersInNavigation() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/products").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.content[0].sources").doesNotExist());
        mvc.perform(get("/products").param("query", "a").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("page=1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("query=a")));
        mvc.perform(get("/api/v1/products/search").param("size", "51"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/products").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/products").param("tagId", "999999").with(user("max").roles("USER")))
                .andExpect(status().isNotFound());
    }
}
