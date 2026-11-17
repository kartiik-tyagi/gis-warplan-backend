package com.drdo.jayant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.drdo.jayant.domain.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenId(String tokenId);

    List<UserSession> findByUserId(Long userId);

    Page<UserSession> findByUserId(Long userId, Pageable pageable);
}
