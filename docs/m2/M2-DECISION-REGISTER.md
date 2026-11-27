# M2 decision register

M2 Design v1.2 · 2026-11-27. Product Owner decisions and release authorization recorded. Authority: Product Owner approval in the M2 Design v1.1 brief and the 2026-11-27 F03/F04 approval instruction, together with [approved v1.6](../MILESTONE-0-FINAL-v1.6.md). The 2026-11-27 release directive separately authorizes implementation once the documentation release is verified. [Sign-off package](M2-DESIGN-SIGNOFF.md).

| ID | Status | Binding decision |
| --- | --- | --- |
| P01 | APPROVED A | Course → Level → Lesson, independently managed. A canonical Passage may be unassigned or placed in one Lesson. Deactivation only affects navigation. |
| P02 | APPROVED A | DRAFT → publisher validation → FROZEN. No persisted multistage review or mandatory second reviewer. Freeze, pointer switch, audit and receipt atomic. |
| P03 | APPROVED A | Valid private plain-text explanation required for every published question. No HTML/rich-text authoring or public exposure. |
| P04 | APPROVED A | ACTIVE ADMIN only, explicitly provisioned independent CONTENT_EDIT and CONTENT_PUBLISH. One Admin can hold both. TEACHER/delegation excluded. |
| P05 | APPROVED A | Public discovery off in M2, safe projections and isolated fixtures allowed. M3 activation separately approved. TECH-06 sample delivery gated. D02/D10 unchanged. |
| P06 | APPROVED A | Atomic explicit Passage/Exercise bundles, plus standalone Passage replacement. Stale-pinned Exercises become freshly ineligible until successor editions publish. Frozen versions retained, no repin. |

## Finding crosswalk

| Finding | Design disposition | Primary contract |
| --- | --- | --- |
| R01 | Single-mode editions publishable with conditional duration. | [ERD](M2-ERD.md#exact-mode-checks) |
| R02 | Resolved. Canonical authoring independent of catalog, optional single placement. | [Roadmap](M2-IMPLEMENTATION-ROADMAP.md) |
| R03 | Atomic receipt and lookup concealment retained. F03 approves receipt-only reconciliation. | [API](M2-API-CONTRACT.md#publication-reconciliation) |
| R04 | Independent GLOBAL grants retained. F04 approves the narrow staff fence privilege extension in M2 design. Provisioning and verification remain implementation gates. | [Security](M2-SECURITY-DESIGN.md#protected-staff-read-boundary) |
| R05 | Resolved in design. Shared bounded validation registry. Measurement open. | [API](M2-API-CONTRACT.md) |
| R06 | Exact pin and bundle gate with retained paragraph UUIDs and restrictive Evidence references. | [Architecture](M2-ARCHITECTURE.md#passage-draft-paragraph-identity) |
| R07 | Resolved. EXAM requires bounded duration; no EXAM requires null. | [ERD](M2-ERD.md) |

D01–D11 and PO-01–PO-06 remain unchanged in the approved baseline. The owner-supplied M2 remediation brief directs platform-wide Learning and Exam support and permits single-mode ExerciseVersions. This is the M2 interpretation used for review and sign-off; the historical D04 wording and v1.6 bytes are preserved. TECH-06/07/09/11 and non-identity TECH-12 retain official gates. P04 settles the initial capability policy within TECH-10; G02 gates provisioning.

## v1.2 remediation crosswalk

| Finding | Corrected sections | Acceptance and milestone | Approval status |
| --- | --- | --- | --- |
| M2-F01 · High | [Architecture · Exercise edition modes](M2-ARCHITECTURE.md#exercise-edition-modes), [ERD · Exact mode checks](M2-ERD.md#exact-mode-checks), [API · Exercise mode validation](M2-API-CONTRACT.md#exercise-mode-validation), [UX · Exercise and Passage editor](M2-UX-SPECIFICATION.md#exercise-and-passage-editor-behavior) | [T18](M2-TEST-PLAN.md#t18--exercise-edition-modes-and-duration), M2.3 save, M2.4 freeze and M2.5 projection fixture | PASS in design under the owner-supplied M2 interpretation. |
| M2-F02 · High | [API · Publication reconciliation](M2-API-CONTRACT.md#publication-reconciliation), [Security · Staff projection allowlist](M2-SECURITY-DESIGN.md#staff-projection-allowlist) | [T16](M2-TEST-PLAN.md#t16--publication-concealment-and-recovery), M2.4 | PASS in design. P02/P06 support actor/root concealment. |
| M2-F03 · Medium | [Architecture · Publication outcome and replay](M2-ARCHITECTURE.md#publication-outcome-and-replay), [API · Publication reconciliation](M2-API-CONTRACT.md#publication-reconciliation), [Security · Staff projection allowlist](M2-SECURITY-DESIGN.md#staff-projection-allowlist), [UX · Publication recovery](M2-UX-SPECIFICATION.md#publication-recovery) | [T16](M2-TEST-PLAN.md#t16--publication-concealment-and-recovery), M2.4 and M2.6 UX | APPROVED BY PRODUCT OWNER. [Approval record](#product-owner-approval-record). |
| M2-F04 · High | [Security · Protected staff read boundary](M2-SECURITY-DESIGN.md#protected-staff-read-boundary), [Architecture · Protected staff reads](M2-ARCHITECTURE.md#protected-staff-reads), [API · Admin endpoints](M2-API-CONTRACT.md#admin-endpoints) | [T08/T11](M2-TEST-PLAN.md#t08-and-t11--protected-staff-read-and-revoke), first protected GET acceptance in M2.1 or M2.2 and every subsequent staff GET family | APPROVED BY PRODUCT OWNER for M2 design. [Approval record](#product-owner-approval-record). |
| M2-F05 · Medium | [Architecture · Passage draft paragraph identity](M2-ARCHITECTURE.md#passage-draft-paragraph-identity), [ERD · Paragraph identity](M2-ERD.md#paragraph-identity-and-restricted-reads), [API · Passage paragraph updates](M2-API-CONTRACT.md#passage-paragraph-updates), [UX · Exercise and Passage editor](M2-UX-SPECIFICATION.md#exercise-and-passage-editor-behavior) | [T14](M2-TEST-PLAN.md#t14--passage-paragraphs-and-evidence), M2.2 ID/PUT and M2.3 Evidence integration | PASS in design. P06 and exact-edition membership are preserved. |

## Product Owner approval record

Approval context for F03 and F04: the Product Owner's explicit M2 Design v1.2 approval instruction of 2026-11-27 following independent re-review. The approvals apply to the M2 design contracts.

| Decision ID | Date | Status | Approved decision |
| --- | --- | --- | --- |
| F03 | 2026-11-27 | APPROVED BY PRODUCT OWNER | Receipt-only COMMITTED/UNKNOWN. Verify a durable receipt on primary PostgreSQL. Replay the original result for the same UUID and intent and reject changed intent with 409 only against a committed receipt visible to the actor/root. Rollback without a durable receipt leaves no original intent to compare. The client preserves UUID and payload. No operation ledger. |
| F04 | 2026-11-27 | APPROVED BY PRODUCT OWNER | Add the narrow M2 staff-reader fence privilege contract: content_staff_reader has USAGE on account_fence_routines and EXECUTE on the exact lockAndCheckActor(uuid,bigint) signature in that schema. Use only allowlisted scoped projections. Hold the fence on the same connection and outer transaction through full DTO materialization. No authority to provision or change the database under this approval. |

G01–G03 remain [open technical decisions](M2-OPEN-QUESTIONS.md#remaining-technical-gates). The Product Owner’s 2026-11-27 M2 Release and Autonomous Implementation directive authorizes freezing and releasing v1.2 to main, then implementing on a dedicated feature branch after verification of the remote documentation commit. Existing technical and milestone gates still apply.
