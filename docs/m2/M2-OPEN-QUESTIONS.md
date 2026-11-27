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

## Reading implementation status

G03 now has isolated V15 Passage read and paragraph edit candidate measurements. Live authoring still needs bounded graph/cardinality limits and representative AUTH, fence, contention and pool data. Exercise/Evidence database tests require coordinator allocation and actual guarded persistence. G02 production principal mapping remains pending. Development and test use isolated synthetic identities. See the [M2.2 evidence and traceability](M2.2-ACCEPTANCE-REPORT.md) and [migration proposal](M2.2-READING-MIGRATION-SPEC.md).
