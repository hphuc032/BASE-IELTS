# Milestone 0 closure and freeze preparation

**Current verdict: READY WITH EXPLICIT LATER-MILESTONE GATES**

The v1.6 candidate specifies the three targeted M1 corrections. The v1.5 assessment below is retained as historical evidence. v1.4 remains standing. No approval, promotion or implementation authorization is implied.

| Gate | Current static contract | Runtime evidence after authorization |
| --- | --- | --- |
| G1 | §2.1 requires enum status NOT NULL in addition to the existing CHECK. | R16-01 uses actual PostgreSQL. |
| G2–G4 | §5.4.2 narrows denial EXECUTE to mapped principals, binds session_user and serializes issuer cancellation, disablement and recovery. | R16-02/03 verify negative grants, actual actors, rollback and races. |
| G5 | §5.4.2 bounds retention and batch, uses terminal timestamps and rechecks after user-before-token locks. | R16-04 proves no live or premature deletion, including races. |
| G6–G7 | §3.5 single ledger atomically limits all entries; caps admission, metadata and cleanup, reserves AUTH and existing PREAUTH login capacity. No design assertion of absolute shared-JVM availability. | R16-05/06 prove caps, exception handling, auth continuity and response targets. |
| G8 | Existing native account row before JVM stripe unchanged. PREAUTH mutex is released before DB/AUTH operations. | R15-01 and related fences/dispatch tests. |
| G9–G10 | D01–D11, historical digests, ADR IDs and links verified statically. | No runtime implication. |
| G11 | L-01 is an OPEN M4 IMPLEMENTATION GATE. Worker inactive pending TECH-09 and consistent partition discovery, claims, locks, per-attempt transactions, indexes and budgets. | M4 specification and proof only, proposed deferral requires owner. |
| G12–G13 | All owner choices remain open. M1 foundation is identity-only; production enablement is separate. | Owner gate and later acceptance distinct. |

A1/C-01, A2/C-02 and A3/C-03 are resolved in proposed design. No additional concrete M1 design blocker was identified in this targeted scope. Remaining prerequisites are explicit owner acceptance of technical choices and evidence disposition, an accepted pinned authority transition and separate implementation authorization. Runtime tests remain unexecuted. The absent v1.4 chat export and three historical digest mismatches below remain unresolved evidence limitations. [v1.6 candidate](MILESTONE-0-FINAL-v1.6.md), [v1.6 manifest](MILESTONE-0-v1.6-MANIFEST.md), [owner checklist](MILESTONE-0-PRODUCT-OWNER-SIGNOFF.md)

## Preserved v1.5 closure assessment · historical

**Verdict: NOT READY — M1 OR ARCHITECTURE BLOCKERS REMAIN**

The v1.5 fence, ADR crosswalk and traceability address the three supplied R14 findings at design level. The freeze is blocked by three narrow M1 contract gaps: SQL NULL account status, operator denial-audit authority and cleanup/cancellation semantics, and bounded PREAUTH admission. Assessment has a separate M4 transaction issue. No new architecture version is needed to prepare owner decisions. Keep the candidate bytes intact and have the owner accept precise corrections in a controlled decision record before promoting a resolved contract. No implementation or PostgreSQL acceptance tests ran.

## Scope, inputs and authority

Branch main, HEAD bb205adde745faeca3805ef89cdd107ecb3e7d34. Standing technical proposal remains [v1.4](MILESTONE-0-FINAL-v1.4.md). [v1.5](MILESTONE-0-FINAL-v1.5.md) is review-only. D01–D11 remain approved and unchanged. The original [PRD](IELTS_Learning_Platform_PRD_Agent_Guide.md) governs unaffected product requirements. The scope is documentation verification, not promotion.

Documents reviewed or compared for the applicable sections: [Decision Register](DECISION-REGISTER.md), [ADR Register](ADR-REGISTER.md), [Document Authority](DOCUMENT-AUTHORITY.md), [Traceability](MILESTONE-0-TRACEABILITY.md), [Review Response](MILESTONE-0-REVIEW-RESPONSE.md), [v1.3 independent review](MILESTONE-0-INDEPENDENT-REVIEW-v1.3.md), [v1.3 architecture](MILESTONE-0-FINAL-v1.3.md), [v1.2 architecture](MILESTONE-0-FINAL-v1.2.md), [v1.1 architecture](MILESTONE-0-FINAL-v1.1.md), [v1.0 baseline](IELTS_MILESTONE_0_FINAL.md), [original architecture pack](IELTS_Milestone_0_ERD_API_Architecture_Pack.md), [v0.2 pack](IELTS_Milestone_0_ERD_API_Architecture_Pack_v0.2.md), [v1.1 review](MILESTONE-0-v1.1-INDEPENDENT-REVIEW.md), [v1.2 review](MILESTONE-0-v1.2-INDEPENDENT-REVIEW.md), and [historical final review](MILESTONE-0-FINAL-REVIEW.md). The owner-supplied v1.4 findings, not a reconstructed original transcript, establish the R14 scope.

## R14 dispositions

| Finding | Result | Evidence and gate |
| --- | --- | --- |
| R14-01 · fencing | Addressed in candidate design, version-pinned DB proof pending authorized M1 | v1.5 §§3.2–3.4, 5.3 and 5.4.1, M0-ADR-16, R15-01. See analysis below. |
| R14-02 · ADR references | Clarified external snapshot and verified current registry | The available v1.4 intake register had ADR-16 in summary form. The earlier reviewer reported a 01–15 snapshot. Historical absence is unproven. Current 01–16 are unique and resolve; 16 is proposed account fencing, 03/08 map PREAUTH, 07 maps expiry partitioning. Historical identifier crosswalk remains in place. No technical ADR approved. |
| R14-03 · test authority | Addressed in documentation | Traceability A–F separate preserved history, standing v1.4 tests, blocked I14-01 and candidate-only R15-01–03. Future promotion would make R15-01 the I14-01 extension, R15-02 the ADR check and R15-03 the authority check. C/D remain the other standing acceptance requirements until an approved change. Later and production tests stay at their own gates. |

### PostgreSQL grants and invariants

The candidate defines fence_guard as boolean NOT NULL DEFAULT false with a false-only CHECK. NOT NULL prevents the SQL UNKNOWN bypass on that column, true violates the CHECK, and an omitted value receives false. The column has no index, FK, authorization meaning or trigger side effects in the contract. A same-value UPDATE could still execute DML under the owner grant. The guarantee rests on fixed non-mutating function bodies and protected ownership, not an assertion that the owner literally has no UPDATE. Inspect actual trigger catalog and grants in M1. PostgreSQL row-locking SELECT requires UPDATE on a column in addition to SELECT, and the proposed row lock conflicts with credential/revision writes to that row. [PostgreSQL 17 SELECT](https://www.postgresql.org/docs/17/sql-select.html), [PostgreSQL 17 locking](https://www.postgresql.org/docs/17/explicit-locking.html), [PostgreSQL 17 constraints](https://www.postgresql.org/docs/17/ddl-constraints.html)

The non-login fence owner has column SELECT on id, password_hash, status and security_revision and column UPDATE only on fence_guard. It owns functions, while the controlled migration owner owns the users table and schemas. It gets no mutation privilege on credentials, roles, status, revisions, verification or account kind. Runtime and operator logins lack direct users UPDATE, cannot inherit or assume the owners, and cannot create in trusted schemas. The exact definer signatures revoke PUBLIC EXECUTE, use fixed pg_catalog, pg_temp-last name resolution plus schema-qualified objects, and disallow dynamic SQL, callbacks and unlisted grants. identity_api may invoke both login snapshot lock and safe actor check. Author/publisher/assessment writers only obtain actor-check boolean; the operator, approver, lifecycle and cleanup routes use their typed mutation functions taking the same native row, without invoking credential-returning functions. M1 excludes later role grants. The fixed definitions and privilege/role metadata must be inspected on the real M1 engine. [PostgreSQL SECURITY DEFINER guidance](https://www.postgresql.org/docs/17/sql-createfunction.html)

### Concurrency and test sufficiency

| Scenario | Candidate check and required M1 observation |
| --- | --- |
| Login vs password reset, disablement, role revocation or approved Admin recovery | Login captures the credential tuple, verifies outside the lock, then locks that UUID in the primary outer transaction and checks the tuple under the local stripe. A prior durable mutation changes revision or eligibility, and stale activation fails. If login wins, later security change invalidates that generation at the request gate. R15-01 covers reset/disable/recovery; I13-01 covers role change. |
| Profile update or protected read vs revocation | Scope is rechecked under the same row then local stripe, writes hold through commit, fenced reads materialize safe projection before release. A read admitted before a revoke can complete with its already-materialized result even if network delivery happens later. Add a barrier case for profile/read before and after revocation; no request newly gated afterward may see private data. |
| Logout vs replacement login and multiple tabs | Both obtain row before stripe; exact generation revocation and no AUTH cookie clear protect the newer server binding. Delayed browser responses may still produce stale-cookie recovery. Require one usable generation, bounded explicit re-login and no automatic replay of mutation. Run S12-03/I13-02 alongside R15-01. |
| Rollback, uncertain commit, known commit with lost HTTP response | Rollback releases native lock and discards provisional authority. Uncertain login commit invalidates affected bindings with no cookie issued; for a generic protected write outcome reconcile before deliberate retry. Known commit retains the durable result and current binding only. Include fault barriers for pre-commit rollback, unknown completion and known completion before response. Use S12-01/02 as well as R15-01. |
| Lock order | Governance when needed → UUID-ordered DB account rows → ordered local stripes → downstream locks. Local-only expiry cleanup does not acquire the database while holding a stripe. Same connection, no new earlier-tier acquire once stripe taken. Use bounded waits, observe blocked transactions and confirm no AB-BA cycle in named races. Static inspection does not establish deadlock freedom for a future implementation. |

R15-01 is sufficient for its stated fence fixture when supplemented by these existing cases and explicit profile/read/uncertain-commit assertions. All are later M1 acceptance, not prerequisites for starting a correctly authorized implementation.

## Targeted regression findings and minimum proposed corrections

| ID / impact | Verified issue and smallest change for consideration | Future proof |
| --- | --- | --- |
| C-01 · M1 blocker | v1.5 §2.1 names status values but does not expressly require users.status NOT NULL. SQL CHECK accepts UNKNOWN, so NULL can pass the credential-state disjunction. Preserve D08/D11 and the existing ADMIN/credential rules; explicitly require the status enum column to be NOT NULL in the accepted identity contract. Generic lifecycle gates already restrict ADMIN to dedicated governance and reject credential changes on PENDING, DISABLED or DEDICATED_OPERATOR. Those rules must remain. | I14-02 supplemental: reject NULL status and every invalid credential/verification tuple, while valid state transitions succeed. Confirm constraints and routine grants on PostgreSQL. |
| C-02 · M1 governance blocker | v1.5 §§4.1/5.4 define appendDenied under an audit_append caller row and infer actor from session_user, but omit explicit operator/approver EXECUTE on that exact routine. Using the generic login would lose attribution. Cancellation locks governance → target → approval but does not require the issuer or separately granted revoker. Cleanup bounds cutoff and protects live tokens, yet does not say which per-token timestamp determines age. Accept precise grants allowing the applicable mapped principal logins to execute only the sanitized denial append, no direct audit DML. Require issuer or explicitly scoped independent revoker under lock, and use the recorded consumed_at or invalidated_at, else expiration, to prove each terminal row is past cutoff. | I14-04 supplemental: distinct actual operator/approver logins append sanitized denial under their own identity, unrelated login denied. Cancel/recover race yields one terminal state plus correct audit. Reject unrelated approver, live and recently terminal tokens, even when issued long ago. |
| C-03 · M1 security design blocker | §§3.1/12 specify 2,000 entries and 15 minutes, while eviction versus 429, atomic allocation, per-source/global issue settings and metadata/cleanup bounds remain unresolved. Shared JVM prevents an unconditional no-starvation guarantee. Choose fail-closed 429 at atomic cap, a fixed ingress/issuance budget and bounded source-map/cleanup state with TTL and no queue growth. Isolate AUTH storage from PREAUTH eviction and reserve/test separate execution capacity. | I14-03 supplemental: many simultaneous requests at cap, churn unique sources and stalled cleanup. Prove exact max, metadata/queue bounds, no AUTH eviction and measurable AUTH service under approved admitted load. Production verifies capacity separately. |
| L-01 · M4 blocker only | §9.5 has two LIMIT 50 FOR UPDATE SKIP LOCKED scans per cycle while claiming at most 15/5 and grading one attempt per transaction. Top-level locking selects lock returned rows until that transaction ends. A scanning transaction therefore either holds extra locks or releases any alleged claims before grading. LIMIT bounds returned rows, not physical index work with skipped locks or interleaved failure classes. Separate bounded unlocked candidate discovery per class from per-attempt claim/recheck/grade on one connection and transaction. Use partial class-specific indexes or benchmark equivalent bounded paths, with stable age order and scan/time budgets. | I14-05/G13-02 supplemental: inspect lock snapshots, prove only a claimed attempt is locked by its grading transaction, release on rollback, and test a large retry backlog plus fresh traffic using query plans and accepted lag budgets. No M1 worker or table. |

Recovery cancellation and execution already serialize on governance, target and approval in that order, with one-use checks and operator/approver separation. The missing cases above concern actual cancellation scope and denial attribution. No broader redesign is proposed. Treat the three M1 corrections as design decisions to accept before promotion. Runtime proof belongs in authorized M1 acceptance. Production-only measurement includes email timing/delivery, ingress resource headroom, operator readiness and real recovery drills. [PostgreSQL row-lock release and SKIP LOCKED](https://www.postgresql.org/docs/17/sql-select.html)

## Historical integrity investigation

Expected values below come from the **v1.4 working-tree snapshot** in the historical section of Review Response. Observed values come from the **v1.5 intake** recorded separately in that same document. Both describe raw SHA-256; the current candidate manifest is a third snapshot.

| Mutable companion | Expected SHA-256 | Observed at v1.5 intake SHA-256 | Evidence disposition |
| --- | --- | --- | --- |
| DECISION-REGISTER.md | B2A64F2055E5733AC849CE0BEC5E33AA4D9120655E4B2FE7E3F1ED313BB64F14 | 0A70E9FEFA9E69619B67E4FC8C8014F6A80F64B90D15DE14349451863D8235F1 | Difference predates v1.5 work. Expected matching copy unavailable. Exact cause and the old bytes remain unresolved. |
| DOCUMENT-AUTHORITY.md | F56D9406E2E444A18F5B28F3DD5024132F9F2F9051F38AFAB676BD6ADE87FA77 | B754692AAD6C947DA765003F3EFA671580F4B179D6BC4546FE8B042FC1F33C71 | Difference predates v1.5 work. Expected matching copy unavailable. Exact cause remains unresolved. |
| MILESTONE-0-TRACEABILITY.md | 00EDAD6FE0513F5F93AAE82F5DC9196BCE1791DE7D52488446D27AD8A2F36A47 | 2F337394D39B462B304C267F9F7228C8D31256E89632DE86730224030E093BE6 | Difference predates v1.5 work. Historical quoted trace reconstructs to the observed intake bytes, while the expected matching copy is unavailable. |

All three were mutable working-tree documents and are absent from available Git history. Different snapshots or serialization are plausible. No matching expected copies or evidence establishing tampering, a particular edit, or byte normalization as the cause were found. E-02 asks the owner to accept the limitation or obtain original copies. The missing standalone review is separately covered by E-01. No historical expected digest was overwritten.

## Decision and freeze checklist

| Gate | Status |
| --- | --- |
| D01–D11 | Verified against v1.0 §2, exact wording aside from table layout whitespace. |
| R14 and V13 dispositions | R14-01 design addressed, R14-02 clarified, R14-03 separated. V13-N01 remains design-remediated with runtime fencing proof pending; N02 retains guards plus C-01, N03 retains proposed cap plus C-03, N04 retains governance semantics plus C-02, N05 retains partition intent plus L-01, N06/N07 remain proposed M4 behavior. |
| Evidence and authority | Existing history unchanged. Original transcript unavailable and three mutable snapshots remain discrepant. v1.4 standing, v1.5 candidate only. |
| Owner choices | TECH-01–05 and identity TECH-12 presented; TECH-08 invariant acceptance at architecture sign-off. TECH-06/07/09/10/11 and non-identity TECH-12 proposed for explicit later gates with disabled dependents. No acceptance recorded. |
| M1 scope | Identity-only and independently testable with controlled mail and public health fixture. C-01–03 prevent recommending freeze or authorization as written. |
| Later/production | L-01 M4 only. Public email and real administration remain disabled pending independent production readiness. No runtime test passed claim. |

**Owner actions:** decide E-01 and E-02, accept or request changes to C-01–03 and decide M1 technical choices, accept TECH-08 invariants and record safe later deferrals including L-01, then consider explicit E-03 promotion of the resolved contract. Record separate M1 authorization only after those gates. No automatic approval or further architecture version.

## Git integrity

Branch and HEAD verified before and after. Existing files and historical hashes are unchanged. The only workspace changes are the five new closure Markdown files, untracked on main. No staging, commits, push, branch switch, source, SQL, database changes, dependencies or implementation tests. [Manifest](MILESTONE-0-v1.5-MANIFEST.md), [Provenance](MILESTONE-0-v1.4-REVIEW-PROVENANCE.md), [Owner decisions](MILESTONE-0-PRODUCT-OWNER-SIGNOFF.md), [M1 proposal](MILESTONE-1-IMPLEMENTATION-PLAN.md)
