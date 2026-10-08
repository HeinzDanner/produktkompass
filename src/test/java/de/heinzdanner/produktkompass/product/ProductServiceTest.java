package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.dto.ProductRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ProductService productService;

    @Test
    void getAllProductsReturnsAllProductsFromRepository() {
        Product product1 = new Product("Bio-Hafermilch", "Klassische Hafermilch.");
        Product product2 = new Product("Fairtrade Schokolade", "Zartbitterschokolade.");

        when(productRepository.findAllWithDetails()).thenReturn(List.of(product1, product2));

        List<Product> result = productService.getAllProducts();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Product::getName)
                .containsExactly("Bio-Hafermilch", "Fairtrade Schokolade");
    }

    @Test
    void getProductByIdReturnsProductWhenPresent() {
        Product product = new Product("Bio-Hafermilch", "Klassische Hafermilch.");

        when(productRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(product));

        Optional<Product> result = productService.getProductById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Bio-Hafermilch");
    }

    @Test
    void searchProductsFiltersByNameBrandAndCategory() {
        Brand brand = new Brand("Alnatura", "Bio");
        Category category = new Category("Milchalternativen", "Pflanzlich");
        Company company = new Company("Test Company", "https://example.com", "Deutschland", "Beschreibung");
        setField(brand, "id", 1L);
        setField(category, "id", 1L);

        Product product1 = new Product("Bio-Hafermilch", "Klassische Hafermilch.");
        product1.setBrand(brand);
        product1.setCompany(company);
        product1.setLabels(java.util.Set.of(ProductLabel.VEGAN));
        product1.addCategory(category);

        Product product2 = new Product("Kakao Drink", "Getränk");
        product2.setBrand(new Brand("TestBrand", "Brand"));

        when(productRepository.findAllWithDetails()).thenReturn(List.of(product1, product2));

        List<Product> result = productService.searchProducts("hafer", 1L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Bio-Hafermilch");
    }

    @Test
    void searchProductsAlsoFindsByCategoryCompanyAndLabel() {
        Brand brand = new Brand("Lindt", "Schokolade");
        Category category = new Category("Süßwaren und Schokolade", "Schokolade");
        Company company = new Company("Lindt & Sprüngli AG", "https://example.com", "Schweiz", "Beschreibung");

        Product product = new Product("Excellence 70%", "Zartbitterschokolade.");
        product.setBrand(brand);
        product.setCompany(company);
        product.setLabels(java.util.Set.of(ProductLabel.VEGETARIAN));
        product.addCategory(category);

        when(productRepository.findAllWithDetails()).thenReturn(List.of(product));

        assertThat(productService.searchProducts("schokolade", null, null)).hasSize(1);
        assertThat(productService.searchProducts("lindt", null, null)).hasSize(1);
        assertThat(productService.searchProducts("vegetarian", null, null)).hasSize(1);
    }

    @Test
    void getProductsByCategoryReturnsMatchingProducts() {
        Category category = new Category("Milchalternativen", "Pflanzlich");
        setField(category, "id", 7L);

        Product product = new Product("Bio-Hafermilch", "Klassische Hafermilch.");
        product.addCategory(category);

        when(productRepository.findByCategoryId(7L)).thenReturn(List.of(product));

        List<Product> result = productService.getProductsByCategory(7L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getCategories()).extracting(Category::getName)
                .containsExactly("Milchalternativen");
    }

    @Test
    void getAlternativeProductsReturnsAlternativesForProduct() {
        Product product = new Product("Bio-Hafermilch", "Klassische Hafermilch.");
        Product alternative = new Product("Haferdrink", "Alternative ohne Tierprodukte.");
        product.addAlternativeProduct(alternative);

        when(productRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(product));

        List<Product> result = productService.getAlternativeProducts(10L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Haferdrink");
    }

    @Test
    void getAlternativeProductsFallsBackToCategoryAndAttributes() {
        Category category = new Category("Milchprodukte und Milchalternativen", "Beschreibung");
        setField(category, "id", 1L);

        Product product = new Product("Oat Drink", "Haferdrink");
        setField(product, "id", 10L);
        product.addCategory(category);
        product.setNutritionGrade(NutritionGrade.A);
        product.setPackagingType(PackagingType.TETRA_PAK);
        product.setMadeInGermany(false);
        product.setLabels(java.util.Set.of(ProductLabel.VEGAN));

        Product strongMatch = new Product("Sojadrink", "Alternative");
        setField(strongMatch, "id", 11L);
        strongMatch.addCategory(category);
        strongMatch.setNutritionGrade(NutritionGrade.A);
        strongMatch.setPackagingType(PackagingType.TETRA_PAK);
        strongMatch.setMadeInGermany(false);
        strongMatch.setLabels(java.util.Set.of(ProductLabel.VEGAN));

        Product weakerMatch = new Product("Schokomilch", "Andere Alternative");
        setField(weakerMatch, "id", 12L);
        weakerMatch.addCategory(category);
        weakerMatch.setNutritionGrade(NutritionGrade.C);

        when(productRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(product));
        when(productRepository.findAutomaticAlternativeIds(10L, org.springframework.data.domain.PageRequest.of(0, 6)))
                .thenReturn(List.of(11L, 12L));
        when(productRepository.findAlternativeProductsByIds(List.of(11L, 12L)))
                .thenReturn(List.of(weakerMatch, strongMatch));

        List<Product> result = productService.getAlternativeProducts(10L);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getName()).isEqualTo("Sojadrink");
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.never()).findAllWithDetails();
    }

    @Test
    void createProductSavesProductWithBrandAndCategories() {
        Brand brand = new Brand("Alnatura", "Bio");
        setField(brand, "id", 1L);

        Category category = new Category("Milchalternativen", "Pflanzlich");
        setField(category, "id", 2L);

        ProductRequest request = new ProductRequest(
                "Bio-Hafermilch",
                "Klassische Hafermilch.",
                1L,
                null,
                NutritionGrade.A,
                PackagingType.TETRA_PAK,
                "1 l",
                true,
                IngredientStatus.DOES_NOT_CONTAIN,
                IngredientStatus.UNKNOWN,
                IngredientStatus.DOES_NOT_CONTAIN,
                IngredientStatus.DOES_NOT_CONTAIN,
                java.util.Set.of(ProductLabel.VEGAN, ProductLabel.BIO),
                List.of(2L),
                List.of(),
                List.of());

        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));
        when(categoryRepository.findAllById(List.of(2L))).thenReturn(List.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            setField(product, "id", 99L);
            return product;
        });

        Product result = productService.createProduct(request);

        assertThat(result.getName()).isEqualTo("Bio-Hafermilch");
        assertThat(result.getBrand().getName()).isEqualTo("Alnatura");
        assertThat(result.getCategories()).hasSize(1);
        assertThat(result.getCategories().iterator().next().getName()).isEqualTo("Milchalternativen");
        assertThat(result.getNutritionGrade()).isEqualTo(NutritionGrade.A);
        assertThat(result.getPackagingType()).isEqualTo(PackagingType.TETRA_PAK);
        assertThat(result.getPackageSize()).isEqualTo("1 l");
        assertThat(result.getMadeInGermany()).isTrue();
        assertThat(result.getLabels()).containsExactlyInAnyOrder(ProductLabel.VEGAN, ProductLabel.BIO);
    }

    private static void setField(Object target, String fieldName, Long value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
