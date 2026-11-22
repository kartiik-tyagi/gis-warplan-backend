package com.gis.warplan.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link MoonPhase} by name. Text that is not a phase is read as empty so older notes do not break loading. */
@Converter
public class MoonPhaseConverter implements AttributeConverter<MoonPhase, String> {

    @Override
    public String convertToDatabaseColumn(MoonPhase phase) {
        return phase == null ? null : phase.name();
    }

    @Override
    public MoonPhase convertToEntityAttribute(String stored) {
        return MoonPhase.parse(stored).orElse(null);
    }
}
