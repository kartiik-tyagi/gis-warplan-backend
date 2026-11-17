package com.drdo.jayant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drdo.jayant.domain.Game;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByScenarioId(Long scenarioId);

    List<Game> findByActiveTrue();
}
