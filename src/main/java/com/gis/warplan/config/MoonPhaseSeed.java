package com.gis.warplan.config;

import com.gis.warplan.domain.MoonPhase;

/** The eight phases shipped with the application. Existing rows are left alone so a later icon change sticks. */
final class MoonPhaseSeed {

    private MoonPhaseSeed() {
    }

    static MoonPhase[] rows() {
        return new MoonPhase[] {
            row("NEW_MOON", "New moon", 1, disc(false)),
            row("WAXING_CRESCENT", "Waxing crescent", 2, crescent(true)),
            row("FIRST_QUARTER", "First quarter", 3, quarter(true)),
            row("WAXING_GIBBOUS", "Waxing gibbous", 4, gibbous(true)),
            row("FULL_MOON", "Full moon", 5, disc(true)),
            row("WANING_GIBBOUS", "Waning gibbous", 6, gibbous(false)),
            row("LAST_QUARTER", "Last quarter", 7, quarter(false)),
            row("WANING_CRESCENT", "Waning crescent", 8, crescent(false))
        };
    }

    private static MoonPhase row(String code, String label, int sortOrder, String body) {
        MoonPhase phase = new MoonPhase();
        phase.setCode(code);
        phase.setLabel(label);
        phase.setSortOrder(sortOrder);
        phase.setIconSvg("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" + body + "</svg>");
        return phase;
    }

    private static String disc(boolean filled) {
        return filled
                ? "<circle cx=\"12\" cy=\"12\" r=\"8\" fill=\"currentColor\"/>"
                : "<circle cx=\"12\" cy=\"12\" r=\"7.25\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.6\"/>";
    }

    /** Right side lit when waxing, left side lit when waning. */
    private static String quarter(boolean waxing) {
        String sweep = waxing ? "1" : "0";
        return "<path fill=\"currentColor\" d=\"M12 4A8 8 0 0 " + sweep + " 12 20Z\"/>"
                + "<circle cx=\"12\" cy=\"12\" r=\"8\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.4\"/>";
    }

    private static String crescent(boolean waxing) {
        String path = waxing
                ? "M15.4 5.2A8 8 0 0 1 15.4 18.8 5.6 8 0 0 0 15.4 5.2Z"
                : "M8.6 5.2A8 8 0 0 0 8.6 18.8 5.6 8 0 0 1 8.6 5.2Z";
        return "<circle cx=\"12\" cy=\"12\" r=\"8\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.4\" opacity=\"0.4\"/>"
                + "<path fill=\"currentColor\" d=\"" + path + "\"/>";
    }

    private static String gibbous(boolean waxing) {
        String bite = waxing
                ? "M9.2 6.1A7.2 8 0 0 0 9.2 17.9 4.4 8 0 0 1 9.2 6.1Z"
                : "M14.8 6.1A7.2 8 0 0 1 14.8 17.9 4.4 8 0 0 0 14.8 6.1Z";
        return "<path fill=\"currentColor\" fill-rule=\"evenodd\" d=\"M12 4a8 8 0 1 0 0.01 0Z " + bite + "\"/>";
    }
}
