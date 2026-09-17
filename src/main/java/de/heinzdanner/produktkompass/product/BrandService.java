package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.dto.BrandRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;
    private final AuditLogService auditLogService;

    public BrandService(BrandRepository brandRepository, AuditLogService auditLogService) {
        this.brandRepository = brandRepository;
        this.auditLogService = auditLogService;
    }

    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    public Optional<Brand> getBrandById(Long id) {
        return brandRepository.findById(id);
    }

    @Transactional
    public Brand createBrand(BrandRequest request) {
        Brand brand = new Brand(request.name(), request.description());
        Brand savedBrand = brandRepository.save(brand);
        auditLogService.logAction("Brand", savedBrand.getId(), "CREATE", "Created brand: " + savedBrand.getName());
        return savedBrand;
    }

    @Transactional
    public Optional<Brand> updateBrand(Long id, BrandRequest request) {
        return brandRepository.findById(id)
                .map(existingBrand -> {
                    existingBrand.setName(request.name());
                    existingBrand.setDescription(request.description());
                    Brand updatedBrand = brandRepository.save(existingBrand);
                    auditLogService.logAction("Brand", updatedBrand.getId(), "UPDATE", "Updated brand: " + updatedBrand.getName());
                    return updatedBrand;
                });
    }

    @Transactional
    public boolean deleteBrand(Long id) {
        if (!brandRepository.existsById(id)) {
            return false;
        }

        brandRepository.deleteById(id);
        auditLogService.logAction("Brand", id, "DELETE", "Deleted brand with id: " + id);
        return true;
    }
}
