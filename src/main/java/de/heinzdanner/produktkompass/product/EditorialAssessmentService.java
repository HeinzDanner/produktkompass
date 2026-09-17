package de.heinzdanner.produktkompass.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class EditorialAssessmentService {

    private final EditorialAssessmentRepository editorialAssessmentRepository;

    public EditorialAssessmentService(EditorialAssessmentRepository editorialAssessmentRepository) {
        this.editorialAssessmentRepository = editorialAssessmentRepository;
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
        return editorialAssessmentRepository.save(assessment);
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
                    return editorialAssessmentRepository.save(existing);
                });
    }

    @Transactional
    public boolean deleteAssessment(Long id) {
        if (!editorialAssessmentRepository.existsById(id)) {
            return false;
        }
        editorialAssessmentRepository.deleteById(id);
        return true;
    }
}
