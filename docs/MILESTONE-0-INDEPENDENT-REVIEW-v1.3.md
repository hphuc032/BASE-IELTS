# IELTS Milestone 0 v1.3 · Independent Architecture & Security Review

**Final Recommendation: NOT READY FOR PRODUCT OWNER SIGN-OFF**

**Review Date:** 2026-11-26  
**Repository:** `D:/BASE-IELTS` (branch `main`, tracked `HEAD bb205ad`)  
**Reviewer Role:** Independent Senior Software Architect, Application Security Architect, Database Security Reviewer, and Technical Auditor  
**Review Mode:** Read-only design and documentation audit (no application code, SQL migrations, or runtime tests exist or were executed)

---

## 1. Executive Summary

An independent, adversarial audit of the Milestone 0 v1.3 documentation package (`docs/MILESTONE-0-FINAL-v1.3.md` and its companion authority, decision, ADR, traceability, and review-response documents) confirms substantial progress over v1.2 in authentication snapshot integrity (`§3.4`), Spring Security/CSRF namespace isolation (`§§3.1–3.4`), governance payload specificity (`§4.1`), database decision-clock semantics (`§9.4`), Unicode/normalization determinism (`§7.4`), and 43-FK `RESTRICT` retention (`§§5.1, 5.5`).

However, the v1.3 architecture **is NOT READY FOR PRODUCT OWNER SIGN-OFF**. The database privilege and callable-routine boundary (`§5.4`) leaves a critical operational contradiction with the global lock ordering (`§5.3`) and introduces an unguarded privilege-escalation path in `identity_lifecycle`. In addition, the expiry-worker scan specification (`§9.5`) can starve newly expired healthy Exam attempts behind a backlog of >100 failing retry attempts, `deadline_capacity` lacks an absent-row locking protocol under `READ COMMITTED`, and the in-memory `PREAUTH` session store lacks a global capacity cap. All three Product Owner decision bundles (`V12-D01`–`V12-D03`, encompassing `TECH-01`–`TECH-12`) remain explicitly open.

### Overall Risk Assessment

- **Identity & Database Security Risk (High):** Cannot currently satisfy both the `§5.3` account-before-local-guard lock ordering (`(2) -> (3)`) and the `§5.4` no-direct-DML / strict routine-allowlist rules (`I13-04`) simultaneously; `identity_lifecycle.mutate` lacks explicit guards against public-account `ADMIN` promotion and `PENDING` credential writes.
- **Session & Availability Risk (Medium):** `AUTH`/`PREAUTH` namespace isolation and single-process fencing are well-specified, but an unbounded in-memory `PREAUTH` store exposes the sole serving process to heap exhaustion and full authenticated session loss.
- **Assessment & Expiry Worker Risk (Medium):** Decision clock (`§9.4`) and grading determinism (`§7.4`) are sound, but `§9.5` has a >100-retry scan-starvation edge case and an unspecified absent-row lock mechanism on `deadline_capacity`, while `§6.1` misorders `POST /attempts` idempotency replay relative to `404`/`429`.

### Summary Counts

| Category | Count | Details |
| --- | --- | --- |
| **Historical v1.2 Findings (12 total)** | **9 Resolved, 3 Partially Resolved, 0 Not Resolved, 0 Regressed, 0 Unverifiable** | Resolved: `V12-H01`, `V12-H02`, `V12-M01`, `V12-M02`, `V12-M03`, `V12-M04`, `V12-M05`, `V12-M06`, `V12-H05`. Partially Resolved: `V12-H03`, `V12-H04`, `V12-H06`. |
| **New v1.3 Findings (7 total)** | **0 Critical, 2 High, 4 Medium, 1 Low** | High: `V13-N01`, `V13-N02`. Medium: `V13-N03`, `V13-N04`, `V13-N05`, `V13-N06`. Low: `V13-N07`. |
| **Open Product Owner Decisions** | **3 Open Decision Bundles (`TECH-01`–`TECH-12`)** | `V12-D01`, `V12-D02`, `V12-D03` all remain `OPEN OWNER DECISION`. |

---

## 2. Review Scope and Evidence

### Documents Reviewed

Current v1.3 package (all untracked in working tree outside `HEAD bb205ad`):

- [MILESTONE-0-FINAL-v1.3.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-FINAL-v1.3.md) (SHA-256: `A321708932D0A96D41439A911F0D1ADEBE453DB0E93E2E4A35A2DB02B1E3776D`)
- [DECISION-REGISTER.md](file:///d:/BASE-IELTS/docs/DECISION-REGISTER.md) (SHA-256: `B64EF97CC1642A4BD2384EF3E17F6C6E60ADBB6262A10657C54F0D57F4F73FBB`)
- [ADR-REGISTER.md](file:///d:/BASE-IELTS/docs/ADR-REGISTER.md) (SHA-256: `5530F37AF1D399448F881FCBDF6F02391905D0929E98F20F43A1375788157660`)
- [DOCUMENT-AUTHORITY.md](file:///d:/BASE-IELTS/docs/DOCUMENT-AUTHORITY.md) (SHA-256: `816E77DEA4F0DD9BF7277DEAF6F363C574006A140CA4C76AA69704358D29335F`)
- [MILESTONE-0-TRACEABILITY.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-TRACEABILITY.md) (SHA-256: `DC3600EB834E501E510608399F375A60033106A8634A61570A7032D6242238A2`)
- [MILESTONE-0-REVIEW-RESPONSE.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-REVIEW-RESPONSE.md) (SHA-256: `17B5027A5686320328D529FE1D37F349616B008F4EE15E36057AEFA0807DADAA`)

All five companion SHA-256 digests published in [MILESTONE-0-REVIEW-RESPONSE.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-REVIEW-RESPONSE.md) were independently recomputed over raw file bytes and verified as exact matches.

Historical baseline and immutable evidence artifacts inspected:

- [IELTS_Learning_Platform_PRD_Agent_Guide.md](file:///d:/BASE-IELTS/docs/IELTS_Learning_Platform_PRD_Agent_Guide.md) (tracked in `HEAD`, SHA-256: `4D31788EC49793004F31A146C169E663025EB034549D17BB1CCEEDA73E5E9EB6`)
- [IELTS_MILESTONE_0_FINAL.md](file:///d:/BASE-IELTS/docs/IELTS_MILESTONE_0_FINAL.md) (v1.0 consolidation, SHA-256: `8C719D63F5D6CBD46499DD0B8EC89EADDE75A365240AD5093B57F3E425FA2B0A`)
- [IELTS_MILESTONE_0_REMEDIATION_PLAN.md](file:///d:/BASE-IELTS/docs/IELTS_MILESTONE_0_REMEDIATION_PLAN.md) (SHA-256: `D90E196DB08136891D29DC417AC3CD38B8A41101975E4009F17BB907A02C9241`)
- [MILESTONE-0-v1.1-INDEPENDENT-REVIEW.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-v1.1-INDEPENDENT-REVIEW.md) (SHA-256: `A9DF8CDC4CE7533E011554B05469FA5D61CEB9619196259DEBB8F319A4CF230D`)
- [MILESTONE-0-FINAL-v1.2.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-FINAL-v1.2.md) (SHA-256: `5A31F8BCAA0900B51B7CBFCEE628A2295774F9A209E27A54B7C3557A1C26EA5B`)
- [MILESTONE-0-v1.2-INDEPENDENT-REVIEW.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-v1.2-INDEPENDENT-REVIEW.md) (SHA-256: `015AA33B8E561D2FBC7729386C9275B79D5F10081A70831D4C9E122EEF588B01`)

### Authority Hierarchy

Per [DOCUMENT-AUTHORITY.md](file:///d:/BASE-IELTS/docs/DOCUMENT-AUTHORITY.md):
1. **Approved Product Decisions:** `D01`–`D11` in [DECISION-REGISTER.md](file:///d:/BASE-IELTS/docs/DECISION-REGISTER.md) (preserving `v1.0 §2` verbatim) and `PRD D-01`–`D-08` (except where explicitly superseded by `D01`–`D11`).
2. **Current Technical Proposal (Unapproved):** [MILESTONE-0-FINAL-v1.3.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-FINAL-v1.3.md) is the sole current technical proposal; [ADR-REGISTER.md](file:///d:/BASE-IELTS/docs/ADR-REGISTER.md) defines `M0-ADR-01`–`15`; [MILESTONE-0-TRACEABILITY.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-TRACEABILITY.md) defines proposed acceptance specs.
3. **Open Technical Decisions:** `TECH-01`–`TECH-12` (mapped to `V12-D01`–`V12-D03`) remain open and unapproved.
4. **Superseded Drafts & Immutable Historical Evidence:** `v0.1`, `v0.2`, `v1.0`, `v1.1`, `v1.2` packs are superseded as technical proposals; prior independent reviews remain immutable evidence.

### Review Limitations

- The repository contains documentation only (`docs/*.md`); no Java/Spring Boot source, Next.js code, SQL DDL/Flyway migrations, or test harnesses exist.
- All runtime, concurrency, PostgreSQL privilege, Spring filter-chain, and benchmark assessments are design-level verifications against documented contracts and PostgreSQL/Spring semantics. No code or SQL was executed.

---

## 3. Historical Finding Verification

### Summary Matrix (`V12-H01`–`V12-H06`, `V12-M01`–`V12-M06`)

| Finding ID | Author Disposition | Independent Disposition | Primary v1.3 Sections | Remaining Gap / Dependency |
| --- | --- | --- | --- | --- |
| `V12-H01` | REMEDIATED IN DESIGN | **RESOLVED** | `§§2.1, 3.2, 3.4, 4.1`; `I13-01` | Pending `V13-N01` (`§5.4` lock routine) and `TECH-02/05/12` |
| `V12-H02` | REMEDIATED IN DESIGN | **RESOLVED** | `§§3.1–3.4, 6.1`; `I13-02` | Pending `V12-D01` (`TECH-01/02/12`); see `V13-N03` for `PREAUTH` capacity |
| `V12-H03` | REMEDIATED IN DESIGN | **PARTIALLY RESOLVED** | `§§4, 4.1, 5.4–5.5`; `I13-03` | `identity_lifecycle` lacks public-account `ADMIN` guard (`V13-N02`); missing `cancelApproval` & `audit_append` actor gap (`V13-N04`) |
| `V12-H04` | REMEDIATED IN DESIGN | **PARTIALLY RESOLVED** | `§§4.1, 5.3–5.4`; `I13-04`, `P13-01` | Missing `§5.4` account-lock/login-check routine (`V13-N01`), missing `identity_lifecycle` guards (`V13-N02`), missing cancel/cleanup routines (`V13-N04`) |
| `V12-H05` | REMEDIATED IN DESIGN | **RESOLVED** | `§§5.6, 9.1–9.2, 9.4`; `G13-01` | Pending M4 (`TECH-09`) |
| `V12-H06` | REMEDIATED IN DESIGN | **PARTIALLY RESOLVED** | `§§5.3, 7.3–7.4, 9.5`; `G13-02` | >100-retry scan starvation (`V13-N05`); absent `deadline_capacity` row lock race (`V13-N06`); pending benchmark (`TECH-09`) |
| `V12-M01` | REMEDIATED IN DESIGN | **RESOLVED** | `§§2.2–2.3, 6.1`; `I13-05` | Pending `TECH-03/04/12` & production timing run |
| `V12-M02` | REMEDIATED IN DESIGN | **RESOLVED** | `§§5.3, 5.6, 8, 9.1`; `P13-02` | Pending `TECH-08/09` |
| `V12-M03` | REMEDIATED IN DESIGN | **RESOLVED** | `§§3.4, 6.1, 8, 9.2–9.3`; `I13-06`, `R13-01` | See `V13-N07` for `POST /attempts` replay placement in `§6.1` |
| `V12-M04` | REMEDIATED IN DESIGN | **RESOLVED** | `§§7.1–7.4, 9.2–9.3`; `G13-03` | Pending M4 (`TECH-09`) |
| `V12-M05` | REMEDIATED IN DESIGN | **RESOLVED** | `§§4.1, 5.1, 5.5–5.6, 9.1`; `I13-07`, `P13-03` | Token cleanup routine missing in `§5.4` (tracked under `V13-N04` / `TECH-12`) |
| `V12-M06` | REMEDIATED IN DESIGN | **RESOLVED** | `§§5.3, 8, 11`; `D13-01` | Additional tests needed for `V13-N01`–`V13-N07` (see §9) |

---

### Detailed Per-Finding Verification

#### `V12-H01` — Login Credential Snapshot vs. Concurrent Mutations
1. **Original root cause:** Login verified a password hash outside the transaction while recovery/mutation updated it; "password version" was undefined, and hash and `security_revision` were not required to come from one atomic read snapshot.
2. **Proposed remediation:** Single-statement primary read `(user_id, password_hash, status, security_revision)`, dummy hash work on missing/ineligible accounts, lock `users` row + local stripe, compare exact captured tuple `(id, ACTIVE, revision, hash)` against locked row, increment `security_revision` atomically on every credential/eligibility/status/role mutation, reject overflow, remove "password version".
3. **Exact implementing sections:** `§§2.1, 3.2, 3.4, 4.1`.
4. **Root cause vs. mitigation:** Addresses the root cause directly at the snapshot/recheck contract level.
5. **Enforceability:** The tuple-comparison invariant is enforceable, *contingent* on resolving `V13-N01` (how `identity_api` acquires the `users` row lock under `§5.4`'s no-direct-DML boundary).
6. **Remaining ambiguity/contradiction:** `§2.1` does not explicitly state that `consume` increments `security_revision` upon `PENDING -> ACTIVE` transition, though `§3.4` covers it ("Any credential, verification eligibility, status or role mutation increments the single durable security_revision"). Because `PENDING` has null `password_hash` and is ineligible for login, no prior login snapshot can pass as `ACTIVE`.
7. **Concrete failure scenario:** Login reads `(U, H1, ACTIVE, 5)`; concurrently `recover` resets hash to `H2` and sets `security_revision = 6`. Login acquires row lock, compares `(U, ACTIVE, 5, H1)` against `(U, ACTIVE, 6, H2)` -> fails closed (`401 LOGIN_FAILED`, no binding/cookie).
8. **Acceptance test adequacy:** `I13-01` pauses login after snapshot, races reset, role change, and disablement, plus rollback check; detects the failure.
9. **Dependencies & approval:** M1; `TECH-02/05/12` open (`V12-D01`, `V12-D02`).
10. **Independent disposition:** **RESOLVED** (execution depends on `V13-N01` lock-routine specification under `V12-H04`).

#### `V12-H02` — Spring Security Session, Cookie, and CSRF Integration
1. **Original root cause:** Ambiguous Spring `HttpSession` vs. dual-cookie `PREAUTH`/`AUTH` selection, unguarded context persistence/promotion, parallel anonymous bootstrap pairing, undefined 401/403 precedence, and missing dispatch revalidation.
2. **Proposed remediation:** Dedicated server-side `SecurityContextRepository` and scoped CSRF repository; explicit cookie suppression (`HttpSession`, `JSESSIONID`, default CSRF/logout emitters disabled); single explicit cookie writer; exact scope routing table (`PREAUTH` vs. `AUTH`); provisional context withheld until commit; REQUEST/FORWARD/ERROR/ASYNC dispatch revalidation and thread-local clear; client bootstrap serialization + 403 discard/deliberate resubmit.
3. **Exact implementing sections:** `§§3.1–3.4, 6.1`; `M0-ADR-03/08`.
4. **Root cause vs. mitigation:** Addresses the root cause by specifying filter ordering, namespace separation, and provisional context lifecycle.
5. **Enforceability:** Enforceable once Spring Security and dependency versions are pinned (`TECH-01`). (Note: global memory bounding of `PREAUTH` sessions is separately raised as `V13-N03`).
6. **Remaining ambiguity/contradiction:** `§3.1` excludes "polling" from idle-clock touch without identifying any polling endpoint in `§6`/`§9` (noted in §5 Cross-Section Consistency).
7. **Concrete failure scenario:** Two parallel `GET /auth/csrf?scope=PREAUTH` calls set `PREAUTH=P2` last in cookie jar while client holds `T1` -> `POST /auth/register` returns `403 CSRF_INVALID`, client discards `T1`, fetches `T2` for `P2`, user deliberately resubmits. Stale `AUTH` + valid `PREAUTH` on private write returns `401` before CSRF.
8. **Acceptance test adequacy:** `I13-02` exercises reordered bootstraps/logins/logout, JSESSIONID absence, `PREAUTH`/`AUTH` cross-namespace rejection, provisional persistence failure, and FORWARD/ERROR/ASYNC dispatch rejection.
9. **Dependencies & approval:** M1; `TECH-01/02/12` open (`V12-D01`).
10. **Independent disposition:** **RESOLVED**.

#### `V12-H03` — Admin Governance, Recovery Approvals, and Principal Authentication
1. **Original root cause:** Recovery approval omitted revision/replacement payload constraints, approval-state columns, relational audit links, and relied on caller-supplied actor strings.
2. **Proposed remediation:** `admin_governance` (singleton + `bootstrap_event_id` FK), `recovery_approvals` (immutable payload: `target_user_id`, `expected_security_revision`, `action`, `replacement_email_canonical`, distinct `approver_principal_id`/`designated_operator_principal_id`, `operator_scope`, `evidence_ref`, `consumed_at`/`cancelled_at`), `admin_audit` (`recovery_approval_id` FK), and DB-login-derived principal identity for `operator_approver`/`deployment_operator`.
3. **Exact implementing sections:** `§§4, 4.1, 5.4, 5.5`.
4. **Root cause vs. mitigation:** Directly fixes `admin_governance`, `recovery_approvals`, and `admin_audit` data model and `bootstrap`/`recover` flows, *but* leaves a side-door and lifecycle gap in `§5.4`.
5. **Enforceability:** Partially enforceable:
   - `bootstrap` and `recover` derive actor identity from authenticated DB roles and lock `admin_governance(1)` first.
   - However, `identity_lifecycle.mutate` (`§5.4`) omits any restriction preventing `GRANT_ROLE(ADMIN)` on a `PUBLIC_LEARNER` without approval or mailbox attestation (`V13-N02`), no routine exists to set `recovery_approvals.cancelled_at` (`V13-N04`), and `audit_append` trusts an external adapter context for denial actor identity rather than using `operator_approver`/`deployment_operator` DB login (`V13-N04`).
6. **Remaining ambiguity/contradiction:** `§4` prohibits "public account promotion", `§4.1` binds `ADMIN` evidence to `OPERATOR_ATTESTED` or verified mailbox, yet `§5.4` `identity_lifecycle.mutate` allows `GRANT_ROLE` with any role enum and no approval check.
7. **Concrete failure scenario:** An internal caller invokes `identity_lifecycle.mutate(public_learner_id, rev, GRANT_ROLE, null, ADMIN)` -> promotes a `PUBLIC_LEARNER` to `ADMIN` with zero `recovery_approvals`, zero `admin_audit`, and zero `OPERATOR_ATTESTED` evidence. Separately, an approver needs to cancel a compromised live approval before expiry and has no callable `cancelApproval` operation.
8. **Acceptance test adequacy:** `I13-03` tests `bootstrap`/`recover` scenarios thoroughly, but does not test `identity_lifecycle` attempting `GRANT_ROLE(ADMIN)` on a `PUBLIC_LEARNER` or cancelling an approval.
9. **Dependencies & approval:** M1; `TECH-05/12` open (`V12-D02`).
10. **Independent disposition:** **PARTIALLY RESOLVED**.

#### `V12-H04` — Privileged Database Write Boundary and Routine Matrix
1. **Original root cause:** Undefined callable routine surface, risk of excessive `EXECUTE` / DML grants, owner-privilege inheritance, caller-supplied publisher identity, and independent connection commits.
2. **Proposed remediation:** Callable matrix (`§5.4`) across 8 caller boundaries, non-login routine owners, `PUBLIC`/`CREATE` revocations, fixed name resolution without dynamic SQL, single checked-out connection and outer transaction coupling, and explicit threat-boundary statement.
3. **Exact implementing sections:** `§§4.1, 5.3, 5.4`.
4. **Root cause vs. mitigation:** Establishes the structural database security model, *but* the matrix is incomplete and internally inconsistent with `§5.3` and `§4`.
5. **Enforceability:** **Not currently enforceable as written:**
   - **Missing account-lock routines (`V13-N01`):** `§5.3` requires acquiring `(2) DB account row lock` on `users` before `(3) JVM local stripe` before `(5)–(8)` domain locks on the same outer connection. PostgreSQL requires `UPDATE` privilege on `users` for `SELECT ... FOR UPDATE`/`FOR SHARE`, which `§4` and `§5.4` forbid ("No authoring or identity grants"). Yet `§5.4` provides no `lockAndCheckAccount` / `lockForLogin` routine for `identity_api`, `content_author_writer`, `content_publisher_writer`, or `assessment_writer`, and `I13-04` denies all unlisted `EXECUTE`/DML.
   - **Under-constrained `identity_lifecycle` (`V13-N02`):** `mutate` permits `GRANT_ROLE(ADMIN)` on `PUBLIC_LEARNER` and `SET_CREDENTIAL` on `PENDING` accounts.
   - **Missing lifecycle routines & denial actor trust (`V13-N04`):** No routine sets `recovery_approvals.cancelled_at` (`§4.1`) or deletes expired tokens past retention (`§5.5`), and `audit_append` trusts an external adapter context instead of DB login identity.
6. **Remaining ambiguity/contradiction:** Direct conflict between `§5.3`/`§5.4` ("actor/account lock and routine call on the same checked-out connection", "No authoring or identity grants") and PostgreSQL `SELECT FOR UPDATE` privilege rules, plus missing routines against `I13-04`'s strict allowlist.
7. **Concrete failure scenario:** Implementing `I13-04` (revoking `UPDATE` on `users` from `assessment_writer` and `identity_api` and denying unlisted routines) causes `POST /auth/login` and `PUT /attempts/{id}/answers/{qid}` to fail with `permission denied for table users` when attempting `(2)` before `(3)`; moving the `users` lock inside `assessment_writer` routines (after `(3)` is acquired in Java) causes an unresolvable AB-BA deadlock with `login` (which acquires `(2)` then `(3)`).
8. **Acceptance test adequacy:** `I13-04/P13-01` specifies enumerating grants and denying unlisted `EXECUTE`/DML, which directly exposes the missing lock routines if executed against PostgreSQL.
9. **Dependencies & approval:** M1 identity, M2/M4 content; `TECH-05/08/10/12` open (`V12-D02`, `V12-D03`).
10. **Independent disposition:** **PARTIALLY RESOLVED**.

#### `V12-H05` — Database Decision Clock After Attempt Lock
1. **Original root cause:** Generic "server time after locking" allowed PostgreSQL `now()`/`transaction_timestamp()`, which returns transaction-start time before lock wait and violates Exam cutoffs.
2. **Proposed remediation:** Explicitly requires `clock_timestamp()` on the primary in a separate statement *after* attempt-row lock acquisition (`§9.4`); prohibits `now()`, `transaction_timestamp()`, pre-lock statement time, and app/client clocks; uses `now >= deadline` consistently for cutoff and `MANUAL` vs. `TIME_EXPIRED` (`submittedAt = deadline`); defines 503 pause on primary time regression.
3. **Exact implementing sections:** `§§5.6, 9.1–9.2, 9.4`.
4. **Root cause vs. mitigation:** Directly addresses the root cause.
5. **Enforceability:** Enforceable with standard PostgreSQL `clock_timestamp()`.
6. **Remaining ambiguity/contradiction:** Minor operational detail: primary clock regression detection across a process restart on DB failover relies on durable timestamps (`started_at`, `updated_at`) rather than volatile last-seen timestamps; however, `§9.4` explicitly requires an approved clock continuity check on failover before unpausing.
7. **Concrete failure scenario:** Transaction B starts before deadline, blocks on Connection A's attempt lock until `clock_timestamp() == deadline`; after acquiring lock, B samples `clock_timestamp()` -> save/mark return `409`, submit grades with `TIME_EXPIRED` and `submittedAt = deadline`.
8. **Acceptance test adequacy:** `G13-01` tests exact boundary and post-boundary lock release under a controlled DB clock harness plus before-boundary control.
9. **Dependencies & approval:** M4; `TECH-09` open (`V12-D03`).
10. **Independent disposition:** **RESOLVED**.

#### `V12-H06` — Expiry Worker Capacity, Single-Flight Scheduling, and Overload
1. **Original root cause:** 60s worker tick with 20 claims × 5s worst-case tx (100s) could overlap or under-serve uncapped Exam arrival bursts.
2. **Proposed remediation:** `deadline_capacity` table capping fresh Exam admissions at 10 per UTC deadline minute (`§9.5`); 30s single-flight scheduler with 25s hard cycle cap; 1s worker claim deadline; scan ≤100 IDs / claim ≤20 (15 fresh / 5 retry); 1/2/4/8 min backoff + park at 5 failures; separate foreground pool; explicit benchmark thresholds.
3. **Exact implementing sections:** `§§5.3, 7.3–7.4, 9.5`.
4. **Root cause vs. mitigation:** Fixes steady-state arrival bounding and single-flight execution, *but* introduces/leaves two concurrency/backlog bugs.
5. **Enforceability:**
   - **>100-retry scan starvation (`V13-N05`):** Up to 150 retryable failures can accumulate over a 15-minute fault at 10/min; a single `LIMIT 100` scan ordered by `(next_eligible_at, deadline_at, id)` fills completely with retries and starves all newly due fresh attempts.
   - **Absent `deadline_capacity` row lock (`V13-N06`):** Under `READ COMMITTED`, `SELECT ... FOR UPDATE` on a brand-new minute row acquires no lock, causing concurrent Exam starts on a new minute to fail with `23505 unique_violation`.
6. **Remaining ambiguity/contradiction:** `§9.5` treats fresh and retry claims as partitioned quotas (15/5) at claim time, yet scans them via a single unpartitioned 100-row index slice.
7. **Concrete failure scenario:** A 15-minute grading fault creates 150 retryable attempts (`next_eligible_at` in past). When grading recovers, newly due healthy attempts have later `next_eligible_at` and never enter the top-100 scan, breaching the 60s p95 / 120s p99 healthy finalization SLO.
8. **Acceptance test adequacy:** `G13-02` only tests 60 due fixtures (`60 < 100`), so it fails to detect >100-retry starvation, and does not test concurrent starts on an uninitialized `deadline_capacity` minute row.
9. **Dependencies & approval:** M4; `TECH-09` and benchmark open (`V12-D03`).
10. **Independent disposition:** **PARTIALLY RESOLVED**.

#### `V12-M01` — Registration/Resend Timing, Admission, and Delivery Bounds
1. **Original root cause:** Unbounded DB lock/lookup/queue paths before 300 ms floor and lack of numeric timing tolerance/sample baseline.
2. **Proposed remediation:** Fail-closed admission (16 concurrent, 20/s), 20 ms lock / 50 ms SQL / 150 ms total operation budgets, 300 ms floor outside DB/pool/slot, uniform 202 on all admitted failures/ineligible states/full queue, uniform 429/503, and concrete production timing baseline (10 req/s, 500 samples/cohort, p50 ≤15 ms, p95 ≤30 ms, 99% within 300–450 ms).
3. **Exact implementing sections:** `§§2.2–2.3, 6.1`.
4. **Root cause vs. mitigation:** Addresses the root cause and sets objective acceptance gates.
5. **Enforceability:** Enforceable using standard timeouts/semaphores and controlled load testing.
6. **Remaining ambiguity/contradiction:** None identified.
7. **Concrete failure scenario:** Locked canonical row or full mail queue cancels/aborts within ≤150 ms and pads to 300 ms, returning identical 202 payload/headers to absent/pending/active cases.
8. **Acceptance test adequacy:** `I13-05` verifies all cohorts, 150 ms/300 ms budgets, crash before enqueue, and production percentile thresholds.
9. **Dependencies & approval:** M1 contract + production measurement; `TECH-03/04/12` open (`V12-D02`).
10. **Independent disposition:** **RESOLVED**.

#### `V12-M02` — Fresh Start Isolation, Post-Lock Re-read, and Replay Authorization
1. **Original root cause:** Potential stale snapshot between public RR reads and fresh starts, plus ambiguous owner/role validation on idempotent replay.
2. **Proposed remediation:** `READ COMMITTED` for fresh starts (`§5.6`), post-lock fresh statement reading current frozen eligibility after `(5) -> (6)` locks, replay checking current verified `LEARNER`/ownership without repinning, RR/single-statement isolation retained for public reads.
3. **Exact implementing sections:** `§§5.3, 5.6, 8, 9.1`.
4. **Root cause vs. mitigation:** Addresses the root cause cleanly.
5. **Enforceability:** Enforceable in PostgreSQL `READ COMMITTED`.
6. **Remaining ambiguity/contradiction:** None in `§5.6`; see `V13-N07` regarding `§6.1` precedence listing.
7. **Concrete failure scenario:** Start pauses before `(5)` passage lock; publisher withdraws exercise and commits; start resumes, acquires `(5)->(6)`, runs fresh statement -> sees withdrawal (`404`). Existing matching key after withdrawal returns `200` for verified owner, `403` if `LEARNER` role was revoked.
8. **Acceptance test adequacy:** `P13-02` directly tests the lock barrier, withdrawal/republish race, replay auth, and rollback cleanup.
9. **Dependencies & approval:** M2/M4; `TECH-08/09` open (`V12-D03`).
10. **Independent disposition:** **RESOLVED**.

#### `V12-M03` — API Capability, Ownership, Reveal, and Error Precedence
1. **Original root cause:** Ambiguous role requirements on result/review/feedback after `LEARNER` removal, missing unified error precedence, and §1 vs §3.3 logout cookie-clear wording drift.
2. **Proposed remediation:** Explicit `ACTIVE verified LEARNER` gate across all attempt routes (`§6.1`), unified precedence order, concealed `404` vs `403`/`409`/`412`/`428`/`413`/`415`, and §1 aligned with §3.3's no-clear logout.
3. **Exact implementing sections:** `§§1, 3.4, 6.1, 8, 9.2–9.3`.
4. **Root cause vs. mitigation:** Addresses all components of `V12-M03`.
5. **Enforceability:** Enforceable via explicit filter/controller precedence.
6. **Remaining ambiguity/contradiction:** Minor precedence omission for `POST /attempts` replay (`200`/`409 IDEMPOTENCY_CONFLICT`) relative to `404`/`429` in `§6.1` (tracked as `V13-N07`).
7. **Concrete failure scenario:** Former learner whose `LEARNER` role was removed tries `GET /attempts/{id}/review` -> `403 FORBIDDEN` at step 4 (`§6.1`). Different learner tries `GET /attempts/{id}/review` -> `404 RESOURCE_NOT_FOUND` at step 6.
8. **Acceptance test adequacy:** `I13-06/R13-01` tests the full role/owner/status/precondition matrix.
9. **Dependencies & approval:** M1 identity, M4 attempt API; `TECH-01/07/12` open.
10. **Independent disposition:** **RESOLVED**.

#### `V12-M04` — Unicode Normalization, Word Count, State vs. Grade Equality, Graph Budget
1. **Original root cause:** Unpinned Unicode version/locale casing, ambiguous normalized-empty vs. null persistence, and unmeasured canonical graph byte accounting.
2. **Proposed remediation:** Pinned `GR-1` (Unicode 15.1.0, full non-Turkic default case folding), ordered normalization pipeline, conjunctive 2,000 scalar / 8,192 UTF-8 byte checks on decoded input and normalized output, normalized-empty mapped to persisted `null` before state comparison (no lock), RFC 8785 canonical JSON 512 KiB graph budget, and 9 literal test fixtures.
3. **Exact implementing sections:** `§§7.1–7.4, 9.2–9.3`.
4. **Root cause vs. mitigation:** Fully resolves the normalization and determinism root causes.
5. **Enforceability:** Enforceable with pinned ICU/Unicode 15.1.0 and RFC 8785 serializer.
6. **Remaining ambiguity/contradiction:** None.
7. **Concrete failure scenario:** Client sends `"\t\u00A0"` -> normalizes empty -> mapped to `null` -> `UNANSWERED`, no score lock. Client sends `"café"` then `"cafe\u0301"` at current revision -> distinct raw scalar sequences -> not a no-op (409 if score-locked).
8. **Acceptance test adequacy:** `G13-03` tests every literal fixture in `§7.4` plus 512 KiB / +1 byte boundary.
9. **Dependencies & approval:** M4; `TECH-09` open (`V12-D03`).
10. **Independent disposition:** **RESOLVED**.

#### `V12-M05` — Foreign Key Lifecycle, Retention, and Derived Canonical Exercise ID
1. **Original root cause:** Unspecified per-relation `ON DELETE`/`ON UPDATE` behavior and redundant mutable-check `requested_exercise_id` on `attempts`.
2. **Proposed remediation:** Explicit `ON UPDATE RESTRICT` and `ON DELETE RESTRICT` across all 43 FKs (`§5.5` + `§5.1`), relation-by-relation lifecycle rules, native FKs `admin_governance.bootstrap_event_id -> admin_audit.id` and `admin_audit.recovery_approval_id -> recovery_approvals.id`, and removal of independent request exercise ID column on `attempts` (derived from `exercise_versions.exercise_id`).
3. **Exact implementing sections:** `§§4.1, 5.1, 5.5–5.6, 9.1`.
4. **Root cause vs. mitigation:** Addresses FK lifecycle and canonical identity drift directly. (Note: missing token-cleanup callable routine in `§5.4` is tracked under `V13-N04`/`V12-H04`).
5. **Enforceability:** Enforceable in PostgreSQL DDL.
6. **Remaining ambiguity/contradiction:** None on FKs themselves.
7. **Concrete failure scenario:** Attempted delete on user/role/governance/approval referenced by live audit or membership fails with FK `RESTRICT`; `attempts` cannot store a mismatched `exercise_id` because it derives solely from `exercise_version_id`.
8. **Acceptance test adequacy:** `I13-07` (M1 identity FKs) and `P13-03` (16 composite + standalone FKs) verify metadata and restriction behavior.
9. **Dependencies & approval:** M1 identity, M2/M4 content; `TECH-08/12` open.
10. **Independent disposition:** **RESOLVED**.

#### `V12-M06` — Traceability Barriers, Thresholds, M1 Isolation, and Search vs. Indexing
1. **Original root cause:** Missing numeric/barrier specifications in tests, stale v1.1 links, M1/Reading coupling in `SE-04`, and ambiguous internal search vs. external index sample exposure.
2. **Proposed remediation:** Versioned `I13-*`, `P13-*`, `G13-*`, `R13-01`, `D13-01` acceptance specs; M1 isolation using health/test fixture; explicit distinction between internal safe search projection and external `noindex`/sitemap exclusion; measurable numeric criteria.
3. **Exact implementing sections:** `§§5.3, 8, 11`, [MILESTONE-0-TRACEABILITY.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-TRACEABILITY.md).
4. **Root cause vs. mitigation:** Addresses all v1.2 traceability defects. (New test gaps introduced by `V13-N01`–`V13-N07` are documented in §9).
5. **Enforceability:** Verifiable against v1.3 specs.
6. **Remaining ambiguity/contradiction:** None in historical scope.
7. **Concrete failure scenario:** M1 runs `I13-01`–`I13-07` on identity-only schema without content tables; M3 tests internal search attaching safe samples via snapshot projector while sitemaps/external indexers receive none.
8. **Acceptance test adequacy:** `D13-01` verifies test isolation, route inventory, D01–D11 text, and digests.
9. **Dependencies & approval:** M1 and later gates; dependent `TECH` items open.
10. **Independent disposition:** **RESOLVED**.

---

## 4. New Findings

### `V13-N01` · High · Missing DB Account-Lock / Login-Recheck Routine Contradicts `§5.3` Lock Order and `§4` No-Direct-DML Rule

- **Exact section and subsection:** `§5.4` (lines 166–179), `§3.2` (lines 47–49), `§3.4` (line 59), `§4` (line 79), `§5.3` (line 156); [MILESTONE-0-TRACEABILITY.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-TRACEABILITY.md) `I13-04/P13-01`.
- **Evidence from the document:**
  - `§5.3`: `"All writers use this order and acquire their entire footprint before mutation: (1) singleton Admin governance row..., (2) DB account rows sorted by UUID, (3) process-local account guard stripes sorted by stripe index, ... (5) stable passage rows..., (6) stable exercise rows..., (7) Exam deadline capacity buckets..., (8) attempt rows..."` and `"Protected attempt writers acquire the identity prefix, then attempt; fresh starts acquire the identity and content prefixes..."`
  - `§3.2`: `"Verify the password outside the fence, then take the DB account row lock and process-local account guard in §5.3 order. Recheck the credential snapshot described in §3.4 under the account-row fence."` and `"Protected writes take account row and local guard, revalidate generation/revision and expiry before mutation, and hold through commit."`
  - `§5.4`: `"Use dedicated connections keyed by service boundary, with actor/account lock and routine call on the same checked-out connection and outer transaction."` Meanwhile `§5.4` lists only `register`, `issue`, `consume`, `profile` under `identity_api`, only `editDraft`/`publish`/`withdraw` under content writers, and only `start`/`save`/`mark`/`submit` under `assessment_writer` (explicitly stating `"No authoring or identity grants"`), while `§4` states `"Runtime DB role cannot directly write roles, state or credential columns"` and `I13-04` mandates `"Assert every unlisted EXECUTE/CREATE/DML denied."`
- **Root cause:** In PostgreSQL, `SELECT ... FROM users ... FOR UPDATE` (and `FOR SHARE` / `FOR KEY SHARE` / `FOR NO KEY UPDATE`) requires the `UPDATE` privilege on at least one column of `users`. Neither a table/column `UPDATE` grant on `users` nor a callable `SECURITY DEFINER` account-lock/recheck routine is declared in `§5.4`. Furthermore, moving the `users` lock inside domain routines (`assessment_writer.start`/`save`/`mark`/`submit`, `content_*_writer`) cannot work because `(3)` (the JVM process-local account stripe lock) must be acquired *after* `(2)` (the PostgreSQL `users` row lock) and *before* `(5)–(8)` (content/bucket/attempt locks) to avoid AB-BA deadlocks with `login`.
- **Concrete exploit or failure scenario:**
  1. Implementing `I13-04` strictly (no `UPDATE` on `users` for runtime roles, and no unlisted routines) causes `POST /auth/login` and every protected write (`PATCH /users/me`, `editDraft`, `publish`, `start`, `save`, `mark`, `submit`) to fail at runtime with PostgreSQL `ERROR: permission denied for table users` when acquiring `(2)` before `(3)`.
  2. Conversely, if an implementer works around this by acquiring the JVM stripe lock `(3)` first and taking `(2)` inside the domain DB routine, Thread A (`POST /auth/login`: acquires DB `(2)` -> waits on JVM `(3)`) and Thread B (`PUT /attempts/{id}/answers/{q}`: holds JVM `(3)` -> waits on DB `(2)`) enter an undetectable cross-tier AB-BA deadlock until lock/statement timeout.
  3. Granting broad `UPDATE` on `users` to `assessment_writer` and `content_*_writer` breaks `§4` and `§5.4` privilege isolation.
- **Impact:** Blocks M1 login and protected writes under `I13-04`, or introduces cross-tier deadlocks / excessive DB privileges.
- **Required correction:** Explicitly add narrow, non-mutating `SECURITY DEFINER` account-fencing routines to `§5.4`:
  - `identity_api`: `lockAndReadCredentialSnapshot(user UUID)` (or `lockForLogin`) returning `(user_id, password_hash, status, security_revision)` under `FOR UPDATE`/`FOR NO KEY UPDATE` on the caller's outer transaction.
  - `identity_api`, `content_author_writer`, `content_publisher_writer`, `assessment_writer`: a dedicated `lockAndCheckActor(actor UUID, expectedSecurityRevision bigint)` routine executing `SELECT ... FROM users ... FOR SHARE` (or `FOR UPDATE`) and verifying `ACTIVE` + `security_revision` on the checked-out connection before acquiring JVM `(3)` and calling the domain routine.
- **Proposed acceptance test:** Under `I13-04/P13-01`, verify `identity_api` and `assessment_writer` have zero direct `SELECT ... FOR UPDATE`/`UPDATE` privileges on `users`, can invoke only the listed lock routine on the same outer connection before `(3)`, and race `login` vs. `save` vs. external `recover` with zero deadlocks and immediate `401` on revision mismatch.
- **Milestone dependency:** M1 (identity/login) and M2/M4 (content/assessment protected writes).
- **Product Owner approval required:** Architecture/privilege-matrix remediation before sign-off; covered under `TECH-05`/`TECH-08`/`TECH-12` (`V12-D02`, `V12-D03`).

---

### `V13-N02` · High · `identity_lifecycle.mutate` (`§5.4`) Permits Unapproved `ADMIN` Promotion of Public Accounts and `SET_CREDENTIAL` on `PENDING` Accounts

- **Exact section and subsection:** `§5.4` (line 171), `§4` (lines 79, 81), `§4.1` (line 90), `§2.1` (line 15).
- **Evidence from the document:**
  - `§5.4`: `identity_lifecycle · disable and security changes`: `"mutate(target UUID, expectedSecurityRevision bigint, action DISABLE, SET_CREDENTIAL, GRANT_ROLE or REVOKE_ROLE, optional credentialHash text or role enum as appropriate). Reject unexpected fields and require dedicated recovery for an operator credential reset. Allowlisted internal operations only. Lock governance before account for any potentially Admin-eligible target, check authority and usable count and advance revision on security change. No HTTP entry point in M1. Identity API cannot invoke this operation."`
  - `§4`: `"Reject self-approval, missing/expired approvals, wrong targets, public account promotion or restart-triggered bootstrap."`
  - `§4.1`: `"immutable account_kind PUBLIC_LEARNER or DEDICATED_OPERATOR... Only the operator path can assign the dedicated kind."`
  - `§2.1`: `"PENDING has neither hash nor verification timestamp. ACTIVE requires both."`
- **Root cause:** While `§4` and `§4.1` restrict Admin identities to `DEDICATED_OPERATOR` accounts provisioned via bootstrap or two-person `recovery_approvals`, `§5.4`'s `identity_lifecycle.mutate` routine checks only "dedicated recovery for an operator credential reset" and "usable count". It fails to prohibit:
  1. `GRANT_ROLE(..., ADMIN)` on a `PUBLIC_LEARNER` account (or without a consumed `recovery_approval` / `OPERATOR_ATTESTED` evidence), and
  2. `SET_CREDENTIAL` on a `PENDING` (or `DISABLED`) account, which would bypass `D08` mailbox-token verification and violate `§2.1`'s `PENDING` null-credential invariant.
- **Concrete exploit or failure scenario:**
  1. An internal service bug or compromised caller authorized to use the `identity_lifecycle` role invokes `mutate(public_learner_uuid, rev, GRANT_ROLE, NULL, 'ADMIN')`. The database routine locks `admin_governance`, verifies `usable_admin_count >= 1`, grants `ADMIN` to the `PUBLIC_LEARNER`, and commits—silently creating an unattested Admin without `recovery_approvals`, `admin_audit`, or `OPERATOR_ATTESTED` mailbox evidence.
  2. An internal caller invokes `mutate(pending_user_uuid, rev, SET_CREDENTIAL, '$argon2...', NULL)`. `users.password_hash` becomes non-null while `verified_at` remains `NULL`, violating `§2.1` and `D08`.
- **Impact:** Compromises `D11` / `§4` two-person Admin governance and `D08` registration verification invariants at the database routine boundary.
- **Required correction:** Explicitly constrain `identity_lifecycle.mutate` in `§5.4`:
  1. Reject `GRANT_ROLE` with `ADMIN` (and `REVOKE_ROLE` of `ADMIN` / `DISABLE` of a `DEDICATED_OPERATOR` Admin, or restrict `ADMIN` membership mutations exclusively to `deployment_operator` bootstrap/recovery with `recovery_approvals` + `admin_audit`).
  2. Require `target.account_kind = PUBLIC_LEARNER` and `target.status = ACTIVE` (with non-null `verified_at`) for `SET_CREDENTIAL`, rejecting `PENDING`, `DISABLED`, and `DEDICATED_OPERATOR` targets.
  3. Enforce `§2.1` matching-nullness (`(status = 'PENDING' AND password_hash IS NULL AND verified_at IS NULL) OR (status = 'ACTIVE' AND password_hash IS NOT NULL AND verified_at IS NOT NULL) OR ...`) as a database `CHECK` constraint on `users`.
- **Proposed acceptance test:** Add negative tests in `I13-03`/`I13-04` attempting `identity_lifecycle.mutate(..., GRANT_ROLE, ADMIN)` on both `PUBLIC_LEARNER` and `DEDICATED_OPERATOR` accounts, and `SET_CREDENTIAL` on `PENDING` and `DISABLED` accounts; assert DB routine rejection with zero state change.
- **Milestone dependency:** M1 (`identity_lifecycle` migrations and routines).
- **Product Owner approval required:** Required under `TECH-05` / `TECH-12` (`V12-D02`).

---

### `V13-N03` · Medium · Unbounded In-Memory `PREAUTH` CSRF Session Store Risks Heap Exhaustion and Full Single-Process Outage

- **Exact section and subsection:** `§3.1` (lines 39, 43), `§3.4` (lines 61, 67).
- **Evidence from the document:**
  - `§3.1`: `"Propose one serving application process, process-local account bindings... Crash loses all app sessions; restart always requires login."` and `"Bound anonymous CSRF sessions separately (proposed 15-minute lifetime, source admission limits and bounded cleanup)."`
  - `§3.4`: `"GET /auth/csrf?scope=PREAUTH | Only PREAUTH; issue/reuse a bounded anonymous session."`
- **Root cause:** Unlike `AUTH` sessions (naturally bounded by active user count), the volatile mail queue (`§2.3`, explicitly bounded), and `deadline_capacity` (`§9.5`, capped at 10/min), `PREAUTH` sessions are stateful in-memory server-side objects retained for 15 minutes inside the sole serving JVM process. Per-source admission limits do not bound total memory under distributed or spoofed-origin anonymous traffic, and no global capacity cap, memory ceiling, or bounded eviction/fail-closed policy is specified.
- **Concrete exploit or failure scenario:** An unauthenticated botnet distributes `GET /api/v1/auth/csrf?scope=PREAUTH` requests without cookies across many source IPs (staying below per-source limits). Each request allocates a new 15-minute `PREAUTH` session in JVM memory. Within minutes, JVM heap exhausts (`OutOfMemoryError`) and crashes the single serving process—evicting **all** active `AUTH` sessions (`D07`/`TECH-02`) and interrupting active learner Exams.
- **Impact:** Unauthenticated denial of service against the entire single-process platform.
- **Required correction:** Define an explicit global capacity bound (e.g., max active `PREAUTH` entries or bounded ring/LRU cache sized to expected scale, with global issuance rate limit and/or bounded oldest-entry eviction or `429 ADMISSION_LIMIT` fail-closed behavior) in `§3.1` and `TECH-02`/`TECH-04`/`TECH-12`.
- **Proposed acceptance test:** Under `I13-02`/`I13-05`, flood `GET /auth/csrf?scope=PREAUTH` from simulated distinct source addresses beyond the global cap; assert memory remains bounded, `AUTH` sessions stay intact and responsive, and excess `PREAUTH` allocations either evict deterministically or return `429`.
- **Milestone dependency:** M1 (`TECH-02`, `TECH-04`, `TECH-12`).
- **Product Owner approval required:** Yes, as part of `V12-D01` / `V12-D02` (`TECH-02/04/12`).

---

### `V13-N04` · Medium · Missing Callable Routines for Approval Cancellation (`§4.1`) and Token Cleanup (`§5.5`), Plus Trusted-Adapter Gap in `audit_append` (`§5.4`)

- **Exact section and subsection:** `§4.1` (line 88), `§5.4` (lines 166, 171–174), `§5.5` (lines 187, 189).
- **Evidence from the document:**
  - `§4.1`: `recovery_approvals` includes `"nullable consumed_at/cancelled_at... Payload immutable, only consumption/cancellation changes."`
  - `§5.5`: `"Disable under revision/governance fences, invalidate tokens and cancel approvals as applicable."` and `"Verification tokens: Only expired, consumed or invalidated rows past approved retention may be explicitly deleted. Cleanup follows user then token order."`
  - `§5.4`: Lists no `cancelApproval` operation under `operator_approver` or `deployment_operator`, and no `cleanupTokens` routine under any identity role. Meanwhile `audit_append` (`line 174`) states `"Denial writer is a narrowly trusted adapter and records the verified external actor from its authenticated operation context"`, contradicting `line 166` (`"Deployment operator/approver authority instead comes from their distinct trusted database login mappings, not supplied actor IDs"`).
- **Root cause:** Incomplete alignment between data-lifecycle rules (`§§4.1, 5.5`) and the allowlisted routine surface (`§5.4`), plus separating denial audit logging into a trusted-adapter role rather than granting `appendDenied` directly to the authenticated `operator_approver`/`deployment_operator` DB login roles.
- **Concrete exploit or failure scenario:**
  1. An approver issues an erroneous `recovery_approval` (or an Admin target is disabled), but no allowlisted routine exists to set `cancelled_at` (`I13-04` denies unlisted `EXECUTE` and direct `UPDATE`), leaving the approval live until `expires_at`.
  2. Scheduled token cleanup after 7 days (`TECH-04`) has no allowlisted DB routine (`I13-04` denies direct `DELETE` on `verification_tokens`).
  3. A compromised `audit_append` adapter can record false actor IDs in denial audits because the DB routine accepts the actor from the adapter's context rather than `session_user`.
- **Impact:** Incomplete lifecycle enforcement in M1 (`cancelled_at`, token retention cleanup) and weakened non-repudiation for denial audits.
- **Required correction:** Add `cancelApproval(approval UUID)` (deriving actor from `operator_approver` DB login, locking `(1) governance -> (2) target -> (4) approval`) and `cleanupExpiredTokens(cutoff UTC, batchLimit int)` (locking `(2) -> (4)`, enforcing `consumed_at/invalidated_at/expires_at < cutoff` and rejecting live tokens) to `§5.4`; grant `appendDenied` directly to `operator_approver` and `deployment_operator` DB logins so `session_user` attributes denial actors identically to success audits.
- **Proposed acceptance test:** Under `I13-03`/`I13-04`/`I13-07`, cancel an unconsumed approval and assert subsequent `recover` fails; test `cleanupExpiredTokens` deletes only past-retention dead tokens while rejecting live tokens; verify `appendDenied` records DB `session_user` and rejects arbitrary caller-supplied actor IDs.
- **Milestone dependency:** M1 (`TECH-04`, `TECH-05`, `TECH-12`).
- **Product Owner approval required:** Covered under `V12-D02` (`TECH-04/05/12`).

---

### `V13-N05` · Medium · Single 100-ID Expiry Worker Scan Can Be Starved by >100 Retryable Failures

- **Exact section and subsection:** `§9.5` (lines 322, 324, 326, 328); [MILESTONE-0-TRACEABILITY.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-TRACEABILITY.md) `G13-02`.
- **Evidence from the document:**
  - `§9.5`: `"Scan indexed due keys (partial index over next_eligible_at, deadline_at, id for IN_PROGRESS Exam not parked), at most 100 IDs and 20 claims, with up to 5 retry claims and reserved capacity for up to 15 fresh claims when present."`
  - `§9.5`: `"Delay 1, 2, 4 and 8 minutes at successive failures; after five confirmed failures park for operator investigation."`
  - `§9.5`: Capacity admission allows 10 Exams per deadline minute (`15 min × 10/min = 150` potential failing attempts before parking).
- **Root cause:** The worker specification enforces separate claim quotas (15 fresh vs. 5 retry), yet appears to feed both from a **single** `LIMIT 100` index scan ordered by `(next_eligible_at, deadline_at, id)`. Because failed attempts retain older `next_eligible_at` values than newly expired fresh attempts, >100 accumulated retryable attempts push all fresh attempts out of the 100-ID scan window.
- **Concrete exploit or failure scenario:** A transient 15-minute grading disruption occurs while learners start Exams at the permitted 10/min rate. Up to 150 attempts enter retry state (`failure_count` 1–4, unparked) with `next_eligible_at <= now`. Once grading recovers, newly expired healthy attempts arrive with `next_eligible_at > retry.next_eligible_at`. Each 30s worker cycle scans the oldest 100 due rows—all 100 are retries (`failure_count > 0`). The worker claims 5 retries and **0 fresh attempts** (wasting the 15 reserved fresh slots). Fresh healthy attempts wait >5 minutes behind the retry drain, violating the `p95 ≤ 60 s` / `p99 ≤ 120 s` healthy SLO. `G13-02` misses this because its overload fixture uses only 60 due attempts (`60 < 100`).
- **Impact:** Healthy overdue Exam finalization starves during recovery from transient outages within permitted admission capacity.
- **Required correction:** Partition the scan into two bounded index queries (e.g., partial index or `(failure_count = 0)` vs. `(failure_count > 0)`), scanning up to e.g. 50 fresh due IDs for the 15 fresh claim slots and up to 50 retry due IDs for the 5 retry claim slots (still ≤100 total scanned IDs).
- **Proposed acceptance test:** Extend `G13-02` to seed 120 unparked retry-eligible attempts (`failure_count = 1..4`, `next_eligible_at < now`) plus newly due fresh attempts (`failure_count = 0`); verify each cycle still claims up to 15 fresh attempts and meets `p95 ≤ 60 s` / `p99 ≤ 120 s` for healthy arrivals.
- **Milestone dependency:** M4 (`TECH-09`).
- **Product Owner approval required:** Yes, part of `TECH-09` (`V12-D03`).

---

### `V13-N06` · Medium · Absent `deadline_capacity` Row Locking Race Under `READ COMMITTED`

- **Exact section and subsection:** `§5.6` (line 195), `§9.5` (line 322).
- **Evidence from the document:**
  - `§5.6`: `"Use READ COMMITTED for fresh starts... Acquire the Exam capacity bucket lock after content locks and before inserting the attempt if needed (§9.5)."`
  - `§9.5`: `"For Exam only, persist deadline_capacity(bucket_start_utc UTC minute PK, reserved_count integer 0–10, updated_at UTC)... To select the candidate bucket, precompute from primary clock plus duration, lock bucket, then sample §9.4 time and recompute. If bucket changed, roll back and retry the whole action once with the same key, otherwise apply the reservation."`
- **Root cause:** `deadline_capacity` rows are per-minute dynamic rows (cleaned up 24h after deadline). At the start of every new minute, the target `bucket_start_utc` row does not yet exist. In PostgreSQL `READ COMMITTED`, `SELECT ... FROM deadline_capacity WHERE bucket_start_utc = $1 FOR UPDATE` on a missing row returns 0 rows and acquires **no lock**—the exact "locking an absent row" hazard previously identified on `admin_governance` (`V11-H03`).
- **Concrete exploit or failure scenario:** At `12:01:00.010`, Learners A and B concurrently start 60-minute Exams targeting new bucket `13:01:00`. Both execute `SELECT ... FOR UPDATE` on `13:01:00` -> both get 0 rows and no lock. Both insert `(13:01:00, 1, ...)`: A succeeds, B blocks on the PK index and aborts with `23505 unique_violation`, causing spurious `503`/retries instead of clean serialization for slots 1–10. Conversely, if an implementer uses `INSERT ... ON CONFLICT DO UPDATE SET reserved_count = reserved_count + 1` *before* sampling `clock_timestamp()`, they risk mutating the wrong bucket if the clock crosses a minute boundary or handling rollback contention poorly.
- **Impact:** Spurious start conflicts/retries on the first concurrent Exam starts of every new UTC minute.
- **Required correction:** Explicitly specify the row-creation-before-lock pattern in `§9.5`: either `INSERT INTO deadline_capacity(bucket_start_utc, reserved_count, updated_at) VALUES ($1, 0, clock_timestamp()) ON CONFLICT DO NOTHING` followed by `SELECT ... FOR UPDATE` (before sampling `§9.4` time and checking `reserved_count < 10`), or pre-populate upcoming minute buckets ahead of time.
- **Proposed acceptance test:** In `G13-02`, barrier-synchronize 11 concurrent Exam starts targeting a brand-new, unseeded `bucket_start_utc` minute; assert exactly 10 succeed (`201`) with `reserved_count = 10` and the 11th receives `429 EXAM_CAPACITY` with zero `23505`/`503` failures.
- **Milestone dependency:** M4 (`TECH-09`).
- **Product Owner approval required:** Covered under `TECH-09` (`V12-D03`).

---

### `V13-N07` · Low · `§6.1` Error Precedence Omits `POST /attempts` Idempotent Replay (`200`) and `409 IDEMPOTENCY_CONFLICT` Placement Ahead of `404`/`429`

- **Exact section and subsection:** `§6.1` (line 219), `§5.6` (line 195), `§9.1` (line 288), `§9.5` (line 322).
- **Evidence from the document:**
  - `§6.1`: Orders `404 RESOURCE_NOT_FOUND` (step 6) -> `429 ADMISSION_LIMIT` (step 8) -> `409 ATTEMPT_CLOSED`/`RESULT_PENDING` (step 9), and appends `"Start replay conflict is 409 IDEMPOTENCY_CONFLICT"` at the end without a numbered position.
  - `§5.6`: `"Under the account/revision and local guard prefix, check a prior key for the authorized owner. Match request pair against immutable stored mode and stable exercise ID derived through the pinned edition... Existing matching key bypasses only fresh content eligibility... A different pair conflicts."`
  - `§9.5`: `"Request replay never reserves again."`
- **Root cause:** `§6.1` uses one generic numbered precedence pipeline without carving out `POST /attempts`'s idempotency lookup (`§5.6`) ahead of fresh-exercise `404` eligibility and `429` capacity/start-rate admission.
- **Concrete exploit or failure scenario:** A learner starts an attempt (`E1`, `LEARNING`, `K1`), `E1` is later withdrawn, and the learner's client retries `POST /attempts` with `(E1, LEARNING, K1)` (or sends `(E_withdrawn, EXAM, K1)`). An implementer following `§6.1` step 6 checks `E1` public eligibility first and returns `404` instead of `200` replay (breaking `AT-01`) or `409 IDEMPOTENCY_CONFLICT`. Similarly, applying step 8 (`429` start throttle / `EXAM_CAPACITY`) before replay blocks safe idempotent retries.
- **Impact:** Breaks `AT-01` replay-after-withdrawal and safe idempotent retry under throttle if `§6.1` is implemented literally over `§5.6`.
- **Required correction:** Explicitly place `POST /attempts` idempotency lookup (`200` replay / `409 IDEMPOTENCY_CONFLICT`) after `400 INVALID_REQUEST` syntax validation (step 5) and before fresh-exercise eligibility (`404`, step 6) and fresh-start capacity/throttle admission (`429`, step 8) in `§6.1`.
- **Proposed acceptance test:** Under `AT-01`/`P13-02`, test `POST /attempts` with an existing `Idempotency-Key` when (a) the exercise has since been withdrawn (`200`), (b) the request specifies a different withdrawn exercise (`409 IDEMPOTENCY_CONFLICT`), and (c) the `deadline_capacity` bucket is at 10/10 (`200` replay, not `429`).
- **Milestone dependency:** M4 (`TECH-09`).
- **Product Owner approval required:** No new decision; aligns `§6.1` with existing `§5.6`/`§9.1` contract.

---

## 5. Cross-Section Consistency

1. **`§5.3` / `§3.2` / `§3.4` vs. `§5.4` (Lock Order vs. DB Routine Matrix):**
   `§§3.2, 3.4, 5.3` require acquiring `(2)` PostgreSQL `users` row locks before `(3)` JVM local guard stripes on the same outer connection across `login` (`identity_api`) and all protected writes (`content_*_writer`, `assessment_writer`), whereas `§5.4` omits the lock/snapshot-recheck routines and `§4` forbids direct DML (`V13-N01`).
2. **`§4` / `§4.1` vs. `§5.4` (`identity_lifecycle` and Governance Boundaries):**
   `§§4, 4.1` ban public-account promotion to `ADMIN`, require `OPERATOR_ATTESTED` evidence for Admins, define `recovery_approvals.cancelled_at`, and require DB-login attribution for operator/approver actions; `§5.4` leaves `identity_lifecycle.mutate` open to `GRANT_ROLE(ADMIN)` on public accounts and `SET_CREDENTIAL` on `PENDING` accounts (`V13-N02`), omits `cancelApproval` and `cleanupTokens`, and attributes denial audits via a trusted adapter (`V13-N04`).
3. **`§5.6` / `§9.1` / `§9.5` vs. `§6.1` (Idempotent Start Precedence):**
   `§5.6` checks idempotent replay before fresh content eligibility (`404`) and before `deadline_capacity` (`429`), whereas `§6.1` lists `404` and `429` before `409` without positioning idempotency lookup (`V13-N07`).
4. **`§5.1` / `§5.2` vs. `§9.3` (`evidence` Cardinality):**
   `§9.3` describes review `evidence` in singular (`"Evidence is paragraph ID and reviewed excerpt"`), while `§5.1` defines an `evidence` table and `§5.2` specifies `"One marking row and up to one explanation per question"` without specifying whether `evidence` is 0..1 or 0..N per question. Clarify cardinality before M2/M4 DDL.
5. **`§3.1` vs. `§6` / `§9` ("Polling" Idle-Clock Exclusion):**
   `§3.1` states `"Public reads, failed calls, health, static assets, polling and CSRF bootstrap do not touch the authoritative idle clock"`, and `SE-02` tests `"background polling"`. However, neither `§6` nor `§9` defines a specific polling route (e.g., whether `GET /users/me` or `GET /attempts/{id}` overdue recovery is classified as polling vs. qualifying activity). Enumerate exact excluded routes under `TECH-02`/`TECH-12`.
6. **`§5.4` vs. `§8` (`reading_activity` and `user_vocabulary` Write Boundary):**
   `§8` defines learner progress and vocabulary CRUD (`§5.1` composite FKs), and `§5.3` prohibits direct content-table DML, yet `§5.4` does not list `progress_writer` / `vocabulary_writer` boundaries. Acceptable for M1/M0 provided M3/M4 defines their routine boundaries under `TECH-08`/`TECH-10`.

---

## 6. Security and Failure Scenario Analysis

### 6.1 Identity and Credential Security
- **Registration & Verification (`§2`):** Separating `POST /auth/register` (`{email, displayName}`, null password, `PENDING`) from `POST /auth/verify-email` (`{token, newPassword}`) completely eliminates pre-verification password hijacking. Fragment-based token delivery with `no-store`, `no-referrer`, strict CSP, and memory-only JS handoff minimizes referer/log/cache exposure, while acknowledging residual client-browser visibility before script execution.
- **Credential Snapshot & Login Races (`§3.4`):** Single-statement snapshot `(user_id, password_hash, status, security_revision)` followed by dummy/real hash verification outside the lock and exact-tuple comparison under `(2) users -> (3) local stripe` prevents TOCTOU acceptance of stale passwords across concurrent resets, role changes, or disablement—subject to fixing `V13-N01`.
- **Account Enumeration & Timing (`§2.3`):** Account-independent validation/throttling (`429`), 20 ms/50 ms/150 ms DB timeouts, and external 300 ms floor padding ensure locked rows, full mail queues, and existing vs. absent accounts collapse onto an identical `202` envelope within measurable p50/p95 tolerances.

### 6.2 Session and Authentication Architecture
- **Dual-Tab Login / Logout / CSRF Scenarios (`§§3.1–3.4`):**
  - *Scenario A (Logout then stale cookie):* Tab 1 holds `AUTH=G1`, Tab 2 logs out (`204`, no `Set-Cookie` clear). Tab 1 sends `PATCH /users/me` -> server checks `AUTH` before `CSRF` -> `401 AUTH_REQUIRED` -> Tab 1 clears UI state.
  - *Scenario B (Logout then fast re-login):* Tab 2 logs out (`G1` revoked, no cookie clear), then logs in (`G2`, `Set-Cookie: AUTH=G2`). Because logout sent no late `Set-Cookie` clear, a slow logout response cannot delete `AUTH=G2` in the browser jar.
  - *Scenario C (Concurrent parallel `PREAUTH` CSRF bootstrap):* Browser receives `PREAUTH=P2` last while JS saves `T1` -> `POST /auth/register` gets `403 CSRF_INVALID` -> client discards `T1`, calls `GET /auth/csrf?scope=PREAUTH` (reuses `P2`, returns `T2`), user deliberately resubmits.
  - *Scenario D (Two concurrent logins):* Tab 1 and Tab 2 race `POST /auth/login`. `(2) users -> (3) stripe` serializes replacement; `G2` replaces `G1`. Even if browser delivers `Set-Cookie: AUTH=G1` after `G2`, `G1 != G2` fails immediately with `401`, triggering bounded reconciliation without replaying writes.
  - *Scenario E (Process crash/restart & `PREAUTH` exhaustion):* Stop-before-start guarantees no split-brain `AUTH` bindings; crash clears all sessions (`§3.1`). However, an unauthenticated flood of `GET /auth/csrf?scope=PREAUTH` (`V13-N03`) can crash the sole process due to missing global `PREAUTH` capacity bounds.

### 6.3 Admin Governance and Recovery
- **Bootstrap & Recovery (`§§4, 4.1`):** Pre-seeded `admin_governance(id=1)` eliminates the absent-row bootstrap race. Inserting `BOOTSTRAP_SUCCESS` audit against `admin_governance(1)` before updating `admin_governance.bootstrap_event_id` cleanly avoids cyclic-insert deadlock. `recovery_approvals` pins exact `target_user_id`, `expected_security_revision`, `action`, `replacement_email_canonical`, distinct `approver_principal_id` / `designated_operator_principal_id`, and single-use consumption.
- **Residual Gaps (`V13-N02`, `V13-N04`):** `identity_lifecycle.mutate` lacks `ADMIN`-promotion and `PENDING`-credential guards; `recovery_approvals.cancelled_at` lacks a callable cancellation routine; `audit_append` relies on an adapter-supplied actor for denials instead of DB `session_user`.

### 6.4 Database Security and Integrity (All 43 FKs)
- **43 Foreign Keys Verified (`§§5.1, 5.5`):** All **16 composite FKs** (referencing declared PK or nonpartial `UNIQUE` tuples, with documented nullability on `passages.current_version_id`, `exercises.current_version_id`, `marking.correct_option_id`, `reading_activity.last_paragraph_id`, and `vocabulary_sources.paragraph_id` paired with required `passage_version_id` FK), **25 standalone FKs**, and **2 governance/audit FKs** (`admin_governance.bootstrap_event_id -> admin_audit.id`, `admin_audit.recovery_approval_id -> recovery_approvals.id`) are logically sound and consistently enforce `ON UPDATE RESTRICT` and `ON DELETE RESTRICT`.
- **Privilege Boundary Gap (`V13-N01`, `V13-N02`, `V13-N04`):** The `§5.4` routine model correctly adopts non-login owners, `PUBLIC` revocations, fixed search paths, and single-connection outer-transaction coupling, but requires the missing account-lock routines, `identity_lifecycle` restrictions, and cleanup/cancellation routines to be enforceable in PostgreSQL.

### 6.5 Publication and Content Integrity
- **Freeze, Snapshots & Withdrawal (`§§5.3, 5.6, 8`):** Locking `(5) passages -> (6) exercises` in `READ COMMITTED` and issuing a new statement post-lock ensures fresh starts never pin an about-to-be-withdrawn edition. Parent passage republish (`PV2`) hides stale `EV1` (which pins `PV1`) from public discovery until coordinated republish, while existing in-progress attempts on `EV1` continue reading `PV1` coherently. Public reads use single-statement or repeatable-read snapshots without write-order locks.

### 6.6 Assessment and Deterministic Grading
- **Disjoint Commands & Grading (`§§7.1–7.4, 9.1–9.4`):** Separating `/answers/{qid}` from `/marks/{qid}` prevents accidental score locks during review flagging. `GR-1` (Unicode 15.1.0, NFC -> White_Space collapse/trim -> non-Turkic default case fold -> NFC) and RFC 8785 512 KiB graph budgeting are deterministic and preserve historical grading across edition changes. `clock_timestamp()` after attempt-row lock (`§9.4`) prevents pre-wait transaction-start clock bypass.

### 6.7 Expiry Worker and Performance Limits
- **Budgets & Mutual Consistency (`§9.5`):** 10 admissions/deadline minute vs. 15 fresh claims per 30s cycle (30/min) gives 3x steady-state headroom; 20 claims × 1s claim deadline fits inside the 25s cycle cap (with <1s remaining cutoff) before the 30s tick. However, `V13-N05` (>100 retry scan starvation) and `V13-N06` (absent `deadline_capacity` row race) must be resolved before M4/benchmarking.

---

## 7. Product Owner Decisions

### Verification of Approved Baseline (`D01`–`D11`)

Comparison of [DECISION-REGISTER.md](file:///d:/BASE-IELTS/docs/DECISION-REGISTER.md) (lines 5–17) against [IELTS_MILESTONE_0_FINAL.md](file:///d:/BASE-IELTS/docs/IELTS_MILESTONE_0_FINAL.md) (§2, lines 72–113) confirms that all eleven approved decisions **`D01`–`D11` match verbatim** with no silent reinterpretation or modification.

### Analysis of Open Decision Bundles (`V12-D01`–`V12-D03`)

All twelve technical items (`TECH-01`–`TECH-12`) remain explicitly marked **OPEN OWNER DECISION**. No deferral or approval is recorded.

| Item | Undecided Scope (`TECH` IDs) | Security / Operational Impact | Dependent Milestones | Can Functionality Remain Safely Disabled? | Exact Decision Required from Product Owner |
| --- | --- | --- | --- | --- | --- |
| **`V12-D01`** | `TECH-01`, `TECH-02`, `TECH-12` (Java/Spring/PG/Next versions, same-origin ingress, single-process stop-before-start fencing, password/CSRF/role/retention policy) | Wrong Spring Security / cookie / deployment topology breaks single-session (`D07`), CSRF, or restart-logout guarantees; unverified retention blocks schema persistence. | **M1** (before authorization & initialization) | **No** for M1 runtime: serving must remain off until exclusive single-process cutover and toolchain compatibility are proven. | Approve exact compatible runtime versions, same-origin deployment topology with tested stop-before-start exclusion, scoped Spring repositories, password/hash parameters, CSRF bounds, and identity/audit retention policy. |
| **`V12-D02`** | `TECH-03`, `TECH-04`, `TECH-05`, `TECH-12` (M1 dev/test vs. real mail mode, admission/timing/throttle/cleanup thresholds, operator/approver identities, mailbox attestation evidence, audit destination, recovery runbook) | Without `TECH-03/04`, public registration risks account enumeration or non-functional mail; without `TECH-05`, Admin bootstrap/recovery lacks verified human separation and mailbox proof. | **M1** (foundation mode & `TECH-04/05/12` before M1 work); **Production** (real mail & operator enablement separately gated) | **Yes** for production registration/resend (`503 SERVICE_UNAVAILABLE`) and production operator operations, provided M1 uses approved dev/test fixtures only. | Choose M1 mail mode (dev/test-only vs. provider-backed), approve `TECH-04` bounds (30m TTL, 60s cooldown, 5/hr email, 20/hr source, 16 conc / 20/s, 150ms DB / 300ms floor), approve `OPERATOR_ATTESTED` evidence flow, named operator/approver principals, secret handoff, and audit retention; explicitly defer production mail/operator rollout if desired. |
| **`V12-D03`** | `TECH-06`–`TECH-11` (Sample formula, Learning score-lock semantics, `TECH-08` freeze/write boundary & withdrawal/takedown, `GR-1` & `§9.5` capacity/worker budgets, authoring grants, deferred features & broader deletion) | Implementing M2–M4 without `TECH-08` risks mutable published content; without `TECH-06/07/09/10`, sample disclosure, grading clocks, worker capacity, and restricted access remain unauthorized assumptions. | **Architecture sign-off** (`TECH-08` design); **M2–M4** (`TECH-06/07/09/10/11/12` before dependent release) | **Yes**: Reading, authoring, samples, grading, expiry worker, and deferred features (dictionary/AI/media/Listening) can stay disabled in M1 (`§11.1`). | Approve `TECH-08` immutable-graph/write-boundary design at Milestone 0 sign-off, and explicitly approve or defer `TECH-06`, `TECH-07`, `TECH-09`, `TECH-10`, `TECH-11`, and non-identity `TECH-12` items to their M2–M4 gates with affected capabilities disabled. |

---

## 8. Milestone Authorization Assessment

### M1 Scope Isolation Check

- **Identity-only scope confirmed:** `§11.1` limits M1 strictly to identity migrations (`users`, `roles`, `user_roles`, `verification_tokens`, `admin_governance`, `recovery_approvals`, `admin_audit`), internal/test identity lifecycle hooks (with no HTTP route), operator bootstrap/recovery CLI/runner, `PREAUTH`/`AUTH` session/CSRF, identity API (`/auth/*`, `/users/me`), minimal auth UI, and health/error handling.
- **No future feature leakage:** Public Reading, exercises, draft/publication, samples, attempts, grading, expiry worker (`deadline_capacity`), vocabulary, and progress tables/endpoints are explicitly excluded from M1 (`§11.1`, `§9.5`). `SE-04` (`M1-01`–`M1-04`, `I13-01`–`I13-07`) relies solely on health/test fixtures in M1, keeping M1 independent of M2–M4.
- **Production readiness gates:** Properly separated from M1 foundation acceptance (`§11.2`: production registration returns `503` without configured mail; production operator enablement requires separate runbook, identities, and recovery drill).

### Separation of Governance States

The v1.3 package accurately separates and does **not** conflate the six governance states:
1. **Documentation remediation:** Completed in v1.3 working-tree package (awaiting fixes for `V13-N01`–`V13-N07`).
2. **Product Owner decisions (`V12-D01`–`V12-D03`):** Currently `OPEN OWNER DECISION`.
3. **Architecture approval (Milestone 0 sign-off):** Currently open; blocked by `V13-N01`–`V13-N06` and `V12-D01`–`V12-D03`.
4. **M1 implementation authorization:** Explicitly withheld (`§11.3`: `"Milestone 0 remains open... Milestone 1 requires separate explicit authorization"`).
5. **M1 acceptance:** Contingent on passing `I13-01`–`I13-07` and `M1-01`–`M1-04` after authorization.
6. **Production readiness:** Separately gated (`§11.2`) beyond M1 acceptance.

---

## 9. Acceptance Test Gap Analysis

[MILESTONE-0-TRACEABILITY.md](file:///d:/BASE-IELTS/docs/MILESTONE-0-TRACEABILITY.md) significantly improves over v1.2, but the following concrete acceptance-test gaps remain:

1. **Account-Lock Routine vs. No-Direct-DML (`V13-N01` / `I13-04`):** `I13-04` requires denying all unlisted `EXECUTE` and direct DML, yet no test verifies how `login` and protected writers (`assessment_writer`, `content_*_writer`) acquire `(2)` DB `users` row locks before `(3)` JVM local stripes on the same connection without deadlocking or holding `UPDATE` privileges on `users`.
2. **`identity_lifecycle` Privilege Escalation & `PENDING` Guards (`V13-N02` / `I13-03`–`I13-04`):** No negative test verifies that `identity_lifecycle.mutate` rejects `GRANT_ROLE(ADMIN)` on `PUBLIC_LEARNER` (and unauthorized `ADMIN` mutations) or rejects `SET_CREDENTIAL` on `PENDING`/`DISABLED` accounts.
3. **Global `PREAUTH` Memory Exhaustion (`V13-N03` / `I13-02`):** `I13-02` tests cookie/CSRF ordering and `I13-05` tests register/resend rate limits, but no test exercises a distributed flood of `GET /auth/csrf?scope=PREAUTH` across distinct source IPs to prove bounded heap usage and `AUTH` session survival.
4. **Approval Cancellation, Token Cleanup & Denial Actor Source (`V13-N04` / `I13-03`–`I13-04`, `I13-07`):** No test covers explicit cancellation of a live `recovery_approval`, post-retention deletion of dead verification tokens (while rejecting live-token deletion), or DB-login actor attribution on `appendDenied`.
5. **>100-Retry Expiry Backlog (`V13-N05` / `G13-02`):** `G13-02`'s overload test uses 60 due fixtures (`< 100` scan ceiling), leaving the >100-retry scan-starvation failure untested.
6. **Concurrent Starts on Unseeded `deadline_capacity` Minute (`V13-N06` / `G13-02`):** `G13-02` reserves 10 slots and rejects the 11th, but does not mandate barrier-synchronized concurrency on an uninitialized `bucket_start_utc` row to catch absent-row `unique_violation` races.
7. **`POST /attempts` Replay Precedence Over `404`/`429` (`V13-N07` / `P13-02`, `AT-01`):** No explicit test sends a conflicting `(withdrawn_exercise, EXAM, existing_key)` expecting `409 IDEMPOTENCY_CONFLICT` (rather than `404`), nor a matching replay when `deadline_capacity` is full expecting `200` (rather than `429`).

---

## 10. Final Recommendation

**NOT READY FOR PRODUCT OWNER SIGN-OFF**

### Evidence-Based Rationale

1. **Unresolved M1 Database Privilege & Locking Contradiction (`V13-N01`, `V12-H04`):** As documented, `§5.4` prohibits direct DML on identity tables and grants no account-lock/login-recheck routines to `identity_api`, `content_*_writer`, or `assessment_writer`, making `§§3.2, 3.4, 5.3`'s `(2) DB account row -> (3) JVM local stripe` ordering impossible to satisfy under `I13-04` without either cross-tier deadlocks or unauthorized `UPDATE` grants.
2. **Unguarded Admin Escalation & `PENDING` Credential Path (`V13-N02`, `V12-H03`):** `identity_lifecycle.mutate` (`§5.4`) omits database-enforced prohibitions against `GRANT_ROLE(ADMIN)` on `PUBLIC_LEARNER` accounts and `SET_CREDENTIAL` on `PENDING` accounts, undermining `D08`, `D11`, and `§4`.
3. **Additional Medium/Low Design Defects (`V13-N03`–`V13-N07`, `V12-H06`):** Unbounded `PREAUTH` in-memory capacity, missing approval-cancellation/token-cleanup routines and denial actor attribution, >100-retry expiry scan starvation, absent `deadline_capacity` row locking under `READ COMMITTED`, and `§6.1` replay precedence must be corrected in the architecture contract.
4. **Open Owner Decisions (`V12-D01`–`V12-D03`):** All `TECH-01`–`TECH-12` choices remain open and require explicit owner resolution/deferral alongside the targeted contract fixes above before Product Owner sign-off and M1 authorization.

---

## 11. Repository Integrity

| Check | Result |
| --- | --- |
| **Git branch before review** | `main` (`HEAD bb205ad`) |
| **Git branch after review** | `main` (`HEAD bb205ad`) |
| **Pre-existing untracked files (`git status`)** | 16 untracked files in `docs/` (`ADR-REGISTER.md`, `DECISION-REGISTER.md`, `DOCUMENT-AUTHORITY.md`, `IELTS_MILESTONE_0_FINAL.md`, `IELTS_MILESTONE_0_REMEDIATION_PLAN.md`, `MILESTONE-0-DECISIONS-AND-PLAN.md`, `MILESTONE-0-DESIGN-PROPOSAL.md`, `MILESTONE-0-FINAL-REVIEW.md`, `MILESTONE-0-FINAL-v1.1.md`, `MILESTONE-0-FINAL-v1.2.md`, `MILESTONE-0-FINAL-v1.3.md`, `MILESTONE-0-REVIEW-RESPONSE.md`, `MILESTONE-0-REVIEW.md`, `MILESTONE-0-TRACEABILITY.md`, `MILESTONE-0-v1.1-INDEPENDENT-REVIEW.md`, `MILESTONE-0-v1.2-INDEPENDENT-REVIEW.md`); all preserved byte-for-byte unchanged |
| **Files created** | `docs/MILESTONE-0-INDEPENDENT-REVIEW-v1.3.md` (this report only) |
| **Files modified** | None |
| **Files staged** | None |
| **Commits created** | None |
