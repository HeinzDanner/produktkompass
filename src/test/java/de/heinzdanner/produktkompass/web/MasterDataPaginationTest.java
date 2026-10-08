package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.product.*;
import de.heinzdanner.produktkompass.product.dto.LookupOption;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true")
@Transactional
class MasterDataPaginationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private BrandRepository brands;
    @Autowired private CategoryRepository categories;
    @Autowired private CompanyRepository companies;
    @Autowired private SourceRepository sources;
    @Autowired private ProductRepository products;
    @MockitoSpyBean private BrandService brandService;
    @MockitoSpyBean private CategoryService categoryService;
    @MockitoSpyBean private CompanyService companyService;
    @MockitoSpyBean private SourceService sourceService;

    private MockMvc mvc;
    private final List<Brand> brandFixtures = new ArrayList<>();
    private final List<Category> categoryFixtures = new ArrayList<>();
    private final List<Company> companyFixtures = new ArrayList<>();
    private final List<Source> sourceFixtures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (int i = 0; i < 25; i++) {
            String name = "000 lookup %02d".formatted(i);
            brandFixtures.add(brands.save(new Brand(name, "")));
            categoryFixtures.add(categories.save(new Category(name, "")));
            companyFixtures.add(companies.save(new Company(name, "", "", "")));
            sourceFixtures.add(sources.save(new Source(name, "https://example.org/" + i, "", null)));
        }
        brands.flush();
    }

    @Test
    void allMasterApisReturnStableBoundedSearchPages() throws Exception {
        for (String endpoint : List.of("brands", "categories", "companies", "sources")) {
            mvc.perform(get("/api/v1/" + endpoint).param("query", "000 LOOKUP").param("page", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(5))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(25))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.hasPrevious").value(true))
                    .andExpect(jsonPath("$.hasNext").value(false));
            mvc.perform(get("/api/v1/" + endpoint).param("query", "000 lookup").param("page", "-1").param("size", "500"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/v1/" + endpoint).param("query", "000 lookup").param("page", "99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
            mvc.perform(get("/api/v1/" + endpoint).param("query", "000 lookup").param("size", "0"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void searchTreatsSqlWildcardsLiterallyAndUsesIdTieBreak() {
        Source first = sources.save(new Source("literal %_!", "https://example.org/first", "", null));
        Source second = sources.save(new Source("literal %_!", "https://example.org/second", "", null));
        sources.flush();
        assertThat(sourceService.getSourcePage("%_!", 0, 1).getContent()).extracting(Source::getId)
                .containsExactly(first.getId());
        assertThat(sourceService.getSourcePage("%_!", 1, 1).getContent()).extracting(Source::getId)
                .containsExactly(second.getId());
        assertThat(brandService.getBrandPage("does not exist", 0, 20)).isEmpty();
    }

    @Test
    void publicFiltersKeepOffPageSelectionsWithoutFullLoads() throws Exception {
        var selectedBrand = brandFixtures.getLast().getId();
        var selectedCategory = categoryFixtures.getLast().getId();
        var result = mvc.perform(get("/products").param("brandId", selectedBrand.toString())
                        .param("categoryId", selectedCategory.toString()))
                .andExpect(status().isOk()).andReturn();
        assertOptions(result.getModelAndView().getModel().get("brands"), selectedBrand);
        assertOptions(result.getModelAndView().getModel().get("categories"), selectedCategory);
        assertThat(result.getResponse().getContentAsString().replaceAll("\\s+", " "))
                .contains("value=\"" + selectedBrand + "\" selected=\"selected\"",
                        "value=\"" + selectedCategory + "\" selected=\"selected\"", "lookup-picker.js");
        verifyNoFullLoads();
    }

    @Test
    void adminTablesPageIndependentlyAndKeepOtherParameters() throws Exception {
        var result = mvc.perform(get("/admin").with(user("admin").roles("ADMIN"))
                        .param("brandQuery", "000 lookup").param("brandPage", "1")
                        .param("categoryQuery", "000 lookup").param("categoryPage", "0")
                        .param("companyQuery", "000 lookup").param("companyPage", "1")
                        .param("sourceQuery", "000 lookup").param("sourcePage", "1"))
                .andExpect(status().isOk()).andReturn();
        var model = result.getModelAndView().getModel();
        assertThat((List<?>) model.get("brands")).hasSize(5);
        assertThat((List<?>) model.get("categories")).hasSize(20);
        assertThat((List<?>) model.get("companies")).hasSize(5);
        assertThat((List<?>) model.get("sources")).hasSize(5);
        assertThat(((Page<?>) model.get("brandPage")).getTotalElements()).isEqualTo(25);
        assertThat(model.get("brandPreviousUrl").toString()).contains("brandPage=0", "companyPage=1", "categoryQuery=000");
        assertThat(model.get("productNextUrl").toString()).contains("sourcePage=1", "brandPage=1");
        assertThat(model.get("dataQualityWarnings").toString()).contains("000 lookup 20").doesNotContain("000 lookup 00");
        verifyNoFullLoads();
        mvc.perform(get("/admin").with(user("admin").roles("ADMIN")).param("brandPage", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin").with(user("admin").roles("ADMIN")).param("brandPage", "wrong"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCreationPreservesOffPageSelectionsAndIndependentListState() throws Exception {
        var result = mvc.perform(post("/admin/products").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "")
                        .param("brandId", brandFixtures.getLast().getId().toString())
                        .param("companyId", companyFixtures.getLast().getId().toString())
                        .param("categoryIds", categoryFixtures.getLast().getId().toString())
                        .param("sourceIds", sourceFixtures.getLast().getId().toString())
                        .param("brandQuery", "000 lookup").param("brandPage", "1"))
                .andExpect(status().isOk()).andReturn();
        var model = result.getModelAndView().getModel();
        assertOptions(model.get("brandOptions"), brandFixtures.getLast().getId());
        assertOptions(model.get("companyOptions"), companyFixtures.getLast().getId());
        assertOptions(model.get("categoryOptions"), categoryFixtures.getLast().getId());
        assertOptions(model.get("sourceOptions"), sourceFixtures.getLast().getId());
        assertThat(result.getResponse().getContentAsString())
                .contains("value=\"" + brandFixtures.getLast().getId() + "\" selected=\"selected\"",
                        "value=\"" + sourceFixtures.getLast().getId() + "\" selected=\"selected\"", "lookup-picker.js");
        assertThat(((Page<?>) model.get("brandPage")).getNumber()).isEqualTo(1);
        verifyNoFullLoads();
    }

    @Test
    void selectedOptionsAreNeverTruncatedAndUnavailableIdsAreRetained() {
        List<Long> selected = brandFixtures.stream().map(Brand::getId).toList();
        assertThat(brandService.getBrandOptions(selected)).extracting(LookupOption::id).containsAll(selected);
        assertThat(brandService.getBrandOptions(List.of(Long.MAX_VALUE))).hasSize(21)
                .anySatisfy(option -> {
                    assertThat(option.id()).isEqualTo(Long.MAX_VALUE);
                    assertThat(option.name()).contains("Nicht verfügbar");
                });
    }

    @Test
    void editAndInvalidUpdateKeepAllSelectedMasterDataOutsideInitialPages() throws Exception {
        Product product = new Product("Lookup edit fixture", "");
        product.setBrand(brandFixtures.getLast());
        product.setCompany(companyFixtures.getLast());
        product.setCategories(new java.util.HashSet<>(java.util.Set.of(categoryFixtures.getLast())));
        product.setSources(new java.util.HashSet<>(java.util.Set.of(sourceFixtures.getLast())));
        products.saveAndFlush(product);
        var edit = mvc.perform(get("/admin/products/" + product.getId() + "/edit")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn();
        var model = edit.getModelAndView().getModel();
        assertOptions(model.get("brandOptions"), brandFixtures.getLast().getId());
        assertOptions(model.get("companyOptions"), companyFixtures.getLast().getId());
        assertOptions(model.get("categoryOptions"), categoryFixtures.getLast().getId());
        assertOptions(model.get("sourceOptions"), sourceFixtures.getLast().getId());
        var invalid = mvc.perform(post("/admin/products/" + product.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "")
                        .param("brandId", brandFixtures.getLast().getId().toString())
                        .param("companyId", companyFixtures.getLast().getId().toString())
                        .param("categoryIds", categoryFixtures.getLast().getId().toString())
                        .param("sourceIds", sourceFixtures.getLast().getId().toString()))
                .andExpect(status().isOk()).andReturn();
        assertThat(invalid.getModelAndView().getViewName()).isEqualTo("admin-product-edit");
        assertOptions(invalid.getModelAndView().getModel().get("sourceOptions"), sourceFixtures.getLast().getId());
        assertThat(invalid.getResponse().getContentAsString())
                .contains("value=\"" + sourceFixtures.getLast().getId() + "\" selected=\"selected\"");
        verifyNoFullLoads();
    }

    @SuppressWarnings("unchecked")
    private void assertOptions(Object options, Long selectedId) {
        assertThat((List<LookupOption>) options).hasSizeLessThanOrEqualTo(21)
                .extracting(LookupOption::id).contains(selectedId);
    }

    private void verifyNoFullLoads() {
        verify(brandService, never()).getAllBrands();
        verify(categoryService, never()).getAllCategories();
        verify(companyService, never()).getAllCompanies();
        verify(sourceService, never()).getAllSources();
    }
}
