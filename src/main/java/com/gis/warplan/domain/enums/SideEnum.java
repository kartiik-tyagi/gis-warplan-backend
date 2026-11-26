package com.gis.warplan.domain.enums;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Choice tied to a country side. The primary key is {@code code} plus {@code sideId},
 * so the same code and label may exist for another side.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class SideEnum implements EnumMutable {

    @Id
    @Column(length = 40, nullable = false)
    private String code;

    @Id
    @Column(name = "side_id", nullable = false)
    private Long sideId;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;
}
