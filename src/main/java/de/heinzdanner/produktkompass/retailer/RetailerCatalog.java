package de.heinzdanner.produktkompass.retailer;

import java.time.LocalDateTime;

public record RetailerCatalog(String code, String displayName, String sourceKind,
                              LocalDateTime importedAt, long productCount) {
    public String sourceDescription() {
        return "SYNTHETIC".equals(sourceKind) ? "Synthetische Demo-Daten" : "Importierter Katalog-Snapshot";
    }
}
