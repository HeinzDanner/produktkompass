package de.heinzdanner.produktkompass.validation;

import java.net.URI;

public final class HttpUrls {
    private HttpUrls() {
    }

    public static String normalize(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null ? uri.toASCIIString() : null;
        } catch (IllegalArgumentException invalidUri) {
            return null;
        }
    }
}
