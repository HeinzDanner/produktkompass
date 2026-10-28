package de.heinzdanner.produktkompass.cart;

public enum TagState {
    NEUTRAL, PREFERRED, EXCLUDED;

    public String value() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static TagState fromValue(String value) {
        for (TagState state : values()) {
            if (state.value().equals(value)) return state;
        }
        throw new IllegalArgumentException("Unbekannter Tag-Zustand: " + value);
    }
}
