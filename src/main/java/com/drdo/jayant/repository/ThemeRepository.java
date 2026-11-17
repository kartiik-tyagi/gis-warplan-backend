package com.drdo.jayant.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drdo.jayant.domain.Theme;

public interface ThemeRepository extends JpaRepository<Theme, Long> {

    Optional<Theme> findByName(String name);

    Optional<Theme> findFirstByDefaultThemeTrueOrderByIdAsc();
}
