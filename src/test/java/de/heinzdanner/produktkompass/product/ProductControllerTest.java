package de.heinzdanner.produktkompass.product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    private MockMvc mockMvc;

    @Test
    void searchProductsReturnsFilteredProducts() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(productController).build();

        Brand brand = new Brand("Alnatura", "Bio");
        setField(brand, "id", 1L);

        Category category = new Category("Milchalternativen", "Pflanzlich");
        setField(category, "id", 1L);

        Product product = new Product("Bio-Hafermilch", "Klassische Hafermilch.");
        product.setBrand(brand);
        product.addCategory(category);
        setField(product, "id", 10L);

        when(productService.searchPage("hafer", 1L, 1L, null, null, null, null, null, false, 0, 20))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        List.of(de.heinzdanner.produktkompass.product.dto.ProductSummary.fromEntity(product))));

        mockMvc.perform(get("/api/v1/products/search")
                        .param("name", "hafer")
                        .param("brandId", "1")
                        .param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].name").value("Bio-Hafermilch"))
                .andExpect(jsonPath("$.content[0].brand.name").value("Alnatura"))
                .andExpect(jsonPath("$.content[0].categories[0].name").value("Milchalternativen"));
    }

    @Test
    void getAlternativeProductsReturnsAlternativeProductIds() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(productController).build();

        Product alternative = new Product("Haferdrink", "Alternative ohne Tierprodukte.");
        setField(alternative, "id", 99L);

        Product product = new Product("Bio-Hafermilch", "Klassische Hafermilch.");
        product.addAlternativeProduct(alternative);
        setField(product, "id", 10L);

        when(productService.getAlternativeProductResponses(10L)).thenReturn(
                List.of(de.heinzdanner.produktkompass.product.dto.ProductResponse.fromEntity(alternative)));

        mockMvc.perform(get("/api/v1/products/10/alternatives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(99))
                .andExpect(jsonPath("$[0].name").value("Haferdrink"));
    }

    private static void setField(Object target, String fieldName, Long value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
