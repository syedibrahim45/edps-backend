# EDPS Backend

Spring Boot 4.1 / Java 21 REST API for the Early Disease Prediction System. The existing `users`, `patients`, and `predictions` tables and API routes are retained. Hibernate's `update` mode adds the `integrity_audit_records` table without dropping existing tables. Jakarta validation annotations validate API data at runtime and are explicitly excluded from DDL generation.

## Run locally

Requirements: JDK 21, MySQL with an `edps_db` database, and the Python FastAPI service (optional until a prediction is requested).

Set the database connection values in PowerShell, then start Spring Boot:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/edps_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<your-local-mysql-password>"
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The default profile is `dev`, intended only for local development with synthetic data. It allows API access without login and permits the Vite origin `http://localhost:5173`. Tests use an isolated H2 database and mock the Python service.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/edps_db?...` | JDBC URL |
| `DB_USERNAME` | `root` | MySQL username |
| `DB_PASSWORD` | empty | MySQL password; provide via the environment, not source control |
| `ML_SERVICE_URL` | `http://localhost:8000` | FastAPI base URL; endpoint path is `/predict` |
| `ML_CONNECT_TIMEOUT` | `2s` | Connection timeout |
| `ML_READ_TIMEOUT` | `5s` | Prediction response timeout |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` in dev | Comma-separated exact browser origins |
| `APP_ADMIN_USERNAME` | required in production | Production Basic-auth admin username |
| `APP_ADMIN_PASSWORD` | required in production | Production Basic-auth admin password |

For production, set `SPRING_PROFILES_ACTIVE=prod` and configure all environment values, including non-empty `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Every API route except `/actuator/health` and `/csrf` then requires HTTP Basic authentication as the configured admin. Production writes require a CSRF token: fetch `/csrf` with browser credentials, then send the returned token in `X-XSRF-TOKEN` on POST/PUT/DELETE requests (keep the `XSRF-TOKEN` cookie). The `CORS_ALLOWED_ORIGINS` value is required. Use TLS at the ingress/reverse proxy and restrict network access to the backend and database. The built-in single-admin Basic-auth account is a deployment safeguard, not a replacement for a multi-user identity provider or a token-based login flow.

## REST API

Successful prediction and history responses use this shape:

```json
{
  "id": 17,
  "diseaseType": "Diabetes",
  "predictionResult": "1",
  "riskLevel": "High Risk",
  "probability": 0.82,
  "createdAt": "2026-10-13T01:25:00",
  "patient": {
    "id": 4,
    "name": "Example Patient",
    "age": 55,
    "gender": "Female",
    "phone": null,
    "email": null,
    "bloodPressure": 135,
    "glucose": 140,
    "bmi": 29.4,
    "heartRate": 80,
    "cholesterol": 210
  }
}
```

### Users

* `POST /users` — create a user from `{"username":"demo","password":"at-least-8-characters","role":"USER"}`.
* `GET /users`, `GET /users/{id}`, `PUT /users/{id}`, `DELETE /users/{id}` — user CRUD.

User passwords are BCrypt-encoded on create/update and omitted from all JSON responses. Existing records are not rewritten automatically.

### Patients

* `POST /patients` — create a patient. Do not supply `id`; duplicate insert attempts using an ID receive `409 Conflict`.
* `GET /patients`, `GET /patients/{id}`, `PUT /patients/{id}`, `DELETE /patients/{id}` — patient CRUD. Create, update, and delete operations append audit events in the same database transaction.

### Predictions

* `POST /predictions` — run a model prediction, persist it, and append its integrity audit record.
* `GET /predictions` — prediction history, newest first.
* `GET /predictions/{id}` — get one prediction.
* `GET /predictions/patient/{patientId}` — patient-specific history, newest first.
* `PUT /predictions/{id}`, `DELETE /predictions/{id}` — update/delete a result; these actions are also audited.

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

`patientId` may also be supplied as `patient.id` for compatibility with the previous nested entity request. If both are supplied, they must match. If `patient` is omitted for an existing ID, stored health values are used. Supplied existing-patient measurements are used for inference only; they never overwrite the saved patient. A missing patient ID returns `404`. For a new patient, `name`, `age`, `gender`, blood pressure, glucose, BMI, heart rate, and cholesterol are required. Numeric ranges and email format are validated. Gender accepts `male`/`m`/`0` and `female`/`f`/`1`; the FastAPI service always receives integer `gender` (`male=0`, `female=1`) and snake_case health keys.

When overriding an existing patient's measurements, send the complete health-parameter set in `patient`; partial measurement sets are rejected rather than silently mixing old and new readings.

Spring sends the model:

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

The model must respond with `prediction`, a risk classification (`risk_level`, `riskLevel`, `classification`, `risk`, or legacy `disease`), and a numeric `probability` from 0 through 1. Optional `disease_type` or `diseaseType` supplies the disease type. The prior response shape `{"prediction":1,"disease":"High Risk","probability":0.82}` remains accepted and maps `disease` to `riskLevel`. Connection failures/timeouts return `503`; rejected or malformed model responses return `502`. No patient, prediction, or audit rows are committed when prediction fails.

Validation errors use `400`; missing resources use `404`; conflicting data uses `409`. Error responses include `timestamp`, `status`, `error`, `message`, `path`, and `fieldErrors`.

### Prototype integrity audit

* `GET /integrity/audit/records` — list audit records.
* `GET /integrity/audit/verify` — recompute payload SHA-256 hashes and the ordered previous/current hash links.

Each audit event stores a unique reference, UTC timestamp, canonical payload, payload SHA-256, previous hash, and current hash. Patient CRUD events include a SHA-256 fingerprint of the patient snapshot without storing the raw name, contact details, or health measurements; prediction events record prediction metadata and patient/prediction references. This is an application-level, single-process prototype integrity log: it is **not a decentralized blockchain** and does not provide tamper resistance against an administrator who can rewrite the database. Back up and restrict database access; multi-instance deployments need a shared transactional chain lock or a dedicated append-only store.
