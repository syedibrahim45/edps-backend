# EDPS Backend Deployment

## Configuration

Set configuration outside the application and container. Required for the `prod` profile:

| Variable | Required | Description |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Yes | Use `prod` for deployment |
| `DB_URL` | Yes | MySQL 8 JDBC URL, including database `edps_db` |
| `DB_USERNAME` | Yes | Database account |
| `DB_PASSWORD` | Yes | Database password |
| `APP_ADMIN_USERNAME` | Yes | Shared demo Basic-auth admin username |
| `APP_ADMIN_PASSWORD` | Yes | Shared demo Basic-auth admin password |
| `CORS_ALLOWED_ORIGINS` | Yes | Comma-separated exact frontend origins |
| `ML_SERVICE_URL` | No | FastAPI base URL; default `http://localhost:8000` |
| `ML_CONNECT_TIMEOUT` | No | Default `2s` |
| `ML_READ_TIMEOUT` | No | Default `5s` |

Use a least-privilege MySQL account rather than `root` outside local development. The configured account must have access to the existing application tables and be permitted to create/update the additive audit tables. Never pass credentials as Docker build arguments, bake them into an image, or commit them. Use the deployment platform's secret store and TLS.

## Build and local startup (PowerShell)

Ensure MySQL is running and `edps_db` exists. These commands do not drop tables:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/edps_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "root"
$securePassword = Read-Host "MySQL password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $securePassword).Password
$env:SPRING_PROFILES_ACTIVE = "dev"
.\mvnw.cmd --batch-mode clean test
.\mvnw.cmd spring-boot:run
```

The current environment's automated tests use H2 and do not validate MySQL authentication. If the app reports `Access denied ... (using password: NO)`, Maven did not inherit `DB_PASSWORD`; set the variable in the same PowerShell terminal that launches the app. Do not fall back to a second database or disable startup failure.

## Docker

Build from the repository root:

```powershell
docker build -t edps-backend:local .
```

The image uses Java 21, runs as a non-root user, and defaults to the `prod` profile. Pass runtime environment variables through a protected secrets mechanism. Example for a preconfigured local container network (replace secret placeholders locally; do not commit them):

```powershell
docker run --rm -p 8080:8080 `
  -e DB_URL="jdbc:mysql://host.docker.internal:3306/edps_db?serverTimezone=UTC" `
  -e DB_USERNAME="edps_app" `
  -e DB_PASSWORD="$env:DB_PASSWORD" `
  -e APP_ADMIN_USERNAME="$env:APP_ADMIN_USERNAME" `
  -e APP_ADMIN_PASSWORD="$env:APP_ADMIN_PASSWORD" `
  -e CORS_ALLOWED_ORIGINS="http://localhost:5173" `
  -e ML_SERVICE_URL="http://host.docker.internal:8000" `
  edps-backend:local
```

Do not deploy the example hostnames as-is without confirming container networking and firewall policy. The backend health probe is `GET /actuator/health` on port 8080; use this path for orchestrator readiness/liveness as appropriate.

## Schema and data safety

Hibernate currently uses `ddl-auto=update` so the existing schema and data are preserved while the application adds its audit tables. No destructive schema operation is part of startup. Back up `edps_db`, test schema changes against a copy, and review generated DDL before release. For ongoing production releases, move schema changes to reviewed, versioned Flyway/Liquibase migrations after documenting/baselining the actual existing MySQL schema; do not apply a guessed initial migration to production. Retain and back up the existing `patients`, `predictions`, and audit tables.

## Network, authentication, and privacy

- Expose the backend API, not the FastAPI service, to the React browser.
- Configure exact frontend origins in `CORS_ALLOWED_ORIGINS`.
- `prod` requires HTTP Basic credentials on API routes except health and CSRF bootstrap; writes also require a CSRF cookie and `X-XSRF-TOKEN` header.
- React must use credentialed requests, fetch `/csrf`, retain its cookie, and send its returned token on `POST`/`PUT`/`DELETE`. Set Basic auth credentials through a suitable private deployment mechanism; browser storage of long-lived shared credentials has risks.
- The single configured admin is an academic-demo safeguard, not user-specific authorization. Do not expose real patient data publicly. Use synthetic records in demonstrations.
- Keep audit and database access restricted. The hash chain is an application-level centralized integrity prototype, not a decentralized blockchain or a substitute for database access controls.
