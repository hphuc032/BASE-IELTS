# Decision register

The Product Owner approved the following values. This table preserves the wording in v1.0. Technical contracts implementing these values remain proposals.

| ID | Decision | Approved value |
| --- | --- | --- |
| D01 | Authentication | Secure cookie + server-managed session |
| D02 | Public Reading | Published PUBLIC Reading can be read without login |
| D03 | MVP question types | MCQ Single; T/F/NG; Y/N/NG; Short Answer; Sentence Completion |
| D04 | Modes | Every MVP exercise supports Learning and Exam |
| D05 | Retakes | Unlimited independent public-practice attempts |
| D06 | Session timeout | 60-minute idle; 7-day absolute lifetime |
| D07 | Multi-device | One active authenticated session per account |
| D08 | Registration | PENDING until email verification |
| D09 | Learning feedback | Configurable per exercise |
| D10 | Public exercise preview | Sample questions, without answers |
| D11 | First Admin | Seed/provision from deployment environment configuration |

## Technical choices pending Product Owner approval

| ID | Proposed choice and consequence | Gate |
| --- | --- | --- |
| TECH-01 | Verify exact compatible Java/Spring/Security, PostgreSQL, Node/Next and CI versions. Adopt modular monolith, Next.js/TypeScript, JPA, Flyway, typed REST and OpenAPI if accepted. One HTTPS ingress, private backend, client-fetched auth. SSR or separate origins change cookie/auth integration. | Before M1 authorization and initialization |
| TECH-02 | One serving process, authoritative in-memory generations, durable PostgreSQL account fences and restart logout. Confirm stop-before-start and exclusive ingress. Shared deployment requires a separately approved shared generation and ownership protocol. | Before M1 authorization |
| TECH-03 | Dev/test-only verification delivery in M1, with production register/resend disabled until real configuration. Provider-backed M1 requires operational setup and sender approval. | Delivery mode before M1 authorization; provider/sender before production |
| TECH-04 | Proposed 30-minute token TTL, resend cooldown 60 seconds, 5 issuances per canonical email per hour and 20 registration/resend requests per source per hour. Use a trusted ingress source address, count email limits without exposing account existence, and apply separate source-based verification/login throttles (proposed 20/hour and 30/hour respectively). Cleanup daily after 7 days, subject to retention. Proposed 300 ms minimum acknowledgment, 150 ms DB/pool operation budget, service-wide admission, global PREAUTH CSRF session cap (proposed 2,000 active entries, isolated from AUTH) and volatile handoff with §2.3 timing thresholds. Short TTL reduces exposure and increases resend needs. | Before identity implementation |
| TECH-05 | Durable singleton governance row, dedicated operator bootstrap, independently attested mailbox control, revision- and exact-payload-bound one-use recovery approval enforced by distinct mapped principals and serialized last-usable-Admin protection. Requires named operator roles, managed secret channel, audit destination and recovery runbook. | Before M1 authorization |
| TECH-06 | Fixed version membership, count max(1,min(2,floor(questionCount/10))), authored position order. Stable per edition, with successor resampling permitted under public projection rules. | Before discovery implementation |
| TECH-07 | First valid nonblank Learning answer locks scoring when configured, with item feedback and no re-answer. Separate mark and answer commands share revision and cannot commit each other. | Before assessment implementation |
| TECH-08 | Approve database-enforced freeze, narrow write boundary and snapshot projection, ordinary withdrawal preserves existing owner attempts, exceptional takedown handled separately. Parent edition replacement can hide stale exercises. | Design at sign-off; publication/takedown before dependent release |
| TECH-09 | Proposed §7.4 Unicode GR-1 and §§9.4–9.5 post-lock primary clock, 10 admissions per deadline minute, single-flight 30-second scheduler, 25-second cycle, ≤20 claims and bounded retry/park flow. Approve limits and benchmark targets before M4. Fixed order proposed; cancellation and grace disabled by default. | Before assessment implementation |
| TECH-10 | Approve author/publisher capabilities and draft workflow. Restricted grants and learner support remain default-deny. | Before affected Reading/authoring release |
| TECH-11 | Decide vocabulary deduplication, progress, taxonomy, dictionary/licensing, media/TTS, AI/privacy, branding, Listening and class/meeting delivery when scheduled. IELTS band conversion requires separate approval. | Before dependent feature |
| TECH-12 | Define identity and audit retention before M1 persistence, broader deletion/privacy before production. Define password policy, adaptive hash parameters, role authority, CSRF and safe error/browser integration before identity work. | Before dependent authorization |

Every TECH item is open. Record approval or a specific deferral with the disabled capability and next gate. Later-feature deferrals do not authorize future implementation.

## Approval and deferral evidence

V11-D01 maps to TECH-01/02/12, V11-D02 to TECH-03/04/05/12, and V11-D03 to TECH-06–11. All remain open. Production mail/provider and production operator enablement are separate from foundation acceptance. Approve the foundational choices before M1 and record later deferrals with the affected capability disabled. No deferrals have been accepted in this package.

## v1.3 explicit approval requests

| Review item | Requires explicit decision | Recommended disposition and gate |
| --- | --- | --- |
| V12-D01 | TECH-01/02/12. Exact compatible runtime/deployment evidence, scoped Spring repositories, cookie/CSRF behavior, identity security and retention. | Approve before M1 authorization. Keep serving off until single-process stop-before-start is evidenced. |
| V12-D02 | TECH-03–05/12. Delivery mode, admission and timing targets, verification limits, dedicated operator mailbox evidence and mapped identities, audit/retention. | Approve foundation settings before dependent M1 work. Proposed dev/test-only verification. Real mail and production operator enablement may be deferred only while disabled. |
| V12-D03 | TECH-06–11. Immutable graph/routine boundary, withdrawal and takedown, preview, feedback, grants, grading/capacity and other feature settings. | TECH-08 write/snapshot design approval at sign-off. Propose deferring later feature choices to M2–M4/dependent gate with functionality disabled. |

All three remain OPEN OWNER DECISION. These are proposed deferrals only. No owner approval or accepted deferral is recorded.

## v1.4 remediation items (V13-N01–V13-N07)

| Review item | Related decisions / TECH | Proposed disposition and gate |
| --- | --- | --- |
| V13-N01 | TECH-02/05/08/10/12 | Non-mutating `SECURITY DEFINER` account fencing. v1.5 candidate M0-ADR-16 proposes a constrained column grant to the non-login routine owner. Owner and version-pinned acceptance remain open. |
| V13-N02 | D08, D11; TECH-05/12 | Explicit `identity_lifecycle.mutate` rejections and `users` state `CHECK` constraint in design. Approve before M1 authorization. |
| V13-N03 | D07; TECH-02/04 | Global `PREAUTH` CSRF session cap (proposed 2,000 active entries, isolated from `AUTH`) is an **OPEN OWNER DECISION** under TECH-04. Approve before M1 authorization. |
| V13-N04 | TECH-04/05/12 | `cancelApproval`, `cleanupExpiredTokens`, and `session_user` attribution in `appendDenied` in design. Approve before M1 authorization. |
| V13-N05 | TECH-09 | Partitioned dual-scan expiry worker (`LIMIT 50` fresh + `LIMIT 50` retry) in design. Approve before M4 assessment implementation. |
| V13-N06 | TECH-09 | Idempotent `INSERT ... ON CONFLICT DO NOTHING` before `deadline_capacity` row lock in design. Approve before M4 assessment implementation. |
| V13-N07 | — | Explicit step (5b) in §6.1 error precedence for `POST /attempts` idempotency replay before 404/429. Clarifies existing contract; no new TECH decision. |

## v1.6 targeted proposal · approval still open

TECH-04 proposes fail-closed atomic PREAUTH storage/admission under §§2.3 and 3.5, with seven-day terminal-token retention subject to TECH-12. TECH-05/12 include §5.4.2 mapped denial-audit roles and issuer-only cancellation plus the narrow disabling path. TECH-09 remains OPEN M4 IMPLEMENTATION GATE; no accepted deferral or worker activation. See the [owner checklist](MILESTONE-0-PRODUCT-OWNER-SIGNOFF.md). D01–D11 are unchanged.

## Product Owner confirmations · 2026-11-26

PO-01 approves TECH-03 M1 test delivery; PO-02 approves TECH-01 local PostgreSQL 17 via Compose, without settling final toolchain acceptance; PO-03 accepts E-01 with missing transcript disclosed; PO-04 accepts E-02 with unknown discrepancy causes disclosed; PO-05 approves M1 identity retention and terminal-token floor; PO-06 approves development/test Admin governance scope. See the [preflight decision record](MILESTONE-1-PREFLIGHT.md) for exact scope and remaining decisions. These confirmations supersede any earlier OPEN label only to the extent stated. E-03 and separate M1 authorization remain open. D01–D11 and historical digest values are unchanged.

## Official Product Owner approval · 2026-11-26

Authority: the Product Owner's “OFFICIAL ARCHITECTURE APPROVAL AND MILESTONE 1 IMPLEMENTATION AUTHORIZATION” message in this conversation. TECH-01–05, TECH-08, identity TECH-12 and v1.6 A1–A3 are approved within the preflight scope. PO-01–06 remain approved. TECH-06/07/09/10/11 and non-identity TECH-12 are explicitly deferred with dependent functionality disabled. The expiry worker remains an OPEN M4 IMPLEMENTATION GATE. E-01 and E-02 are accepted evidence limitations, not recovery of the review transcript or proof of historical byte equality. E-03 promotes the raw-manifest-verified v1.6. This record supersedes earlier open-status notes. D01–D11 remain unchanged. Separate identity-only M1.1–M1.8 authorization was explicitly issued; production and M2 are excluded.

## M1.8 operator reconciliation approval

The Product Owner authorized a narrowly scoped non-HTTP management workflow. Recovery is reconciled by its existing approval UUID. Learner credential reset requires a caller-generated UUID and stores its receipt atomically with credential and revision changes. Read-only lookups return only outcome and the committed target revision. Missing receipts or active approvals are UNKNOWN. Durably cancelled unconsumed recovery approvals return NOT_COMMITTED. Expiry alone remains UNKNOWN because an already-started recovery may still commit. The change preserves distinct operator, approver and lifecycle principals. No table read privilege is granted to those logins.
