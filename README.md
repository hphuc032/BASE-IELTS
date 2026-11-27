# BASE IELTS

An identity-only foundation for the BASE IELTS learning platform. See the [approved architecture](docs/MILESTONE-0-FINAL-v1.6.md), [authority record](docs/DOCUMENT-AUTHORITY.md) and [current progress](docs/MILESTONE-1-PROGRESS.md).

## Local foundation

Use Java 21, Node.js 24, npm, and Docker Desktop with its Linux Engine running. The Maven Wrapper pins Maven 3.9.11.

Create a local `.env` from [the example](.env.example), set a development-only bootstrap password, then run:

```powershell
docker compose config --quiet
docker compose up -d --wait postgres
docker compose ps
```

PostgreSQL listens only on loopback and keeps data in a named volume. Do not delete the volume to rotate credentials. The bootstrap identity is for local initialization only. The backend uses distinct runtime and migration identities. With their development credentials configured in the ignored `.env`, run `./scripts/initialize-dev-db.ps1` to provision the roles and `./scripts/test-dev-backend.ps1 -Migrate` after building to migrate and verify real JDBC connectivity. Routine backend starts leave Flyway disabled.

Build the backend and frontend:

```powershell
Push-Location backend
.\mvnw.cmd -B verify
Pop-Location
Push-Location frontend
npm.cmd ci
npm.cmd run lint
npm.cmd run build
Pop-Location
```

For database-connected backend startup once the runtime role is provisioned, export `BASE_DB_JDBC_URL`, `BASE_DB_RUNTIME_USER`, and `BASE_DB_RUNTIME_PASSWORD` in the backend process and run `backend\mvnw.cmd -f backend\pom.xml spring-boot:run` from the repository root. The backend defaults to `127.0.0.1:8080`. The public endpoint `GET /api/v1/health` exposes only health status.

In `frontend`, run `npm.cmd run dev` and open `http://127.0.0.1:3000`. Next.js forwards `/api/v1/*` to the private backend origin. Enable identity routes explicitly with `BASE_IDENTITY_ENABLED=true`. The process-local test mailbox also requires the dev or test Spring profile and `BASE_IDENTITY_VERIFICATION_CAPTURE=true`. It is read by integration fixtures and exposes no HTTP inbox. Production email delivery is disabled.

The local Playwright fixture uses Edge loopback, an already migrated dedicated test database named `base_ielts_m1test_<digits>` and synthetic email identities. Build Next with `BASE_BACKEND_ORIGIN=http://127.0.0.1:18083` before running `./scripts/test-auth-browser.ps1 -FixtureDatabase <test-database>`. Run the regular frontend build with its normal backend origin to return to interactive development. Browser loopback tests do not validate production HTTPS or ingress policy.

If `docker version` cannot find `dockerDesktopLinuxEngine`, open Docker Desktop in your desktop user session and wait for the Linux Engine before retrying. Registry access is needed to resolve uncached dependencies and the pinned image.

## M1.8 management outcomes on the isolated fixture

Use the existing DevelopmentAdminCommand launcher with the synthetic test-only flag. RECOVERY_OUTCOME uses BASE_ADMIN_APPROVAL and the deployment-operator credential. RESET_CREDENTIAL uses BASE_ADMIN_OPERATION, BASE_ADMIN_TARGET, BASE_ADMIN_REVISION and the lifecycle operator credential. Generate and retain the operation UUID before RESET_CREDENTIAL. After an uncertain reset, run RESET_OUTCOME with the original UUID and lifecycle credential. Lookup never needs the replacement password. UNKNOWN means no committed proof was found. Preserve the original reference and reconcile before deliberately retrying. Production enablement remains outside M1.
