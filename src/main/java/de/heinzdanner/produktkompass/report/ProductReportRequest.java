package de.heinzdanner.produktkompass.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProductReportRequest {

    @NotBlank(message = "Bitte beschreibe den vermuteten Fehler.")
    @Size(max = 2000, message = "Die Beschreibung darf höchstens 2000 Zeichen enthalten.")
    private String description;

    private ReportTargetField targetField;
    private ReportProvenance provenance;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ReportTargetField getTargetField() {
        return targetField;
    }

    public void setTargetField(ReportTargetField targetField) {
        this.targetField = targetField;
    }

    public ReportProvenance getProvenance() {
        return provenance;
    }

    public void setProvenance(ReportProvenance provenance) {
        this.provenance = provenance;
    }
}
