package com.gis.warplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.MapLayer;

public interface MapLayerRepository extends JpaRepository<MapLayer, Long> {

    Optional<MapLayer> findByCode(String code);

    List<MapLayer> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<MapLayer> findAllByOrderBySortOrderAscIdAsc();

    long countByGisServerId(Long gisServerId);
}
