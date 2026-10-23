package de.heinzdanner.produktkompass.report;

public enum ReportTargetField {
    NAME("Produktname"), BRAND("Marke"), COMPANY("Unternehmen"),
    INGREDIENTS("Zutaten / Allergene"), NUTRITION("Nährwerte / Nutri-Score"),
    LABELS("Labels / Siegel"), PACKAGING("Verpackung / Menge"),
    ORIGIN("Herkunft"), OTHER("Sonstiges");

    private final String label;

    ReportTargetField(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
