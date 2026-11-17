package com.drdo.jayant.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "alliance_member", uniqueConstraints = @UniqueConstraint(columnNames = {"allianceTeamId", "memberTeamId"}))
@Getter
@Setter
public class AllianceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long allianceTeamId;

    @Column(nullable = false)
    private Long memberTeamId;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
