package com.gis.warplan.domain;

import java.util.Locale;

public enum AssetKind {
    ICON("icons"),
    IMAGE("images"),
    DOCUMENT("documents"),
    APPLICATION("application");

    private final String folder;

    AssetKind(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }

    public static AssetKind fromQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AssetKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
