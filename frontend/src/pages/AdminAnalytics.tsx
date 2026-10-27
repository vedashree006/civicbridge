import { useCallback, useEffect, useState } from 'react';
import { getOverview, getTrends } from '../api/analytics';
import { AnalyticsOverview, AnalyticsTrends, TrendPoint } from '../types';
import BarChart from '../components/BarChart';

const REFRESH_MS = 30_000;

type Granularity = 'daily' | 'weekly' | 'monthly';
type SeriesKey = keyof Omit<TrendPoint, 'bucketStart'>;

const SERIES: { key: SeriesKey; label: string }[] = [
  { key: 'logins', label: 'Logins' },
  { key: 'logouts', label: 'Logouts' },
  { key: 'caseSubmissions', label: 'Case submissions' },
  { key: 'documentUploads', label: 'Document uploads' },
  { key: 'aiGuidanceUses', label: 'AI guidance uses' },
  { key: 'pageViews', label: 'Page views (consented)' },
  { key: 'uniqueActiveUsers', label: 'Unique users with activity' },
  { key: 'visitorDays', label: 'Anonymous visitor-days (consented)' },
];

function Metric({ label, value, hint }: { label: string; value: number | string; hint: string }) {
  return (
    <div className="metric card">
      <div className="metric__value">{value}</div>
      <div className="metric__label">{label}</div>
      <div className="metric__hint">{hint}</div>
    </div>
  );
}

export default function AdminAnalytics() {
  const [overview, setOverview] = useState<AnalyticsOverview | null>(null);
  const [trends, setTrends] = useState<AnalyticsTrends | null>(null);
  const [granularity, setGranularity] = useState<Granularity>('daily');
  const [series, setSeries] = useState<SeriesKey>('logins');
  const [error, setError] = useState<string | null>(null);
  const [trendError, setTrendError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const loadOverview = useCallback(async () => {
    try {
      setOverview(await getOverview());
      setError(null);
    } catch (e: any) {
      setError(e?.response?.status === 403 ? 'You are not authorized to view analytics.' : 'Could not load analytics. Retrying automatically.');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadTrends = useCallback(async () => {
    try {
      setTrends(await getTrends(granularity));
      setTrendError(null);
    } catch {
      setTrendError('Could not load activity trends.');
    }
  }, [granularity]);

  useEffect(() => {
    loadOverview();
    const t = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        loadOverview();
        loadTrends();
      }
    }, REFRESH_MS);
    return () => window.clearInterval(t);
  }, [loadOverview, loadTrends]);

  useEffect(() => {
    loadTrends();
  }, [loadTrends]);

  const windowMin = overview ? Math.round(overview.activeWindowSeconds / 60) : 5;
  const allZero = trends ? trends.points.every((p) => p[series] === 0) : false;

  return (
    <div className="container wide">
      <div className="row-between">
        <h2>Usage analytics</h2>
        <button className="btn-secondary" onClick={() => { loadOverview(); loadTrends(); }}>Refresh now</button>
      </div>
      <p className="muted">
        Aggregate figures only. Refreshes every {REFRESH_MS / 1000}s while this tab is visible.
        {overview && ` Last updated ${new Date(overview.generatedAt).toLocaleTimeString()}.`}
      </p>

      {loading && <p role="status">Loading metrics…</p>}
      {error && <p className="error" role="alert">{error}</p>}

      {overview && (
        <>
          {!overview.redisAvailable && (
            <p className="warning" role="status">
              Redis is unavailable: session validation falls back to the database. Metrics remain correct but slower.
            </p>
          )}

          <h3>Accounts &amp; sessions</h3>
          <div className="metric-grid">
            <Metric label="Registered accounts" value={overview.registeredUsers} hint="All accounts ever created (not online status)." />
            <Metric label="Authenticated users" value={overview.authenticatedUsers} hint="Distinct users with at least one open, unexpired session." />
            <Metric label="Open sessions" value={overview.openSessions} hint="Open sessions of any recency; one user may have several." />
            <Metric label={`Active sessions (${windowMin} min)`} value={overview.activeSessions} hint={`Open sessions seen in the last ${windowMin} minutes.`} />
            <Metric label={`Active users (${windowMin} min)`} value={overview.activeUsers} hint="Distinct users behind the active sessions." />
            <Metric label={`Users who acted (${windowMin} min)`} value={overview.recentlyActedUsers} hint="Distinct users with a login, case, upload or AI event." />
            <Metric label="Unique signed-in users today" value={overview.uniqueAuthenticatedUsersToday} hint="Distinct users whose session was seen today." />
            <Metric label="Anonymous visitors today" value={overview.anonymousVisitorsToday} hint="Logged-out visitors who accepted analytics cookies only." />
          </div>

          <h3>Events</h3>
          <div className="card">
            <table>
              <thead>
                <tr><th>Event</th><th>Today</th><th>Last 7 days</th><th>Last 30 days</th></tr>
              </thead>
              <tbody>
                {[
                  ['LOGIN_SUCCESS', 'Successful logins'],
                  ['LOGOUT', 'Logouts'],
                  ['REGISTRATION', 'Registrations'],
                  ['CASE_SUBMITTED', 'Case submissions'],
                  ['DOCUMENT_UPLOADED', 'Document uploads'],
                  ['AI_GUIDANCE_USED', 'AI guidance uses'],
                  ['PAGE_VIEW', 'Page views (consented only)'],
                ].map(([k, label]) => (
                  <tr key={k}>
                    <td>{label}</td>
                    <td>{overview.today[k] ?? 0}</td>
                    <td>{overview.last7Days[k] ?? 0}</td>
                    <td>{overview.last30Days[k] ?? 0}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      <h3>Activity trends</h3>
      <div className="card">
        <div className="controls">
          <label>
            Period{' '}
            <select value={granularity} onChange={(e) => setGranularity(e.target.value as Granularity)}>
              <option value="daily">Daily (14 days)</option>
              <option value="weekly">Weekly (8 weeks)</option>
              <option value="monthly">Monthly (3 months, retention-limited)</option>
            </select>
          </label>
          <label>
            Metric{' '}
            <select value={series} onChange={(e) => setSeries(e.target.value as SeriesKey)}>
              {SERIES.map((s) => <option key={s.key} value={s.key}>{s.label}</option>)}
            </select>
          </label>
        </div>
        {trendError && <p className="error" role="alert">{trendError}</p>}
        {!trends && !trendError && <p role="status">Loading trends…</p>}
        {trends && (allZero ? (
          <p className="muted">No recorded activity for this metric in the selected period.</p>
        ) : (
          <BarChart
            labels={trends.points.map((p) => p.bucketStart)}
            values={trends.points.map((p) => p[series])}
            ariaLabel={`${SERIES.find((s) => s.key === series)?.label} per ${granularity.replace('ly', '')} bucket`}
          />
        ))}
        {trends && <p className="muted">Buckets use the {trends.zone} calendar. Weekly buckets start on Monday.</p>}
      </div>
    </div>
  );
}
