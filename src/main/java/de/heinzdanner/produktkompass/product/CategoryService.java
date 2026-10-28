package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.dto.CategoryRequest;
import de.heinzdanner.produktkompass.product.dto.LookupOption;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    public CategoryService(CategoryRepository categoryRepository, AuditLogService auditLogService) {
        this.categoryRepository = categoryRepository;
        this.auditLogService = auditLogService;
    }

    public List<LookupOption> getPublicCategories() {
        return categoryRepository.findPublicOptions();
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Page<Category> getCategoryPage(String query, int page, int size) {
        return categoryRepository.search(MasterDataPaging.pattern(query), MasterDataPaging.request(page, size, "name"));
    }

    public List<LookupOption> getCategoryOptions(Collection<Long> selectedIds) {
        return MasterDataPaging.options(selectedIds, getCategoryPage("", 0, 20).getContent(),
                categoryRepository::findById, Category::getId, Category::getName);
    }

    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }

    @Transactional
    public Category createCategory(CategoryRequest request) {
        Category category = new Category(request.name(), request.description());
        Category savedCategory = categoryRepository.save(category);
        auditLogService.logAction("Category", savedCategory.getId(), "CREATE", "Created category: " + savedCategory.getName());
        return savedCategory;
    }

    @Transactional
    public Optional<Category> updateCategory(Long id, CategoryRequest request) {
        return categoryRepository.findById(id)
                .map(existingCategory -> {
                    existingCategory.setName(request.name());
                    existingCategory.setDescription(request.description());
                    Category updatedCategory = categoryRepository.save(existingCategory);
                    auditLogService.logAction("Category", updatedCategory.getId(), "UPDATE", "Updated category: " + updatedCategory.getName());
                    return updatedCategory;
                });
    }

    @Transactional
    public boolean deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            return false;
        }

        categoryRepository.deleteById(id);
        auditLogService.logAction("Category", id, "DELETE", "Deleted category with id: " + id);
        return true;
    }
}
