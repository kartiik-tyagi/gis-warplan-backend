package com.gis.warplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.GisServer;

public interface GisServerRepository extends JpaRepository<GisServer, Long> {

    Optional<GisServer> findByCode(String code);

    Optional<GisServer> findFirstByDefaultServerTrueAndEnabledTrueOrderByIdAsc();

    Optional<GisServer> findFirstByEnabledTrueOrderByIdAsc();

    List<GisServer> findAllByOrderByIdAsc();

    long countByDefaultServerTrue();
}
