package com.gis.warplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByName(String name);

    List<AppUser> findByAdministeredSideId(Long sideId);

    List<AppUser> findByPlayingOrbatId(Long playingOrbatId);

    List<AppUser> findByStandardNodeIdIsNotNull();

    List<AppUser> findByActiveTrueOrderByNameAsc();

    long countByThemeId(Long themeId);
}
