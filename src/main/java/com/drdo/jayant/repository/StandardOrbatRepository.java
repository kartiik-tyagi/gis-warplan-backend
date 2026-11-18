package com.drdo.jayant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drdo.jayant.domain.StandardOrbat;

public interface StandardOrbatRepository extends JpaRepository<StandardOrbat, Long> {

    List<StandardOrbat> findBySideId(Long sideId);
}
