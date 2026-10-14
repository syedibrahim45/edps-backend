# EDPS Backend API Contract

Base URL: `http://localhost:8080`

All responses are JSON unless the endpoint is a legacy delete operation, which returns a text success message. Validation errors return `400`, missing resources return `404`, conflicting operations return `409`, ML service unavailability/timeouts return `503`, and rejected/malformed ML responses return `502`. Structured errors have `timestamp`, `status`, `error`, `message`, `path`, and `fieldErrors`.

The development profile permits requests without credentials for local synthetic-data use. In `prod`, all API paths except `/actuator/health` and `/csrf` require HTTP Basic authentication. Production write operations require CSRF cookie/token.

## Patients

| Method | Path | Success |
| --- | --- | --- |
| `POST` | `/patients` | `200`, created patient object |
| `GET` | `/patients` | `200`, patient array |
| `GET` | `/patients/{id}` | `200`, patient object |
| `PUT` | `/patients/{id}` | `200`, updated patient object |
| `DELETE` | `/patients/{id}` | `200`, `Patient deleted successfully` |

Patient fields: `id`, `name`, `age`, `gender`, `phone`, `email`, `bloodPressure`, `glucose`, `bmi`, `heartRate`, `cholesterol`. Omit `id` on create. Name, age, gender, and health values are required for CRUD writes. Email and phone are optional. Invalid inputs return `400`; absent patient IDs return `404`. A patient referenced by prediction history cannot be deleted (`409`); data is retained.

## Predictions

| Method | Path | Success |
| --- | --- | --- |
| `POST` | `/predictions` | `200`, persisted prediction response |
| `GET` | `/predictions` | `200`, predictions newest first |
| `GET` | `/predictions/{id}` | `200`, prediction response |
| `GET` | `/predictions/patient/{patientId}` | `200`, patient history newest first |
| `PUT` | `/predictions/{id}` | `200`, updated prediction response |
| `DELETE` | `/predictions/{id}` | `200`, `Prediction deleted successfully` |

New patient request:

```json
{
  "patient": {
    "name": "Synthetic Patient",
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

Existing patient request:

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

Alternatively identify the existing patient with `patient.id`. If both IDs are present, they must match. If no measurements are supplied, stored health values are used. Supplied overrides form a one-time inference snapshot; they do not modify the patient profile. A partial measurement override is rejected. Gender inputs `male`/`m`/`0` map to integer `0`; `female`/`f`/`1` map to `1`.

The prediction response contains `id`, `diseaseType`, `predictionResult`, `riskLevel`, `probability`, `createdAt`, and nested `patient`. FastAPI is called at `POST {ML_SERVICE_URL}/predict` with numeric `gender` and snake_case health fields. Responses accept `prediction`, `probability`, and a risk key (`risk_level`, `riskLevel`, `classification`, `risk`, or legacy `disease`); `disease_type`/`diseaseType` is optional.

## ML dashboard proxy

These backend routes proxy configured FastAPI routes; browser clients should call the backend, not port 8000 directly.

| Method | Backend path | FastAPI path | Response |
| --- | --- | --- | --- |
| `GET` | `/api/ml/health` | `/health` | ML JSON object/array |
| `GET` | `/api/ml/explainability/global` | `/explainability/global` | ML JSON object/array |
| `POST` | `/api/ml/explainability/individual` | `/explainability/individual` | ML JSON object/array |
| `GET` | `/api/ml/federated/results` | `/federated/results` | ML JSON object/array |

Individual explainability accepts a non-empty JSON object. The proxy passes JSON through, validates that the response is non-empty JSON object/array, applies configured connect/read timeouts, and does not log request/response contents.

## Audit and health

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/integrity/audit/records` | Return stored audit records |
| `GET` | `/integrity/audit/verify` | Verify payload hashes and ordered hash links |
| `GET` | `/actuator/health` | Spring health endpoint; permitted unauthenticated in `prod` |
| `GET` | `/csrf` | Obtain CSRF token/cookie for authenticated production writes |

The audit is a centralized, blockchain-inspired SHA-256 chain prototype, not a decentralized blockchain. New audit payloads avoid raw patient details and prediction results. Existing audit payloads are preserved unchanged so stored chain hashes continue to verify.

## Users

Legacy user endpoints remain: `POST/GET /users`, `GET/PUT/DELETE /users/{id}`. Passwords are BCrypt-encoded and write-only in JSON. This CRUD API does not implement separate user login or role-based ownership.
