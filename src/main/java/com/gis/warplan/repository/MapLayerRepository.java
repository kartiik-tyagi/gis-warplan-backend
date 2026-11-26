package com.gis.warplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.MapLayer;

public interface MapLayerRepository extends JpaRepository<MapLayer, Long> {

    Optional<MapLayer> findByCode(String code);

    Optional<MapLayer> findByGisServerIdAndWmsLayers(Long gisServerId, String wmsLayers);

    List<MapLayer> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<MapLayer> findAllByOrderBySortOrderAscIdAsc();

    List<MapLayer> findByGisServerIdOrderBySortOrderAscIdAsc(Long gisServerId);

    long countByGisServerId(Long gisServerId);
}
