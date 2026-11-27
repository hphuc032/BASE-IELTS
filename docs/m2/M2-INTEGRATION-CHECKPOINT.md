# M2 integration checkpoint · 2026-11-27

**Integration Owner** · M2.1 Catalog **BLOCKED** · M2.2 Reading **BLOCKED**.

## V16 forensic record

Recovered the 12,577-byte LF source from Agent A's working tree and copied it byte-for-byte into the integration checkout. The [exact SQL](../../backend/src/main/resources/db/migration/V16__staff_catalog_routines.sql) has raw SHA-256 `2e488fc22aba5df1af34fb73de0118d46e155d0fd4bd90a85f0f4b0a12810c81`. The file has 209 LF line endings and no CRLF. Git attributes pin this path against checkout conversion. Path history across local refs and reflogs returned no committed V16. A filename scan of the registered Reading worktree and Codex worktree directories found no independent copy. Arbitrary local backup media and unnamed unreachable blobs were outside the search. Flyway CRC32 over the concatenated SQL lines is `957144442`. The checksum establishes agreement with Flyway history. SHA-256 identifies the recovered byte sequence.

On PostgreSQL 17.12, read-only `psql -X -v ON_ERROR_STOP=1` queries against `identity.flyway_schema_history` produced the following. Timestamps are recorded as returned by PostgreSQL.

| Fixture suffix after base_ielts_m2test_ | Version | Description | Checksum | Installed on | Success |
| --- | --- | --- | ---: | --- | --- |
| v16fresh_20261127044354291 | 15 | reading passage storage | -570604612 | 2026-11-27 11:44:06.480417 | true |
| v16fresh_20261127044354291 | 16 | staff catalog routines | 957144442 | 2026-11-27 11:44:06.542397 | true |
| v16upgrade_20261127044354291 | 15 | reading passage storage | -570604612 | 2026-11-27 11:44:07.581921 | true |
| v16upgrade_20261127044354291 | 16 | staff catalog routines | 957144442 | 2026-11-27 11:44:07.72673 | true |

## Initial migration allocation (historical)

| Versions | Owner | Contract and dependencies | Status |
| --- | --- | --- | --- |
| V1–V12 | Existing M1 | Accepted identity, account fence in V2, governance and audit | Preserved |
| V13–V14 | Agent A | Course, Level, ReadingLesson, capability grants and typed Catalog audit. Depends on M1 identity. | Preserved |
| V15 | Agent B | Passage, Topic, PassageVersion, stable Paragraph identity and Translation. Depends on M1 identity. | Verified in the shared migration order |
| V16 | Agent A | Catalog staff projections and commands. Depends on V2 and V13–V14. V15 has no SQL dependency for V16. | Recovered, applied and validated |
| V17 | Integration Owner | Catalog point selection using existing Catalog primary keys. Depends on V16. | Fresh and upgrade migration tests passed |
| V18 | Agent B | Private Exercise, ExerciseVersion, groups, questions, marking, options, explanations and Evidence. Depends on V15 exact edition keys. | Reserved |
| V19 | Integration Owner | Passage-to-ReadingLesson placement and shared typed Reading audit extension. Depends on V13–V15 and V18. | Reserved pending placement and audit review |

Agent A owns the Catalog route and test coverage for course, level and lesson. Finish list/parent access-path evidence and representative G03 contention measurements. Agent B owns Reading persistence for V18 and guarded staff operations. Submit real-schema PostgreSQL verification before requesting V19 integration. The Integration Owner owns migration numbers, shared runner, placement and cross-module security. All work uses the existing AUTH, scoped CSRF and account-fence seams.

V18 must enforce an ExerciseVersion's exact PassageVersion with a composite FK inside the exercise's Passage family. Evidence must bind the exact question edition, pinned passage edition and stable paragraph via three composite FKs. Groups and options require edition/question-scoped reference keys. Frozen versions and descendants require database immutability guards, including attempts to reparent a child. Exercise modes use the approved LEARNING/EXAM combinations and nullable 1–86,400-second EXAM duration. V19 must reference `content_catalog.reading_lesson(id)` and preserve the unassignment revision tombstone. Reading audit and CAS must commit with each guarded write. Test real PostgreSQL for wrong-edition references, both orders of evidence insertion versus paragraph deletion, frozen descendant updates, rollback, concurrent CAS and fence retention through DTO materialization. [Agent B proposal at the verified checkpoint](https://github.com/hphuc032/BASE-IELTS/blob/483b9d0d27a36d6e6d0c58fd239bd2dbfd6ab87b/docs/m2/M2.2-READING-MIGRATION-SPEC.md)

## Runner and G03

The HTTP script loaded V1/V2 and then V13/V14 with duplicate fixture-owned implementations such as `get_course`. Java calls `get_item`, `list_items` and `mutate_item`. The isolated HTTP runner now pins V16 SHA-256, loads V16/V17 definitions, then applies test-only role ownership and column/EXECUTE grants. The distinct Flyway runner performs the complete fresh and upgrade paths. The HTTP script still uses selective SQL initialization and has no Flyway upgrade mode.

On fixture `base_ielts_m2test_fresh_20261127051605593`, insert 10,000 synthetic courses and ANALYZE in a rolled-back transaction. Bind kind `COURSE` and UUID `780650af-e57a-7100-2a3e-3e4e25071c13`. `EXPLAIN (ANALYZE, BUFFERS)` on the inner detail statements gave:

| Statement | Access path | Rows returned / discarded | Shared hits | Execution |
| --- | --- | --- | ---: | ---: |
| V16 `SELECT * FROM catalog_staff.items($1) i WHERE i.id=$2` | Function Scan | 1 / 9,999 | 370 | 4.846 ms |
| V17 projection with `id=$2` inside each resource branch | Index Scan on course_pkey | 1 / 0 | 3 | 0.045 ms |

Existing indexes were unique btrees on ID and slug and a btree on (display_order,id), with 10,000 estimated rows after ANALYZE. Each probe used one SQL statement. The planned V17 statement pruned the Level and Lesson branches for this bind. V17 adds no index. It changes detail and mutation UUID lookups while keeping routine signatures, ACLs, checks, fixed search paths and atomic audit. The list/count implementation remains on the V16 path. The probe is a single inner SQL sample. Detail route percentiles, parent/list plans, pool acquisition, overload and representative contention remain open. The synthetic HTTP sample measured 120 course list requests, six JDBC statements per request, peak reader pool 4/4 and p50/p95/p99/max of 34.498/48.228/54.031/54.110 ms. JDBC through COMMIT was 28.591/40.776/46.172/47.115 ms. A held-row HTTP request failed closed at 262.835 ms with a 250 ms lock timeout. G03 stays open.

## Regression and release

- `pwsh -NoProfile -File scripts/test-m2-catalog.ps1 -IncludeReading -ComposeRoot D:/BASE-IELTS` exited 0 twice, 8 passed and zero failures, errors or skips in each run. The first tested fresh V1–V16 and V15→V16, the second fresh V1–V17 and V15→V17. Both included fresh V15 and V14→V15 Reading storage tests. Fixtures used prefixes `base_ielts_m2test_fresh_`, `base_ielts_m2test_upgrade_`, `base_ielts_m2test_reading_fresh_` and `base_ielts_m2test_reading_upgrade_`, first with suffix `20261127051518624`, then `20261127051605593`. Catalog tests independently checked the V16 history checksum, V15 identity preservation and denied M1 runtime EXECUTE on the Catalog detail routine.
- First `pwsh -NoProfile -File scripts/test-m2-staff-auth.ps1 -IncludeCatalog` exited 1, 15 passed, zero assertion failures, one SQL error and zero skips. The privilege test referenced obsolete `require_capability`. The fixture assertion now targets `require_access`.
- GitHub `git ls-remote origin` verified main `35bd9b95e2d99098f8ed9477a7ed79d4763ad4f3`, Agent A `860fbea3a510c125bda94012faf9a73bbe100935` and Agent B `483b9d0d27a36d6e6d0c58fd239bd2dbfd6ab87b`. The integration branch `codex/m2-integration` starts from the Agent A descendant of that verified common baseline. Merge preview reported one add/add conflict in `docs/m2/M2.2-ACCEPTANCE-REPORT.md`. Branch integration, full M1 regression, frontend lint/build, Edge and cross-module acceptance remain pending. Both original worktrees were preserved.
- M2.1 requires full-sequence staff HTTP regression and G03 resource evidence before acceptance. M2.2 requires actual staff persistence, real-schema Evidence/Exercise tests and Lesson placement verification. G02 remains open and no production provisioning was performed. No integration commit or push was made.

### Final staff regression

The corrected staff runner exited 0 with 16 passed, zero failures, errors or skips. Five exercised Catalog HTTP against PostgreSQL, including capability separation, denied direct routine access, session revocation, concurrent CAS and audit rollback. The final 120-request list sample yielded request p50/p95/p99/max 36.975/47.079/48.408/48.702 ms and JDBC through COMMIT 31.255/39.013/41.726/42.320 ms. The held-row request failed closed after 265.073 ms. See the ignored local log backend/m2-integration-staff.log.

The approved roadmap assigns the Exercise/Evidence graph to M2.3 and the staff editors to M2.6. The reservations protect numbering and provide an integration target. Confirm the scope of any release that brings in the Reading editor routes or treats Exercise/Evidence as M2.2 acceptance.

Combined source-copy command used the Maven Wrapper with -B -Dtest=ReadingDraftContractTest,ReadingEditionLifecycleTest,ReadingStaffProjectionTest,CatalogRouteBoundaryProofTest test. Exit 0, 19 passed, zero failures, errors or skips. Seventeen exercised Agent B's verified domain checkpoint and two exercised Catalog route boundaries. This source copy excludes the disputed report and the later-scope frontend routes. See backend/m2-integration-domain.log.

## 2026-11-27 authoritative allocation after PO-07 and PO-08

| Version | Owner | Dependencies and current allocation |
| --- | --- | --- |
| V1–V12 | M1 | Accepted identity foundation, including V2 account fencing. |
| V13–V14 | Catalog | M1. Catalog identities, capabilities and typed audit. |
| V15 | Reading | V1 identity. Passage versions, topics, paragraphs and translations. |
| V16 | Catalog | V2, V13 and V14. Recovered routines, raw SHA-256 2e488fc22aba5df1af34fb73de0118d46e155d0fd4bd90a85f0f4b0a12810c81 and Flyway checksum 957144442. |
| V17 | Integration Owner | V16. Existing primary-key lookup correction using scoped SQL predicates. No new index. |
| V17.1 | Catalog | V16–V17. Pushes the parent condition into Catalog list queries using existing indexes. |
| V17.2 | Integration Owner | V13 and V15. Private Passage-to-ReadingLesson placement and retained unassignment revision. |
| V18 | M2.3 | V15 exact edition keys. Reserved for Exercise/Evidence and unimplemented during M2.2. |
| V19 | Integration Owner | Unallocated. The placement relation is covered by V17.2. |

The API and ERD require durable placement with a restrictive FK to Catalog ReadingLesson, a positive CAS revision, and a retained unassignment tombstone. V17.2 adds the relation before the reserved V18 in Flyway order. No out-of-order exception or version reuse is needed.

### Default-order migration allocation

The Integration Owner allocates V17.2 to the M2.2 private placement relation. Dotted versions sort between V17 and the reserved V18. V19 is unnecessary for this placement relation. This change adds the restrictive Catalog FK and the ordered placement index from the approved ERD, without touching V1–V17, granting access, activating staff endpoints, or implementing M2.3. V18 stays reserved and unimplemented. The earlier sequencing concern applies to placing this M2.2 work at V19. Guarded authoring and audit remain subject to the staff transaction and G03 contracts.

## 2026-11-27 integrated verification

- The first shared Flyway runner attempt successfully installed V1–V17, then the Catalog test rejected the generated fixture URL on all six Catalog tests. The URL allowlist now admits only the isolated loopback staff database pattern. No HTTP assertions or production policies were relaxed.
- The final `pwsh -NoProfile -File scripts/test-m2-staff-auth.ps1 -IncludeCatalog` exited 0. PostgreSQL 17.12 fixtures `staff_fresh_5f99ec8b93994064a24b2cf9ec20b55d` and `staff_upgrade_5f99ec8b93994064a24b2cf9ec20b55d` passed 1 Flyway/placement test and 17 staff tests each, 36 passes overall and zero failures, errors or skips. Upgrade used V15 as baseline. Full Flyway migration, validation and no-op rerun preceded fixture grants. See the M2.1 report for exact G03 before/after measurements and remaining limits.
- The first full M1 backend regression exited 1 with 44 reported, 28 passed, three failures, zero errors and 13 skipped. Two nested PostgreSQL observer processes inherited no Compose configuration in the managed checkout. The third failure was a 429 on PREAUTH from the shared default MockMvc source. The rerun passed an explicit Compose environment to the fixture processes and used a dedicated mock source for the commit-outcome test while retaining real admission rules.
- The corrected M1 backend run executed the existing full recovery launcher in the integration worktree with V12 pinned, Compose project base-ielts, D:/BASE-IELTS/.env as the fixture settings source, and backend/mvnw.cmd -B -f backend/pom.xml verify. Synthetic PostgreSQL 17.12 database base_ielts_m1test_261126220031. Exit 0, 46 reported, 33 passed, 0 failures/errors and 13 opt-in tests skipped. The independent audit oracle verified four committed recoveries with distinct approvals and correct attribution. See backend/m2-m1-backend-regression-rerun.log.
- An offline npm restore exited 1 with ENOTCACHED for zod-validation-error 4.0.2. Registry-backed npm --prefix frontend ci, npm --prefix frontend run lint, and npm --prefix frontend run build with BASE_BACKEND_ORIGIN=http://127.0.0.1:18083 then exited 0. Next 16.4.6 compiled the baseline routes. No frontend editor paths were added.
- Repository base for the working-copy runs was 860fbea3a510c125bda94012faf9a73bbe100935. GitHub re-verified both source checkpoints and the design release. Agent assignments were executed on separate owned file sets in the integration checkout. The original feature worktrees and remotes were preserved.

### Checkpoint boundary

The approved v1.6 Git object matches the manifested SHA-256 9a7e1e0812c8c26b8828e627289f183a1a9103955bd408a9ab7a76b8a1bff602. The clean Windows checkout materializes CRLF, and its LF-normalized digest matches the same object. No architecture document, M1 production source, accepted M1 migration, or frontend file was edited. The M1 test-only source isolates a mock client from unrelated request load.

The Catalog candidate retains an explicit disabled default. Adoption of G03 thresholds still requires representative Lesson, top-level exact-count/search and overload evidence. The Reading candidate has no runtime database configuration. V17.2 safely establishes placement storage, while the Reading scope still needs guarded read/write routines, placement CAS and audit integration, real authorization and contention tests, and measured G03 limits. Both acceptance decisions remain BLOCKED. Production grants, publication, public discovery, Exercise/Evidence, and staff editor UI remain gated.

### Checkpoint file inventory

- `.gitattributes`
- `backend/src/main/java/com/baseielts/platform/content/CatalogHttpInput.java`
- `backend/src/main/java/com/baseielts/platform/content/CatalogRoutines.java`
- `backend/src/main/java/com/baseielts/platform/content/CatalogStaffApi.java`
- `backend/src/main/java/com/baseielts/platform/content/CatalogStaffSecurity.java`
- `backend/src/main/java/com/baseielts/platform/content/reading/ReadingDraftInput.java`
- `backend/src/main/java/com/baseielts/platform/content/reading/ReadingRoutines.java`
- `backend/src/main/java/com/baseielts/platform/content/reading/ReadingStaffApi.java`
- `backend/src/main/java/com/baseielts/platform/content/reading/ReadingStaffProjection.java`
- `backend/src/main/java/com/baseielts/platform/content/reading/ReadingStaffSecurity.java`
- `backend/src/main/resources/db/migration/V16__staff_catalog_routines.sql`
- `backend/src/main/resources/db/migration/V17__catalog_point_lookups.sql`
- `backend/src/main/resources/db/migration/V17_1__catalog_parent_lookups.sql`
- `backend/src/main/resources/db/migration/V17_2__reading_lesson_placement.sql`
- `backend/src/test/java/com/baseielts/platform/content/CatalogPostgresTest.java`
- `backend/src/test/java/com/baseielts/platform/content/reading/PassageAuthoringContractTest.java`
- `backend/src/test/java/com/baseielts/platform/content/reading/ReadingRouteBoundaryTest.java`
- `backend/src/test/java/com/baseielts/platform/content/StaffFixtureMigrationTest.java`
- `backend/src/test/java/com/baseielts/platform/identity/CatalogRouteBoundaryProofTest.java`
- `backend/src/test/java/com/baseielts/platform/identity/CatalogStaffApiPostgresTest.java`
- `backend/src/test/java/com/baseielts/platform/identity/VerificationPostgresTest.java`
- `backend/src/test/postgresql/catalog-staff.sql`
- `docs/m2/M2-DECISION-REGISTER.md`
- `docs/m2/M2-IMPLEMENTATION-PROGRESS.md`
- `docs/m2/M2-IMPLEMENTATION-ROADMAP.md`
- `docs/m2/M2-INTEGRATION-CHECKPOINT.md`
- `docs/m2/M2-OPEN-QUESTIONS.md`
- `docs/m2/M2.1-ACCEPTANCE-REPORT.md`
- `docs/m2/M2.1-MIGRATION-OWNERSHIP.md`
- `docs/m2/M2.2-ACCEPTANCE-REPORT.md`
- `docs/m2/M2.2-READING-MIGRATION-SPEC.md`
- `scripts/initialize-dev-db.ps1`
- `scripts/test-m2-catalog.ps1`
- `scripts/test-m2-staff-auth.ps1`

### Final storage and browser results

- pwsh -NoProfile -File scripts/test-m2-catalog.ps1 -IncludeReading -ComposeRoot D:/BASE-IELTS exited 0 with 8 passes, no failures, errors or skips on PostgreSQL 17.12. Catalog fresh and V15 upgrade used V17.2. Reading storage tested fresh V15 and V14 to V15. Fixture prefixes were base_ielts_m2test_fresh_, base_ielts_m2test_upgrade_, base_ielts_m2test_reading_fresh_, and base_ielts_m2test_reading_upgrade_, all with suffix 20261127053335084.
- The existing scripts/test-auth-browser.ps1 launcher ran from this checkout against PostgreSQL 17.12 fixture base_ielts_m1test_261127012 with Flyway target 12 and the admitted Compose environment. Its three invocations used -FixtureDatabase base_ielts_m1test_261127012, then the same with -Https -ActiveShutdown, then with -Https -ActiveShutdown -GracefulShutdown. Each invocation passed one Edge test with no failures, errors or skips. Elapsed test times were 3.9, 20.8 and 22.3 seconds respectively. The launcher seeded synthetic records and cleaned its own temporary triggers and certificates.
- Release classification is an incomplete, disabled-by-default integration checkpoint for independent review. Neither acceptance gate is closed by this checkpoint.

### Git disposition

Automatic approval review rejected staging the reviewed checkpoint file set because G03 and live Reading persistence are still open. The staging command did not execute. The working changes remain local on codex/m2-integration at base 860fbea3a510c125bda94012faf9a73bbe100935. The target branch did not exist on GitHub at verification time. No commit, push, or main merge occurred.

## 2026-11-27 · Agent B PostgreSQL handoff

Integration Owner approves the following byte-pinned migration snapshot for isolated PostgreSQL development and verification. The snapshot consists of Git commit `860fbea3a510c125bda94012faf9a73bbe100935` for the tracked baseline and the four exact raw files listed below from `backend/src/main/resources/db/migration` in this checkout. Copy the files without newline conversion, verify SHA-256 after copying, and stop on any mismatch. V16 must also match the installed Flyway checksum `957144442`. The snapshot approval is for fixture use. The branch has no committed integration SHA.

| File | Raw SHA-256 |
| --- | --- |
| V16__staff_catalog_routines.sql | 2e488fc22aba5df1af34fb73de0118d46e155d0fd4bd90a85f0f4b0a12810c81 |
| V17__catalog_point_lookups.sql | e76b48517190d1e8e76d5bf83e429ab62f16b279245cf7986c920c1511a8445c |
| V17_1__catalog_parent_lookups.sql | ca40cc120dfb8a157638e702c2bf5c10bc54b9e67742fd268c3e04398fd66945 |
| V17_2__reading_lesson_placement.sql | 72cae4c69f59638be913a3b85152a52ad0af0cfcd750160a6d1fce1a74b609e4 |

**Allocation.** Agent B owns Flyway V17.3 with filename `V17_3__reading_staff_authoring.sql` for scoped Reading staff routines and authorization, guarded placement and the typed same-transaction audit extension. The order is V16 → V17 → V17.1 → V17.2 → V17.3 → reserved V18. This supersedes the earlier unallocated draft/audit status. A further migration version requires a new allocation. Preserve the existing migration files.

Reuse tracked `StaffAuthentication` (Git blob `e74cf571afa0b79451ae4455e7db2ece52e15896`), including the AUTH cookie, scoped CSRF and account fence on the same PostgreSQL connection through DTO materialization or mutation and audit commit. Reuse the V17.2 placement relation and its FK to V13 `content_catalog.reading_lesson(id)`. Keep route enablement and real provisioning gated. Exercise/Evidence, publication and editor UI remain outside this allocation. Verify fresh and V17.2 upgrade with isolated principals, effective privileges, CAS, rollback, audit and G03 measurements. Fixture-only principal provisioning belongs in test setup.

### V17.3 shared audit decision

Use the existing content_catalog.content_audit_events relation for Reading events under the approved shared audit contract. V17.3 is the coordinated extension point. Leave applied V14 and Catalog-owned source files intact. Extend the enum labels, add nullable Reading targets and replace the target-shape check while preserving the existing Catalog alternatives. Compare discriminator and operation labels as text in migration-time checks. New enum values as enum literals, and commands inserting Reading events, may be exercised only after the migration transaction commits.

Passage events require the stable Passage FK and the exact composite Passage/edition FK. Placement events require the retained V17.2 placement row and a null edition. Use a dedicated nullable placement target if needed to keep the foreign keys conditional on the event shape. Every Reading alternative excludes Catalog target columns. Keep audit append-only and scoped to the guarded writer transaction. Grant test-only column-scoped INSERT to the approved non-login routine owner through fixture setup. No new table or role grants to runtime logins. Existing Catalog insert behavior, grants, historic rows, and query paths must survive fresh and V17.2 upgrade tests. Verify a single success record and atomic rollback for Reading writes.

### Agent B isolated HTTP transport snapshot

Approved for isolated PostgreSQL HTTP fixture composition only. Copy the five raw files from backend/src/main/java/com/baseielts/platform/content/reading in the integration checkout and verify SHA-256 after copying. Stop on a mismatch. Combine with the previously pinned migration snapshot and Agent B's V17.3 candidate. The snapshot is uncommitted. Enable the route only in the isolated fixture with test database beans and test principals. This approval does not enable any production route or grant.

| File | Raw SHA-256 |
| --- | --- |
| ReadingStaffApi.java | f6a195b7928a37301a4e1cc3f0e6ddbed95df376309f181f0b63c7718f28dea2 |
| ReadingRoutines.java | 1352e0f8b242b563aa974f8f15ba3d2f89b304af33f6d4e99415dc28ebae08a5 |
| ReadingStaffSecurity.java | 273bc91f3f2dd43c6da0d442bee685f4e478cd1053423340b430942bc99917bf |
| ReadingStaffProjection.java | 44b357760c670687c4eabad092bb82c536166768c63eb737e3e85f9a28cb7474 |
| ReadingDraftInput.java | 4a5b93cbf272df80460fd8681d85481a3ef949c37395f068c4df79ae4809addf |

## 2026-11-27 · PO-09 and PO-10 execution

The Product Owner approved normal HTTP p95 ≤ 100 ms and p99 ≤ 200 ms, and fail-fast Catalog HTTP 503 at pool saturation with bounded admission and no protected business SQL for rejected work. Keep saturation samples out of normal-workload percentiles. [Decision register](M2-DECISION-REGISTER.md#2026-11-27-performance-and-overload-approvals)

Agent A received the Catalog assignment in the existing agent A chat on feature/m2-content-foundation, including Lesson lookup/list, Course list/exact-count search, separate overload and recovery tests, and full Catalog PostgreSQL regression. Agent B received the V17.3 Reading assignment in the existing agent B chat on feature/m2.2-reading. Agent B must preserve the pinned migration and transport snapshot and report live scoped HTTP and database evidence independently of Catalog G03.

The GitHub branch heads observed at dispatch were Catalog 860fbea3a510c125bda94012faf9a73bbe100935, Reading 483b9d0d27a36d6e6d0c58fd239bd2dbfd6ab87b, and main 35bd9b95e2d99098f8ed9477a7ed79d4763ad4f3. The latter is also the feature branches' local merge base. The local integration branch remains based at the Catalog head. The exact integration ref was absent from GitHub.

### Current migration allocation

| Versions | Owner | Disposition |
| --- | --- | --- |
| V1–V12 | M1 | Preserve accepted identity history. |
| V13–V14 | Catalog | Preserve Catalog schema, authority and typed audit history. |
| V15 | Reading | Preserve Passage foundation. |
| V16 | Catalog | Preserve checksum-pinned staff routines. Flyway checksum 957144442. |
| V17–V17.1 | Integration Owner and Catalog | Preserve point and parent lookup corrections. |
| V17.2 | Integration Owner | Preserve private Passage-to-Catalog-Lesson placement. |
| V17.3 | Agent B | Reading staff authorization and routines, guarded placement, and shared typed audit extension. |
| V18 | M2.3 | Reserved. |
| V19 | Integration Owner | Reserved unless formally reassigned. |

Both acceptance decisions remain open. Earlier 36-test fresh/upgrade staff evidence covers the V17.2 integration snapshot. It cannot substitute for independently reviewed PO-09/PO-10 results or a combined run on final branch artifacts through V17.3. Integration waits for both completed branch diffs and their evidence.

### Independent baseline and draft review

All four pinned raw SHA-256 hashes were rechecked successfully. Read-only queries on both existing PostgreSQL 17.12 V16 fixtures returned version 16, checksum 957144442 and success true. The pinned StaffAuthentication and V13 Catalog Lesson schema match the tracked baseline.

The feature worktrees both have uncommitted candidates. Agent A's observed G03 test used MockMvc and is preliminary evidence for the new normal HTTP and overload gates. Agent B's observed V17.3 candidate uses the existing audit relation and placement FK. Its placement routine acquired a Passage write lock before checking Lesson existence, contrary to the approved P01 Catalog root → level → lesson → placement order. The observed list routine also lacked the archived-root exclusion in the Reading migration specification. Both observations were sent to Agent B for reconciliation and real PostgreSQL verification. These are draft observations pending a final source snapshot.

### Product Owner follow-up verification

Neither feature branch is authorized to merge while the agents are still preparing final commits. Catalog must supply authenticated loopback HTTP from the running application and actual bounded PostgreSQL pool. Use representative normal HTTP p95 ≤ 100 ms and p99 ≤ 200 ms, then separately prove prompt 503, zero protected SQL, bounded queueing and recovery after held connections are released.

A later Reading draft showed Catalog root → level → lesson → placement locking and archived-root exclusion from the list. This is preliminary source evidence. Agent B reported successful fixture CAS races and atomic audit. Independent verification requires the final commit, exact migration checksum and real PostgreSQL results. Review whether the observed old Lesson association can become stale before placement lock acquisition and require revalidation of the actual old chain. The approved contract preserves exact historical staff edition reads under F04, excludes archived roots from active lists, and forbids new drafts for archived roots. Evaluate draft read, preview, edit and placement against the approved contract without changing historical visibility implicitly.

On receipt of both eligible commits, review exact diffs and migration order, run isolated combined PostgreSQL through V17.3, run relevant M1 security and frontend/Edge regression, and update each acceptance report before any checkpoint release.

### Independent V17.3 candidate review

Agent B's unstaged V17.3 matched raw SHA-256 2d32eafaf4a7889c8d1add753e9f24224296295606be0b8dcf056667575c31e1 and independently computed Flyway checksum -574889560. Static review confirmed that the routine locks both Catalog chains in hierarchy order, locks placement next, and compares both prior Lesson and prior revision against the locked row. It uses unique-key CAS for absent placement and takes no explicit Passage authoring fence. The protected list filters archived roots, clone denies archived roots, and exact historical staff edition reads remain fenced and available.

Independently ran Agent B's pwsh -NoProfile -File scripts/test-m2-reading-v15.ps1 -MigrationCommit 860fbea3a510c125bda94012faf9a73bbe100935 -PlacementSnapshot <integration worktree> -Staff. PostgreSQL 17.12 project baseielts-reading-80f5c758b5c7 used the isolated base_ielts_m2test_reading_v17_3_fresh and base_ielts_m2test_reading_v17_3_upgrade databases. Exit 0, 11 passed with zero failures, errors or skips. Normal loopback p95/p99 (120 samples each, milliseconds): draft GET 31.134/31.420, draft PUT 24.227/25.511, placement PUT 17.525/18.105, list/count GET 42.121/46.962. Evidence is backend/m2-reading-independent.log. The four workloads meet PO-09 within the fixture envelope. Deployment graph and response bounds and other route measurements remain open.

Agent A separately reported 800 successful loopback Catalog requests with worst route p95/p99 44.033/63.453 ms across its 10,000-Course fixtures. Reader saturation returned sixteen 503s within 17.695 ms maximum with zero reported checkout, SQL or pool waiters and recovered after release. This is Agent A's isolated evidence through V17.2. It is not an independent combined run. Agent A also documented a 222.379 ms p99 at 40,005 Courses, outside that fixture envelope.

Neither feature candidate has a final commit. The pinned snapshot can support isolated checks, but does not replace a self-contained committed baseline. Exact final branch-diff review, combined PostgreSQL regression through V17.3, full applicable M1/frontend/Edge reruns and release remain pending. No merge or staging occurred.

## 2026-11-27 final integration phase

The integration worktree combines the reviewed Catalog admission candidate and Reading staff routines with the existing self-contained V17.2 work. Tracked source baselines remain Catalog 860fbea3a510c125bda94012faf9a73bbe100935 and Reading 483b9d0d27a36d6e6d0c58fd239bd2dbfd6ab87b. GitHub reported those exact heads and main 35bd9b95e2d99098f8ed9477a7ed79d4763ad4f3. The designated codex/m2-integration ref was absent. Candidate changes were imported by bytes from the uncommitted worktrees without merging either branch. The source worktrees were left intact.

| Imported file | Raw SHA-256 at import |
| --- | --- |
| CatalogStaffApi.java | b9dbbe88bd05af538a34e84eabf84fd90ce63b3f2150ae3c540c6dcd910d8f7e |
| CatalogConnectionAdmission.java | 933f2f9863ff08f01a4ddefcae001ef21a61fa5f14539692c1c361ce131ebf91 |
| CatalogG03PostgresTest.java | 9198b446d6ae4353b15ebaed61f46474a7c2ca1c756e310f77ad6b45318ff710 |
| CatalogConnectionAdmissionTest.java | 6c49d110cd0b0216021d654be6304563e62e20262b022e4a059a6e2febd6d78b |
| V17_3__reading_staff_authoring.sql | 2d32eafaf4a7889c8d1add753e9f24224296295606be0b8dcf056667575c31e1 |
| reading-staff.sql | 6213837b29d0f234ad3a32d54d70420d5a3a8e3a4df00a74a0ed09b88219ce85 |
| ReadingStaffPersistencePostgresTest.java | 3595d96ac22145326a92d122bdf98a9dd2347ee54098d83d20d7e2196c676240 |

The shared runner validates V16 checksum 957144442 and V17.3 checksum -574889560 and exercises fresh, V15 upgrade and V17.2 upgrade. Catalog and Reading grants are limited to test fixtures. The Reading tests run against databases initialized by the combined runner. Wire performance uses a separate deterministic 10,000-Course dataset per history to prevent functional fixture accumulation. The Catalog rollback trigger is fixture-only. Its original unconditional invocation reached Reading's audit rows and caused permission errors (first run exit 1, Reading stage eight executed, one failure, four errors). It now fires only on Course audit rows. The next run exited 1 after PostgreSQL observed a synthetic revoke timestamp older than its grant timestamp by about 95 ms. The fixture now uses the greater of the two timestamps. Production audit, constraints, role grants and clocks were preserved.

### Combined acceptance results

- PASS. `pwsh -NoProfile -File scripts/test-m2-staff-auth.ps1 -Combined` exited 0 with 57 passes, zero failures, errors or skips after the two fixture corrections. It made five fresh or upgrade migration assertions, two 21-case functional/security runs, two real HTTP Catalog cases, and eight Reading/cross-module cases. PostgreSQL image pins 17.12. Fixtures under container base-ielts-staff-test-f927e03852e344c1a3d3dcf8ee31c9c8 used staff_fresh_f927e03852e344c1a3d3dcf8ee31c9c8, staff_upgrade_f927e03852e344c1a3d3dcf8ee31c9c8, staff_placement_f927e03852e344c1a3d3dcf8ee31c9c8, staff_fresh_b52d0f2d87dc4e99b4f376532a025a05 and staff_upgrade_b64cd4b6567c4abca14ae8ef91fd0f33. See backend/m2-combined-third.log.
- PASS. Fresh installed twenty migrations. V15→V17.3 applied five and V17.2→V17.3 applied one. Flyway validation and zero-migration replay passed. V16 and V17.3 checksums were asserted. V18/V19 remain reserved. SHA-256 of the v1.6 Git blob still matches 9a7e1e0812c8c26b8828e627289f183a1a9103955bd408a9ab7a76b8a1bff602. No M1 production source, V1–V12 or frontend source was edited.
- PASS. The integrated cross-module test observed Reading placement waiting on real PostgreSQL Catalog locks. Catalog status and audit rolled back, and Reading placement committed with its own audit. Separate Reading and Catalog cases covered same-transaction account fencing, role authorization, rollback, CAS and privacy.
- PASS within fixture envelopes. Catalog 800 normal HTTP 200 responses, worst p95/p99 64.143/88.277 ms. Separate overload 16 HTTP 503s, max 29.228 ms, zero checkout or protected SQL, zero pool waiters, two successful recovery probes. Reading four 120-request workloads, worst p95/p99 54.599/56.433 ms. Each module report preserves route figures and limits.
- FAIL in earlier diagnostic. Catalog p99 222.379 ms at 40,005 Courses. This dataset is outside the 10,000-Course fixture evidence above. NOT TESTED in this combined run at the larger size.
- NOT TESTED for G03 release. Agreed representative Catalog cardinality, Reading aggregate graph/translation cardinality limits, response envelope and performance for create/clone/preview. Keep both milestone acceptance decisions open until the remaining G03 limits are resolved and verified. No production activation.

### Storage and M1 regression

- PASS. `pwsh -NoProfile -File scripts/test-m2-catalog.ps1 -IncludeReading -ComposeRoot D:/BASE-IELTS` exited 0, 8 passed, 0 failures/errors/skips. PostgreSQL 17.12 database names used suffix 20261127093748844 under the Catalog fresh/upgrade and Reading fresh/upgrade prefixes. The V15 Catalog fixture retained a typed audit row across V17.3, and could still insert Catalog audit events. The Reading foundation storage checks exercised V15 fresh and V14 upgrade separately. See backend/m2-final-storage.log.
- PASS. The existing full M1 recovery launcher invoked `backend/mvnw.cmd -B -f backend/pom.xml clean verify` on PostgreSQL 17.12 fixture base_ielts_m1test_261126220031. The invocation used integration source, Flyway target 12, Compose project base-ielts and the existing fixture environment at D:/BASE-IELTS/.env. Exit 0, 59 reported, 35 passed, 0 failures/errors, 24 opt-in skips. The independent audit witness found four committed recoveries, distinct approvals and correct attribution. The V17.3 runner separately covered its opt-in Catalog/Reading cases. See backend/m2-final-m1.log.

### Integrated source, test and runner hash inventory

Raw SHA-256 at final validation, including integration-owned test and runner adaptations.

| Path | Raw SHA-256 |
| --- | --- |
| `.gitattributes` | 61171cb1510ada0aa2994017ad1bc1da553bf16507c780e07163116fcf42bb39 |
| `backend/src/main/java/com/baseielts/platform/content/CatalogConnectionAdmission.java` | 933f2f9863ff08f01a4ddefcae001ef21a61fa5f14539692c1c361ce131ebf91 |
| `backend/src/main/java/com/baseielts/platform/content/CatalogHttpInput.java` | b9519c66dde732fbe1a1f6d68d2d992b31d2e28abbac7233b7b1f78c91080a4c |
| `backend/src/main/java/com/baseielts/platform/content/CatalogRoutines.java` | 29bc3593a2045b75fa78b6a6f7713a3c54cd8df6468ba0d18c62d3c5c2f13edd |
| `backend/src/main/java/com/baseielts/platform/content/CatalogStaffApi.java` | b9dbbe88bd05af538a34e84eabf84fd90ce63b3f2150ae3c540c6dcd910d8f7e |
| `backend/src/main/java/com/baseielts/platform/content/CatalogStaffSecurity.java` | 080ad97c36d624c3a41a06bbac79cd85b9de10b3fd1cd5ea337c9d34b5534ce5 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingDraftInput.java` | 4a5b93cbf272df80460fd8681d85481a3ef949c37395f068c4df79ae4809addf |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingRoutines.java` | 1352e0f8b242b563aa974f8f15ba3d2f89b304af33f6d4e99415dc28ebae08a5 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingStaffApi.java` | f6a195b7928a37301a4e1cc3f0e6ddbed95df376309f181f0b63c7718f28dea2 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingStaffProjection.java` | 44b357760c670687c4eabad092bb82c536166768c63eb737e3e85f9a28cb7474 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingStaffSecurity.java` | 273bc91f3f2dd43c6da0d442bee685f4e478cd1053423340b430942bc99917bf |
| `backend/src/main/resources/db/migration/V16__staff_catalog_routines.sql` | 2e488fc22aba5df1af34fb73de0118d46e155d0fd4bd90a85f0f4b0a12810c81 |
| `backend/src/main/resources/db/migration/V17__catalog_point_lookups.sql` | e76b48517190d1e8e76d5bf83e429ab62f16b279245cf7986c920c1511a8445c |
| `backend/src/main/resources/db/migration/V17_1__catalog_parent_lookups.sql` | ca40cc120dfb8a157638e702c2bf5c10bc54b9e67742fd268c3e04398fd66945 |
| `backend/src/main/resources/db/migration/V17_2__reading_lesson_placement.sql` | 72cae4c69f59638be913a3b85152a52ad0af0cfcd750160a6d1fce1a74b609e4 |
| `backend/src/main/resources/db/migration/V17_3__reading_staff_authoring.sql` | 2d32eafaf4a7889c8d1add753e9f24224296295606be0b8dcf056667575c31e1 |
| `backend/src/test/java/com/baseielts/platform/content/CatalogConnectionAdmissionTest.java` | 6c49d110cd0b0216021d654be6304563e62e20262b022e4a059a6e2febd6d78b |
| `backend/src/test/java/com/baseielts/platform/content/CatalogPostgresTest.java` | d01091de918d3f0240b3de65d9240df805d0b6a21ff6d4e80a7b5f43b6db2ac3 |
| `backend/src/test/java/com/baseielts/platform/content/reading/PassageAuthoringContractTest.java` | 04f24029c18929b4a4974b83fc1455046fa996b3d0c3635b835690db31f1055f |
| `backend/src/test/java/com/baseielts/platform/content/reading/ReadingRouteBoundaryTest.java` | 505f827d4504e281219a3c4659b82092e8bdc7cd606f906a95dd1a9b478c1632 |
| `backend/src/test/java/com/baseielts/platform/content/StaffFixtureMigrationTest.java` | d4eb0c6885ab3ccac6b155eb11f40699717f1abac0eecb7351328165c7634765 |
| `backend/src/test/java/com/baseielts/platform/identity/CatalogG03PostgresTest.java` | 9198b446d6ae4353b15ebaed61f46474a7c2ca1c756e310f77ad6b45318ff710 |
| `backend/src/test/java/com/baseielts/platform/identity/CatalogRouteBoundaryProofTest.java` | e4fae6af9ce18ca4b0f13853f2e14e7fe39bcc381add3ed234fd11639a53c56c |
| `backend/src/test/java/com/baseielts/platform/identity/CatalogStaffApiPostgresTest.java` | c0cb1897141b535d6da9064a4e2d2af618fe01209fa332cbd48916a19e0bf831 |
| `backend/src/test/java/com/baseielts/platform/identity/ReadingStaffPersistencePostgresTest.java` | 109b0af9a0afc60d0299fddccc83f140e125c6f93fcda890138c7321e45de68c |
| `backend/src/test/java/com/baseielts/platform/identity/VerificationPostgresTest.java` | 11e753c952897e71704ab14ad6ea280cb928ddf72f0aa830961debd7f23bce2a |
| `backend/src/test/postgresql/catalog-staff.sql` | b1c9f7b1999748194c21062a55cfa4f5348fd9b8fbb35b8f1d9d06053c5e672e |
| `backend/src/test/postgresql/reading-staff.sql` | 6213837b29d0f234ad3a32d54d70420d5a3a8e3a4df00a74a0ed09b88219ce85 |
| `scripts/initialize-dev-db.ps1` | 5f694c9dca9e8f71693fa0e210098053f3783982ba37514bdc9e33b5c0e6d09a |
| `scripts/test-m2-catalog.ps1` | c27ef32770a3d76dbe98ce003459b474ec29739624ec445caafb059a4245ec31 |
| `scripts/test-m2-staff-auth.ps1` | f20ede342b751d20de21474b203b0a6f193d6513438916f29aa4b9f933716edb |

### Frontend, Edge and release disposition

- PASS. npm --prefix frontend run lint and npm --prefix frontend run build exited 0 against unchanged frontend source. The build used BASE_BACKEND_ORIGIN=http://127.0.0.1:18083. See backend/m2-final-frontend.log.
- PASS. The existing scripts/test-auth-browser.ps1 launcher used PostgreSQL 17.12 fixture base_ielts_m1test_261127012, Flyway target 12 and the local Compose fixture settings. The standard, HTTPS forced-shutdown and HTTPS graceful-shutdown runs each passed one Edge case, with zero failures or skips. Times were 6.7, 25.1 and 27.9 seconds. No M2 staff editor routes are in scope for browser acceptance.
- BLOCKED for release. Catalog and Reading passed the integrated functional suite and the measured PO-09/PO-10 cases. The G03 workload and Reading cardinality checks listed above remain open. The staged index is empty. No commit, push, main merge or deployment. GitHub has no codex/m2-integration ref to verify as a release SHA.

## PO-11 and PO-12 release gate

The new baseline and working-copy hashes are in [the manifest](M2-INTEGRATION-BASELINE-MANIFEST.md). Raw V16–V17.3 bytes matched all earlier pins. Local CRC calculation returned V16 957144442 and V17.3 -574889560. The raw V17.3 SHA-256 remains 2d32eafaf4a7889c8d1add753e9f24224296295606be0b8dcf056667575c31e1. V18 and V19 remain reserved.

- PASS. Maven Wrapper clean verify with the locally resolved Mockito 5.17.0 javaagent exited 0. 60 reported, 24 passed, 0 failures/errors, 36 opt-in skips. The isolated Agent B domain command selecting ReadingDraftContractTest, ReadingEditionLifecycleTest and ReadingStaffProjectionTest from source 483b9d0d27a36d6e6d0c58fd239bd2dbfd6ab87b exited 0 with 17 passes, zero failures/errors/skips.
- PASS. npm --prefix frontend run lint and npm --prefix frontend run build exited 0, including TypeScript. See backend/m2-po11-frontend.log.
- BLOCKED. The exact integrated PostgreSQL command was pwsh -NoProfile -File scripts/test-m2-staff-auth.ps1 -Combined. Docker run did not return or reach fixture identity output. Docker version failed to respond within 12 seconds. The two explicit Windows engine endpoints independently timed out at eight seconds. A read-only JDBC version query confirmed that the existing local database was still answering as PostgreSQL 17.12. This is distinct from the unresponsive Docker control API. Only two CLI processes created by the failed probes were stopped. Docker Desktop and database services were left running.
- NOT TESTED on this source. PO-11 wire/overload, combined PostgreSQL, new Reading byte-limit case, full opt-in M1 PostgreSQL regression and Edge. Historical V17.3 results remain separately recorded and do not count for these reruns.
- BLOCKED. Reading cardinality is disposed as an open release gate. The existing byte budgets bound requests and returned projections. Explicit graph and translation counts and cap-plus-one verification still need agreement and evidence. No unresolved Critical or High issue was identified in the reviewed Passage control paths. Exercise persistence and Evidence remain M2.3 under PO-07.
- BLOCKED. PO-12 authorizes a commit/push only after the gates pass. GitHub reported the same source feature SHAs and main, with no codex/m2-integration ref. The staging index is empty and the candidate is local. G01 and G02 stay open, public discovery remains M3, and no production action occurred.

The interrupted Docker launch ultimately exited 1 with "Unable to start the isolated PostgreSQL fixture." No fixture identity or Maven test result was emitted. Restoring Docker engine control or supplying a separate isolated PostgreSQL 17.12 fixture is necessary for the rerun.

## Dedicated WSL PostgreSQL fixture and Reading limits

The original local PostgreSQL server answered a read-only JDBC version probe with 17.12 (Debian 17.12-1.pgdg12+1). It received no fixture database, credential, migration or row changes. Docker Desktop did not restart and no Docker volume was modified or deleted.

Downloaded the official PostgreSQL 17.12 source archive and checked SHA-256 9cf136a703d8ee2f83465c43be4e9e8e7930c82b0bfba13be7e2537be769ac55. Built under /tmp/base-ielts-pg1712-rFzStgkc with configure --without-icu --without-readline --without-zlib and make -j8. The initial configure lacked Bison and Flex. Extracted bison, flex and m4 packages in the build directory and set BISON_PKGDATADIR and M4 explicitly. No system package was installed. The built version reported postgres (PostgreSQL) 17.12. [PostgreSQL source archive](https://www.postgresql.org/ftp/source/v17.12/)

The first private-cluster run reached fixture creation but Windows JDBC refused the unforwarded loopback port. A fixed-port Windows relay then received EACCES. The final run used a separate data directory at /tmp/base-ielts-pg1712-S9MD8CXq/data, a private WSL socket, SCRAM on the fixture TCP interface, and an OS-assigned Windows loopback relay port. The WSL listener used port 55481 and accepted TCP from the private WSL NAT gateway. Exact launch command: pwsh -NoProfile -File scripts/test-m2-acceptance-on-wsl.ps1 -FixtureRoot /tmp/base-ielts-pg1712-S9MD8CXq -PrivateWslAddress 172.21.88.4. The launcher verified server_version_num 170012 and data-directory identity before using synthetic credentials. The first cluster and the final cluster are separate from the existing development database and Docker volumes.

The initial Reading HTTP authoring envelope is 32 paragraphs per draft and two translations per paragraph. Agent B was notified before edits. Application validation rejects either count over its limit before graph mutation. The existing normalized 512 KiB database input and 768 KiB response bounds remain. V17.3 is unchanged. The combined wire test verifies both limits at the boundary and checks that the rejected requests leave the graph, revision and audit unchanged. A separate real PostgreSQL case exercises the database normalized byte limit. Imported frozen clone sources require an operational inventory before activation. Historical editions and publication rules remain unchanged.

### Isolated WSL combined results

PASS. The combined launcher exited 0, with 62 passed, zero failures, errors or skips. Seven fixtures in the dedicated PostgreSQL 17.12 instance covered fresh, approved V15 and V17.2 upgrades and independent wire workloads. Their database names were staff_fresh_e9552deba6304451b13b34d7bd5608f4, staff_upgrade_e9552deba6304451b13b34d7bd5608f4, staff_placement_e9552deba6304451b13b34d7bd5608f4, staff_fresh_342191b49d594b58939ba5ec96f98416, staff_upgrade_3b30b292fedc4a40825c61a82c68edbf, staff_fresh_9d5fc250a5fa4dd3b1d4575ad22f215a and staff_upgrade_a5097ab15d044fc2baa30a9e8e258b2d. Windows loopback was 64628. Selections were seven migration checks, two 21-test Catalog/security suites, four real Catalog wire tests and nine Reading/cross-module tests. See backend/m2-wsl-combined-final.log.

PASS. The combined runner verified Flyway zero-migration reruns, V16 checksum 957144442 and V17.3 checksum -574889560. Raw V17.3 remained 2d32eafaf4a7889c8d1add753e9f24224296295606be0b8dcf056667575c31e1. The original 40,005-Course diagnostic and previous fixture failures remain separate historical evidence.

### Isolated M1, frontend and Edge results

PASS. The existing PostgreSQL opt-in M1 launcher ran `backend/mvnw.cmd -B -f backend/pom.xml clean verify` against V12 database base_ielts_m1test_2026112711262302401. Exit 0, 60 reported, 35 passed, zero failures or errors, and 25 opt-in skips. The suite verified the AUTH/session and rollback behavior and an independent audit oracle confirmed four committed recoveries with distinct approvals and correct attribution. The combined staff run executed its separately opted-in cases. See backend/m2-wsl-m1.log.

PASS. On the same tested source, npm --prefix frontend run lint and production build both exited 0, including TypeScript. Three Edge invocations with database base_ielts_m1test_2026112711262302402 passed one test each (standard 4.4 s, HTTPS forced 23.7 s, HTTPS graceful 27.5 s), with zero failures or skips. The launcher exited 0 and stopped only its private relay and PostgreSQL process. Fixture data is retained. No Docker process, database or volume was restarted, reset or removed.

PO-11 replaces the earlier provisional Catalog envelope. HTTP admission at the approved limits passes, and the Reading cardinality check passes in the initial fixture envelope. Remaining production deployment decisions are separate from PO-12 feature release. Historical failures, not-tested deferred features and previous measurement boundaries remain documented.
