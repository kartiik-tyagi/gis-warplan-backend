package com.gis.warplan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.MapViewDefaults;

public interface MapViewDefaultsRepository extends JpaRepository<MapViewDefaults, Long> {

    Optional<MapViewDefaults> findFirstByOrderByIdAsc();
}
