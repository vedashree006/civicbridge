package com.civicbridge.analytics;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "consent_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConsentRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "policy_version", nullable = false, length = 20)
    private String policyVersion;
    @Column(nullable = false) private boolean preferences;
    @Column(nullable = false) private boolean analytics;
    @Column(nullable = false) private boolean marketing;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
