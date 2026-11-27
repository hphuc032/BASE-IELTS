# Milestone 1 acceptance report

## Verdict

M1.1–M1.8 passed for the local Identity Foundation. Milestone 0 remains closed and v1.6 remains authoritative. The approved architecture file has SHA-256 `9A7E1E0812C8C26B8828E627289F183A1A9103955BD408A9AB7A76B8A1BFF602`. The Product Owner separately authorized scoped management reconciliation and retains the existing product decisions. Release authorization is conditional on the verified Git preflight. Production enablement and M2 are outside this acceptance.

## Implementation and evidence

| Work package | Accepted behavior |
| --- | --- |
| M1.1 | Loopback backend, pinned PostgreSQL 17.12 in Compose, separate database runtime and migration identities, public health with no session allocation, same-origin Next ingress |
| M1.2–M1.3 | Identity-only schema, V1–V12, role separation, native PostgreSQL row fences, restricted definer routines, no broad runtime or management access |
| M1.4 | Pending registration, scoped CSRF, atomic activation, dev/test-only mail, durable token and cleanup behavior, 150 ms verification budget, no production delivery |
| M1.5 | One authoritative AUTH binding per account, separate bounded PREAUTH, session expiry and generation handling, revision fencing, stale cookie rejection |
| M1.6 | Separate test operator, approver and lifecycle roles, bootstrap, one-use approvals, last-Admin protection and scoped outcome reads |
| M1.7 | Registration, confirmation, login/logout, profile edit and session-expired UI |
| M1.8 | Backend, PostgreSQL privilege and migration fixtures, frontend and three Edge scenarios on the final working tree |

Management reconciliation uses V11 and V12. Recovery looks up the existing approval UUID using a separate read-only deployment-operator connection. It correlates consumption with the successful audit and returns the target revision at commit. Durable cancellation can prove NOT_COMMITTED. Expiry or missing proof returns UNKNOWN. Lifecycle reset writes one receipt atomically with credential and revision changes. The lifecycle login looks up only its own operation UUID in a read-only transaction. Tests suppressed real socket acknowledgements, then used the actual management CLI outcome implementation and authorized connections to confirm commit. Reusing a consumed approval or operation reference cannot repeat the mutation. The fixture oracle separately confirmed audit attribution and distinct consumed approvals. Runtime and approver logins cannot execute either read. The tested logins cannot read the receipt or audit tables directly or call the private mutation routine.

Forced and graceful shutdown tests observed a real profile PATCH executing inside PostgreSQL before stopping admission. Independent reads verified rollback after forced exit and commit after graceful shutdown. No old application connections remained. Ingress returned 503 while closed, rejected the old AUTH cookie on replacement, and accepted fresh authentication. Browser coverage includes HTTP cookie isolation, HTTPS, Host, CSRF, scope, expiry and logout.

## Verification reliability classification

The full run exercised 33 registration requests in bounded concurrency bands including pool reservations, with 29 distinct captures and no duplicate capture. Authenticated profile reads returned 200 in 25–83 ms. During separately controlled retention concurrency, PostgreSQL `pg_locks.waitstart` produced 26 samples, p50 11.037 ms, p95 19.414 ms and max 19.926 ms. Issuance used bounded lock waits, sent no mail while blocked and resumed after cleanup released its lock. An unrelated live token survived.

The historical SQLSTATE 08006 commit timeout did not reproduce in the normal bounded workload. Its historical root cause is unknown. Injected lost acknowledgement independently proved a committed live token can exist even if the caller cannot confirm COMMIT. The application closed the connection, sent no mail on uncertainty and preserved the 150 ms deadline. This is local-bench evidence. Production database or network tails and pool pressure can still consume the available deadline.

## Final regression

| Command or invocation | Result |
| --- | --- |
| `pwsh -NoProfile -File scripts/test-admin-recovery.ps1 -FixtureDatabase base_ielts_m1test_261126220031 -Full` | Exit 0, Maven clean verify 17 passed, 0 failed/errors/skipped. Audit oracle passed. |
| `scripts/test-dev-backend.ps1 -Migrate` | Exit 0, V10 to V12, JDBC health 200 with no cookie. Preserved applied V1–V10. |
| `scripts/test-dev-backend.ps1 -Migrate -FixtureDatabase base_ielts_m1test_261127012` | Exit 0, fresh V1–V12, JDBC health 200 with no cookie. |
| Six `backend/src/test/postgresql/*.sql` fixtures via `docker compose exec -T postgres psql -Xq -v ON_ERROR_STOP=1` on the fresh fixture | All six exited 0. Account fencing, governance including expiry, identity schema, profile retry, retention and verification. |
| `npm --prefix frontend run lint`, `npm --prefix frontend run build` | Both exit 0. Next 16.4.6. Build used BASE_BACKEND_ORIGIN=http://127.0.0.1:18083. |
| `scripts/test-auth-browser.ps1 -FixtureDatabase base_ielts_m1test_261127012` with BASE_BROWSER_MIXED_LOAD=true | Exit 0, Edge 1 passed in 1.1 minutes. |
| Same Edge launcher with `-Https -ActiveShutdown` | Exit 0, 1 passed in 30.9 seconds. |
| Same Edge launcher with `-Https -ActiveShutdown -GracefulShutdown` | Exit 0, 1 passed in 31.0 seconds. |

The final local regression has no failed or skipped required case. Earlier failures and their superseding results are preserved in [M1 progress](MILESTONE-1-PROGRESS.md). Historical V9→V10 verification remains in that record. This run verified V10→V12 and clean V1→V12. The toolchain pins Java 21, Maven Wrapper 3.9.11, Spring Boot 3.5.16, PostgreSQL 17.12, Next 16.4.6 and Playwright 1.61.1.

## Local startup, shutdown and production gates

Follow the [README](../README.md) and use the ignored local environment file with separate role credentials. Start Docker PostgreSQL with `docker compose up -d --wait postgres`. Run the dev database migration smoke before bringing up the regular application. Stop local ingress first and allow the owned application process to terminate before starting its replacement. `docker compose stop postgres` stops the local database without deleting its volume. The synthetic management command is restricted to isolated fixture databases.

Production HTTPS configuration, real delivery, production operator onboarding, retention operations and production traffic characterization remain separate gates. No future-domain tables or endpoints were introduced.
