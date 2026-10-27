package com.civicbridge.analytics;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "user_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSession {
    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "end_reason", length = 20)
    private String endReason;
}
