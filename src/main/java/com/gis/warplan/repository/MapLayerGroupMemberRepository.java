package com.gis.warplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.MapLayerGroupMember;

public interface MapLayerGroupMemberRepository extends JpaRepository<MapLayerGroupMember, Long> {

    List<MapLayerGroupMember> findByGroupIdOrderBySortOrderAscIdAsc(Long groupId);

    Optional<MapLayerGroupMember> findByGroupIdAndMapLayerId(Long groupId, Long mapLayerId);

    void deleteByGroupId(Long groupId);

    void deleteByMapLayerId(Long mapLayerId);

    long countByMapLayerId(Long mapLayerId);
}
