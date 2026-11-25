package com.gis.warplan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.Game;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByScenarioId(Long scenarioId);

    List<Game> findByScenarioIdOrderByNameAsc(Long scenarioId);

    List<Game> findByActiveTrue();
}
