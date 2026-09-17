package de.heinzdanner.produktkompass.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EditorialAssessmentRepository extends JpaRepository<EditorialAssessment, Long> {

    List<EditorialAssessment> findByProductId(Long productId);

    List<EditorialAssessment> findByStatus(EditorialAssessment.AssessmentStatus status);
}
