package com.gis.warplan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.Scenario;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    List<Scenario> findByActiveTrue();
}
