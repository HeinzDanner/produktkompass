package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.dto.BrandRequest;
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
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private BrandService brandService;

    @Test
    void createBrandSavesBrand() {
        BrandRequest request = new BrandRequest("Alnatura", "Bio-Marke");
        Brand savedBrand = new Brand("Alnatura", "Bio-Marke");

        when(brandRepository.save(any(Brand.class))).thenReturn(savedBrand);

        Brand result = brandService.createBrand(request);

        assertThat(result.getName()).isEqualTo("Alnatura");
        assertThat(result.getDescription()).isEqualTo("Bio-Marke");
    }

    @Test
    void updateBrandUpdatesExistingBrand() {
        Brand existing = new Brand("Alt", "Alt");
        BrandRequest request = new BrandRequest("Neu", "Neu");

        when(brandRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(brandRepository.save(existing)).thenReturn(existing);

        Optional<Brand> result = brandService.updateBrand(1L, request);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Neu");
        assertThat(result.get().getDescription()).isEqualTo("Neu");
    }
}
