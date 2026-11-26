package com.gis.warplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.GsStyle;

public interface GsStyleRepository extends JpaRepository<GsStyle, Long> {

    List<GsStyle> findByGisServerIdOrderByNameAsc(Long gisServerId);

    Optional<GsStyle> findByGisServerIdAndName(Long gisServerId, String name);
}
