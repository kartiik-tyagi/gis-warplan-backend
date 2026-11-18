package com.gis.warplan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.Functionality;

public interface FunctionalityRepository extends JpaRepository<Functionality, Long> {

    Optional<Functionality> findByCode(String code);
}
