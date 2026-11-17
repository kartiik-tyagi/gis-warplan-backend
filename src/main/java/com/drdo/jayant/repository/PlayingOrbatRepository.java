package com.drdo.jayant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drdo.jayant.domain.PlayingOrbat;

public interface PlayingOrbatRepository extends JpaRepository<PlayingOrbat, Long> {

    List<PlayingOrbat> findByTeamId(Long teamId);

    List<PlayingOrbat> findByScenarioId(Long scenarioId);
}
