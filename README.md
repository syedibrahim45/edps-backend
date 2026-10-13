# EDPS Backend

Spring Boot 4.1 / Java 21 REST backend for the Early Disease Prediction System. It uses Spring Data JPA, Hibernate, MySQL 8, and a Python FastAPI prediction service. The existing `users`, `patients`, and `predictions` API routes and tables are retained. Hibernate uses `update`; it does not drop or recreate existing tables. The application additively creates its integrity-audit tables.

## Prerequisites

- JDK 21
- MySQL 8, with the `edps_db` database created
- Git (optional, to clone the repository)
- Python FastAPI service at `http://localhost:8000` when making predictions
- React frontend at `http://localhost:5173` when using the UI

The backend can start without the Python service, but prediction requests will return `503` if it is unavailable. Tests use an isolated H2 database and mock the ML service; they do not require production credentials or a live FastAPI service.

## MySQL setup

Create the development database if it does not already exist. This statement does not drop or recreate existing tables or data:

```sql
CREATE DATABASE IF NOT EXISTS edps_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Set the connection values in the same VS Code integrated **PowerShell** terminal that will run the backend. Enter the MySQL password at the secure prompt; it is not written into the repository:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/edps_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "root"
$securePassword = Read-Host "MySQL password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $securePassword).Password
$env:SPRING_PROFILES_ACTIVE = "dev"

.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

For the frontend, configure its backend base URL as `http://localhost:8080`. When finished, remove the credentials from that terminal environment:

```powershell
Remove-Item Env:DB_PASSWORD
Remove-Item Env:DB_USERNAME
Remove-Item Env:DB_URL
```

If startup reports `Access denied ... (using password: NO)`, the process did not receive `DB_PASSWORD`; set it in the same terminal before starting Maven. If MySQL rejects the username/password, use credentials permitted by your local MySQL installation. Do not paste database credentials into source files, logs, issues, or commits. If valid credentials are not configured, Spring Boot fails startup rather than silently switching databases. Database connectivity was not verified by this repository's automated tests.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/edps_db?...` | MySQL JDBC URL |
| `DB_USERNAME` | `root` | MySQL account |
| `DB_PASSWORD` | empty | MySQL password; set locally via environment |
| `ML_SERVICE_URL` | `http://localhost:8000` | FastAPI base URL; backend calls `POST /predict` |
| `ML_CONNECT_TIMEOUT` | `2s` | ML connection timeout |
| `ML_READ_TIMEOUT` | `5s` | ML response timeout |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` in dev | Comma-separated exact allowed origins |
| `SPRING_PROFILES_ACTIVE` | `dev` | Set to `prod` to enable production security |
| `APP_ADMIN_USERNAME` | required in `prod` | Shared production HTTP Basic account |
| `APP_ADMIN_PASSWORD` | required in `prod` | Shared production HTTP Basic password |

For `prod`, set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `APP_ADMIN_USERNAME`, and `APP_ADMIN_PASSWORD`. The app fails startup if any required production setting is blank. Use TLS at the deployment ingress and restrict access to both the service and database.

## REST API

Development mode allows API access without login and permits the configured Vite origin. Create, update, and delete methods retain the existing response convention (HTTP `200`); successful deletes return a short text message. Error responses are JSON with `timestamp`, `status`, `error`, `message`, `path`, and `fieldErrors`.

### Patients

| Method | Endpoint | Behavior |
| --- | --- | --- |
| `POST` | `/patients` | Create patient; response is the created patient JSON |
| `GET` | `/patients` | List patients |
| `GET` | `/patients/{id}` | Get patient; missing ID returns `404` |
| `PUT` | `/patients/{id}` | Replace patient fields; does not insert another patient |
| `DELETE` | `/patients/{id}` | Delete only if there is no prediction history |

Patient create/update JSON uses `name`, `age`, `gender`, `phone`, `email`, `bloodPressure`, `glucose`, `bmi`, `heartRate`, and `cholesterol`. Name, age, gender, and all listed health measurements are required; phone/email are optional. Numeric ranges and email format are validated. Invalid data returns `400`. Deleting a patient with saved predictions returns `409` and retains both the patient and history.

### Predictions and history

| Method | Endpoint | Behavior |
| --- | --- | --- |
| `POST` | `/predictions` | Call FastAPI and persist prediction, patient (if new), and audit record |
| `GET` | `/predictions` | List persisted predictions newest first |
| `GET` | `/predictions/{id}` | Get one prediction |
| `GET` | `/predictions/patient/{patientId}` | List a patient's predictions newest first |
| `PUT` | `/predictions/{id}` | Update a saved prediction result |
| `DELETE` | `/predictions/{id}` | Delete a saved prediction |

Create a new patient and predict in one request:

```json
{
  "patient": {
    "name": "Example Patient",
    "age": 55,
    "gender": "female",
    "bloodPressure": 135,
    "glucose": 140,
    "bmi": 29.4,
    "heartRate": 80,
    "cholesterol": 210
  }
}
```

Use an existing patient without creating a duplicate:

```json
{
  "patientId": 4,
  "patient": {
    "age": 56,
    "gender": "female",
    "bloodPressure": 136,
    "glucose": 142,
    "bmi": 29.5,
    "heartRate": 81,
    "cholesterol": 211
  }
}
```

The existing patient can also be selected by `patient.id`; if both IDs are sent, they must match. If no measurements are supplied for an existing patient, its saved health values are used. Supplied measurements are a **one-time prediction snapshot**: they are sent to the model for that prediction and do not update the stored patient profile. If any health measurement override is supplied, the complete age/gender/health-parameter set is required, avoiding accidental mixtures of old and new measurements.

Gender accepts `male`, `m`, or `0` and `female`, `f`, or `1`. The outgoing FastAPI request uses numeric gender (`male=0`, `female=1`) and snake_case feature names:

```json
{
  "age": 55,
  "gender": 1,
  "blood_pressure": 135,
  "glucose": 140,
  "bmi": 29.4,
  "heart_rate": 80,
  "cholesterol": 210
}
```

FastAPI must return a `prediction`, a classification in `risk_level`, `riskLevel`, `classification`, `risk`, or legacy `disease`, and `probability` between `0` and `1`. `disease_type` or `diseaseType` is optional; the existing response `{"prediction":1,"disease":"High Risk","probability":0.82}` is supported. Connection failures/timeouts return `503`; rejected or invalid model responses return `502`. Prediction writes occur only after a valid model response and are transactional.

### Users

Existing `POST/GET/PUT/DELETE /users` CRUD paths remain. User passwords are BCrypt encoded and never returned in JSON. This user CRUD API is not itself the production login flow.

### Integrity audit

- `GET /integrity/audit/records` — list persistent audit records.
- `GET /integrity/audit/verify` — recompute SHA-256 payload hashes and verify previous/current hash links.

Patient and prediction events are audited transactionally. Audit payloads contain event names and record references only, not names, contact details, measurements, disease/risk classifications, or probabilities. A database-backed singleton row lock serializes audit appends across application instances. `integrity_audit_records` and the singleton audit lock table are additive; existing audit history is retained.

This is a **centralized, blockchain-inspired integrity prototype**, not Hyperledger or a decentralized blockchain. It cannot prevent a sufficiently privileged database administrator from rewriting history, and SHA-256 is an integrity mechanism, not encryption or anonymization. Protect audit endpoint access and database backups appropriately.

### Production security and React integration

With `SPRING_PROFILES_ACTIVE=prod`, every route except `/actuator/health` and `/csrf` requires HTTP Basic authentication. CORS allows only the exact configured origins. Production write requests also require CSRF protection:

1. React sends credentials to `GET /csrf` and retains the `XSRF-TOKEN` cookie.
2. Send the returned token as `X-XSRF-TOKEN` on `POST`, `PUT`, and `DELETE`, along with HTTP Basic credentials.
3. Set `credentials: "include"` for cross-origin fetch requests and configure the API origin in `CORS_ALLOWED_ORIGINS`.

The configured Basic account is a shared academic-demo administrator, not per-user authorization. Do not expose real patient records through the unauthenticated `dev` profile or deploy the shared account without TLS, network restrictions, and suitable credential handling. A private deployment with the existing React client must implement the Basic credential and CSRF cookie/header behavior.

## Tests and CI

Run the test suite on Windows PowerShell:

```powershell
.\mvnw.cmd clean test
```

The tests use H2, mock FastAPI, and cover patient CRUD validation, prediction flows and history, gender mapping, rollback on ML failure, audit tampering and concurrent append verification, and production authentication/CSRF/CORS behavior. A GitHub Actions workflow runs the same clean Maven tests with Java 21 on pushes and pull requests without requiring secrets.

## Known limitations

- MySQL connectivity requires valid local or deployment credentials and a running MySQL server; this cannot be exercised by the H2 test suite.
- FastAPI behavior is mocked in tests; the Python service must be started separately for live prediction.
- Multi-user login, patient ownership/authorization, and per-role access control are not implemented. Production currently uses one configured Basic-auth admin.
- Existing-patient prediction measurement overrides do not persist to the patient profile.
- The audit chain is centrally stored and application-level; it is not immutable against database administrators and uses a serialized singleton lock to prevent concurrent forks.
