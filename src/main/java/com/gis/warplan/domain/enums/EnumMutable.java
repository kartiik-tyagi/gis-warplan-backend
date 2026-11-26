package com.gis.warplan.domain.enums;

/** A choice row. Side lists also carry {@code sideId}; other lists leave it empty. */
public interface EnumMutable {

    String getCode();

    void setCode(String code);

    String getLabel();

    void setLabel(String label);

    int getSortOrder();

    void setSortOrder(int sortOrder);

    boolean isActive();

    void setActive(boolean active);

    Long getSideId();

    void setSideId(Long sideId);
}
