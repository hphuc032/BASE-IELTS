# BASE IELTS M2 Design v1.2 sign-off

Decision record date: 2026-11-27. Scope: Learning Content Foundation design.

**M2 DESIGN — APPROVED FOR FREEZE AND RELEASE**

**M2 IMPLEMENTATION — AUTHORIZED AFTER THE DOCUMENTATION RELEASE IS VERIFIED**

## Project baseline

M1 Identity Foundation is accepted. M2 v1.2 builds on the approved Milestone 0 v1.6 baseline. The v1.6 raw SHA-256 is `9a7e1e0812c8c26b8828e627289f183a1a9103955bd408a9ab7a76b8a1bff602`. D01–D11, the historical records, M1 acceptance artifacts and Flyway V1–V12 are preserved. F04 is an M2-specific design extension to the fence caller boundary. It leaves the v1.6 file, M1 roles and the existing fence routine unchanged. [Architecture v1.6](../MILESTONE-0-FINAL-v1.6.md) · [Manifest](../MILESTONE-0-v1.6-MANIFEST.md) · [M1 acceptance](../MILESTONE-1-ACCEPTANCE-REPORT.md) · [D01–D11](../DECISION-REGISTER.md)

## Approved product decisions

| ID | Approved scope |
| --- | --- |
| P01 | Course → Level → Lesson, with optional Passage placement. |
| P02 | DRAFT → Publisher validation → FROZEN. |
| P03 | Every published question has a private plain-text explanation. |
| P04 | ACTIVE ADMIN may receive CONTENT_EDIT and CONTENT_PUBLISH independently. One Admin may hold both. |
| P05 | Public discovery activation is deferred to M3. Samples retain the separate TECH-06 gate. |
| P06 | Publication bundles are atomic. Replacing a Passage alone temporarily makes incompatible exercises ineligible for fresh public discovery. |

## Independent review disposition

| Finding | Disposition | Acceptance contract |
| --- | --- | --- |
| F01 | PASS in design. All three mode configurations can publish under the owner-supplied M2 interpretation of D04. | T18 |
| F02 | PASS in design. Absent and inaccessible publication receipts share a concealed 404. | T16 |
| F03 | APPROVED BY PRODUCT OWNER on 2026-11-27. Receipt-only reconciliation on primary PostgreSQL. | T16, M2.4 and M2.6 |
| F04 | APPROVED BY PRODUCT OWNER on 2026-11-27. Narrow staff reader privilege contract and fenced materialization. | T08/T11, first protected GET in M2.1 or M2.2 and subsequent protected GETs |
| F05 | PASS in design. Paragraph UUID identity and exact-edition Evidence survive edits and reordering. | T14 |

[Approval record and finding crosswalk](M2-DECISION-REGISTER.md#product-owner-approval-record) · [Acceptance contracts](M2-TEST-PLAN.md#targeted-acceptance-contracts)

- **F03:** A durable receipt on the primary yields COMMITTED. UNKNOWN means commitment could not be established. A changed intent yields 409 OPERATION_CONFLICT only when a committed receipt visible to the actor/root already binds the UUID. After rollback without a receipt the server has no prior binding to compare. Retry preserves the original UUID and exact payload. If reload loses the payload, offer lookup with the original UUID and explain the uncertainty. Freeze, current pointers, receipt and audit stay atomic. [Publication reconciliation](M2-API-CONTRACT.md#publication-reconciliation)
- **F04:** The approved fence privileges for `content_staff_reader` are schema `USAGE` on `account_fence_routines` and `EXECUTE` on `account_fence_routines."lockAndCheckActor"(uuid,bigint)`. Use narrowly scoped projectors. Their non-login owners receive only the SELECT privileges required by the allowlist. Staff GET keeps the account fence on the same connection and outer transaction through full DTO materialization. The approval records a design requirement for future M2 implementation. [Protected staff read boundary](M2-SECURITY-DESIGN.md#protected-staff-read-boundary)

## Open technical decisions and verification

| ID | Remaining decision |
| --- | --- |
| G01 | Canonical encoder, payload digest and compatibility vectors before M2.4 publish/replay. |
| G02 | Provisioning principal lifecycle, mapping, rotation and recovery before real grant provisioning. |
| G03 | Aggregate graph/bundle caps and query, lock and transaction budgets before each affected path. |

The [technical gates](M2-OPEN-QUESTIONS.md#remaining-technical-gates) retain their owners and exit criteria. T16 and T08/T11 remain required implementation acceptance. Runtime, database and browser tests were not executed for this design sign-off package.

## Canonical ten-document inventory

| Document | Contract |
| --- | --- |
| [M2-PRD.md](M2-PRD.md) | Product behavior and scope. |
| [M2-ARCHITECTURE.md](M2-ARCHITECTURE.md) | Transaction boundaries, replay and staff fencing. |
| [M2-ERD.md](M2-ERD.md) | Immutable version graph, receipt and audit persistence. |
| [M2-API-CONTRACT.md](M2-API-CONTRACT.md) | Endpoints, DTOs, replay errors and concealed lookup. |
| [M2-SECURITY-DESIGN.md](M2-SECURITY-DESIGN.md) | Effective privilege boundaries and scoped projection allowlist. |
| [M2-UX-SPECIFICATION.md](M2-UX-SPECIFICATION.md) | Editor and UNKNOWN recovery behavior. |
| [M2-TEST-PLAN.md](M2-TEST-PLAN.md) | Planned acceptance tests and finding traceability. |
| [M2-IMPLEMENTATION-ROADMAP.md](M2-IMPLEMENTATION-ROADMAP.md) | Milestone prerequisites and exit criteria. |
| [M2-DECISION-REGISTER.md](M2-DECISION-REGISTER.md) | Product and finding approvals. |
| [M2-OPEN-QUESTIONS.md](M2-OPEN-QUESTIONS.md) | Open technical gates and later activation. |

## Consistency and authorization

The ten documents agree on the receipt-only boundary for 409, primary lookup, the UNKNOWN behavior and the exact staff fence privilege. The design preserves the uniform 404 for F02 and the PASS dispositions of F01 and F05. F03 and F04 have approval records and retain runtime acceptance requirements. G01–G03 remain open.

At design freeze, M2.1 had not started. The Product Owner’s M2 Release and Autonomous Implementation directive of 2026-11-27 authorizes the v1.2 documentation commit and push to main. It separately authorizes implementation and tested checkpoint releases on a dedicated feature branch from the verified documentation commit. Implementation starts after the documentation push and remote SHA verification. G01–G03 still gate dependent work. Provisioning uses G02, and public discovery and production deployment are outside this authorization.
