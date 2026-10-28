package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class EditorialAssessmentService {

    private final EditorialAssessmentRepository editorialAssessmentRepository;
    private final AuditLogService auditLogService;

    public EditorialAssessmentService(EditorialAssessmentRepository editorialAssessmentRepository,
                                      AuditLogService auditLogService) {
        this.editorialAssessmentRepository = editorialAssessmentRepository;
        this.auditLogService = auditLogService;
    }

    public List<EditorialAssessment> getAllAssessments() {
        return editorialAssessmentRepository.findAll();
    }

    public List<EditorialAssessment> getAssessmentsForProduct(Long productId) {
        return editorialAssessmentRepository.findByProductId(productId);
    }

    public Optional<EditorialAssessment> getById(Long id) {
        return editorialAssessmentRepository.findById(id);
    }

    @Transactional
    public EditorialAssessment createAssessment(EditorialAssessment assessment) {
        EditorialAssessment saved = editorialAssessmentRepository.save(assessment);
        auditLogService.logAction("EditorialAssessment", saved.getId(), "CREATE",
                "Created assessment for product: " + saved.getProduct().getId() + "; Status: " + saved.getStatus());
        return saved;
    }

    @Transactional
    public Optional<EditorialAssessment> updateAssessment(Long id, EditorialAssessment updatedAssessment) {
        return editorialAssessmentRepository.findById(id)
                .map(existing -> {
                    existing.setAssessmentType(updatedAssessment.getAssessmentType());
                    existing.setLabel(updatedAssessment.getLabel());
                    existing.setScore(updatedAssessment.getScore());
                    existing.setRationale(updatedAssessment.getRationale());
                    existing.setStatus(updatedAssessment.getStatus());
                    existing.setSource(updatedAssessment.getSource());
                    existing.setReviewedBy(updatedAssessment.getReviewedBy());
                    existing.setReviewedAt(updatedAssessment.getReviewedAt());
                    EditorialAssessment saved = editorialAssessmentRepository.save(existing);
                    auditLogService.logAction("EditorialAssessment", saved.getId(), "UPDATE",
                            "Updated assessment for product: " + saved.getProduct().getId() + "; Status: " + saved.getStatus());
                    return saved;
                });
    }

    @Transactional
    public boolean deleteAssessment(Long id) {
        if (!editorialAssessmentRepository.existsById(id)) {
            return false;
        }
        editorialAssessmentRepository.deleteById(id);
        auditLogService.logAction("EditorialAssessment", id, "DELETE", "Deleted assessment with id: " + id);
        return true;
    }
}
