package com.gis.warplan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.AllianceMember;

public interface AllianceMemberRepository extends JpaRepository<AllianceMember, Long> {

    List<AllianceMember> findByAllianceSideId(Long allianceSideId);

    List<AllianceMember> findByMemberSideId(Long memberSideId);
}
