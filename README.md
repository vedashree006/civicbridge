# CivicBridge — Public Service Case Management Platform

## 1. Architecture Overview

```
                        ┌─────────────────────┐
                        │   React + TS SPA     │  (Vercel / Netlify)
                        │  (Vite, Axios, JWT)  │
                        └──────────┬───────────┘
                                   │ HTTPS / REST (JSON)
                                   ▼
                        ┌─────────────────────┐
                        │  Spring Boot API     │  (Docker container → Render/
                        │  (Java 21)           │   Railway/Fly.io/AWS ECS)
                        │  - Controllers       │
                        │  - Services          │
                        │  - Security (JWT)    │
                        │  - Repositories (JPA)│
                        └──┬──────────┬────────┘
                           │          │
                  ┌────────┘          └─────────┐
                  ▼                              ▼
         ┌────────────────┐            ┌─────────────────┐
         │  PostgreSQL     │            │  Redis          │
         │  (system of     │            │  (sessions,     │
         │  record)        │            │  rate-limit,    │
         └────────────────┘            │  cache)          │
                                        └─────────────────┘
                  │
                  ▼
         ┌────────────────┐
         │  Kafka          │  → async events: case.created, case.status_changed,
         │  (event bus)    │    document.uploaded → consumed by Notification
         └────────────────┘    Service & Audit Service (roadmap Phase 5)
```

Clean-architecture layering per module (backend):
`controller (HTTP) → service (use-case/business rules) → repository (persistence) → entity (domain)`
DTOs cross the controller boundary; entities never leak to the client. Each service depends on
repository **interfaces**, not implementations (DIP). Each class has one reason to change (SRP).

## 2. Core Modules (MVP → Full)

| Module | MVP (this scaffold) | Later phases |
|---|---|---|
| Auth & RBAC | JWT login/register, roles CITIZEN/OFFICER/ADMIN | OAuth2/SSO, MFA |
| Case Tracking | CRUD, status enum, assignment | SLA timers, priority scoring |
| Documents | Upload metadata + local/S3 storage | Virus scan, OCR |
| Workflows | Fixed linear status transitions | Configurable rule engine (JSON-defined steps) |
| Notifications | In-app notification row on status change | Kafka + email/SMS via SES/SNS |
| Dashboards | Summary counts endpoint | Real-time via WebSocket, charts |
| Audit Log | AOP aspect logs writes to `audit_log` table | Immutable log shipped to S3/Glacier |
| AI Document Guidance | Stub endpoint (pluggable service interface) | Real LLM call (Claude API) analyzing uploaded docs for completeness |

## 3. Database Design (PostgreSQL)

```
users(id, email UNIQUE, password_hash, full_name, role, enabled, created_at)
cases(id, reference_no UNIQUE, title, description, status, category,
      citizen_id FK users, assigned_officer_id FK users NULL,
      created_at, updated_at)
documents(id, case_id FK cases, file_name, storage_path, content_type,
          size_bytes, uploaded_by FK users, uploaded_at)
workflow_steps(id, case_id FK cases, step_name, step_order, status,
                acted_by FK users NULL, acted_at NULL, comment)
notifications(id, user_id FK users, message, is_read, created_at)
audit_log(id, actor_id FK users NULL, action, entity_type, entity_id,
          details JSONB, created_at)
```

Indices: `cases(status)`, `cases(assigned_officer_id)`, `documents(case_id)`,
`notifications(user_id, is_read)`, `audit_log(entity_type, entity_id)`.

## 4. Step-by-Step Roadmap

**Phase 0 — Scaffold (delivered in this zip)**
- Spring Boot skeleton, Postgres via Docker Compose, JWT auth, RBAC, Case CRUD,
  Document upload (local disk), Audit AOP, Dashboard summary, React+TS shell with
  login/dashboard/case pages, `.env`-driven config on both sides.

**Phase 1 — Harden Auth**
- Refresh tokens, password reset flow, account lockout, integration tests for security config.

**Phase 2 — Workflow Engine**
- Replace fixed status enum transitions with a `workflow_steps` table driven by a
  per-category JSON template (e.g. "permit-application": [submit, review, site-visit, approve]).

**Phase 3 — Storage & AI**
- Swap local disk for S3 (`DocumentStorageService` interface already isolates this).
- Implement `AiGuidanceService` to call an LLM with the uploaded document's extracted
  text + case category, returning a checklist of missing/incorrect fields.

**Phase 4 — Async & Notifications**
- Introduce Kafka topics (`case-events`), producers in `CaseService`, consumers in a new
  `notification-service` (or in-process listener) that writes notifications and sends email via SES.

**Phase 5 — Observability & Deploy**
- Add Actuator + Micrometer + structured logging, Testcontainers for repository tests,
  GitHub Actions CI, deploy backend container to AWS ECS Fargate behind an ALB,
  deploy frontend to Vercel/Netlify pointing `VITE_API_URL` at the ALB DNS.

## 5. Deployment split

- **Frontend** (`/frontend`): push to GitHub, import into Vercel or Netlify, set env var
  `VITE_API_URL=https://your-backend-host/api`. Static build (`npm run build`) → `dist/`.
- **Backend** (`/backend`): build Docker image (`Dockerfile` included), push to a container
  host (Render/Railway are simplest for a first deploy; AWS ECS for production). Configure
  `.env` (see `.env.example`) as environment variables in that host's dashboard — never
  commit real secrets.
- **Local dev**: `docker compose up` starts Postgres + Redis (+ Kafka placeholder) so the
  backend has a full local environment before you touch the cloud.


## 6. Session tracking, admin analytics & cookie consent (added)

See `docs/ANALYTICS_AND_CONSENT.md` for the full design, metric definitions, endpoints, cookie inventory and
open legal-review items. Quick start: `cp .env.example .env`, set real secrets (`JWT_SECRET`, `ANALYTICS_HASH_SALT`,
DB password), `docker compose up --build`, then `cd frontend && npm install && npm run dev`.
Admin analytics lives at `/admin/analytics` (ADMIN role only). To create an admin, promote a registered user in the database:
`UPDATE users SET role='ADMIN' WHERE email='you@example.org';` (self-registration only creates CITIZEN accounts).

Tests: `cd backend && mvn test` (unit + H2-based integration tests; Redis intentionally unavailable in tests),
`cd frontend && npm test` (vitest).


## 7. 3D sparkle background (added)

An app-wide, decorative, interactive 3D sparkle background (midnight / indigo / violet / cool white with cyan accents).
**No new dependencies** — it is a small dependency-free WebGL particle system (one vertex buffer, one draw call),
so `package.json` is unchanged and there is no lockfile drift.

| File | Purpose |
| --- | --- |
| `frontend/src/components/SparkleBackground.tsx` | Mounted **once** in `App.tsx`, behind every route. Lazy-loads the renderer; falls back to CSS. |
| `frontend/src/background/sparkleRenderer.ts` | WebGL renderer: pointer parallax, fps cap, pause when tab hidden, adaptive quality, context-loss recovery, full cleanup on unmount. |
| `frontend/src/background/sparkleShaders.ts` | GLSL: depth, slow rotation/drift, depth-of-field, soft twinkle, diffraction cross on stars. |
| `frontend/src/background/sparkleConfig.ts` | Pure helpers: device quality profile, deterministic particles, per-route intensity (auth screens strongest, data screens calmer). |
| `frontend/src/background/sparkle.css` | Gradient/glow layers (also the no-WebGL fallback). |
| `frontend/src/components/PageTransition.tsx` | Soft route fade. |
| `frontend/src/styles.css` | Dark theme with translucent glass surfaces (AA contrast, ≥ 6:1 for body/controls). |
| `frontend/src/components/BarChart.tsx` | Colours moved to CSS classes so the chart suits the dark theme. |

Behaviour: `pointer-events: none`, `aria-hidden`, `z-index: 0` (content and the cookie banner sit above);
respects `prefers-reduced-motion` (single still frame, no drift/parallax/route fade); fewer particles, 30 fps cap and
no parallax on mobile / low-end / data-saver devices; paused while the tab is hidden; CSS fallback if WebGL is unavailable.
It makes no network calls and does not touch auth, cookies, consent or analytics.
Tuning: particle counts and caps live in `getQualityProfile`; per-route strength in `intensityForPath`.
Screenshots from the verification run are in `docs/screenshots/`.

Run: `cd frontend && npm install && npm run dev` (then open http://localhost:5173). Tests: `npm test`
(includes `src/background/sparkleConfig.test.ts`).
