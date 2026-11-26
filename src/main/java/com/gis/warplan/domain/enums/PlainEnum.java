package com.gis.warplan.domain.enums;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/** Choice that is not tied to a side. {@code code} is the primary key. */
@MappedSuperclass
@Getter
@Setter
public abstract class PlainEnum implements EnumMutable {

    @Id
    @Column(length = 40, nullable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    @Override
    public Long getSideId() {
        return null;
    }

    @Override
    public void setSideId(Long sideId) {
        // This list does not store a side.
    }
}
