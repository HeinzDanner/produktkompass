package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.dto.CategoryRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createCategorySavesCategory() {
        CategoryRequest request = new CategoryRequest("Milchalternativen", "Pflanzlich");
        Category savedCategory = new Category("Milchalternativen", "Pflanzlich");

        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        Category result = categoryService.createCategory(request);

        assertThat(result.getName()).isEqualTo("Milchalternativen");
        assertThat(result.getDescription()).isEqualTo("Pflanzlich");
    }

    @Test
    void updateCategoryUpdatesExistingCategory() {
        Category existing = new Category("Alt", "Alt");
        CategoryRequest request = new CategoryRequest("Neu", "Neu");

        when(categoryRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);

        Optional<Category> result = categoryService.updateCategory(2L, request);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Neu");
        assertThat(result.get().getDescription()).isEqualTo("Neu");
    }
}
