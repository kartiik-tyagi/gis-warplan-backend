package com.gis.warplan.domain;

import java.util.Optional;

/** The moon phase chosen for a game. Stored by enum name. */
public enum MoonPhase {

    NEW_MOON("New moon"),
    WAXING_CRESCENT("Waxing crescent"),
    FIRST_QUARTER("First quarter"),
    WAXING_GIBBOUS("Waxing gibbous"),
    FULL_MOON("Full moon"),
    WANING_GIBBOUS("Waning gibbous"),
    LAST_QUARTER("Last quarter"),
    WANING_CRESCENT("Waning crescent");

    private final String label;

    MoonPhase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Accepts the enum name or the display label. Blank and unknown text do not match. */
    public static Optional<MoonPhase> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String text = raw.trim();
        String code = text.replace(' ', '_').replace('-', '_');
        for (MoonPhase phase : values()) {
            if (phase.name().equalsIgnoreCase(code) || phase.label.equalsIgnoreCase(text)) {
                return Optional.of(phase);
            }
        }
        return Optional.empty();
    }
}
