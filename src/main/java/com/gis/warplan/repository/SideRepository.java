package com.gis.warplan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.Side;

public interface SideRepository extends JpaRepository<Side, Long> {

    List<Side> findByName(String name);

    List<Side> findByGameId(Long gameId);

    List<Side> findByKind(String kind);

    long countByThemeId(Long themeId);

    List<Side> findByThemeId(Long themeId);
}
