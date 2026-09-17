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
        setField(brand, "id", 1L);
        setField(category, "id", 1L);

        Product product1 = new Product("Bio-Hafermilch", "Klassische Hafermilch.");
        product1.setBrand(brand);
        product1.addCategory(category);

        Product product2 = new Product("Kakao Drink", "Getränk");
        product2.setBrand(new Brand("TestBrand", "Brand"));

        when(productRepository.findAllWithDetails()).thenReturn(List.of(product1, product2));

        List<Product> result = productService.searchProducts("hafer", 1L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Bio-Hafermilch");
    }

    @Test
    void getAlternativeProductsReturnsAlternativesForProduct() {
        Product product = new Product("Bio-Hafermilch", "Klassische Hafermilch.");
        Product alternative = new Product("Haferdrink", "Alternative ohne Tierprodukte.");
        product.addAlternativeProduct(alternative);

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        List<Product> result = productService.getAlternativeProducts(10L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Haferdrink");
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
