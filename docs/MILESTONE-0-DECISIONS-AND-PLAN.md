# Milestone 0 decisions, proposed ADRs and Milestone 1 plan

**Status** Proposed for Product Owner review · 2026-11-26

D01–D05 remain approved as recorded in the [independent review](MILESTONE-0-REVIEW.md). The technical selections below are pending. Milestone gates describe when implementation depends on each choice. Any unresolved item blocking formal Milestone 0 exit needs an owner decision or explicit deferral when approving Milestone 1.

## Open-decision register

| ID | Owner decision | Proposal and gate |
| --- | --- | --- |
| O-01 | Toolchain, persistence, migration, API conventions and test stack | Approve compatibility matrix for Java/Spring Boot/Security, PostgreSQL, Node/Next.js and CI. Approve modular monolith, typed REST DTOs, JPA and Flyway before M1 initialization. Exact versions require target-environment verification. |
| O-02 | Browser/API origins and deployment | One HTTPS origin with reverse proxy; approve development parity and whether personalized SSR is needed. M1 gate. |
| O-03 | Sessions, revocation and timeouts | Single-instance server-side sessions if restart logout is acceptable. Set idle/absolute values, revocation targets and concurrent-session policy. M1 gate. |
| O-04 | CSRF token delivery and endpoint errors | Adopt synchronizer bootstrap and rotation contract, test request flow under selected topology. M1 gate. |
| O-05 | Historical content and migration strategy | Adopt immutable relational versions and pinned grading rules. Design approval at M0 exit, implementation in M2/M4. |
| O-06 | Anonymous exercise discovery and question preview | Propose public cards without prompts for initial release. D02 Reading access is settled. Decide ahead of Reading discovery. |
| O-07 | Learning feedback | Propose post-submit feedback in both modes. Before M4 decide whether item feedback is needed; if approved, specify answer locking, reveal DTO and reuse across modes. |
| O-08 | Vocabulary duplicate policy | Choose one normalized term with multiple sources or separate saved entries. M3 gate. |
| O-09 | Dictionary | Select provider only after licensing review. Manual save works independently. M3 gate if lookup ships. |
| O-10 | Media storage and TTS | Decide storage region, retention, media access and whether audio/upload is needed. Gate before integration. |
| O-11 | Email and account activation | Decide verification/reset scope, provider and ACTIVE versus PENDING registration before M1 auth contract. |
| O-12 | AI provider/privacy | Defer integration, preserve human review for assisted drafts and obtain data-processing approval before provider use. |
| O-13 | Publication workflow | Choose teacher permissions, editor scope, approval requirements, required reviewed explanations and withdrawal/archive behavior before M2. Draft editing and publishing are separate capabilities. |
| O-14 | Progress policy | Define completion, resume reset, lookup behavior and dashboard metrics before M3/M5. |
| O-15 | Retention and privacy | Decide account deletion, session/audit retention, historical content and extraordinary takedown. Resolve identity storage implications before M1 and remaining details before production. |
| O-16 | First-admin bootstrap and role grants | Controlled operator provisioning and audited role changes. Decide recovery and last-admin policy before M1. |
| O-17 | Branding and accessibility wireframes | Review initial screen flows for M0 exit; defer final branding choices until they affect delivery. |
| O-18 | Exam deadline and integrity boundary | Approve required duration, deadline finalization mechanism, public-content boundary, timing/recovery and any grace/cancellation policy. M4 gate. |
| O-19 | Assessment protocol and grading | Approve mandatory start keys, conditional autosave, duplicate submit replay, grader policy/limits and fixed order. D03 subset and D05 public retakes are settled. Decide any restricted retakes separately. M4 gate. |
| O-20 | Restricted grants and private support | Decide whether restricted learner delivery and audited support ship in MVP. Default deny until a grant model exists. Teacher learner-attempt access depends on assignment scope. |
| O-21 | Taxonomy and scores | Approve difficulty taxonomy and CEFR use for M2. Report raw practice score; any IELTS band conversion requires separate approval. |
| O-22 | Later-release scope | Confirm Listening and class/meeting integrations remain on later roadmap. Determine provider and integration depth when scheduled. |

## Proposed ADRs

Date for all records is 2026-11-26. Decision owner is the Product Owner. Status for each technical decision is **Proposed**. Topic names are used below because PRD §18 and Pack §9 reuse ADR numbers for different topics.

| Topic | Context and proposed decision | Alternatives and consequences |
| --- | --- | --- |
| Application and data foundation | At initial scale, use a Spring Boot modular monolith, PostgreSQL, thin Next.js frontend, versioned REST DTOs and Flyway. | Compare multiple deployable domain services and migration tooling alternatives. Single transaction boundaries and fewer operational components, with module boundaries enforced in the application. |
| Session topology | Fulfil D01 with same-origin ingress, host-only session cookie, synchronizer token and server revocation. | Distinct same-site origins require credentialed CORS and browser tests. Cross-site cookies introduce compatibility risk. In-memory sessions simplify a single instance and require reauthentication on restart. |
| Published versions | Retain immutable exercise/passage graphs with explicit membership references and grader revisions. | Attempt JSON snapshots also isolate edits, with per-attempt duplication and weaker relational guarantees. Version graphs add publish transactions and retention requirements. |
| Attempt consistency | Deduplicate starts per learner/key. Serialize save and finalization with a locking boundary and use per-item revisions. Grade synchronously under the same transaction. | Optional keys create ambiguous retries. Asynchronous grading requires a durable recovery protocol. Synchronous grading keeps atomicity and needs bounded exercise size. |
| Assessment payloads and policy | Use typed learner/author DTOs and versioned, deterministic marking for D03. Persist original outcomes. | Fully normalized storage adds tables; arbitrary JSON weakens validation. Typed protected fields and relational FKs for references keep storage flexible while preserving validation. |
| Public and Exam projections | Retain D02 public content and enforce D04 aid restrictions on the exam attempt surface. Apply explicit capability and ownership checks plus review gates. | Compare card-only public discovery with prompt previews. Closed-book assessment would require a different content/access model and owner review. Exam practice remains timed and excludes in-attempt aids. |

## Milestone 1 after approval

Proposed scope reconciles PRD §16 with Pack §11 by including only identity, security and a minimal frontend authentication shell. Reading authoring, Reading learner features and assessment follow M2, M3 and M4 respectively.

1. Approve architecture, O-01–O-04, O-11, O-16 and identity-relevant retention. Resolve or explicitly defer the other exit items and approve this scope. Record accepted ADRs and verify runtime compatibility in the selected Windows/CI and deployment environments.
2. Initialize approved backend and frontend foundations. Configure local PostgreSQL and deployment routing, health and structured errors. Keep sample environment configuration free of credentials. Use request-scoped auth and a shared API client.
3. Add identity-only versioned migrations for users, roles and membership, with canonical email uniqueness and seeded role definitions. Use a controlled Admin bootstrap. Validate fresh-database migration and repeat startup.
4. Implement session and CSRF flows, registration/login/logout, profile, auth screens and minimal signed-in shell. Establish authentication/authorization defaults, safe error bodies, identity lifecycle and audit events for enabled admin operations. Implement email functionality only if approved for this scope.
5. Verify clean builds and the AC-01/AC-02 foundation, plus migration and test portions of AC-16/AC-17. Exercise duplicate email registration, role escalation rejection, rotation, expiry/revocation, missing or stale CSRF including pre-auth writes, unauthorized responses, logout, and browser proxy/cookie behavior. Confirm separation of public reads and private paths with test-only routes if needed.
6. Obtain M1 acceptance before starting Reading implementation. Carry forward M2 publication/FK tests, M3 anonymous/private and vocabulary ownership tests, and M4 leakage/IDOR, timing, concurrency, timeout recovery and historical replay tests, including same-key starts under race and version changes.

Implementation remains pending explicit Product Owner approval.
