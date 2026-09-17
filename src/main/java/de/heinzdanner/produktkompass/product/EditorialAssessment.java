package de.heinzdanner.produktkompass.product;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "editorial_assessments")
public class EditorialAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Ein Produkt muss angegeben werden.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull(message = "Der Bewertungstyp muss angegeben werden.")
    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false, length = 50)
    private AssessmentType assessmentType;

    @NotBlank(message = "Das Label darf nicht leer sein.")
    @Size(max = 255, message = "Das Label darf maximal 255 Zeichen lang sein.")
    @Column(nullable = false, length = 255)
    private String label;

    @Min(value = 1, message = "Der Score muss mindestens 1 sein.")
    @Max(value = 5, message = "Der Score darf maximal 5 sein.")
    @Column
    private Integer score;

    @Size(max = 2000, message = "Die Begründung darf maximal 2000 Zeichen lang sein.")
    @Column(length = 2000)
    private String rationale;

    @NotNull(message = "Der Status muss angegeben werden.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssessmentStatus status = AssessmentStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private Source source;

    @Size(max = 255, message = "Der Autor darf maximal 255 Zeichen lang sein.")
    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected EditorialAssessment() {
    }

    public EditorialAssessment(Product product, AssessmentType assessmentType, String label, Integer score, String rationale) {
        this.product = product;
        this.assessmentType = assessmentType;
        this.label = label;
        this.score = score;
        this.rationale = rationale;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public AssessmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssessmentStatus status) {
        this.status = status;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public enum AssessmentType {
        ENVIRONMENTAL,
        LABOR_RIGHTS,
        LEGAL_RISK,
        SUPPLY_CHAIN,
        POLITICAL_ASSOCIATION
    }

    public enum AssessmentStatus {
        DRAFT,
        PUBLISHED,
        REJECTED
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EditorialAssessment that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
