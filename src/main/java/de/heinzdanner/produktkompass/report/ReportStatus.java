package de.heinzdanner.produktkompass.report;

public enum ReportStatus {
    OPEN("Offen"), REVIEWED("Geprüft"), CLOSED("Geschlossen");

    private final String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
