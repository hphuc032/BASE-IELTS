# Milestone 1 preflight and Product Owner decision record

Recorded 2026-11-26, Asia/Saigon. Authority is the Product Owner’s “Decision Confirmation and Milestone 1 Execution Directive” in this conversation. Branch main, HEAD bb205adde745faeca3805ef89cdd107ecb3e7d34.

## Confirmed decisions

| Decision | Recorded acceptance and scope |
| --- | --- |
| PO-01 · TECH-03 | Development/test verification adapter only, restricted test-token access. Production registration and resend disabled. No real SMTP, provider or paid mail service. |
| PO-02 · TECH-01 local DB | PostgreSQL 17 via Docker Compose, exact compatible image, named volume, health check and development-only credentials. No existing volume deletion. |
| PO-03 · E-01 | Accepted reconstructed v1.4 finding summary, available evidence and targeted verification as substitute closure basis. Original transcript remains unavailable. |
| PO-04 · E-02 | Accepted three disclosed historical digest discrepancies for closure. Exact causes and expected matching copies remain unknown. Historical expected, observed intake and output hashes stay distinct. |
| PO-05 · identity TECH-12, token retention under TECH-04 | No M1 account hard deletion. Keep terminal tokens at least seven days from the approved terminal-state anchor; never delete live tokens. Preserve Admin/security audit. Broader production retention/deletion is separate. |
| PO-06 · TECH-05 development/test | Dedicated Admin bootstrap from managed deployment environment configuration. No hardcoded credentials. Distinct operator/approver test identities, mailbox evidence, recovery approval and last usable Admin protection. Production operator enablement remains separate. |

Owner also authorizes routine reversible engineering selections inside these constraints. Later functionality stays disabled during M1 by express direction. Formal later deferral sign-off is included in the remaining grouped request. Neither E-03 promotion nor separate M1 implementation authorization was issued in this directive.

## Targeted verification

All nine v1.6 candidate/companion hashes matched before the decision-record update. Historical raw digests and D01–D11 titles/values match the original v1.0 after disregarding table-layout whitespace. Candidate SHA-256: 9A7E1E0812C8C26B8828E627289F183A1A9103955BD408A9AB7A76B8A1BFF602. Source manifest SHA-256: 4F7A9FA99BED367C2ADA88120B1E612EAC0AC503DCA74351AF54AF3073BAA0DF. Both remain unchanged. This decision-record overlay updates mutable registers only; it does not retroactively change the frozen candidate manifest.

A1 requires non-null enum status and the retained credential CHECK. A2 uses scoped authenticated denial EXECUTE, revision-bound recovery, issuer cancellation and terminal-time cleanup. A3 uses atomic bounded PREAUTH membership, separate AUTH and bounded admission. Traceability R16-01–06 and R15-01 are specifications for authorized M1 execution. No additional material design issue in this targeted check. v1.4 is standing; v1.6 is a candidate. Neither architecture approval nor M1 authorization is recorded. [Authority](DOCUMENT-AUTHORITY.md), [Candidate](MILESTONE-0-FINAL-v1.6.md), [Manifest](MILESTONE-0-v1.6-MANIFEST.md), [Traceability](MILESTONE-0-TRACEABILITY.md), [Provenance](MILESTONE-0-v1.4-REVIEW-PROVENANCE.md), [Closure digest evidence](MILESTONE-0-CLOSURE-REPORT.md)

## Workstation evidence

| Check | Observed result and next step |
| --- | --- |
| Java/Maven | Oracle Java 21 (21+35), javac 21, Maven 3.9.11. Ordinary Maven launch prints Access is denied. Inspected launcher: it climbs parent directories when locating .mvn. Skipping startup rc did not remove the diagnostic. Setting MAVEN_BASEDIR to D:\BASE-IELTS for the command process bypassed discovery and mvn -version exited 0 cleanly. No system policy changed. No Maven Wrapper in this docs-only checkout. |
| Frontend/Git | Node 24.14.0, npm 11.9.0 and Git 2.49.0.windows.1. No scaffold or lockfile. git fsck --no-reflogs --no-dangling exited 0. |
| Docker/PostgreSQL | CLI 29.4.3 and Compose v5.1.3 respond. docker version and docker compose ls fail because the desktop-linux pipe dockerDesktopLinuxEngine is missing. Docker Desktop status unreachable and wsl --status returned Wsl/E_ACCESSDENIED in the current context. No Docker engine or PostgreSQL startup verified. No psql or standalone Flyway on PATH. Start Docker Desktop in the desktop user session, wait for Linux Engine, then rerun docker version and docker compose ls before PostgreSQL work. If WSL access still fails there, use an approved functioning Docker host or diagnose Desktop permissions without resetting Docker or deleting volumes. |
| Ports/filesystem | No TCP listeners seen on 3000, 5432 or 8080 during inspection. This does not reserve those ports. Workspace ACL shows modify access for Authenticated Users; authorized Markdown updates demonstrate workspace write access. No Git index access was needed. |
| Artifact network | Direct Maven Central, npm and Docker registry metadata checks failed from this shell with “No such host is known.” Official release and compatibility references establish the proposal below, while exact package resolution and container digest must be confirmed before use. No dependency downloads or installations. |

## One remaining approval request

**Recommended A:** accept the bundle below and explicitly authorize identity-only M1 after E-03 promotion. **Option B:** specify only the items to change or hold. PO-01–06 and E-01/E-02 are already settled and excluded from the request.

- **TECH-01:** Temurin 21.0.13.1+2, Maven Wrapper pinned to Maven 3.9.11, Spring Boot 3.5.16. Use Boot-managed Security 6.5.11, Flyway 11.7.2 with PostgreSQL module, JDBC 42.7.11, JUnit 5.12.2 and Testcontainers 1.21.4; JPA/Hibernate and Bean Validation via Boot. Use springdoc 2.9.0. Pin PostgreSQL 17.12 Bookworm official image by digest once Registry and Engine cooperate. Frontend proposal Node 24.22.3, bundled npm, Next.js 16.4.6, TypeScript 5.9.3, Tailwind 4.1.18, Playwright 1.64.0 and compatible React/ESLint peers resolved and locked during authorized M1.1. Boot documentation supports Java 21/Maven 3.9.11 and lists the managed dependencies. Next.js 16 requires Node ≥20.9 and TypeScript ≥5.1. This is one recommended compatibility set, not a local build or registry-resolution claim. Confirm exact artifacts and peer resolution in the first authorized reproducible build before freezing runtime and dependency locks. [Boot requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Boot BOM](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html), [springdoc releases](https://github.com/springdoc/springdoc-openapi/releases), [PostgreSQL official image tags](https://github.com/docker-library/official-images/blob/master/library/postgres), [Temurin releases](https://github.com/adoptium/temurin21-binaries/releases), [Node 24 distributions](https://nodejs.org/dist/latest-v24.x/), [Next release](https://github.com/vercel/next.js/releases/tag/v16.4.6), [Next compatibility](https://nextjs.org/docs/app/guides/upgrading/version-16), [TypeScript release](https://github.com/microsoft/TypeScript/releases/tag/v5.9.3), [Tailwind release](https://github.com/tailwindlabs/tailwindcss/releases/tag/v4.1.18), [Playwright release](https://github.com/microsoft/playwright/releases/tag/v1.64.0)
- **TECH-02 and A1–A3:** accept the manifested contracts and enforce same-origin/private backend, one process with stop-before-start, in-memory binding, restart logout and durable revision fencing. Accept remaining **TECH-04** v1.6 limits unchanged, including 30-minute verification TTL, 60-second resend cooldown, proposed admission/timing budgets and atomic PREAUTH 2,000 maximum / 15-minute lifetime with fail-closed 429. No change to D06/D07.
- **Remaining TECH-05 and identity TECH-12:** accept v1.6 issuer-only cancellation, exact-payload one-use recovery, mapped identity denial audit and protected runtime roles. Accept explicit AUTH/PREAUTH synchronizer CSRF and adaptive Argon2id (19 MiB, two iterations, parallelism one as proposed initial settings), minimum 15 and supported maximum 128 Unicode code points, no truncation, local common-password screening and bounded hash admission. Benchmark before deployment. Detailed production operator and privacy approval stays gated. [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- **TECH-08 and E-03:** approve immutable edition graphs, guarded writers and coherent snapshot projections as architecture invariants, then explicitly promote this exact v1.6 candidate. Keep content implementation outside M1.
- **Formal later deferrals:** accept the table below. Issue **separate identity-only M1 authorization** after recording promotion.

| Deferral | Disabled | Earliest resolution and acceptance |
| --- | --- | --- |
| TECH-06 | Exercise sample selection/exposure | M3/M4, approve fixed membership and safe projections; passing PU/U12 and I14 eligibility/replay where relevant. D02 remains unchanged. |
| TECH-07 | Learning feedback behavior/rollout | M4, approved feedback/reveal and immutable scoring-lock behavior; R12/R13 prove no unauthorized disclosure. |
| TECH-09 | Assessment grading and expiry worker | M4, resolve L-01 scan/claim transaction boundary and bounded indexed work; approved I14-05/G13-01/02/03. **OPEN M4 IMPLEMENTATION GATE.** |
| TECH-10 | Author/publisher permissions, privileged publish and restricted grants | M2 before authoring rollout; approved DB role matrix and publication/FK/snapshot checks P12/P13. |
| TECH-11 | Future features/providers, including external integrations | Dependent release, approve named feature, cost, licensing and privacy, then isolated release tests before enabling. |
| Non-identity TECH-12 | Later retention/anonymization, deletion and telemetry | Before dependent data handling or production enabling; approved retention/permissions policy with negative deletion and audit preservation tests. PO-05 remains applicable to M1 identity. |

No source, migrations, infrastructure, database, dependency installation, staging or implementation test activity. The existing M1.1–M1.8 plan remains identity-only and conditional. [Implementation plan](MILESTONE-1-IMPLEMENTATION-PLAN.md)

## Decision-only companion content hashes

These replace no historical manifest entry. The candidate and historical files have no content delta. The overlay itself is omitted to avoid self-hashing.

| Updated companion | Raw SHA-256 after PO confirmations |
| --- | --- |
| DECISION-REGISTER.md | 12503C360A353FF181CF9BC81D68AFE084A8D500C0FFADCBFE8478F1132E5976 |
| DOCUMENT-AUTHORITY.md | F3B2D0CB5AC97865D819F0542854364B3399505346B77DD1FEB4EAE4EACC5271 |
| MILESTONE-0-PRODUCT-OWNER-SIGNOFF.md | FE28176B2F969728349162E93DB15A37B45E3A0B64EFE9D13BA016F819838404 |
