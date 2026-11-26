package com.gis.warplan.domain.enums;

import java.io.Serializable;
import java.util.Objects;

/** Identity of a choice that belongs to a side. The code may repeat on another side. */
public class EnumSideKey implements Serializable {

    private String code;

    private Long sideId;

    public EnumSideKey() {
    }

    public EnumSideKey(String code, Long sideId) {
        this.code = code;
        this.sideId = sideId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getSideId() {
        return sideId;
    }

    public void setSideId(Long sideId) {
        this.sideId = sideId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EnumSideKey key)) {
            return false;
        }
        return Objects.equals(code, key.code) && Objects.equals(sideId, key.sideId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, sideId);
    }
}
