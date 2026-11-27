# M2 product requirements

**Proposed for owner review.** Authority remains with [v1.6](../MILESTONE-0-FINAL-v1.6.md), the [Decision Register](../DECISION-REGISTER.md), and the [M1 acceptance record](../MILESTONE-1-ACCEPTANCE-REPORT.md).

| ID | Requirement and observable outcome |
| --- | --- |
| R1 | Authorized staff can list, search, create, edit, reorder and archive course, level and Reading lesson metadata if P01 passes. Parent eligibility controls navigation visibility. |
| R2 | Authorized staff can edit and preview Reading passage and exercise drafts. A stale edit fails without a partial graph change. |
| R3 | Authoring validates exactly the five approved D03 types. Marking and explanation remain protected. |
| R4 | A qualified publisher can atomically freeze valid editions, change eligible pointers and record audit. Withdrawal removes fresh eligibility while retaining editions. |
| R5 | The M2 candidate includes public projection contracts and isolated acceptance fixtures. Canonical Reading routes enable only on the M3 discovery gate. Published PUBLIC Reading needs no login under D02. |
| R6 | Anonymous access never exposes drafts, protected marking, answers, explanation, review or audit. Samples remain disabled pending TECH-06 and the discovery gate. |
| R7 | A successor edition cannot modify a prior frozen graph. Future attempts pin exact exercise and passage editions. |
| R8 | The UI shows revision conflicts, accessible form errors, preview and publication confirmation. Administrative data never enters shared client or server caches. |

**Boundary.** M2 prepares authoring and publication. M3 owns public discovery rollout. TECH-10 must approve staff capabilities before authoring enablement. TECH-08 architectural invariants are approved. TECH-06/07/09/10/11 and non-identity TECH-12 remain gated by the official approval record. D04, D05 and D09 govern later practice. Attempts, scores, feedback, grading, Listening, AI, payments, and production rollout are outside M2.

Course, level and lesson are proposed navigational containers. Passage is reading material. Exercise is a distinct question graph pinned to one passage edition. A lesson does not become a second publishing authority. Authors use original or rights-cleared content. See [decisions](M2-DECISION-REGISTER.md) and [acceptance mapping](M2-TEST-PLAN.md).
