package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.dto.ProductRequest;
import de.heinzdanner.produktkompass.product.dto.ProductResponse;
import de.heinzdanner.produktkompass.product.dto.ProductSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
        return productRepository.findAllWithDetails();
    }

    public Page<ProductSummary> searchPage(String text, Long brandId, Long categoryId, ProductLabel label,
                                           NutritionGrade grade, Boolean madeInGermany, Long tagId,
                                           String username, int page, int size) {
        Page<Product> results = findSearchPage(text, brandId, categoryId, label, grade,
                madeInGermany, tagId, username, page, size);
        return results.map(ProductSummary::fromEntity);
    }

    public Page<Product> getAdminProductPage(String text, int page, int size) {
        Page<Product> results = findSearchPage(text, null, null, null, null, null, null, null, page, size);
        if (!results.isEmpty()) {
            productRepository.findPageSources(results.getContent().stream().map(Product::getId).toList());
        }
        return results;
    }

    public List<ProductOption> getProductOptions(List<Long> ids) {
        return ids.isEmpty() ? List.of() : productRepository.findAllById(ids).stream()
                .map(product -> new ProductOption(product.getId(), product.getName()))
                .toList();
    }

    public record ProductOption(Long id, String name) {
    }

    private Page<Product> findSearchPage(String text, Long brandId, Long categoryId, ProductLabel label,
                                         NutritionGrade grade, Boolean madeInGermany, Long tagId,
                                         String username, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page muss mindestens 0 sein; size muss zwischen 1 und 50 liegen.");
        }
        Page<Product> results = productRepository.findAll(
                ProductSearch.matching(text, brandId, categoryId, label, grade, madeInGermany, tagId, username),
                PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id"))));
        // Load collection data only for the bounded page, never in the paginated query.
        if (!results.isEmpty()) {
            productRepository.findPageCategories(results.getContent().stream().map(Product::getId).toList());
        }
        return results;
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findByIdWithDetails(id);
    }

    public List<Product> getAlternativeProducts(Long productId) {
        Optional<Product> baseProductOptional = productRepository.findByIdWithDetails(productId);
        if (baseProductOptional.isEmpty()) {
            return List.of();
        }

        Product baseProduct = baseProductOptional.get();
        if (baseProduct.getAlternativeProducts() != null && !baseProduct.getAlternativeProducts().isEmpty()) {
            return baseProduct.getAlternativeProducts().stream().toList();
        }

        if (baseProduct.getCategories().isEmpty()) {
            return List.of();
        }
        List<Long> ids = productRepository.findAutomaticAlternativeIds(productId, PageRequest.of(0, 6));
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Product> alternatives = productRepository.findAlternativeProductsByIds(ids);
        // Fetch each collection separately for the six selected products, without a collection-fetch page.
        productRepository.findPageCategories(ids);
        productRepository.findPageSources(ids);
        productRepository.findPageLabels(ids);
        productRepository.findPageAlternatives(ids);
        var byId = alternatives.stream().collect(java.util.stream.Collectors.toMap(Product::getId, product -> product));
        return ids.stream().map(id -> Objects.requireNonNull(byId.get(id),
                "Ausgewähltes Alternativprodukt wurde nicht gefunden: " + id)).toList();
    }

    public List<ProductResponse> getAlternativeProductResponses(Long productId) {
        return getAlternativeProducts(productId).stream().map(ProductResponse::fromEntity).toList();
    }

    public List<Product> getProductsWithAlternatives() {
        return productRepository.findAllWithAlternatives();
    }

    public List<Product> searchProducts(String name, Long brandId, Long categoryId) {
        return searchProducts(name, brandId, categoryId, null, null, null);
    }

    public List<Product> searchProducts(String name, Long brandId, Long categoryId, ProductLabel label,
                                        NutritionGrade nutritionGrade, Boolean madeInGermany) {
        List<Product> products = productRepository.findAllWithDetails();

        if (name != null && !name.isBlank()) {
            String normalizedName = name.trim().toLowerCase();
            products = products.stream()
                    .filter(product -> matchesSearchTerm(product, normalizedName))
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

        if (label != null) {
            products = products.stream()
                    .filter(product -> product.getLabels() != null && product.getLabels().contains(label))
                    .toList();
        }

        if (nutritionGrade != null) {
            products = products.stream()
                    .filter(product -> nutritionGrade.equals(product.getNutritionGrade()))
                    .toList();
        }

        if (madeInGermany != null) {
            products = products.stream()
                    .filter(product -> Objects.equals(product.getMadeInGermany(), madeInGermany))
                    .toList();
        }

        return products;
    }

    private boolean matchesSearchTerm(Product product, String searchTerm) {
        if (product.getName() != null && product.getName().toLowerCase().contains(searchTerm)) {
            return true;
        }
        if (product.getDescription() != null && product.getDescription().toLowerCase().contains(searchTerm)) {
            return true;
        }
        if (product.getBrand() != null && product.getBrand().getName() != null
                && product.getBrand().getName().toLowerCase().contains(searchTerm)) {
            return true;
        }
        if (product.getCompany() != null && product.getCompany().getName() != null
                && product.getCompany().getName().toLowerCase().contains(searchTerm)) {
            return true;
        }
        if (product.getCategories() != null && product.getCategories().stream()
                .anyMatch(category -> category.getName() != null && category.getName().toLowerCase().contains(searchTerm))) {
            return true;
        }
        return product.getLabels() != null && product.getLabels().stream()
                .map(Enum::name)
                .map(label -> label.toLowerCase().replace('_', ' '))
                .anyMatch(label -> label.contains(searchTerm));
    }

    public List<Product> searchProducts(String name, Long brandId) {
        return searchProducts(name, brandId, null);
    }

    public List<Product> searchProducts(String name) {
        return searchProducts(name, null, null);
    }

    public List<Product> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    public List<Product> getProductsByBrand(Long brandId) {
        return productRepository.findAllWithDetails().stream()
                .filter(product -> product.getBrand() != null
                        && Objects.equals(product.getBrand().getId(), brandId))
                .toList();
    }

    public List<Product> getProductsByName(String name) {
        return searchProducts(name, null, null);
    }

    @Transactional
    public Product createProduct(ProductRequest request) {
        Product product = new Product(request.name(), request.description());

        product.setBrand(resolveBrand(request.brandId()));
        product.setCompany(resolveCompany(request.companyId()));
        product.setNutritionGrade(request.nutritionGrade() == null ? NutritionGrade.UNKNOWN : request.nutritionGrade());
        product.setPackagingType(request.packagingType());
        product.setPackageSize(request.packageSize());
        product.setMadeInGermany(request.madeInGermany());
        product.setContainsLactose(request.containsLactose() == null ? IngredientStatus.UNKNOWN : request.containsLactose());
        product.setContainsGluten(request.containsGluten() == null ? IngredientStatus.UNKNOWN : request.containsGluten());
        product.setContainsAlcohol(request.containsAlcohol() == null ? IngredientStatus.UNKNOWN : request.containsAlcohol());
        product.setContainsCaffeine(request.containsCaffeine() == null ? IngredientStatus.UNKNOWN : request.containsCaffeine());
        product.setLabels(new HashSet<>(request.labels()));
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
                    existingProduct.setNutritionGrade(request.nutritionGrade() == null ? NutritionGrade.UNKNOWN : request.nutritionGrade());
                    existingProduct.setPackagingType(request.packagingType());
                    existingProduct.setPackageSize(request.packageSize());
                    existingProduct.setMadeInGermany(request.madeInGermany());
                    existingProduct.setContainsLactose(request.containsLactose() == null ? IngredientStatus.UNKNOWN : request.containsLactose());
                    existingProduct.setContainsGluten(request.containsGluten() == null ? IngredientStatus.UNKNOWN : request.containsGluten());
                    existingProduct.setContainsAlcohol(request.containsAlcohol() == null ? IngredientStatus.UNKNOWN : request.containsAlcohol());
                    existingProduct.setContainsCaffeine(request.containsCaffeine() == null ? IngredientStatus.UNKNOWN : request.containsCaffeine());
                    existingProduct.setLabels(new HashSet<>(request.labels()));
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
