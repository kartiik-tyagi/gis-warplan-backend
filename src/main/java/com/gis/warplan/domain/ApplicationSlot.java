package com.gis.warplan.domain;

import java.util.Locale;

public enum ApplicationSlot {
    LOGO("logo"),
    FAVICON("favicon"),
    LOGIN_BACKGROUND("login-background");

    private final String key;

    ApplicationSlot(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ApplicationSlot fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (ApplicationSlot slot : values()) {
            if (slot.key.equals(value) || slot.name().equalsIgnoreCase(value)) {
                return slot;
            }
        }
        return null;
    }
}
