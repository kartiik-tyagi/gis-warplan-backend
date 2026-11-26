package com.gis.warplan.config;

import com.gis.warplan.domain.MoonPhase;

/** The eight phases. The icon value is a file in the icon store. */
final class MoonPhaseSeed {

    private MoonPhaseSeed() {
    }

    static MoonPhase[] rows() {
        return new MoonPhase[] {
            row("NEW_MOON", "New moon", 1, "moon-new.svg"),
            row("WAXING_CRESCENT", "Waxing crescent", 2, "moon-waxing-crescent.svg"),
            row("FIRST_QUARTER", "First quarter", 3, "moon-first-quarter.svg"),
            row("WAXING_GIBBOUS", "Waxing gibbous", 4, "moon-waxing-gibbous.svg"),
            row("FULL_MOON", "Full moon", 5, "moon-full.svg"),
            row("WANING_GIBBOUS", "Waning gibbous", 6, "moon-waning-gibbous.svg"),
            row("LAST_QUARTER", "Last quarter", 7, "moon-last-quarter.svg"),
            row("WANING_CRESCENT", "Waning crescent", 8, "moon-waning-crescent.svg")
        };
    }

    private static MoonPhase row(String code, String label, int sortOrder, String icon) {
        MoonPhase phase = new MoonPhase();
        phase.setCode(code);
        phase.setLabel(label);
        phase.setSortOrder(sortOrder);
        phase.setIcon(icon);
        return phase;
    }
}
