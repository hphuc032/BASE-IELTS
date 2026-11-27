# M2 product requirements

M2 Design v1.2 · 2026-11-27. Product Owner decisions and release authorization recorded. See the [sign-off package](M2-DESIGN-SIGNOFF.md) and [Decision Register](M2-DECISION-REGISTER.md).

**Proposed for owner review.** Authority remains with [v1.6](../MILESTONE-0-FINAL-v1.6.md), the [Decision Register](../DECISION-REGISTER.md), and the [M1 acceptance record](../MILESTONE-1-ACCEPTANCE-REPORT.md).

| ID | Requirement and observable outcome |
| --- | --- |
| R1 | Authorized staff can list, search, create, edit, reorder and archive course, level and Reading lesson metadata under approved P01. Parent eligibility controls navigation visibility. |
| R2 | Authorized staff can edit and preview Reading passage and exercise drafts. Paragraph edits and reordering preserve identity and Evidence. A stale or invalid edit fails without a partial graph change. |
| R3 | Authoring validates exactly the five approved D03 types. Every published question requires a validated private plain-text explanation. Marking remains protected. |
| R4 | A qualified publisher can atomically freeze valid editions, change eligible pointers and record audit and a durable publication receipt. The authorized originating actor can reconcile a lost response by the original operation UUID on primary PostgreSQL. Unavailable receipts share the same concealed response. After an uncertain response, F03 reports UNKNOWN when lookup cannot establish a committed receipt. Withdrawal removes fresh eligibility while retaining editions. |
| R5 | The M2 candidate includes public projection contracts and isolated acceptance fixtures. Canonical Reading routes enable only on the M3 discovery gate. Published PUBLIC Reading needs no login under D02. |
| R6 | Anonymous access never exposes drafts, protected marking, answers, explanation, review or audit. Samples remain disabled pending TECH-06 and the discovery gate. |
| R7 | A successor edition cannot modify a prior frozen graph. Future attempts pin exact exercise and passage editions. |
| R8 | The UI shows revision conflicts, accessible form errors, preview and publication confirmation. Administrative data never enters shared client or server caches. |

**Boundary.** M2 prepares authoring and publication. M3 owns public discovery rollout. P04 settles the initial capability policy within TECH-10. Provisioning identity and deployment approval remain separate gates. TECH-08 architectural invariants are approved. TECH-06/07/09/10/11 and non-identity TECH-12 remain gated by the official approval record. D04, D05 and D09 govern later practice. Attempts, scores, feedback, grading, Listening, AI, payments, and production rollout are outside M2.

Course, level and lesson are approved navigational containers. Passage is reading material. Exercise is a distinct question graph pinned to one passage edition. A passage may be unassigned or placed in one lesson. Catalog deactivation affects only navigation. Canonical authoring and publication proceed independently. Authors use original or rights-cleared content. See [decisions](M2-DECISION-REGISTER.md) and [acceptance mapping](M2-TEST-PLAN.md).

## Approved release behavior

P01–P06 remain approved in the [decision register](M2-DECISION-REGISTER.md). D04 is applied to the platform: it supports both Learning and Exam, and a valid ExerciseVersion may publish with LEARNING, EXAM, or both. The exact published edition owns modes and conditional Exam duration. D05 unlimited independent retakes and D09 configurable Learning feedback remain later-milestone behavior. D02 remains anonymous access to eligible PUBLIC Reading when M3 is activated. D10 remains answer-free preview when TECH-06 is approved. Neither is activated in M2.

Protected staff GETs fully materialize their projections under the account fence on the same connection and transaction. The Product Owner approved the F03 receipt-only design and F04 narrow staff reader privilege contract on 2026-11-27. These are design approvals. Staff runtime authorization and database provisioning follow the approved contract only during separately authorized M2 implementation. [Approval record](M2-DECISION-REGISTER.md#product-owner-approval-record)
