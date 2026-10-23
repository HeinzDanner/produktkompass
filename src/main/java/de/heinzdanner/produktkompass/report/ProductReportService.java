package de.heinzdanner.produktkompass.report;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@Transactional(readOnly = true)
public class ProductReportService {

    private final ProductRepository products;
    private final ProductReportRepository reports;
    private final AuditLogService auditLogs;

    public ProductReportService(ProductRepository products, ProductReportRepository reports,
                                AuditLogService auditLogs) {
        this.products = products;
        this.reports = reports;
        this.auditLogs = auditLogs;
    }

    public Product getProduct(Long id) {
        return products.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt wurde nicht gefunden."));
    }

    @Transactional
    public void submit(Long productId, @NotNull @Valid ProductReportRequest request) {
        reports.save(new ProductReport(getProduct(productId), request));
    }

    public Page<ProductReport> queue(ReportStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Seitenauswahl.");
        }
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("submittedAt"), Sort.Order.desc("id")));
        return status == null ? reports.findAll(pageable) : reports.findByStatus(status, pageable);
    }

    @Transactional
    public void moderate(Long id, @NotNull ReportStatus status,
                         @NotBlank @Size(max = 255) String moderator) {
        ProductReport report = reports.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Meldung wurde nicht gefunden."));
        ReportStatus previous = report.getStatus();
        if (previous != status) {
            report.moderate(status, moderator);
            auditLogs.logAction("ProductReport", id, "STATUS_UPDATE",
                    "Status: " + previous + " -> " + status + "; Bearbeitet von: " + moderator);
        }
    }
}
