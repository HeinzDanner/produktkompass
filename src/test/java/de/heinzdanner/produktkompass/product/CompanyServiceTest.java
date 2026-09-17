package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.dto.CompanyRequest;
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
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CompanyService companyService;

    @Test
    void createCompanySavesCompany() {
        CompanyRequest request = new CompanyRequest("ACME", "https://acme.example", "DE", "Hersteller");
        Company saved = new Company("ACME", "https://acme.example", "DE", "Hersteller");

        when(companyRepository.save(any(Company.class))).thenReturn(saved);

        Company result = companyService.createCompany(request);

        assertThat(result.getName()).isEqualTo("ACME");
        assertThat(result.getCountry()).isEqualTo("DE");
    }

    @Test
    void updateCompanyUpdatesExistingCompany() {
        Company existing = new Company("Alt", "https://alt.example", "FR", "Alt");
        CompanyRequest request = new CompanyRequest("Neu", "https://neu.example", "AT", "Neu");

        when(companyRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(companyRepository.save(existing)).thenReturn(existing);

        Optional<Company> result = companyService.updateCompany(3L, request);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Neu");
        assertThat(result.get().getCountry()).isEqualTo("AT");
    }
}
