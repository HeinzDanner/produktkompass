package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.dto.SourceRequest;
import de.heinzdanner.produktkompass.product.dto.LookupOption;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SourceService {

    private final SourceRepository sourceRepository;
    private final AuditLogService auditLogService;

    public SourceService(SourceRepository sourceRepository, AuditLogService auditLogService) {
        this.sourceRepository = sourceRepository;
        this.auditLogService = auditLogService;
    }

    public List<Source> getAllSources() {
        return sourceRepository.findAll();
    }

    public Page<Source> getSourcePage(String query, int page, int size) {
        return sourceRepository.search(MasterDataPaging.pattern(query), MasterDataPaging.request(page, size, "title"));
    }

    public List<LookupOption> getSourceOptions(Collection<Long> selectedIds) {
        return MasterDataPaging.options(selectedIds, getSourcePage("", 0, 20).getContent(),
                sourceRepository::findById, Source::getId, Source::getTitle);
    }

    public Optional<Source> getSourceById(Long id) {
        return sourceRepository.findById(id);
    }

    @Transactional
    public Source createSource(SourceRequest request) {
        Source source = new Source(request.title(), request.url(), request.type(), request.verifiedAt());
        Source savedSource = sourceRepository.save(source);
        auditLogService.logAction("Source", savedSource.getId(), "CREATE", "Created source: " + savedSource.getTitle());
        return savedSource;
    }

    @Transactional
    public Optional<Source> updateSource(Long id, SourceRequest request) {
        return sourceRepository.findById(id)
                .map(existingSource -> {
                    existingSource.setTitle(request.title());
                    existingSource.setUrl(request.url());
                    existingSource.setType(request.type());
                    existingSource.setVerifiedAt(request.verifiedAt());
                    Source updatedSource = sourceRepository.save(existingSource);
                    auditLogService.logAction("Source", updatedSource.getId(), "UPDATE", "Updated source: " + updatedSource.getTitle());
                    return updatedSource;
                });
    }

    @Transactional
    public boolean deleteSource(Long id) {
        if (!sourceRepository.existsById(id)) {
            return false;
        }

        sourceRepository.deleteById(id);
        auditLogService.logAction("Source", id, "DELETE", "Deleted source with id: " + id);
        return true;
    }
}
