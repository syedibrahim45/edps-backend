# EDPS Phase 2 Integration Report

**Generated:** 2026-10-14  
**Scope:** `edps-backend`, `edps-ml-service`, and `edps-frontend`  
**Data policy:** Synthetic values only. No patient records were deleted. No credentials were added to source control. Changes are committed locally in their respective repositories; nothing was pushed or deployed.

## Executive summary

The local checkout of each repository matched its live `origin/main` commit at the beginning of this work. The ML service and React development server were started locally. FastAPI direct endpoint checks, all three repositories' automated test suites, frontend lint, and frontend production build passed.

The complete application flow is **not verified**. MySQL service `MySQL80` is already running and accepts TCP connections on port 3306, but this session has no `DB_URL`, `DB_USERNAME`, or `DB_PASSWORD` environment variables. I did not guess credentials, try an empty/root password, or start Spring Boot against a different database. Consequently, live Spring Boot/MySQL/FastAPI connectivity, database persistence, backend proxy calls, and the full browser journey were not tested. The user indicated they would configure the database environment and restart the session; the variables were still absent from this report's session.

## Results

| # | Task | Result | Evidence / limitation |
|---|---|---|---|
| 1 | Compare all local repositories with latest GitHub commits | **PASS** | Initial worktrees were clean and matched live `origin/main`: backend `58d1ad6c02487d512af3bd7046b62dca37187256`; ML `2ce3d758e7cb0d3b8f2fa73f304b7ae6349e54e2`; frontend `f1369c07308415dfeefa89cc041d0a96862e8785`. Task changes are committed locally; no push was made. |
| 2 | Start MySQL and authenticate with environment credentials | **NOT TESTED** | Windows service `MySQL80` was already `Running`; TCP connect to `127.0.0.1:3306` succeeded. `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` were absent, so no SQL login was attempted. Existing records were not modified. |
| 3 | Start FastAPI and verify endpoints | **PASS** | Uvicorn started at `http://127.0.0.1:8000`. Root, health, valid synthetic prediction, global explainability, individual explainability, and federated-results endpoints returned valid responses. Invalid prediction input returned HTTP 422. |
| 4 | Start Spring Boot and confirm MySQL/FastAPI connectivity | **NOT TESTED** | Blocked on the missing MySQL environment credentials. Port 8080 was not listening. |
| 5 | Start React | **PASS** | Vite served the application at `http://127.0.0.1:5173`. |
| 6 | Verify Spring-to-ML proxy endpoints live | **NOT TESTED** | Backend unit/integration tests passed, and the source exposes the proxy routes; live proxy traffic could not be sent without Spring Boot. Direct FastAPI checks are not a substitute for proxy tests. |
| 7 | Complete browser test with synthetic patient data | **PARTIAL** | The app and Federated Learning page loaded in the browser. With Spring Boot unavailable, the page displayed the explicit connection error; no patient or prediction data was submitted. |
| 8 | Create a patient and verify persistence after refresh | **NOT TESTED** | Requires Spring Boot and authenticated MySQL access. |
| 9 | Generate a prediction and confirm MySQL persistence | **NOT TESTED** | FastAPI returned a synthetic prediction directly; no backend prediction row was written or checked. |
| 10 | Verify prediction history after refresh | **NOT TESTED** | Requires a successful backend write and database read-back. |
| 11 | Test global and individual explainability views through the app | **PARTIAL** | Both direct FastAPI routes returned valid response data (7 global feature importance entries and 7 individual feature effects). Browser-to-Spring proxy rendering was not verified. |
| 12 | Test federated-learning dashboard using actual simulation results | **PARTIAL** | FastAPI returned its completed synthetic FedAvg simulation: 3 clients, 10 rounds, 10 per-round metric records. The browser could not retrieve it through Spring Boot. |
| 13 | Verify the persisted audit chain | **NOT TESTED** | The live MySQL-backed chain could not be queried. Backend automated tests passed, but do not prove the existing local database's chain is valid. |
| 14 | Exercise validation, downtime, and authentication failures | **PARTIAL** | FastAPI invalid prediction returned 422. The browser observed `ERR_CONNECTION_REFUSED` from Spring Boot and displayed a visible unavailable state. Backend automated tests passed, including the production security test class (3/3); a live invalid-login/browser test was not run. |
| 15 | Fix verified integration defects without changing API contracts | **PASS** | Removed stale frontend claims that the ML proxy is missing; implemented display of the existing proxied ML health response. Existing request routes/contracts were preserved. |
| 16 | Correct outdated proxy documentation | **PASS** | Updated the frontend README, deployment guide, demo guide, and test report. |
| 17 | Run requested test/build checks | **PASS** | Backend 21/21 tests; ML pytest 28/28; frontend Vitest 17/17; frontend ESLint passed; Vite production build passed. Details follow. |
| 18 | Generate this final integration report | **PASS** | This file records observed evidence and unverified work explicitly. |
| 19 | Prepare a private deployment plan and cost range | **PASS — PLAN ONLY** | See the readiness section. No resource was provisioned and no public deployment was attempted. |
| 20 | Prepare an easy-to-follow VTU demo guide | **PASS** | Updated `edps-frontend/DEMO_GUIDE.md` with working proxy flows, synthetic-data reminders, and current setup guidance. |

## Test evidence

| Check | Command / method | Result |
|---|---|---|
| Backend tests | `$env:MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true'; .\mvnw.cmd --batch-mode test` from `edps-backend` | **PASS**, 21 tests, 0 failures/errors/skips: 12 application tests, 6 ML proxy service tests, 3 production security tests. Surefire XML/text is in `target/surefire-reports/`. |
| ML tests | `.\venv\Scripts\python.exe -m pytest` from `edps-ml-service` | **PASS**, 28 passed. Output included 802 joblib/NumPy deprecation warnings. |
| Frontend tests | `npm test` from `edps-frontend` | **PASS**, 3 files, 17 tests. |
| Frontend lint | `npm run lint` from `edps-frontend` | **PASS**, no reported lint issues. |
| Frontend build | `npm run build` from `edps-frontend` | **PASS**, Vite production build completed. |
| FastAPI live health | `GET /health` | **PASS**, `status=UP`, `model_status=ready`. |
| FastAPI live prediction | `POST /predict` with synthetic `{age:45, gender:1, blood_pressure:122, glucose:105, bmi:24.5, heart_rate:72, cholesterol:185}` | **PASS**, valid response (`prediction=0`, `Low Risk`, probability `0.17`). This output is an academic model result, not medical advice. |
| FastAPI explainability | `GET /explainability/global`; `POST /explainability/individual` with the same synthetic measurements | **PASS**, 7 global features and 7 individual feature effects. |
| FastAPI federated simulation | `GET /federated/results` | **PASS**, completed synthetic simulation with 3 clients and all 10 round records. |
| FastAPI validation | Invalid `POST /predict` body (`{"age":0}`) | **PASS**, HTTP 422. |
| Browser availability handling | Opened app and Federated Learning page while Spring Boot was unavailable | **PASS for failure visibility only**; browser logged `ERR_CONNECTION_REFUSED` and displayed “Simulation results are unavailable.” No fallback/direct FastAPI request occurred. |

The first backend test attempt could not initialize Mockito's Byte Buddy inline agent on this Windows/JDK run. Rerunning with `-Djdk.attach.allowAttachSelf=true` passed all 21 tests. An initial frontend test exposed an ambiguous `UP` assertion after adding the second live service health status; the assertion was corrected, then all 17 tests, lint, and build passed. A first frontend build was run concurrently with other Node operations and failed with a transient file-read error; the final sequential lint/test/build run passed.

### Service logs

- MySQL: Windows service `MySQL80` was reported `Running`; TCP listener 3306 accepted a connection. No authenticated SQL operation was made.
- FastAPI startup: `Application startup complete`; listening on `http://127.0.0.1:8000`. Live log entries showed HTTP 200 for health, global explainability, individual explainability, and federated results, plus HTTP 422 for the invalid prediction request.
- Vite startup: Vite 8.2.2 reported ready at `http://127.0.0.1:5173`.
- Spring Boot: not started. Browser requests to `http://localhost:8080` returned `ERR_CONNECTION_REFUSED`.

## Files changed

### Backend repository

- `FINAL_INTEGRATION_REPORT.md` — this evidence-based report and private deployment readiness plan.

### Frontend repository

- `src/api.js` — validate and expose `GET /api/ml/health`.
- `src/App.jsx` — display live ML proxy health on the dashboard and in retry/error status.
- `src/components/WorkspacePages.jsx` — display ML health in settings and remove obsolete missing-proxy claims.
- `src/api.test.js` — cover ML health route and response validation.
- `src/components/WorkspacePages.test.jsx` — cover ML health rendering.
- `README.md`, `DEPLOYMENT.md`, `DEMO_GUIDE.md`, `FRONTEND_TEST_REPORT.md` — refresh proxy and runtime status documentation.

No backend or ML-service source-code changes were made; the backend repository contains this report only. No credentials were added. No push, database delete, or deployment was performed.

## Remaining blockers and next verification steps

1. Make `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` available to the restarted local VS Code session; do not send or commit their values. The user selected this option, but they were still absent when this report was generated.
2. Confirm the MySQL login and `edps_db` availability without dropping, truncating, or resetting any table.
3. Start Spring Boot with `SPRING_PROFILES_ACTIVE=dev`, `ML_SERVICE_URL=http://127.0.0.1:8000`, and `CORS_ALLOWED_ORIGINS=http://127.0.0.1:5173`. Use the existing secure secret-entry method documented in `edps-backend/DEPLOYMENT.md`.
4. Confirm `/actuator/health`, `/api/ml/health`, and every `/api/ml/**` response through Spring Boot.
5. Complete the browser journey using only synthetic data: patient create → reload/read-back → prediction → database/history read-back → global and individual explainability → actual federated results → audit verify. Exercise invalid fields and production authentication only with configured test credentials.
6. Re-run and append the resulting MySQL/Spring/browser evidence to this report. Until then the overall integration result remains **NOT COMPLETE**.

## Private deployment readiness

**Status: NOT READY FOR DEPLOYMENT; PLAN ONLY.** The local integration is incomplete, the cloud/provider/region and access model are unselected, and the current shared Basic-auth administrator is a demonstration safeguard rather than production multi-user authorization.

### Recommended private topology

1. Place the application in a private network. Permit user access only over the college VPN or an approved zero-trust private access path; use private DNS and deny public ingress to the application services.
2. Serve the React static build over HTTPS behind a private ingress/reverse proxy. Terminate TLS with a managed certificate and redirect or reject HTTP.
3. Keep Spring Boot private behind that ingress. Set the exact frontend HTTPS origin in CORS configuration. Require HTTPS between internal services where supported.
4. Keep FastAPI reachable only from Spring Boot on the private network. Do not publish port 8000 or configure the browser to call it directly.
5. Use managed MySQL or a private MySQL host with public networking disabled, private endpoint/VNet access, TLS in transit, encryption at rest, least-privilege database credentials, automated backups, and a tested restore procedure.
6. Store database and administrator credentials in a secret manager; inject them at runtime using managed identity or an equivalent workload identity. Rotate credentials and never use `root` for the app.
7. Before external users or real data are allowed, replace the shared HTTP Basic account with institutional OIDC/SSO, per-user roles and patient ownership/authorization. Retain CSRF protections for browser writes. Restrict audit and health details to authorized operators.
8. Review the current Hibernate `ddl-auto=update` setting before production; use reviewed/versioned database migrations and a backup/restore rehearsal. Keep synthetic-only data for the college demo.
9. Avoid logging patient measurements, contact information, prediction payloads, credentials, or tokens. Monitor service health, failed authentication, database backups, and audit-chain verification without exposing sensitive fields.

### Budgetary monthly estimate

The provider, region, availability tier, user count, and data-retention requirement have not been selected. These are **planning ranges, not a quote**:

| Cost area | Low-traffic private demo estimate (USD/month) |
|---|---:|
| Small app compute for React hosting, Spring Boot, and FastAPI | $40–$160 |
| Small managed MySQL instance, storage, and backups | $30–$130 |
| Private ingress/VPN or private endpoints, DNS, TLS, monitoring/logging, and modest transfer | $30–$180 |
| **Indicative total, single-region/non-HA** | **$100–$470/month** |
| Redundancy, larger database, WAF/centralized security monitoring, or higher egress | **Add roughly $150–$700+/month** |

Actual charges vary substantially by cloud, region, minimum service tier, storage, backup retention, and idle-time behavior. Confirm using the selected provider's calculator and subscription quotas before approval. This work did not access an Azure subscription or deploy resources.

## Suggested commit messages

- `feat(frontend): surface proxied ML health and refresh integration guidance`
- `docs: record EDPS Phase 2 integration evidence and private deployment plan`
- `docs(frontend): update VTU demo guide for working ML proxy routes`
