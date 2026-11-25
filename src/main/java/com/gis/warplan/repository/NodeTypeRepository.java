package com.gis.warplan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.NodeType;

public interface NodeTypeRepository extends JpaRepository<NodeType, Long> {

    Optional<NodeType> findByCode(String code);

    boolean existsByIconPath(String iconPath);
}
