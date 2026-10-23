package de.heinzdanner.produktkompass.report;

public enum ReportProvenance {
    APP_CATALOG("App-Katalog"), OPEN_FOOD_FACTS("Open Food Facts");

    private final String label;

    ReportProvenance(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
