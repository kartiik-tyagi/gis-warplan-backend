package com.gis.warplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.MoonPhase;

public interface MoonPhaseRepository extends JpaRepository<MoonPhase, String> {

    List<MoonPhase> findAllByOrderBySortOrderAsc();

    Optional<MoonPhase> findByCodeIgnoreCase(String code);

    Optional<MoonPhase> findByLabelIgnoreCase(String label);
}
