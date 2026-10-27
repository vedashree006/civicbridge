package com.civicbridge.analytics;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "analytics_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalyticsEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EventType eventType;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "session_id", length = 36)
    private String sessionId;
    @Column(length = 200)
    private String path;
    /** Daily-rotating salted hash for consented anonymous visitor counts. Never an IP or fingerprint. */
    @Column(name = "visitor_hash", length = 64)
    private String visitorHash;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
