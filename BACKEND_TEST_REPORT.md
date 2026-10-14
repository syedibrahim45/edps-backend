# Backend Test Report

## Pre-change baseline

- Command: `.\mvnw.cmd --batch-mode --no-transfer-progress clean test`
- Runtime: Java 21.0.12
- Result: **PASS**, 17 tests, 0 failures, 0 errors, 0 skipped.

## Final verification

- Command: `.\mvnw.cmd --batch-mode --no-transfer-progress clean test`
- Executed: 2026-10-14
- Runtime: Java 21.0.12
- Result: **PASS**, 21 tests, 0 failures, 0 errors, 0 skipped.
- Maven result: `BUILD SUCCESS`.

A focused `-Dtest=MlPredictionServiceTests test` run also passed all 6 ML service tests, including requests to an isolated local HTTP server.

## Coverage exercised

- Patient CRUD validation, missing-resource responses, prediction history retention, and non-duplication on updates.
- New and existing patient predictions, prediction-history endpoints, gender feature mapping, invalid input handling, and rollback following mocked ML failure.
- Dashboard ML controller route mapping and ML service HTTP proxying, JSON response checks, invalid request handling, and unavailable-service mapping.
- Audit payload/link verification, tampering detection, sensitive-value omission, and concurrent append serialization.
- Production authentication, CSRF-protected writes, health access, and rejection of unconfigured CORS origins.

## External checks and limitations

- MySQL TCP at `localhost:3306` was reachable, but `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` were not present in the test process environment. MySQL authentication and real MySQL persistence could not be verified. Automated persistence tests use isolated H2.
- FastAPI was not running as a live external dependency during the tests. Proxy behavior was verified against a local HTTP stub; prediction controller behavior uses mocked ML responses.
- No frontend/browser end-to-end test was executed. A Docker build was attempted, but could not start because the Docker CLI is not installed or available on PATH in this environment.
- The latest GitHub Actions run queried during this task completed successfully on commit `3fdc606` (`Fix Maven wrapper permissions in backend CI`). This remote run predates the current uncommitted working changes; those changes have not been pushed or run in GitHub Actions.
