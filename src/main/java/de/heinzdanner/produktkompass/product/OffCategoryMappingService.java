package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.dto.OffCategoryOption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class OffCategoryMappingService {
    private final OffCategoryMappingRepository mappings;
    private final OpenFoodFactsDataRepository data;
    private final CategoryRepository categories;
    private final AuditLogService audit;

    public OffCategoryMappingService(OffCategoryMappingRepository mappings, OpenFoodFactsDataRepository data,
                                     CategoryRepository categories, AuditLogService audit) {
        this.mappings = mappings;
        this.data = data;
        this.categories = categories;
        this.audit = audit;
    }

    public Page<OffCategoryOption> page(String query, int page) {
        MasterDataPaging.request(page, 20, "name");
        return data.categoryMappingPage(MasterDataPaging.pattern(query), PageRequest.of(page, 20));
    }

    @Transactional
    public void assign(String value, Long categoryId) {
        if (value == null || value.isBlank() || data.countCategoryEntry(value) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OFF-Kategorie nicht im Bestand gefunden.");
        }
        var previous = mappings.findById(value);
        Long previousId = previous.map(mapping -> mapping.getCategory().getId()).orElse(null);
        if (categoryId == null) {
            if (previous.isEmpty()) return;
            mappings.delete(previous.orElseThrow());
        } else {
            var category = categories.findById(categoryId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "Katalogkategorie nicht gefunden."));
            if (categoryId.equals(previousId)) return;
            mappings.save(new OffCategoryMapping(value, category));
        }
        audit.logAction("OffCategoryMapping", categoryId, categoryId == null ? "DELETE" : previous.isPresent() ? "UPDATE" : "CREATE",
                "OFF: " + value + "; Kategorie: " + previousId + " -> " + categoryId);
    }
}
