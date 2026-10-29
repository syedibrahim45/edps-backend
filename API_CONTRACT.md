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

## Experimental diabetes screening

| Method | Path | Success |
| --- | --- | --- |
| `POST` | `/diabetes-screenings` | `200`, experimental screening response |

This route calls FastAPI `POST /predict/diabetes`. It is independent of
`/predictions`: it does not create or update a patient, prediction-history
record, or integrity-audit record. The request is never mapped from a stored
patient profile; callers must submit the 21 dataset survey features explicitly.

Example request:

```json
{
  "HighBP": 1,
  "HighChol": 1,
  "CholCheck": 1,
  "Smoker": 0,
  "Stroke": 0,
  "HeartDiseaseorAttack": 0,
  "PhysActivity": 1,
  "Fruits": 1,
  "Veggies": 1,
  "HvyAlcoholConsump": 0,
  "AnyHealthcare": 1,
  "NoDocbcCost": 0,
  "DiffWalk": 0,
  "Sex": 0,
  "BMI": 27.5,
  "GenHlth": 3,
  "MentHlth": 0,
  "PhysHlth": 0,
  "Age": 9,
  "Education": 4,
  "Income": 5
}
```

Every field is required, and extra fields are rejected. Values must be JSON
numbers of the specified type; strings and booleans are not coerced.
Categorical fields require integer tokens:

| Feature(s) | Survey meaning | Accepted values |
| --- | --- | --- |
| `HighBP` | Ever told by a health professional that they have high blood pressure | `0` = no, `1` = yes |
| `HighChol` | Ever told by a health professional that they have high cholesterol | `0` = no, `1` = yes |
| `CholCheck` | Cholesterol checked within the prior five years | `0` = no, `1` = yes |
| `Smoker` | Smoked at least 100 cigarettes in their lifetime | `0` = no, `1` = yes |
| `Stroke` | Ever told they had a stroke | `0` = no, `1` = yes |
| `HeartDiseaseorAttack` | Reported coronary heart disease or myocardial infarction | `0` = no, `1` = yes |
| `PhysActivity` | Physical activity in the prior 30 days, outside regular work | `0` = no, `1` = yes |
| `Fruits` | Consumed fruit at least once per day | `0` = no, `1` = yes |
| `Veggies` | Consumed vegetables at least once per day | `0` = no, `1` = yes |
| `HvyAlcoholConsump` | Dataset heavy-alcohol-consumption indicator | `0` = no, `1` = yes |
| `AnyHealthcare` | Any health-care coverage | `0` = no, `1` = yes |
| `NoDocbcCost` | Unable to see a doctor due to cost in the prior 12 months | `0` = no, `1` = yes |
| `DiffWalk` | Serious difficulty walking or climbing stairs | `0` = no, `1` = yes |
| `Sex` | Dataset-coded sex category | `0` = female, `1` = male |
| `BMI` | Body mass index survey value | Any finite JSON number; no range is imposed |
| `GenHlth` | General-health self-rating, from best to worst | Integer `1`–`5` |
| `MentHlth` | Days of poor mental health in the prior 30 days | Integer `0`–`30` |
| `PhysHlth` | Days of poor physical health in the prior 30 days | Integer `0`–`30` |
| `Age` | Dataset age bucket, not chronological years | Integer `1`–`13` |
| `Education` | Dataset education category, ordered by level | Integer `1`–`6` |
| `Income` | Dataset income category, ordered by level | Integer `1`–`8` |

BMI strings, booleans, malformed numbers, NaN, and infinity are rejected.
The source dataset's observed BMI range (12–98) is not a clinical validity
range; the backend does not impose it. Malformed JSON, missing/extra fields,
or invalid values return the backend's structured HTTP `400` response.

Example response:

```json
{
  "predicted_class": 1,
  "predicted_label": "Prediabetes or diabetes reported in dataset label",
  "positive_class_score": 0.72,
  "threshold": 0.6447012298480324,
  "model_id": "logistic_regression_balanced",
  "model_version": "cdc-brfss2015-binary-v1",
  "disclaimer": "Experimental screening output for a survey dataset label only; not a clinical diagnosis or clinically validated risk estimate."
}
```

Class `0` means “No diabetes reported in dataset label”; class `1` means
“Prediabetes or diabetes reported in dataset label.” Class `1` is not a
confirmed diagnosis. `positive_class_score` is the model's class-1 score,
not a clinically calibrated probability. The FastAPI model applies its
saved threshold and returns it for context; the backend does not reinterpret
the score or threshold.

FastAPI HTTP `422` is translated to a sanitized backend `502`; FastAPI `503`,
connection failures, and timeouts become sanitized `503` responses. Other
upstream errors or malformed/incompatible responses become sanitized `502`
responses. Upstream bodies, paths, and stack traces are not returned. In
production, the route requires HTTP Basic `ADMIN` authentication and a valid
CSRF cookie/token for POST requests. Existing dev/test profile security
behavior is unchanged. The configured CORS origin restrictions also apply.

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
