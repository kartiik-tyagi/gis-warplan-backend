package com.gis.warplan.config;

public final class DatabaseName {

    private static final String IDENTIFIER = "[A-Za-z][A-Za-z0-9_]{0,62}";

    private DatabaseName() {
    }

    public static String requireIdentifier(String name) {
        if (name == null || !name.matches(IDENTIFIER)) {
            throw new IllegalStateException("Database name must be a plain identifier.");
        }
        return name;
    }
}
