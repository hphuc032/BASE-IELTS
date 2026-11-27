# v1.3 targeted remediation response

**Status** Design remediation specified for independent re-review. Milestone 0 remains open. No design choice below is an owner approval or an implementation test result. See the [original v1.2 review](MILESTONE-0-v1.2-INDEPENDENT-REVIEW.md), [v1.3 architecture](MILESTONE-0-FINAL-v1.3.md) and [traceability matrix](MILESTONE-0-TRACEABILITY.md).

| Finding | Disposition | Design response and remaining dependency |
| --- | --- | --- |
| V12-H01 | REMEDIATED IN DESIGN | §3.4 defines one captured hash/revision tuple and recheck under the DB fence. M1, TECH-02/05/12 pending. |
| V12-H02 | REMEDIATED IN DESIGN | §§3.3–3.4 and 6.1 define explicit AUTH/PREAUTH namespaces, filter ordering, cookie writer and dispatch coverage. Exact version and integration proof pending TECH-01/02/12. |
| V12-H03 | REMEDIATED IN DESIGN | §4.1 defines immutable target/action/revision and exact replacement payload, distinct actors, consumption and audit. M1, TECH-05/12 pending. |
| V12-H04 | REMEDIATED IN DESIGN | §5.4 defines typed logical operations, caller and owner boundaries, trusted resolution and caller transaction coupling. M1 identity and later M2/M4 content. TECH-05/08/10/12 pending. |
| V12-H05 | REMEDIATED IN DESIGN | §9.4 defines primary database wall-clock time sampled only after acquisition and exact cutoff. M4, TECH-09 pending. |
| V12-H06 | REMEDIATED IN DESIGN | §9.5 defines deadline-window admission, single-flight claim budget, capacity measurements and crash/backoff/park behavior. M4, TECH-09 and benchmark pending. |
| V12-M01 | REMEDIATED IN DESIGN | §2.3 defines bounded work, account-neutral responses, timer tolerances and recovery. M1 controlled-clock fixtures; production timing gate under TECH-03/04/12. |
| V12-M02 | REMEDIATED IN DESIGN | §5.6 defines post-lock READ COMMITTED re-read, atomic pin and scoped replay. M2/M4, TECH-08/09 pending. |
| V12-M03 | REMEDIATED IN DESIGN | §§3.4, 6.1, 8, 9.3 define capability, ownership, reveal and error priority. M1 identity and M4 attempts, dependent choices pending. |
| V12-M04 | REMEDIATED IN DESIGN | §7.4 defines Unicode GR-1, normalized blank, raw equality, literal fixtures and byte accounting. M4, TECH-09 pending. |
| V12-M05 | REMEDIATED IN DESIGN | §§5.5–5.6 define restrictive FK lifecycle, native approval/audit links and derived canonical start identity. M1 identity, then M2/M4. TECH-08/12 pending. |
| V12-M06 | REMEDIATED IN DESIGN | Current traceability provides barriers, expected statuses and measurable outcomes. M1 cases are independent of content. Later tests remain subject to approved contracts. |
| V12-D01 | OPEN OWNER DECISION | Verified toolchain, secure same-origin deployment, exclusive process and scoped Spring security/retention. |
| V12-D02 | OPEN OWNER DECISION | Verification delivery mode/limits, approval identities and mailbox evidence, operational audit and retention. Production readiness remains separate. |
| V12-D03 | OPEN OWNER DECISION | Version graph and write boundary at sign-off. Record explicit owner approval or deferral with affected features disabled for later choices. |
| V12-O01 | OPTIONAL EVIDENCE INCLUDED | Previous inputs and v1.3 companion outputs are pinned by digest. Full external manifest or commit remains optional. |

## Additional edge cases addressed

- A local commit can cross a database deadline minute while reserving Exam capacity. §9.5 recomputes under the bucket lock and rolls back the entire start if the window changed. The same idempotency key can retry.
- An authenticated cookie can outlive server logout. Private writes deny stale AUTH before CSRF. PREAUTH operations use their own cookie scope, and logout sends no late clear that can delete a newer cookie.
- A bootstrap marker must link to durable success audit without a cyclic-insert failure. §5.5 inserts audit against the preexisting singleton, then updates the marker under the same transaction.

Before M1 authorization, resolve TECH-01–05 and TECH-12 identity settings, independent architecture re-review and owner sign-off. Production mail and operator rollout are separately gated. Later preview, content privileges and withdrawal, grading, expiry and provider choices can be deferred only with an explicit owner decision and disabled dependent capability. No approval or accepted deferral is recorded by this response.

Created the v1.3 pack and updated five current registers/traceability/response documents. Historical reviews and prior packs were kept unchanged. No implementation, SQL migration, framework initialization, dependency installation, or test execution occurred. Milestone 0 remains open and M1 remains unauthorized.

## v1.3 working-tree snapshot

SHA-256 over raw companion bytes. This response omits its own digest. The working-tree bundle is not part of HEAD.

| File | SHA-256 |
| --- | --- |
| MILESTONE-0-FINAL-v1.3.md | A321708932D0A96D41439A911F0D1ADEBE453DB0E93E2E4A35A2DB02B1E3776D |
| DECISION-REGISTER.md | B64EF97CC1642A4BD2384EF3E17F6C6E60ADBB6262A10657C54F0D57F4F73FBB |
| ADR-REGISTER.md | 5530F37AF1D399448F881FCBDF6F02391905D0929E98F20F43A1375788157660 |
| DOCUMENT-AUTHORITY.md | 816E77DEA4F0DD9BF7277DEAF6F363C574006A140CA4C76AA69704358D29335F |
| MILESTONE-0-TRACEABILITY.md | DC3600EB834E501E510608399F375A60033106A8634A61570A7032D6242238A2 |

## v1.4 remediation response (V13-N01–V13-N07)

**Status** Remediation complete for independent re-review. Milestone 0 remains open and Milestone 1 remains unauthorized. See the [v1.3 independent review](MILESTONE-0-INDEPENDENT-REVIEW-v1.3.md), [v1.4 architecture](MILESTONE-0-FINAL-v1.4.md) and [traceability matrix](MILESTONE-0-TRACEABILITY.md).

| Finding | Disposition | Design response and remaining dependency |
| --- | --- | --- |
| V13-N01 | REMEDIATED IN DESIGN | §§3.2, 3.4, 5.3, 5.4 add non-mutating `SECURITY DEFINER` `account_fence_routines` (`lockForLogin`, `lockAndCheckActor`) owned by `account_fence_owner`, called on the single outer connection before §5.3 level-(3) JVM stripe acquisition without direct `UPDATE` on `users`. M1 & M2/M4 · TECH-05/08/10/12 pending. |
| V13-N02 | REMEDIATED IN DESIGN | §§2.1, 4, 5.4 add explicit rejections in `identity_lifecycle.mutate` (`GRANT_ROLE(ADMIN)` on `PUBLIC_LEARNER`; `SET_CREDENTIAL` on `PENDING`/`DISABLED`/`DEDICATED_OPERATOR`) plus DB `CHECK` constraint on `users` enforcing matching nullness of credential/verification fields. M1 · TECH-05/12 pending. |
| V13-N03 | REMEDIATED IN DESIGN | §§3.1, 12 add a fixed global `PREAUTH` CSRF capacity bound (proposed 2,000 entries under TECH-04, OPEN OWNER DECISION) with deterministic oldest-entry eviction or `429 ADMISSION_LIMIT`, strict isolation from `AUTH` sessions, and bounded-cardinality metrics. M1 · TECH-02/04 pending. |
| V13-N04 | REMEDIATED IN DESIGN | §§4.1, 5.3, 5.4, 5.5 add `operator_approver.cancelApproval` and `maintenance_cleanup.cleanupExpiredTokens` with §5.3 lock ordering and live-token protection, and bind `audit_append.appendDenied` actor attribution to PostgreSQL `session_user`. M1 · TECH-04/05/12 pending. |
| V13-N05 | REMEDIATED IN DESIGN | §9.5 replaces the single expiry scan with a partitioned dual scan (`LIMIT 50` fresh `failure_count = 0` claiming up to 15; `LIMIT 50` retry `failure_count > 0` claiming up to 5) so retry backlogs cannot starve fresh due attempts. M4 · TECH-09 pending. |
| V13-N06 | REMEDIATED IN DESIGN | §§5.6, 9.5 add idempotent `INSERT INTO deadline_capacity ... ON CONFLICT (bucket_start_utc) DO NOTHING` before `SELECT ... FOR UPDATE` and post-lock `clock_timestamp()` sampling. M4 · TECH-09 pending. |
| V13-N07 | REMEDIATED IN DESIGN | §§6.1, 9.1 add explicit step (5b) in `POST /attempts` error precedence for owner-scoped `Idempotency-Key` replay (`200` / `409 IDEMPOTENCY_CONFLICT`) before resource eligibility (`404`) and capacity admission (`429`). M4 · proposed. |

## v1.4 working-tree snapshot

SHA-256 over raw companion bytes. This response omits its own digest. The working-tree bundle is not part of HEAD.

| File | SHA-256 |
| --- | --- |
| MILESTONE-0-FINAL-v1.4.md | A6FB2B8787C6D21E3431EE5F37B8E20FBE4B0748FFB136D22A1565916130647D |
| DECISION-REGISTER.md | B2A64F2055E5733AC849CE0BEC5E33AA4D9120655E4B2FE7E3F1ED313BB64F14 |
| ADR-REGISTER.md | DD1D5CB1C1EAD95F7E89F623491E66378A1303C8EFE9D0E7435B0B345EB74DE5 |
| DOCUMENT-AUTHORITY.md | F56D9406E2E444A18F5B28F3DD5024132F9F2F9051F38AFAB676BD6ADE87FA77 |
| MILESTONE-0-TRACEABILITY.md | 00EDAD6FE0513F5F93AAE82F5DC9196BCE1791DE7D52488446D27AD8A2F36A47 |

## v1.5 targeted remediation response

**Authority** [v1.4](MILESTONE-0-FINAL-v1.4.md) remains standing. [v1.5](MILESTONE-0-FINAL-v1.5.md) is a candidate only. Milestone 0 remains open. Independent re-review, explicit owner promotion/approved authority update and separate M1 authorization are pending.

**Source limitation.** The expected standalone v1.4 independent review was absent from the checkout and available Git history. The three R14 findings and requested evidence come from the user-supplied text. The v1.4 architecture, the existing ADR row, V13 finding sources and original traceability corroborate the affected sections. No missing report was fabricated. The earlier v1.4 snapshot already differed from the actual Decision Register, Document Authority and Traceability hashes. These differences were present at intake. The remediation uses actual files and preserves their evidence rather than reverting them.

| Finding | Corrected sections | Invariant | Acceptance | Status and gate |
| --- | --- | --- | --- | --- |
| R14-01 | v1.5 §§3.2, 3.4, 5.3–5.4.1, 11.1; ADR-16 | Row lock is supported by narrow owner-only column UPDATE. All relevant flows share the native row lock and cannot alter account state through the fence | I14-01 extension R15-01 | Remediated in design, M1 TECH-02/05/12; later consumers TECH-08/10. Actual PostgreSQL tests pending. |
| R14-02 | ADR Register M0-ADR-16 and crosswalk; v1.5 §§3.1, 5.4.1, 9.5; Traceability D/E | One account-fence ADR identity, separate PREAUTH and expiry proposals under existing ADRs | R15-02 | Remediated in documentation. No ADR accepted. |
| R14-03 | Traceability A–F; Document Authority current references | One standing v1.4 contract, separate candidate tests, preserved historical evidence and future explicit promotion | R15-03 | Remediated in documentation. Candidate use awaits promotion. |

**R14-01 evidence and root cause.** v1.4 §5.4 described an implicit row-locking privilege. PostgreSQL requires UPDATE on at least one selected-table column. The database privilege and row-lock layers now have concrete contracts, with the immutable credential tuple and Spring generation checks at the service layer. The selected inert guard column limits privileges without a second entity lifecycle or a collision-prone advisory-lock protocol. Existing account writers contend on the same row. Fixture failures include denied function execution, obsolete credentials following a committed reset, rollback with a leaked binding and stale auth after a lost response. §E specifies results for each. Real engine execution on the selected version remains unverified. [PostgreSQL SELECT privileges](https://www.postgresql.org/docs/current/sql-select.html)

**R14-02 evidence and root cause.** The intake ADR register already contained an ADR-16 summary row. It lacked a full decision record and bundled V13-N01 account locking, V13-N03 PREAUTH and V13-N05 expiry scan. The registry now separates those concerns and retains historical mappings. Its uniqueness and resolution oracle is R15-02. Technical approval remains separate.

**R14-03 evidence and root cause.** The original traceability intermixed v1.1–v1.3 review material and current v1.4 tests without a distinct authority boundary. A–F preserve the prior trace alongside standings and prerequisites. Candidate E extends I14-01 without rewriting the old test as executed or already in force. R15-03 checks authority, test structure and evidence preservation. No test can override the owner gate.

**Scope and limitations.** No additional architecture defect was remediated outside R14-01–03. Intake discrepancies and the absent historical source are disclosed above. M1 remains identity-only. D01–D11 are unchanged. No source/SQL, runtime configuration, dependencies, database operations, staging or commits were performed. No runtime acceptance case was executed.

### v1.4 intake digests

Raw bytes of reviewed workspace inputs at v1.5 intake. Mutable companion files are updated in place; standing v1.4 and prior architecture/review artifacts are unchanged.

| Input | SHA-256 |
| --- | --- |
| ADR-REGISTER.md | DD1D5CB1C1EAD95F7E89F623491E66378A1303C8EFE9D0E7435B0B345EB74DE5 |
| DECISION-REGISTER.md | 0A70E9FEFA9E69619B67E4FC8C8014F6A80F64B90D15DE14349451863D8235F1 |
| DOCUMENT-AUTHORITY.md | B754692AAD6C947DA765003F3EFA671580F4B179D6BC4546FE8B042FC1F33C71 |
| MILESTONE-0-FINAL-v1.4.md | A6FB2B8787C6D21E3431EE5F37B8E20FBE4B0748FFB136D22A1565916130647D |
| MILESTONE-0-REVIEW-RESPONSE.md | EB0E21F0D7DE0B96E977086B85331351776FE6A6490E11896FD63034506F6883 |
| MILESTONE-0-TRACEABILITY.md | 2F337394D39B462B304C267F9F7228C8D31256E89632DE86730224030E093BE6 |

### v1.5 candidate companion digests

| File | SHA-256 |
| --- | --- |
| MILESTONE-0-FINAL-v1.5.md | B5034BB78ADD5039DE5DFE7FFDC8BC6684C018079D27807CE6752D966FD697F1 |
| ADR-REGISTER.md | 70A69F3905521310A6AC00D75EB66844E5D4039DF7CE7C2496E7D483AE354214 |
| DECISION-REGISTER.md | 9468FA8D64F98064BF92D852984570487D3FC21478A06D446E4E29204A70C6AD |
| DOCUMENT-AUTHORITY.md | 730702E6735B7C07BE818D7FBD5FDF5DFB99C43850B3045505CA5D88E923DF49 |
| MILESTONE-0-TRACEABILITY.md | C7FB67EEB391CE18625B32BF0F523AC3821B5A5A43F198BC83A1728360C68300 |

## v1.6 targeted readiness response

A1/C-01 is resolved in proposed §2.1 via explicit account_status NOT NULL plus the existing CHECK; R16-01 verifies credential/role denial. A2/C-02 is resolved in proposed §5.4.2 via actual mapped denial-audit callers, issuer-only cancellation, atomic disablement invalidation and terminal-time cleanup; R16-02–04 cover negative and concurrency fixtures. A3/C-03 is resolved in proposed §3.5 with a single atomic ledger, bounded issuance/cleanup and reserved AUTH admission; R16-05/06 prove expected behavior during authorized M1. L-01 remains the OPEN M4 IMPLEMENTATION GATE. Runtime tests have not been executed; v1.4 remains standing and v1.6 is only a candidate. Historical digests above remain unchanged; see the [v1.6 manifest](MILESTONE-0-v1.6-MANIFEST.md).
