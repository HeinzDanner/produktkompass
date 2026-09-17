package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.dto.ProductRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final CompanyRepository companyRepository;
    private final SourceRepository sourceRepository;
    private final AuditLogService auditLogService;

    public ProductService(ProductRepository productRepository,
                          BrandRepository brandRepository,
                          CategoryRepository categoryRepository,
                          CompanyRepository companyRepository,
                          SourceRepository sourceRepository,
                          AuditLogService auditLogService) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.companyRepository = companyRepository;
        this.sourceRepository = sourceRepository;
        this.auditLogService = auditLogService;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public List<Product> getAlternativeProducts(Long productId) {
        return productRepository.findById(productId)
                .map(Product::getAlternativeProducts)
                .map(alternatives -> alternatives.stream().toList())
                .orElse(List.of());
    }

    public List<Product> searchProducts(String name, Long brandId, Long categoryId) {
        List<Product> products = productRepository.findAll();

        if (name != null && !name.isBlank()) {
            products = products.stream()
                    .filter(product -> product.getName() != null
                            && product.getName().toLowerCase().contains(name.toLowerCase()))
                    .toList();
        }

        if (brandId != null) {
            products = products.stream()
                    .filter(product -> product.getBrand() != null
                            && Objects.equals(product.getBrand().getId(), brandId))
                    .toList();
        }

        if (categoryId != null) {
            products = products.stream()
                    .filter(product -> product.getCategories() != null
                            && product.getCategories().stream()
                            .anyMatch(category -> Objects.equals(category.getId(), categoryId)))
                    .toList();
        }

        return products;
    }

    @Transactional
    public Product createProduct(ProductRequest request) {
        Product product = new Product(request.name(), request.description());

        product.setBrand(resolveBrand(request.brandId()));
        product.setCompany(resolveCompany(request.companyId()));
        product.setCategories(new HashSet<>(resolveCategories(request.categoryIds())));
        product.setSources(new HashSet<>(resolveSources(request.sourceIds())));
        product.setAlternativeProducts(new HashSet<>(resolveAlternatives(request.alternativeProductIds())));

        Product savedProduct = productRepository.save(product);
        auditLogService.logAction("Product", savedProduct.getId(), "CREATE", "Created product: " + savedProduct.getName());
        return savedProduct;
    }

    @Transactional
    public Optional<Product> updateProduct(Long id, ProductRequest request) {
        return productRepository.findById(id)
                .map(existingProduct -> {
                    existingProduct.setName(request.name());
                    existingProduct.setDescription(request.description());
                    existingProduct.setBrand(resolveBrand(request.brandId()));
                    existingProduct.setCompany(resolveCompany(request.companyId()));
                    existingProduct.setCategories(new HashSet<>(resolveCategories(request.categoryIds())));
                    existingProduct.setSources(new HashSet<>(resolveSources(request.sourceIds())));
                    existingProduct.setAlternativeProducts(new HashSet<>(resolveAlternatives(request.alternativeProductIds())));
                    Product updatedProduct = productRepository.save(existingProduct);
                    auditLogService.logAction("Product", updatedProduct.getId(), "UPDATE", "Updated product: " + updatedProduct.getName());
                    return updatedProduct;
                });
    }

    @Transactional
    public boolean deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            return false;
        }

        productRepository.deleteById(id);
        auditLogService.logAction("Product", id, "DELETE", "Deleted product with id: " + id);
        return true;
    }

    private Brand resolveBrand(Long brandId) {
        if (brandId == null) {
            return null;
        }

        return brandRepository.findById(brandId)
                .orElseThrow(() -> new IllegalArgumentException("Marke nicht gefunden: " + brandId));
    }

    private Company resolveCompany(Long companyId) {
        if (companyId == null) {
            return null;
        }

        return companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Unternehmen nicht gefunden: " + companyId));
    }

    private List<Category> resolveCategories(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }

        List<Category> categories = categoryRepository.findAllById(categoryIds);
        if (categories.size() != new HashSet<>(categoryIds).size()) {
            throw new IllegalArgumentException("Eine oder mehrere Kategorien wurden nicht gefunden.");
        }
        return categories;
    }

    private List<Source> resolveSources(List<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }

        List<Source> sources = sourceRepository.findAllById(sourceIds);
        if (sources.size() != new HashSet<>(sourceIds).size()) {
            throw new IllegalArgumentException("Eine oder mehrere Quellen wurden nicht gefunden.");
        }
        return sources;
    }

    private List<Product> resolveAlternatives(List<Long> alternativeProductIds) {
        if (alternativeProductIds == null || alternativeProductIds.isEmpty()) {
            return List.of();
        }

        List<Product> alternatives = productRepository.findAllById(alternativeProductIds);
        if (alternatives.size() != new HashSet<>(alternativeProductIds).size()) {
            throw new IllegalArgumentException("Eine oder mehrere alternative Produkte wurden nicht gefunden.");
        }
        return alternatives;
    }
}
