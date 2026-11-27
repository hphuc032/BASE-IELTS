# M2 implementation progress

## M2.0 design release

Main was advanced to `35bd9b95e2d99098f8ed9477a7ed79d4763ad4f3`. Both fetch and the live GitHub main ref returned that SHA. `feature/m2-content-foundation` began at that commit. [Design sign-off](M2-DESIGN-SIGNOFF.md) · [Roadmap](M2-IMPLEMENTATION-ROADMAP.md)

## M2.1 catalog schema checkpoint

V13 adds Course, CourseLevel and ReadingLesson tables with scoped uniqueness, restrictive foreign keys, positive position and revision constraints, and ordered indexes. The existing migration owner owns the schema. The checkpoint adds no content logins, grant administration, staff routes or public endpoints. Passage placement depends on the later Passage identity work. Runtime writes and projections wait for the bounded, fenced routines. The migration is additive and transactional. If correction is needed after application, use a forward-only migration.

| Command | Result |
| --- | --- |
| `pwsh -NoProfile -File scripts/test-m2-catalog.ps1` | PostgreSQL 17.12, exit 0, two passed, zero failed, errors or skipped. Empty database applied V1–V13, rerun applied zero. V12 upgrade applied only V13 and retained the PENDING identity fixture and revision. Both checked catalog uniqueness, restrictive FKs, validation, index presence, migration ownership and effective denial for the identity runtime. |
| `backend/mvnw.cmd -B -f backend/pom.xml verify` with MAVEN_BASEDIR set to backend | Host rerun exited 0. Six passed, zero failed or errors, 13 opt-in tests skipped, including the two catalog tests exercised by the dedicated run above. Initial sandbox run stalled after Windows denied a JVM attach named pipe, and was interrupted. No build or security configuration was changed. |
| `npm --prefix frontend run lint` | Exit 0. |
| `npm --prefix frontend run build` | Initial run exited 1 with EPERM on a generated .next directory. Same command with host access exited 0, including TypeScript and static page generation. No frontend changes. |
| `java -jar backend/target/identity-foundation-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev` on port 18092 and the fresh V13 fixture, with BASE_DB_MIGRATE=false | Smoke sequence exited 0. curl confirmed health 200 UP without Set-Cookie and the JDBC pool connected. Anonymous profile, staff catalog, public catalog and Reading requests each returned 401. The test JVM was stopped. |

The PostgreSQL fixtures were `base_ielts_m2test_fresh_20261127034055697` and `base_ielts_m2test_upgrade_20261127034055697`. They were created on local Compose PostgreSQL using existing M1 roles. The standard development database and volumes were preserved. No production deployment or migration.

## Remaining acceptance

M2.1 is in progress. G02 needs a deployment owner to confirm the provisioning principal lifecycle, authenticated mapping, credential ownership, rotation and recovery. G03 needs measured catalog page/search/count and transaction budgets before implementing those operations. Staff REST, F04 effective read grants and concurrency, revision CAS, audit and end-to-end T01/T08/T09/T11/T13/T15 remain open. G01 gates M2.4 publication and does not block the schema checkpoint. M3 discovery and sample activation remain disabled. [Technical gates](M2-OPEN-QUESTIONS.md#remaining-technical-gates)

## M2.1 authority storage and input checkpoint

V14 adds private capability and typed catalog audit storage. The staff runtime remains disabled. The typed input parser and fresh/V13-upgrade PostgreSQL suite passed five cases without skips. The suite also measured a literal course page/count on 10,000 synthetic rows. See the [M2.1 checkpoint report](M2.1-ACCEPTANCE-REPORT.md) for exact commands, fixtures, G02/G03 boundaries and the unresolved authorization integration. [M2.2 status](M2.2-ACCEPTANCE-REPORT.md)

## Parallel M2.1 and M2.2 checkpoint

Decision A is implemented as a narrow shared AUTH transaction seam with scoped CSRF, dedicated DB principal checks and the original M1 account guard. A bounded servlet parser handles the catalog request contract. The staff HTTP routes remain disabled pending G02/G03 and the scoped business routines. Decision B keeps staff UI in M2.6. Under decision C, Agent B prepared private V15 Passage storage independently and verified it against PostgreSQL. The before/after identity regression and focused staff suite passed. Combined catalog/Reading storage also passed. [Intake](M2-PARALLEL-INTAKE-MANIFEST.md) · [M2.1 evidence](M2.1-ACCEPTANCE-REPORT.md) · [M2.2 evidence](M2.2-ACCEPTANCE-REPORT.md)

## 2026-11-27 · Integration owner checkpoint

Both M2.1 and M2.2 acceptance remain blocked. Recovered and checksum-verified V16, tested V17's Catalog point access correction, repaired the isolated staff fixture's routine source, and reserved migration ownership for Reading storage and placement. See [integration checkpoint](M2-INTEGRATION-CHECKPOINT.md) for exact commands, results and open gates.

## 2026-11-27 scope clarification

[PO-07 and PO-08](M2-DECISION-REGISTER.md#2026-11-27-implementation-scope-approvals) confirm M2.2 backend Passage authoring and Passage-to-Lesson placement, M2.3 Exercise/Evidence, and M2.6 staff editors. V18 stays reserved and unimplemented in M2.2. The placement contract uses the existing Catalog ReadingLesson identity and requires an approved forward-only migration order.

### PO-07 and PO-08 execution checkpoint

The staff runner now applies the full Flyway sequence before fixture grants and HTTP tests. V17.1 improves scoped Catalog list plans, and V17.2 adds the private placement relation before reserved V18. The Reading staff route candidate remains disabled pending PostgreSQL routines, atomic audit and acceptance. Both milestone acceptance decisions remain blocked. [Integration evidence](M2-INTEGRATION-CHECKPOINT.md#2026-11-27-integrated-verification)

## 2026-11-27 integrated verification through V17.3

Catalog admission and the Reading V17.3 routines are composed in the isolated integration worktree. The combined 57-test run passed on PostgreSQL 17.12, including fresh and upgrade migration paths, real HTTP fixture measurements, shared audit and cross-module lock/rollback. The separate storage regression passed eight cases. M1 clean verify passed with opt-in skips recorded in the checkpoint. Both milestone release decisions remain open on the G03 workload and graph bounds documented in their acceptance reports. V18 and V19 remain reserved. [Integration results](M2-INTEGRATION-CHECKPOINT.md#2026-11-27-final-integration-phase)

PO-11 and PO-12 are recorded. The integrated launcher now selects the approved Catalog shape and separate overload fixtures. Local clean verify and frontend build passed. Docker engine control timed out before isolated database startup, and Reading graph cardinality remains a documented release gate. [Baseline manifest](M2-INTEGRATION-BASELINE-MANIFEST.md) · [Release evidence](M2-INTEGRATION-CHECKPOINT.md#po-11-and-po-12-release-gate)

### Dedicated PostgreSQL acceptance complete

The PO-11, PO-10, graph-boundary and combined regression gates passed on separate source-built PostgreSQL 17.12. The M1 opt-in, frontend and all three Edge checks also passed. The approved local operational envelopes and fixture commands are recorded in the module reports. PO-12 covers the feature checkpoint; G01, G02 and later content milestones remain gated. [Final acceptance record](M2.1-M2.2-ACCEPTANCE.md)
