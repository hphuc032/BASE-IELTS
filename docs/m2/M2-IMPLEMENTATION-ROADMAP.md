# M2 implementation roadmap

M2 Design v1.2 · 2026-11-27. Product Owner decisions and release authorization recorded. See the [sign-off package](M2-DESIGN-SIGNOFF.md) and [Decision Register](M2-DECISION-REGISTER.md).

The Product Owner authorized M2 after verification of the documentation release. Track technical decisions in the [register](M2-DECISION-REGISTER.md). Migration numbers below are relative, assigned after inspecting current Flyway history. No V1–V12 modifications.

| Stage | Scope, dependencies and deliverables | Exit tests and gate |
| --- | --- | --- |
| M2.0 Design sign-off | Review the [sign-off package](M2-DESIGN-SIGNOFF.md) with P01–P06 and the F03/F04 design approvals recorded. Keep the M2 interpretation of D04 and the [technical gates](M2-OPEN-QUESTIONS.md#remaining-technical-gates). | Approved design release pushed to main and remote SHA verified before creating the implementation branch. |
| M2.1 Catalog foundation | After M2.0 and separate authorization, add catalog and staff REST. Apply the approved F04 reader boundary and complete T08/T11 before first protected GET acceptance. Apply G02 before provisioning and G03 before catalog queries. Staff pages wait for M2.6. | T01, T08, T09, T11, T13, T15. Catalog cannot update content pointers. |
| M2.2 Reading passage/lesson authoring | After M2.0 and separate authorization, canonical authoring may proceed independently of catalog. Placement waits for both. Staff reads implement the approved F04 boundary and require G02, plus G03 on affected paths. Add exact edition staff projection and identity-preserving draft saves. | T02, T05, T08, T09, T11, T14. Paragraph UUIDs persist through reorder and invalid changes roll back atomically. |
| M2.3 Question graph and protected answer data | After M2.2, add Exercise editions, groups, questions, options, marking, explanation and Evidence. Use exact paragraph identity and version-owned modes/duration. | T02–T03, T05, T07–T09, T11, T14–T15, T18 save cases. |
| M2.4 Publication and withdrawal | After M2.2–M2.3, apply P02/P04/P06 and the approved F03 receipt-only reconciliation. Resolve G01 and affected G03 before implementation. History and reconciliation use the approved F04 boundary. | T04–T05, T08, T11, T14, T16–T18, including primary lookup, concealment, replay, rollback/UNKNOWN and mode cases. |
| M2.5 Public projection contract | After M2.4. Safe views/read routines and public DTO tests isolated behind disabled M3 route wiring. No sample membership migration until TECH-06. | T06–T07, T12. Independent DB role denied all answer data. Release of live public discovery belongs to M3. |
| M2.6 Staff frontend | After staff APIs pass. Workspace, catalog, scoped editors and preview, history and publish/withdraw dialogs. Follow draft paragraph UUID and confirmed outcome semantics. | T08–T10, T14, T16 and T18 UI coverage, including unknown-result recovery, session expiry and CSRF. |
| M2.7 Public UI contract | Specify or fixture-render browse and reading screens after safe projection. No site navigation change or endpoint activation during M2. Live feature implementation/enablement belongs to M3. | T07/T10/T12 for isolated fixtures if authorized. M3 remains gated. |
| M2.8 Integrated acceptance | Run M1 regressions, fresh and upgrade PostgreSQL with least-privilege roles, backend/frontend builds and Edge flows. | Applicable T01–T18 and M1 tests pass. Document deferred work and production gates. |

Content migrations are forward-only under separate ownership. A feature can land disabled only if explicitly in the authorized implementation scope, with public access denied by default and permission tests proving the disabled state. No assessment schema or execution in this roadmap.

## Reconciled sequencing

M2.1 and canonical M2.2 can proceed independently after authorization within their respective gates. M2.3 uses exact Passage edition and paragraph IDs. M2.4 adds receipts and lookup under F03/F04 after the applicable technical decisions. See the [remediation crosswalk](M2-DECISION-REGISTER.md#v12-remediation-crosswalk) for work package dependencies. M2.5 verifies a safe fixture. Public discovery remains disabled until M3 and samples remain gated by TECH-06. The recorded M2 authorization takes effect after the design release is verified.

## 2026-11-27 scope clarification

[PO-07 and PO-08](M2-DECISION-REGISTER.md#2026-11-27-implementation-scope-approvals) confirm M2.2 backend Passage authoring and Passage-to-Lesson placement, M2.3 Exercise/Evidence, and M2.6 staff editors. V18 stays reserved and unimplemented in M2.2. The placement contract uses the existing Catalog ReadingLesson identity and requires an approved forward-only migration order.

## 2026-11-27 G03 verification after PO-09 and PO-10

M2.1 and M2.2 must show HTTP p95 ≤ 100 ms and p99 ≤ 200 ms under representative normal workload. M2.1 must independently verify fail-fast Catalog pool admission, HTTP 503 at saturation, no protected SQL on rejection, a bounded queue, and recovery after release. Keep overload observations separate from normal latency percentiles. Preserve the M1 staff transaction and security checks. [Approved decisions](M2-DECISION-REGISTER.md#2026-11-27-performance-and-overload-approvals)

PO-11 sets the initial Catalog operating envelope. PO-12 authorizes the feature checkpoint after all applicable acceptance gates pass. The release does not change the M2.3 Exercise/Evidence, M2.4 publication, M2.6 staff UI or M3 public-discovery sequencing. [Decision register](M2-DECISION-REGISTER.md#2026-11-27-operational-envelope-and-feature-release-approvals)
