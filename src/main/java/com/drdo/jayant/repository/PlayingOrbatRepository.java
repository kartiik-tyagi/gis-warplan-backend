package com.drdo.jayant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drdo.jayant.domain.PlayingOrbat;

public interface PlayingOrbatRepository extends JpaRepository<PlayingOrbat, Long> {

    List<PlayingOrbat> findBySideId(Long sideId);

    List<PlayingOrbat> findByScenarioId(Long scenarioId);

    List<PlayingOrbat> findByGameId(Long gameId);

    Optional<PlayingOrbat> findFirstByGameIdAndSideId(Long gameId, Long sideId);
}
