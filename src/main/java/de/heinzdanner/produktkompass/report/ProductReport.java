package de.heinzdanner.produktkompass.report;

import de.heinzdanner.produktkompass.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_reports")
public class ProductReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @NotBlank
    @Size(max = 2000)
    @Column(nullable = false, length = 2000, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_field", length = 30, updatable = false)
    private ReportTargetField targetField;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, updatable = false)
    private ReportProvenance provenance;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "moderated_by", length = 255)
    private String moderatedBy;

    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;

    protected ProductReport() {
    }

    ProductReport(Product product, ProductReportRequest request) {
        this.product = product;
        this.description = request.getDescription().strip();
        this.targetField = request.getTargetField();
        this.provenance = request.getProvenance();
        this.submittedAt = LocalDateTime.now();
    }

    void moderate(ReportStatus status, String moderator) {
        this.status = status;
        this.moderatedBy = moderator;
        this.moderatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getDescription() { return description; }
    public ReportTargetField getTargetField() { return targetField; }
    public ReportProvenance getProvenance() { return provenance; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public ReportStatus getStatus() { return status; }
    public String getModeratedBy() { return moderatedBy; }
    public LocalDateTime getModeratedAt() { return moderatedAt; }
}
