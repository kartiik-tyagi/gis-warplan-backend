package com.drdo.jayant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drdo.jayant.domain.AllianceMember;

public interface AllianceMemberRepository extends JpaRepository<AllianceMember, Long> {

    List<AllianceMember> findByAllianceTeamId(Long allianceTeamId);

    List<AllianceMember> findByMemberTeamId(Long memberTeamId);
}
