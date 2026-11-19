package com.gis.warplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.gis.warplan.domain.AssetManagement;

import jakarta.persistence.LockModeType;

public interface AssetManagementRepository extends JpaRepository<AssetManagement, Long> {

    /** The singleton row (lowest id wins if more than one ever exists). */
    Optional<AssetManagement> findFirstByOrderByIdAsc();

    /**
     * The singleton row under a write lock, so concurrent per-user label edits of the shared row serialize
     * instead of overwriting each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AssetManagement a order by a.id asc")
    List<AssetManagement> findForUpdate();
}
