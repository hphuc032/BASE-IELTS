# Milestone 0 independent architecture review

**Status** Proposed for Product Owner review · 2026-11-26

Reviewed [PRD v1.0](IELTS_Learning_Platform_PRD_Agent_Guide.md) and [Architecture Pack v0.2](IELTS_Milestone_0_ERD_API_Architecture_Pack_v0.2.md). These are the corresponding files in the checkout. The prompt's canonical filenames are absent. The unversioned pack predates the v0.2 owner decision register. Repository baseline is branch main with documentation only.

## Approved decisions

| ID | Approved scope |
| --- | --- |
| D01 | Cookie authentication using a server-managed session |
| D02 | Anonymous reading of published PUBLIC Reading content |
| D03 | Multiple Choice Single, True/False/Not Given, Yes/No/Not Given, Short Answer and Sentence Completion |
| D04 | Both Learning and Exam modes on every MVP exercise |
| D05 | Unlimited independent retakes for public practice |

The PRD's hyphenated D-01–D-08 are separate product decisions. Pack §10 and the current owner instructions settle D01–D05. The companion contract, ADRs and Milestone 1 scope are proposals.

## Ranked findings

Severity describes the impact if the current draft ships as written. This is a design review of a documentation-only repository.

| Rank | Finding and evidence | Correction |
| --- | --- | --- |
| Critical | F01 · Pack §§3.3, 7.4–7.6, 14.6 describe protected fields but leave projection and review gates underspecified. The review bullets can be read as allowing owners to bypass the state gate. An accidental shared serialization path could expose the full key bank. | Separate public, attempt, review and author projections. Require both authorized scope and reveal state, including SSR and caches. |
| High | F02 · Pack §3 uses a version number and mutable question/paragraph FKs. §15 recommends versioned questions but does not bind groups, passage versions or evidence. FR-04/08/09 historical grading and review can drift or accept mismatched references. | Immutable passage and exercise graphs with composite membership FKs and a retained grading policy revision. |
| High | F03 · Pack §§7.1, 9 and PRD §2.3 still present auth strategy as open after D01. Pack §14.1 lacks an operational pre-login CSRF flow, token renewal and session revocation policy. | Close the strategy question. Approve topology, cookie scope, session expiry/revocation and complete CSRF sequence before scaffolding. |
| High | F04 · Pack §§7.2, 8 and 14.2 leave global discovery, parent visibility and direct-ID rules incomplete. Matrix grants broad Admin visibility while the PRD §4 and pack caveats require scoped access. | Central eligibility predicates, scope-checked lookup, separate author preview and private DTOs. Deny cross-learner access until a scoped workflow exists. |
| High | F05 · FR-07 requires an Exam timer and server restrictions. Pack §§10.2, 14.3 make duration optional and omit deadline behavior, recoverable mark-for-review and a public-content boundary. Earlier retake reviews can also contain the same answers. | Require a positive published Exam duration. Enforce a server deadline and assistance controls on the attempt surface. Obtain owner acknowledgment of the limits of public practice. |
| High | F06 · Pack §§5, 14.3, 14.5, 15.4 leave start replay optional, duplicate submit ambiguous and save/submit races unspecified. FR-08 requires safe retries and historical independence. | Required start idempotency key, per-item concurrency control and atomic finalization under a common attempt lock. |
| Medium | F07 · Pack §§3.3, 4.1 and PRD §2.3/§6.2 retain configurable modes, pending question subset and retake ambiguity after D03–D05. | Derive both modes for every MVP exercise, enforce the five discriminators and put no count cap on public attempts. |
| Medium | F08 · Pack §§3.3, 4.2, 14.4 place limits in protected key examples and leave text counting, normalization and missing answers undefined. FR-06/08 require deterministic grading. | Publish learner instructions/limits separately from answers and pin a precise grading policy. |
| Medium | F09 · Pack §§3.5–3.6 and 15.3 allow cross-passage progress/source links, reference missing normalized_term and duplicate indexes backing uniqueness. | Anchor references to versions, make normalization conditional on approved deduplication and index actual access paths. |
| Medium | F10 · Pack §§7.1, 14.1 conflict on /auth/me versus /users/me. Pack §11's backend-only scope omits the PRD §16 frontend foundation. FR-16 includes unpublish but §7.6 only models archive. | Use one profile route. Include minimal frontend auth foundation for approval. Define withdrawal separately from archival. |
| Low | F11 · Pack §§10.2, 10.3 and 14.2 differ on anonymous question previews. PRD §18 canonical paths and the pack's empty-repo assumption lag the current checkout. | Make exercise previews an explicit open choice and retain existing baseline filenames during review. |

## Milestone 0 exit assessment

D01–D05 are settled. The [corrected logical ERD and endpoint contract](MILESTONE-0-DESIGN-PROPOSAL.md) and [decision register, ADRs and plan](MILESTONE-0-DECISIONS-AND-PLAN.md) are ready for owner review. Milestone 0 remains open pending disposition of findings, approval of the architecture and Milestone 1 scope, and review of the PRD §16 architecture diagram and UX wireframe deliverables. Use the PRD §7.2 diagram as the starting point and confirm the browser/API boundary and Reading/attempt wireframes before closing the milestone.

## Files added in this review

- docs/MILESTONE-0-REVIEW.md
- docs/MILESTONE-0-DESIGN-PROPOSAL.md
- docs/MILESTONE-0-DECISIONS-AND-PLAN.md

Only these Markdown review artifacts were added. No source code, framework initialization, database migrations or dependencies were changed. Implementation awaits explicit Product Owner approval.
