package com.civicbridge.analytics;

import com.civicbridge.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    private AnalyticsEventRepository events;
    private UserSessionRepository sessions;
    private UserRepository users;
    private SessionService sessionService;
    private AnalyticsProperties props;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        events = mock(AnalyticsEventRepository.class);
        sessions = mock(UserSessionRepository.class);
        users = mock(UserRepository.class);
        sessionService = mock(SessionService.class);
        props = new AnalyticsProperties();
        props.setHashSalt("unit-test-salt");
        service = new AnalyticsService(events, sessions, users, sessionService, props, new SimpleRateLimiter());
    }

    @Test
    void normalizePath_rejectsInvalidAndCollapsesIds() {
        assertThat(AnalyticsService.normalizePath("/cases/123")).isEqualTo("/cases/:id");
        assertThat(AnalyticsService.normalizePath("/")).isEqualTo("/");
        assertThat(AnalyticsService.normalizePath("/unknown/xyz")).isEqualTo("/other");
        assertThat(AnalyticsService.normalizePath("/cases?id=1")).isNull();
        assertThat(AnalyticsService.normalizePath("http://evil")).isNull();
        assertThat(AnalyticsService.normalizePath(null)).isNull();
    }

    @Test
    void collectPageView_storesValidEventWithoutRawVisitorId() {
        String vid = UUID.randomUUID().toString();
        var result = service.collectPageView("/dashboard", vid, null, null);
        assertThat(result).isEqualTo(AnalyticsService.CollectResult.STORED);
        ArgumentCaptor<AnalyticsEvent> cap = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(events).save(cap.capture());
        assertThat(cap.getValue().getEventType()).isEqualTo(EventType.PAGE_VIEW);
        assertThat(cap.getValue().getVisitorHash()).hasSize(64).doesNotContain(vid);
    }

    @Test
    void collectPageView_rejectsMalformedVisitorId() {
        assertThat(service.collectPageView("/dashboard", "not-a-uuid", null, null))
                .isEqualTo(AnalyticsService.CollectResult.INVALID);
        verify(events, never()).save(any());
    }

    @Test
    void collectPageView_dropsDuplicateWithinWindow() {
        String vid = UUID.randomUUID().toString();
        service.collectPageView("/cases", vid, null, null);
        var second = service.collectPageView("/cases", vid, null, null);
        assertThat(second).isEqualTo(AnalyticsService.CollectResult.DUPLICATE);
        verify(events, times(1)).save(any());
    }

    @Test
    void collectPageView_rateLimitsFloods() {
        props.setCollectPerMinute(3);
        String vid = UUID.randomUUID().toString();
        int stored = 0, limited = 0;
        for (int i = 0; i < 10; i++) {
            var r = service.collectPageView("/cases/" + i + "0", vid, null, null);
            if (r == AnalyticsService.CollectResult.STORED) stored++;
            if (r == AnalyticsService.CollectResult.RATE_LIMITED) limited++;
        }
        assertThat(stored + limited).isGreaterThan(0);
        assertThat(limited).isGreaterThan(0);
    }

    @Test
    void record_swallowsRepositoryFailure() {
        when(events.save(any())).thenThrow(new RuntimeException("db down"));
        service.record(EventType.LOGIN_SUCCESS, 1L, "sid", null, null); // must not throw
    }

    @Test
    void overview_keepsMetricsDistinct() {
        when(users.count()).thenReturn(100L);
        when(sessions.countDistinctOpenUsers(any())).thenReturn(10L);
        when(sessions.countOpen(any())).thenReturn(14L);
        when(sessions.countActiveSessions(any(), any())).thenReturn(6L);
        when(sessions.countDistinctActiveUsers(any(), any())).thenReturn(4L);
        when(events.countDistinctActingUsersSince(any())).thenReturn(3L);
        when(sessions.countDistinctUsersSeenSince(any())).thenReturn(20L);
        when(events.countDistinctAnonymousVisitorsSince(any())).thenReturn(7L);
        when(events.countByTypeSince(any())).thenReturn(List.<Object[]>of(new Object[]{EventType.LOGIN_SUCCESS, 5L}));

        var o = service.overview();
        assertThat(o.registeredUsers()).isEqualTo(100);
        assertThat(o.authenticatedUsers()).isEqualTo(10);
        assertThat(o.openSessions()).isEqualTo(14);
        assertThat(o.activeSessions()).isEqualTo(6);
        assertThat(o.activeUsers()).isEqualTo(4);
        assertThat(o.recentlyActedUsers()).isEqualTo(3);
        assertThat(o.uniqueAuthenticatedUsersToday()).isEqualTo(20);
        assertThat(o.anonymousVisitorsToday()).isEqualTo(7);
        assertThat(o.today().get("LOGIN_SUCCESS")).isEqualTo(5L);
        assertThat(o.today().get("LOGOUT")).isEqualTo(0L);
        assertThat(o.activeWindowSeconds()).isEqualTo(300);
    }

    @Test
    void trends_bucketsDailyEvents() {
        Instant now = Instant.now();
        when(events.findForTrends(any())).thenReturn(List.<Object[]>of(
                new Object[]{EventType.LOGIN_SUCCESS, now, 1L, null},
                new Object[]{EventType.LOGIN_SUCCESS, now, 1L, null},
                new Object[]{EventType.CASE_SUBMITTED, now, 2L, null},
                new Object[]{EventType.PAGE_VIEW, now, null, "h1"}));
        var t = service.trends("daily", 3);
        assertThat(t.points()).hasSize(3);
        var last = t.points().get(2);
        assertThat(last.logins()).isEqualTo(2);
        assertThat(last.caseSubmissions()).isEqualTo(1);
        assertThat(last.uniqueActiveUsers()).isEqualTo(2);
        assertThat(last.pageViews()).isEqualTo(1);
        assertThat(last.visitorDays()).isEqualTo(1);
    }

    @Test
    void trends_rejectsUnknownGranularity() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.trends("hourly", 3));
    }
}
