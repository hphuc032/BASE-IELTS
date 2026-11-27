# M2 remaining technical gates

M2 Design v1.2 · 2026-11-27. Product Owner decisions and release authorization recorded. See the [sign-off package](M2-DESIGN-SIGNOFF.md) and [Decision Register](M2-DECISION-REGISTER.md).

## Approved decisions with implementation verification

- F03: Approved receipt-only publication reconciliation. T16 verifies primary routing, concealment, same-intent replay and the UNKNOWN boundary at M2.4/M2.6. [Outcome contract](M2-ARCHITECTURE.md#publication-outcome-and-replay)
- F04: Approved scoped staff-reader fence privilege contract. T08/T11 verify effective grants, fence order and full materialization before first protected GET acceptance at M2.1/M2.2. [Privilege and transaction contract](M2-SECURITY-DESIGN.md#protected-staff-read-boundary)
- F05: The paragraph UUID contract is specified. Atomic reorder and Evidence serialization remain implementation verification in [T14](M2-TEST-PLAN.md#targeted-acceptance-contracts).

## Remaining technical gates

| ID | Decision required | Responsible | Latest gate | Acceptance and safe progress |
| --- | --- | --- | --- | --- |
| G01 | Version the canonical publication encoder and digest. Define fields, ordering, byte representation, absence/normalization, and compatibility with older committed receipts. Retain receipts through M2; non-identity TECH-12 gates disposal policy. | Backend lead with database reviewer | Before M2.4 implementation of publish/replay | Fixed cross-layer vectors give the same digest for equivalent sorted bundles, different digests for changed intent and compatible replay after encoder upgrades. Independently authorized catalog/draft work may proceed. |
| G02 | Name environment-specific provisioning principal lifecycle, bootstrap, authenticated grantor mapping, rotation and recovery, with audit responsibilities. | Deployment owner with security/database lead | Before real grant provisioning, including the first staff-route acceptance in M2.1 or M2.2 | T11/T17 verify mappings from session_user, fail closed for invalid mappings, no self grant or role assumption, correct atomic audit/revision and tested recovery/rotation. After separate M2 authorization, isolated schema/DTO work can proceed. Real grant provisioning and staff acceptance wait. |
| G03 | Set the aggregate graph/bundle size and cardinality caps before locks, and bounded query, lock and transaction budgets. Bound expensive search/count/page depth and test representative access paths. | Backend and database leads | Before implementing each affected path: staff catalog queries M2.1, graph edits M2.2–M2.3, publication M2.4 and public fixtures M2.5 | Boundary tests reject cap+1 without mutation, prove lock/query/transaction bounds under contention, and use measured fixture plans and times. Unaffected authorized work may proceed. M2.8 verifies the final budgets. |

## Later activation gates

M3 separately approves public discovery and TECH-06 sample policy. TECH-07 feedback, TECH-09 scoring/expiry, TECH-11 integrations and non-identity TECH-12 remain gated. The M4 expiry-worker gate remains open. No activation in M2.

The Product Owner authorized implementation after the documentation release is verified. The open technical gates above remain in force. Previously closed M1 decisions and v1.6 remain unchanged.

## 2026-11-27 scope clarification

[PO-07 and PO-08](M2-DECISION-REGISTER.md#2026-11-27-implementation-scope-approvals) confirm M2.2 backend Passage authoring and Passage-to-Lesson placement, M2.3 Exercise/Evidence, and M2.6 staff editors. V18 stays reserved and unimplemented in M2.2. The placement contract uses the existing Catalog ReadingLesson identity and requires an approved forward-only migration order.

## 2026-11-27 G03 evidence still required

PO-09 closes the normal HTTP threshold decision at p95 ≤ 100 ms and p99 ≤ 200 ms. PO-10 closes the Catalog saturation policy decision at fail-fast HTTP 503 with no protected SQL on rejection. G03 implementation verification remains open. Report HTTP route, data size, workload mix and concurrency, sample count, p95/p99, status counts, checkout and transaction timing, and security invariants. In a separate saturation run prove bounded admission, no accumulating queue, no SQL for rejected work, and success after release. Treat Reading workload and graph bounds as their own acceptance evidence. [Approved decisions](M2-DECISION-REGISTER.md#2026-11-27-performance-and-overload-approvals)

## Final integrated G03 disposition

Measured Catalog and Reading workloads meet PO-09 at the documented fixture sizes, and Catalog rejected the measured overload before acquiring a connection. The Catalog release workload envelope remains open because a 40,005-course diagnostic exceeded PO-09. Reading still needs explicit aggregate paragraph/translation cardinality limits and cap-plus-one proof, response/workload envelope evidence and create/clone/preview performance coverage. Those operations were covered functionally. Keep routes disabled pending the applicable decisions and verification. G02 remains a separate production-provisioning gate.

PO-11 now resolves the Catalog workload selection at 20,000 Courses, 24 Levels per Course and 256 Lessons per Level, with pools of three readers and two writers. The 40,005-Course outlier remains diagnostic evidence outside the initial envelope. PO-12 authorizes release after verification. The Reading aggregate cardinality disposition remains separate. G01 and G02 stay open. Exercise/Evidence persistence remains M2.3, with V18 and V19 reserved.

## Reading operational cardinality disposition

The initial staff authoring envelope is 32 paragraphs per draft and two translations per paragraph. The HTTP authoring input checks both counts before the database graph mutation and retains the existing byte limits. V17.3 remains byte-identical, so these counts are application and operational limits. They do not alter database constraints or exact historical edition visibility. Before activating authoring with imported or restored data, verify that eligible frozen clone sources fit the same graph envelope. Preserve historical sources outside the envelope and require an explicit scope decision before making them eligible for cloning. Publication and production provisioning remain gated.

Initial feature G03 verification is complete for the PO-11 Catalog envelope and the documented Reading HTTP graph envelope. Broader operating profiles require remeasurement. Imported frozen clone sources require the graph inventory before activation. G01 and G02 remain open and public discovery remains M3. The feature release does not authorize them.
