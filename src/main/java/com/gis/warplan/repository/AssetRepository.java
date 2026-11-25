package com.gis.warplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.ApplicationSlot;
import com.gis.warplan.domain.Asset;
import com.gis.warplan.domain.AssetKind;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByStorageKey(String storageKey);

    List<Asset> findByKindOrderByOriginalNameAsc(AssetKind kind);

    Optional<Asset> findByKindAndSlot(AssetKind kind, ApplicationSlot slot);

    boolean existsByStorageKey(String storageKey);
}
