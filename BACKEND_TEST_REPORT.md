# Backend Test Report

## Run

- Command: `.\mvnw.cmd -B clean test`
- Executed: 2026-10-13
- Result: **PASS**
- Runtime: Java 21.0.12
- Test cases: **17 run, 0 failures, 0 errors, 0 skipped**
- Maven result: `BUILD SUCCESS`

## Coverage executed

- Patient CRUD validation, create/read/update/delete behavior, missing IDs, and safe rejection of deletion when prediction history exists.
- New and existing patient prediction flows, persisted history reads, and male/female feature encoding.
- Invalid input, missing patient, mocked ML failure, and transaction rollback assertions.
- Audit tampering detection, sensitive-value omission from audit payloads, and concurrent append verification.
- Production Basic authentication, CSRF-protected writes, health endpoint access, and CORS denial for an unconfigured origin.
- Legacy and explicit FastAPI response parsing and invalid probability rejection.

## Not verified by this test run

- Live MySQL authentication/connectivity: the test shell had no `DB_URL`, `DB_USERNAME`, or `DB_PASSWORD` environment variables. The MySQL TCP port at `localhost:3306` was reachable, but valid credentials were not available, so a successful DB login could not be established here.
- Live FastAPI requests: the service is intentionally mocked in automated tests.
- Frontend/browser end-to-end flows: not run from this backend-only repository.
