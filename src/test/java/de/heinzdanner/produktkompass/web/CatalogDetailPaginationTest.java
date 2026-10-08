package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.product.Brand;
import de.heinzdanner.produktkompass.product.BrandRepository;
import de.heinzdanner.produktkompass.product.Company;
import de.heinzdanner.produktkompass.product.CompanyRepository;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.product.ProductService;
import de.heinzdanner.produktkompass.user.AppUserRepository;
import de.heinzdanner.produktkompass.user.AppUserService;
import de.heinzdanner.produktkompass.usertag.UserTag;
import de.heinzdanner.produktkompass.usertag.UserTagRepository;
import de.heinzdanner.produktkompass.usertag.UserTagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true")
@Transactional
class CatalogDetailPaginationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private BrandRepository brands;
    @Autowired private CompanyRepository companies;
    @Autowired private ProductRepository products;
    @Autowired private UserTagRepository tags;
    @Autowired private AppUserRepository users;
    @MockitoSpyBean private ProductService service;
    @MockitoSpyBean private UserTagService tagService;
    @MockitoSpyBean private AppUserService userService;
    private MockMvc mvc;
    private Brand brand;
    private Company company;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        brand = brands.save(new Brand("Catalog pagination brand", "Test"));
        company = companies.save(new Company("Catalog pagination company", null, null, "Test"));
        for (int i = 0; i < 25; i++) {
            Product product = new Product("Catalog page %02d".formatted(i), "Test", brand);
            product.setCompany(company);
            products.save(product);
        }
        products.save(new Product("Other catalog brand", "Test"));
        products.flush();
        clearInvocations(service);
        clearInvocations(tagService, userService);
    }

    @Test
    void brandAndCompanyPagesOnlyLoadTheirCurrentProducts() throws Exception {
        for (String path : new String[]{"/brands/" + brand.getId(), "/companies/" + company.getId()}) {
            var result = mvc.perform(get(path).param("page", "1").param("size", "2"))
                    .andExpect(status().isOk()).andReturn();
            assertThat(result.getModelAndView().getModel().get("productPage"))
                    .isInstanceOfSatisfying(Page.class, page -> {
                        assertThat(page.getTotalElements()).isEqualTo(25);
                        assertThat(page.getContent()).hasSize(2);
                        assertThat(page.getNumber()).isEqualTo(1);
                    });
            assertThat(result.getResponse().getContentAsString())
                    .contains("Catalog page 02", "Catalog page 03", "page=2", "size=2")
                    .doesNotContain("Catalog page 00", "Other catalog brand");
            mvc.perform(get(path).param("size", "51")).andExpect(status().isBadRequest());
            mvc.perform(get(path).param("page", "-1")).andExpect(status().isBadRequest());
            var empty = mvc.perform(get(path).param("page", "20").param("size", "2"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(empty).contains("Zur ersten Seite").doesNotContain("Noch keine Produkte hinterlegt.");
        }
        verify(service, never()).getAllProducts();
    }

    @Test
    void favoritesArePagedAndOwnerScoped() throws Exception {
        var owner = users.findByUsername("max").orElseThrow();
        owner.getFavoriteProducts().clear();
        var owned = service.searchPage(null, brand.getId(), null, null, null, null, null, null, 0, 25)
                .getContent();
        owner.getFavoriteProducts().addAll(products.findAllById(owned.stream().map(p -> p.id()).toList()));
        users.saveAndFlush(owner);
        var other = users.findByUsername("admin").orElseThrow();
        other.getFavoriteProducts().add(products.saveAndFlush(new Product("Other user's favorite", "Test")));
        users.saveAndFlush(other);
        var result = mvc.perform(get("/favorites").param("page", "1").param("size", "2")
                        .with(user("max").roles("USER")))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getModelAndView().getModel().get("productPage"))
                .isInstanceOfSatisfying(Page.class, page -> {
                    assertThat(page.getTotalElements()).isEqualTo(25);
                    assertThat(page.getContent()).hasSize(2);
                });
        assertThat(result.getResponse().getContentAsString())
                .contains("Catalog page 02", "Catalog page 03", "page=2", "Favorit entfernen")
                .doesNotContain("Other user's favorite", "Catalog page 00");
        mvc.perform(get("/favorites").param("size", "51").with(user("max").roles("USER")))
                .andExpect(status().isBadRequest());
        verify(userService, never()).getFavoriteProducts("max");
        verify(userService, never()).getFavoriteProductIds("max");
    }

    @Test
    void brandTagsRemainOwnerScopedWithoutLoadingAllTagAssignments() throws Exception {
        var owner = users.findByUsername("max").orElseThrow();
        UserTag ownTag = new UserTag("Own catalog tag", owner);
        ownTag.getBrands().add(brand);
        Long productId = service.searchPage(null, brand.getId(), null, null, null, null, null, null, 0, 1)
                .getContent().getFirst().id();
        ownTag.getProducts().add(products.findById(productId).orElseThrow());
        tags.saveAndFlush(ownTag);
        UserTag otherTag = new UserTag("Other catalog tag", users.findByUsername("admin").orElseThrow());
        otherTag.getBrands().add(brand);
        tags.saveAndFlush(otherTag);
        var result = mvc.perform(get("/brands/" + brand.getId()).with(user("max").roles("USER")))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getModelAndView().getModel().get("assignedTagIds"))
                .isEqualTo(java.util.Set.of(ownTag.getId()));
        assertThat(result.getResponse().getContentAsString())
                .contains("Own catalog tag").doesNotContain("Other catalog tag");
        var detail = mvc.perform(get("/products/" + productId).with(user("max").roles("USER")))
                .andExpect(status().isOk()).andReturn();
        assertThat(detail.getModelAndView().getModel().get("assignedTagIds"))
                .isEqualTo(java.util.Set.of(ownTag.getId()));
        assertThat(detail.getResponse().getContentAsString())
                .contains("Own catalog tag").doesNotContain("Other catalog tag");
        verify(tagService, never()).getTagsForUser("max");
        verify(userService, never()).getFavoriteProductIds("max");
    }
}
