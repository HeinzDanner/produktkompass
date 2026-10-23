package de.heinzdanner.produktkompass.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductReportRepository extends JpaRepository<ProductReport, Long> {

    @Override
    @EntityGraph(attributePaths = "product")
    Page<ProductReport> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "product")
    Page<ProductReport> findByStatus(ReportStatus status, Pageable pageable);
}
