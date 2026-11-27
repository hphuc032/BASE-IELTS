# ADR register

See the [decision register](DECISION-REGISTER.md) for exact approved wording. The namespace M0-ADR distinguishes current records from historical ADR numbers. An approved direction does not approve its technical realization.

| Current ID | Topic and direction | Status |
| --- | --- | --- |
| M0-ADR-01 | Modular monolith, Next.js, JPA, Flyway, typed REST and OpenAPI | Proposed · TECH-01 |
| M0-ADR-02 | PostgreSQL and Spring Boot | Previously confirmed baseline directions; versions and integration proposed · TECH-01 |
| M0-ADR-03 | Cookie and server-managed session | D01 approved; explicit PREAUTH/AUTH repositories, route precedence single cookie writer and isolated bounded PREAUTH proposed · TECH-01/12 |
| M0-ADR-04 | Anonymous published PUBLIC Reading | D02 approved; eligibility and projection proposed · TECH-08 |
| M0-ADR-05 | Five MVP types and deterministic marking | D03 subset approved; schemas, bounds and policy proposed · TECH-09 |
| M0-ADR-06 | Both exercise modes | D04 approved; timer and Exam boundary proposed · TECH-09 |
| M0-ADR-07 | Unlimited independent public retakes | D05 approved; derived canonical request, READ COMMITTED start and bounded deadline-bucket expiry and partitioned fresh/retry scan proposed · TECH-09 |
| M0-ADR-08 | 60-minute idle, 7-day absolute, one active session | D06/D07 approved; single credential snapshot, sole binding, durable revision fence, isolated PREAUTH and single-process store proposed · TECH-02 |
| M0-ADR-09 | Pending registration and email verification | D08 approved; credential binding, scoped fragment handling, async token delivery and provider gate proposed · TECH-03/04/12 |
| M0-ADR-10 | Configurable Learning feedback | D09 approved; disjoint commands and pinned committed item locking proposed · TECH-07 |
| M0-ADR-11 | Public sample questions without answers | D10 approved; fixed membership/count proposed · TECH-06 |
| M0-ADR-12 | Environment-provisioned first Admin | D11 approved; revision/payload-bound one-use recovery approval, trusted principal roles and linked audit proposed · TECH-05 |
| M0-ADR-13 | Guarded database routines, immutable graph/FK retention, atomic pointer switches, single-snapshot projections, UUID IDs and UTC timestamps | Proposed · TECH-08 |
| M0-ADR-14 | Restricted access and publication scope | Proposed · TECH-10 |
| M0-ADR-15 | Provider and feature deferrals, identity/privacy retention | Proposed · TECH-03/11/12 |
| M0-ADR-16 | PostgreSQL account-row fencing | PROPOSED for v1.5 review · TECH-02/05/12; later consumers TECH-08/10 |

## Historical ADR crosswalk

- PRD ADR-001 → 01, 002 → 02, 003 → 03, 004 → 13. Right-hand numbers use the M0-ADR namespace.
- Original pack and v0.2 ADR-001 through 015, in order → 01, 02, 02, 01, 01, 01, 01, 03, 13, 13, 05, 13, 15, 04, 05.
- v1.0 ADR-001 through 023, in order → 01, 02, 02, 01, 01, 01, 01, 03, 03, 08, 08, 09, 12, 13, 07, 05, 06, 07, 10, 11, 04, 14, 15.
- Earlier independent proposed ADR topics Application/Data, Session, Published Versions, Attempt Consistency, Assessment Payloads, Public/Exam → 01/02, 03/08, 13, 07, 05, 04/06/11.

V12-D01–D03 remain open in the Decision Register. Planned routine privileges, scoring clock, GR-1 normalization and scheduler budgets are proposed realizations, with no new accepted ADR.

## M0-ADR-16 · PostgreSQL account-row fencing

**Status** PROPOSED. Product Owner acceptance and version-pinned acceptance execution remain open.

**Context and problem.** V13-N01 requires durable account-row fencing before JVM stripes for account-sensitive paths and cross-process operator changes. R14-01 identifies an invalid privilege assumption in v1.4 §5.4: a locking SELECT requires UPDATE on at least one column even when its routine performs no data changes. A broad runtime users grant would defeat V13-N02 and account-state protection.

**Decision and rationale.** In the v1.5 review candidate use the existing users row, an inert fixed-false fence_guard column, and explicit column UPDATE only for the non-login account_fence_owner. Two narrowly granted SECURITY DEFINER functions hold FOR NO KEY UPDATE on the caller's transaction. No application or operator login receives users UPDATE. Keep operator/domain mutation grants in separate typed routines. This directly contends with account changes and needs no row lifecycle synchronization or advisory-key convention.

**Alternatives.** A one-to-one fence table isolates grants at the cost of a second lock and obligatory synchronization by every user writer. Transaction-scoped advisory locks offer no protected row identity, require collision-safe authorization, sorted/deduplicated keys and complete cooperation by all writers. See the comparison in v1.5 §5.4.1.

**Security consequences.** Least privilege is enforced by column grants, distinct LOGIN/NOLOGIN owners, fixed trusted search path, qualified references, PUBLIC revocation and allowlisted execution. Runtime cannot alter credential, role, status or revision via this fence. The login snapshot is returned only to identity_api. Existing D08/D11 checks and V13-N02 guards remain. Audit roles and definitions via catalog inspection and trust separately governed migration ownership.

**Database, transaction and operational consequences.** One inert constrained column, no new entity/FK. Primary READ COMMITTED, same connection/outer transaction, governance when required → UUID-ordered users rows → local stripes → downstream rows. No speculative authority on rollback/loss. Conflicting mutations contend on the same native row even across processes. Monitor lock waits; bounded contention fails closed. Known commit plus lost response preserves only current authority.

**Dependencies and acceptance.** M1 identity, later authoring/assessment consumers gated by TECH-08/10. TECH-02/05/12 and selected PostgreSQL compatibility required before operation. Acceptance is I14-01 extended/R15-01 for actual column grants, denied calls and DML, PostgreSQL contention, rollback and lost response. R15-02 confirms ID resolution. References: v1.5 §§3.2, 3.4, 5.3–5.4.1 and 11.1; V13-N01/N02 and R14-01/R14-02.

**Crosswalk for bundled v1.4 references.** M0-ADR-16 now denotes account fencing only. V13-N03 PREAUTH capacity and isolation belongs to M0-ADR-03/08 (TECH-02/04). V13-N05 partitioned expiry scan belongs to M0-ADR-07 (TECH-09). These are independent proposed refinements of existing ADRs. Their historical references retain their original meaning in preserved artifacts.

## v1.6 candidate notes

M0-ADR-03 and M0-ADR-08 retain proposed atomically bounded PREAUTH admission under v1.6 §3.5. M0-ADR-12 retains proposed mapped denial audit and recovery/cancellation under §5.4.2. M0-ADR-16 account-fence grants and row-before-stripe ordering are unchanged. M0-ADR-07 still owns the expiry partitioning proposal under the OPEN M4 IMPLEMENTATION GATE. No ADR is promoted or newly approved.

## Effective owner disposition · v1.6

The official approval in the [Decision Register](DECISION-REGISTER.md) accepts the M1 technical realizations of ADR-01/02/03/08/09/12/16 and the approved architectural invariants of ADR-13, by scope of TECH-01–05/08 and identity TECH-12. Later realization details in ADR-04–07/10/11/14/15 remain gated where they depend on deferred decisions, with invariant-level D01–D11 unchanged. This status note supersedes earlier candidate labels only within approved scope.

## M0-ADR-12 implementation note

V11 adds caller-scoped outcome projections to the approved management channel. The recovery projection correlates a consumed approval with a committed success audit. The returned revision is the target account revision at that recovery commit, even for replacement. The lifecycle reset wrapper serializes under the existing governance and account fences, inserts one credential-free receipt, and keeps the original mutation routine private. A missing receipt does not establish rollback. Retry with an already consumed operation UUID cannot mutate credentials again.
