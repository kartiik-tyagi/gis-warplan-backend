package com.drdo.jayant.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drdo.jayant.domain.NodeType;

public interface NodeTypeRepository extends JpaRepository<NodeType, Long> {

    Optional<NodeType> findByCode(String code);
}
