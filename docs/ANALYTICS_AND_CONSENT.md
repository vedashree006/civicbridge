# Analytics, session tracking and cookie consent

## Design summary
* **Auth transport is unchanged**: bearer JWT in the `Authorization` header (kept in `localStorage` as before).
  No authentication cookies are used, so CSRF protection is not needed and cookie-credentialed CORS is disabled.
  Trade-off: tokens in localStorage are readable by injected JavaScript (XSS); moving to HttpOnly cookies would
  require CSRF tokens + SameSite/CORS redesign and was deliberately not done silently. Recommended follow-up.
* **Server-side session registry**: every JWT carries `sid` (session id) and `uid`. The filter accepts a token only while
  the session is open (`user_sessions`). Logout (`POST /api/auth/logout`) ends the session so the token dies immediately.
* **Redis** holds a 60 s "session valid" cache and per-session write-throttle keys. If Redis is down the system falls
  back to PostgreSQL. If PostgreSQL is down, validation fails closed (401).
* **Heartbeat**: the SPA calls `POST /api/session/heartbeat` every 60 s while the tab is visible; the server persists
  `last_seen_at` at most once per 30 s per session. Closed browsers/abandoned tabs stop beating and drop out of "active"
  after 5 min; sessions end at JWT expiry (swept every 60 s).

## Metric definitions
| Metric | Definition |
|---|---|
| Registered accounts | rows in `users` |
| Authenticated users | distinct users with >=1 open, unexpired session |
| Open sessions | open, unexpired sessions (a user may have several) |
| Active sessions | open sessions with `last_seen_at` within the window (default 300 s) |
| Active users | distinct users behind active sessions |
| Users who acted | distinct users with a server-recorded login/case/upload/AI event in the window |
| Unique signed-in users today | distinct users whose session was seen today (Europe/Berlin day) |
| Anonymous visitors today | distinct daily-rotating hashes of consented, logged-out page views |

## Endpoints
| Method & path | Access | Purpose |
|---|---|---|
| `POST /api/auth/logout` | public (idempotent) | end current session |
| `POST /api/session/heartbeat` | authenticated | record activity |
| `POST /api/analytics/collect` | public + `X-Analytics-Consent: granted` | consented page view (validated, de-duplicated, rate-limited) |
| `POST /api/consent` | public | store consent record |
| `GET /api/admin/analytics/overview` | ADMIN | metrics |
| `GET /api/admin/analytics/trends?granularity=daily\|weekly\|monthly` | ADMIN | trends |

## Cookies / storage
`cb_token`, `cb_user` (localStorage, necessary), `cb_consent` (cookie, necessary, 180 d), `cb_vid` (cookie, analytics,
opt-in, 24 h). No preference or marketing cookies exist; the preference category is present but unused. No third-party scripts.

## Retention (configurable)
Events 90 d, ended sessions 30 d, consent records 1095 d. Cleanup job runs daily at 03:30.

## Items requiring legal review (not decided by this software)
1. Legal basis for server-side usage events linked to user accounts (legitimate interest vs. consent) and the DPIA need.
2. Whether `localStorage` auth storage and the consent cookie are "strictly necessary" under TDDDG §25(2) - generally yes.
3. Consent-record retention period (1095 d is a placeholder) and wording of the privacy notice (Art. 13 GDPR).
4. The server trusts the browser's consent signal for `collect`; it cannot independently verify consent.
5. Controller identity/contact, DPO, and processor agreements for hosting.
