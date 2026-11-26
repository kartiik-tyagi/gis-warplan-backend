package com.gis.warplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.MapLayerGroup;

public interface MapLayerGroupRepository extends JpaRepository<MapLayerGroup, Long> {

    Optional<MapLayerGroup> findByCode(String code);

    Optional<MapLayerGroup> findByCodeIgnoreCase(String code);

    Optional<MapLayerGroup> findByGisServerIdAndLabelIgnoreCase(Long gisServerId, String label);

    List<MapLayerGroup> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<MapLayerGroup> findAllByOrderBySortOrderAscIdAsc();

    List<MapLayerGroup> findByGisServerIdOrderBySortOrderAscIdAsc(Long gisServerId);

    long countByGisServerId(Long gisServerId);
}
