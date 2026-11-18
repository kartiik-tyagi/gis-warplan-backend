package com.drdo.jayant.domain;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "alliance_member", uniqueConstraints = @UniqueConstraint(columnNames = {"allianceSideId", "memberSideId"}))
@Getter
@Setter
public class AllianceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long allianceSideId;

    @Column(nullable = false)
    private Long memberSideId;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
