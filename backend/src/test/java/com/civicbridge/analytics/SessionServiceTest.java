package com.civicbridge.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionServiceTest {

    private UserSessionRepository repo;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private SessionService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        repo = mock(UserSessionRepository.class);
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        service = new SessionService(repo, redis, new AnalyticsProperties());
    }

    private UserSession open(String id, long uid, Instant expires) {
        return UserSession.builder().id(id).userId(uid).createdAt(Instant.now())
                .lastSeenAt(Instant.now()).expiresAt(expires).build();
    }

    @Test
    void isActive_trueFromRedisCache() {
        when(ops.get("cb:session:valid:s1")).thenReturn("7");
        assertThat(service.isActive("s1", 7L)).isTrue();
        assertThat(service.isActive("s1", 8L)).isFalse(); // session bound to another user
        verifyNoInteractions(repo);
    }

    @Test
    void isActive_fallsBackToDatabaseWhenRedisDown() {
        when(ops.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(repo.findById("s1")).thenReturn(Optional.of(open("s1", 7, Instant.now().plusSeconds(600))));
        assertThat(service.isActive("s1", 7L)).isTrue();
    }

    @Test
    void isActive_falseWhenEndedExpiredOrUnknown() {
        when(ops.get(anyString())).thenReturn(null);
        UserSession ended = open("e", 7, Instant.now().plusSeconds(600));
        ended.setEndedAt(Instant.now());
        when(repo.findById("e")).thenReturn(Optional.of(ended));
        when(repo.findById("x")).thenReturn(Optional.of(open("x", 7, Instant.now().minusSeconds(1))));
        when(repo.findById("u")).thenReturn(Optional.empty());
        assertThat(service.isActive("e", 7L)).isFalse();
        assertThat(service.isActive("x", 7L)).isFalse();
        assertThat(service.isActive("u", 7L)).isFalse();
        assertThat(service.isActive(null, 7L)).isFalse();
    }

    @Test
    void end_marksSessionEndedAndClearsCache() {
        UserSession s = open("s1", 7, Instant.now().plusSeconds(600));
        when(repo.findById("s1")).thenReturn(Optional.of(s));
        assertThat(service.end("s1", "LOGOUT")).isPresent();
        assertThat(s.getEndedAt()).isNotNull();
        assertThat(s.getEndReason()).isEqualTo("LOGOUT");
        verify(redis).delete("cb:session:valid:s1");
        assertThat(service.end("s1", "LOGOUT")).isEmpty(); // idempotent
    }

    @Test
    void touch_isThrottledByRedisKey() {
        when(ops.setIfAbsent(eq("cb:session:seen:s1"), anyString(), any())).thenReturn(false);
        service.touch("s1");
        verify(repo, never()).touch(any(), any(), any());
    }

    @Test
    void touch_fallsBackToConditionalDbUpdateWhenRedisDown() {
        when(ops.setIfAbsent(anyString(), anyString(), any())).thenThrow(new RuntimeException("down"));
        service.touch("s1");
        verify(repo).touch(eq("s1"), any(), any());
    }
}
