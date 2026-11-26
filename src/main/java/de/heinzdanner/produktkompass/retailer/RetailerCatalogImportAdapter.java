package de.heinzdanner.produktkompass.retailer;

import java.io.Reader;
import java.util.List;

public interface RetailerCatalogImportAdapter {
    List<RetailerProductImportRow> parse(Reader reader) throws Exception;
}
