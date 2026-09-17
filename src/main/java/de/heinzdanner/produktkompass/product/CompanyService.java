package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.dto.CompanyRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    public CompanyService(CompanyRepository companyRepository, AuditLogService auditLogService) {
        this.companyRepository = companyRepository;
        this.auditLogService = auditLogService;
    }

    public List<Company> getAllCompanies() {
        return companyRepository.findAll();
    }

    public Optional<Company> getCompanyById(Long id) {
        return companyRepository.findById(id);
    }

    @Transactional
    public Company createCompany(CompanyRequest request) {
        Company company = new Company(request.name(), request.website(), request.country(), request.description());
        Company savedCompany = companyRepository.save(company);
        auditLogService.logAction("Company", savedCompany.getId(), "CREATE", "Created company: " + savedCompany.getName());
        return savedCompany;
    }

    @Transactional
    public Optional<Company> updateCompany(Long id, CompanyRequest request) {
        return companyRepository.findById(id)
                .map(existingCompany -> {
                    existingCompany.setName(request.name());
                    existingCompany.setWebsite(request.website());
                    existingCompany.setCountry(request.country());
                    existingCompany.setDescription(request.description());
                    Company updatedCompany = companyRepository.save(existingCompany);
                    auditLogService.logAction("Company", updatedCompany.getId(), "UPDATE", "Updated company: " + updatedCompany.getName());
                    return updatedCompany;
                });
    }

    @Transactional
    public boolean deleteCompany(Long id) {
        if (!companyRepository.existsById(id)) {
            return false;
        }

        companyRepository.deleteById(id);
        auditLogService.logAction("Company", id, "DELETE", "Deleted company with id: " + id);
        return true;
    }
}
