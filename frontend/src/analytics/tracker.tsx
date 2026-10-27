import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { apiClient } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useConsent } from '../consent/ConsentContext';
import { getVisitorId } from '../consent/consentStore';

/**
 * Page-view tracking. Does nothing (no cookie, no request) unless analytics consent is currently granted.
 * Withdrawal immediately stops reporting and the consent store deletes the cb_vid cookie.
 */
export function PageViewTracker() {
  const { pathname } = useLocation();
  const { consent } = useConsent();
  const last = useRef<string | null>(null);

  useEffect(() => {
    if (!consent?.analytics) {
      last.current = null;
      return;
    }
    if (last.current === pathname) return;
    last.current = pathname;
    const visitorId = getVisitorId(consent);
    if (!visitorId) return;
    apiClient
      .post('/analytics/collect', { path: pathname, visitorId }, { headers: { 'X-Analytics-Consent': 'granted' } })
      .catch(() => undefined); // analytics failures are never shown to the user
  }, [pathname, consent]);

  return null;
}

/**
 * Keeps the server-side session "recently active" while a signed-in user has the tab visible.
 * Strictly necessary session management (not consent-based); stops when the tab is hidden or the user signs out.
 */
export function SessionHeartbeat({ intervalMs = 60_000 }: { intervalMs?: number }) {
  const { isAuthenticated } = useAuth();

  useEffect(() => {
    if (!isAuthenticated) return;
    const beat = () => {
      if (document.visibilityState === 'visible') apiClient.post('/session/heartbeat').catch(() => undefined);
    };
    const timer = window.setInterval(beat, intervalMs);
    const onVis = () => beat();
    document.addEventListener('visibilitychange', onVis);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVis);
    };
  }, [isAuthenticated, intervalMs]);

  return null;
}
