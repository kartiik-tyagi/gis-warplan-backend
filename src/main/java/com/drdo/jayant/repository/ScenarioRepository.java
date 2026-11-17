package com.drdo.jayant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drdo.jayant.domain.Scenario;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    List<Scenario> findByActiveTrue();

    long countByThemeId(Long themeId);
}
