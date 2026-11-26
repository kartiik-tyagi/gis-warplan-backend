package com.gis.warplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One moon phase. {@code code} is what {@code game.moonphases} stores. */
@Entity
@Table(name = "moon_phase")
@Getter
@Setter
public class MoonPhase {

    @Id
    @Column(length = 40, nullable = false)
    private String code;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false)
    private int sortOrder;

    /** Storage key of an icon in the icon store, such as moon-full.svg. */
    @Column(length = 240)
    private String icon;
}
