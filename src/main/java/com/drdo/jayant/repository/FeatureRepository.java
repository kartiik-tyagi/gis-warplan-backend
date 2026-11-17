package com.drdo.jayant.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drdo.jayant.domain.FeatureEntity;

public interface FeatureRepository extends JpaRepository<FeatureEntity, Long> {

    Optional<FeatureEntity> findByCode(String code);
}
