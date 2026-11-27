# IELTS Learning Platform --- Milestone 0 Remediation Plan

**Document ID:** IELTS-M0-REMEDIATION-001\
**Based on:** `MILESTONE-0-FINAL-REVIEW.md`\
**Target baseline:** `IELTS_MILESTONE_0_FINAL.md` v1.0\
**Date:** 2026-11-26\
**Status:** Remediation instructions for Codex; not an approval to
implement Milestone 1.

------------------------------------------------------------------------

## 1. Purpose and authority

This document translates the independent review findings C01, H01--H06,
M01--M04 and L01 into precise documentation corrections and acceptance
tests.

### 1.1 Approved Product Owner decisions --- immutable

Do not change these decisions while fixing the design:

  -----------------------------------------------------------------------
  ID                      Approved decision       Approved value
  ----------------------- ----------------------- -----------------------
  D01                     Authentication          Cookie + server-managed
                                                  session

  D02                     Public Reading          Published PUBLIC
                                                  Reading is accessible
                                                  anonymously

  D03                     Reading question types  MCQ Single; T/F/NG;
                                                  Y/N/NG; Short Answer;
                                                  Sentence Completion

  D04                     Modes                   Learning and Exam

  D05                     Retakes                 Unlimited

  D06                     Session timeout         60-minute idle timeout;
                                                  7-day absolute lifetime

  D07                     Multi-device            One active
                                                  authenticated session
                                                  per account

  D08                     Registration            PENDING until mailbox
                                                  verification

  D09                     Learning feedback       Configurable per
                                                  exercise

  D10                     Public preview          Sample questions
                                                  without answers

  D11                     Admin bootstrap         Seed/provision from
                                                  deployment environment
                                                  configuration
  -----------------------------------------------------------------------

The remediation may change technical proposals, schemas, DTOs, endpoint
flows, ADR wording, tests and milestone gates. It must not silently
change D01--D11.

### 1.2 Strict scope

This is a design/documentation remediation only.

-   Do not write application source code.
-   Do not create or edit database migrations.
-   Do not initialize Spring Boot, Next.js or other frameworks.
-   Do not add dependencies.
-   Do not start Milestone 1.
-   Do not mark Milestone 0 closed.
-   Do not mark any reviewer proposal as owner-approved without explicit
    Product Owner confirmation.
-   Preserve the independent review report as an immutable historical
    artifact.

------------------------------------------------------------------------

## 2. Required deliverables

Codex must inspect the actual repository and produce:

1.  `docs/MILESTONE-0-FINAL-v1.1.md` --- corrected consolidated
    architecture pack.
2.  `docs/DECISION-REGISTER.md` --- canonical D01--D11 register plus
    technical open decisions and status.
3.  `docs/ADR-REGISTER.md` --- canonical ADR list, with approved
    decisions separated from proposals.
4.  `docs/MILESTONE-0-TRACEABILITY.md` --- every finding mapped to
    changed sections and acceptance tests.
5.  `docs/MILESTONE-0-REVIEW-RESPONSE.md` --- point-by-point response to
    C01, H01--H06, M01--M04 and L01.
6.  Update or add an authority index identifying canonical documents and
    superseded historical documents. Do not delete history.

Use existing repository naming conventions if they differ, but document
the mapping and do not create duplicate competing canonical files.

------------------------------------------------------------------------

## 3. Critical correction C01 --- bind credentials to mailbox ownership

### Required flow

1.  `POST /auth/register` accepts only email and display name. It must
    not accept a usable password.
2.  Create a `PENDING` account with `password_hash = NULL` only while
    the account is pending and has no usable credentials.
3.  Send a verification link containing a cryptographically random,
    single-use opaque token.
4.  `GET /auth/verify-email` displays a confirmation page only. GET must
    not consume the token, activate the account, set a password or
    create an authenticated session.
5.  The user submits `POST /auth/verify-email` with the token and a
    newly chosen password. Require session-backed CSRF protection.
6.  In one transaction, lock the user and token, validate that the
    account is still pending and the token is live, set the password
    hash, mark email verified, activate the account, consume/invalidate
    the token and invalidate all sibling tokens.
7.  Verification must not create an authenticated session. The user must
    log in separately.
8.  Initial registration, duplicate registration and resend must never
    mutate an existing account's password, display name or other profile
    fields.
9.  The original requester must not be able to authenticate with a
    password they supplied before mailbox ownership was proven.

### Schema and constraints

-   Make `password_hash` nullable only for the valid
    pending/no-credential state.
-   Add a database check or equivalent invariant ensuring ACTIVE
    accounts have a password hash and verified timestamp.
-   Ensure PENDING accounts cannot access learner-only APIs.
-   Define behavior for an existing ACTIVE email, existing PENDING
    email, disabled account and canonical-email collision without
    leaking account existence.

### API updates

Suggested registration request:

``` json
{
  "email": "learner@example.com",
  "displayName": "Alex Nguyen"
}
```

Suggested verification request:

``` json
{
  "token": "opaque-one-time-token",
  "newPassword": "user-chosen-password"
}
```

Registration/resend responses must be generic and resistant to account
enumeration. The exact response text is not a source of
account-existence information.

### C01 acceptance tests

-   Registration payload containing a password is rejected or ignored
    only if the contract explicitly documents strict unknown-field
    behavior; prefer reject unknown fields.
-   Pending account has no usable password.
-   A user cannot authenticate before successful verification.
-   A mailbox owner can set a password and activate atomically.
-   Initial requester-supplied password cannot authenticate after
    activation.
-   Replayed, expired, superseded and consumed tokens fail.
-   Verification GET has no state-changing side effect.
-   Verification POST without valid CSRF fails.
-   Verification does not issue a login session.
-   A failed transaction changes neither password nor account state.

------------------------------------------------------------------------

## 4. High corrections H01--H03 --- identity, sessions and Admin

### H01 --- atomic session replacement and expiry

Choose and document one authoritative storage/deployment boundary before
M1.

Acceptable alternatives, subject to owner approval:

-   **Single process:** per-account serialized critical section and
    authoritative in-memory account-to-session binding. A process
    restart invalidates all sessions and forces re-login.
-   **Shared deployment:** serialize one account-to-session binding in a
    shared authoritative store.

The binding must include: - account ID; - opaque session reference or
digest, never raw cookie; - generation/version; - authenticated-at
timestamp; - last qualifying activity; - absolute expiry timestamp; -
revocation state/version.

Login protocol: 1. Verify password and account status. 2. Enter the
account-level serialized replacement operation. 3. Recheck account
status and credential/role version. 4. Atomically replace the prior
binding and advance generation. 5. Activate the new server-side session
and issue its cookie only after the authoritative replacement succeeds.
6. If any step fails, do not issue a usable cookie.

Request protocol: - Every authenticated request validates the presented
session against the current authoritative binding. - Reject at or after
either idle or absolute expiry. - Qualifying activity updates
monotonically and never extends absolute expiry. - Explicitly exclude
static assets, health checks, public reads and CSRF bootstrap from idle
activity. Decide whether background polling qualifies. - Logout and
cleanup compare the exact binding/generation before revoking it. A stale
request must not revoke a newer login. - Define a revocation fence or
serialization policy for sensitive writes already in flight when
revocation occurs. - Privilege changes revoke the session and require
fresh authentication.

### H01 acceptance tests

-   Two concurrent logins result in exactly one valid session.
-   Old-cookie replay fails immediately after replacement.
-   Stale logout cannot revoke a newer session.
-   Concurrent touch and revoke cannot resurrect a revoked session.
-   Idle expiry is enforced at the exact 60-minute boundary.
-   Absolute expiry is enforced at the exact 7-day boundary even with
    activity.
-   Restart behavior matches the selected store.
-   Login racing account disablement cannot leave a usable session.
-   Role/credential version changes invalidate prior session.
-   Static/public/health/CSRF bootstrap traffic does not extend idle
    timeout.

### H02 --- verification token lifecycle and email delivery

Token requirements: - Bind token to pending user and canonical
email/address revision. - Generate cryptographically random opaque
token. - Store only a unique token digest. - Store created, expiry,
consumed and superseded/invalidation state or a monotonic generation. -
Permit at most one live token per pending account. - Serialize issuance,
resend, consume and activation using a consistent user lock. - Resend
supersedes all prior tokens. - Successful activation, email change or
disablement invalidates outstanding tokens. - Reject expired, consumed,
superseded and mismatched-address tokens. - Commit account/token state
before attempting email delivery. - Specify delivery failure, bounded
retry and resend behavior. - Use generic responses, per-account and
per-source rate limits. - Use configured HTTPS origin. - Verification
page must prevent token leakage through referrer, cache, logs, analytics
and third-party assets. - Browser verification POST requires
session-backed CSRF.

Resolve M1 email scope explicitly: - either select provider-backed
delivery for M1; or - approve a development/test-only mail adapter, with
production registration disabled until real provider configuration is
present.

Production must never auto-activate accounts because email is
unavailable.

Before implementation, choose token TTL, resend limits, throttling
policy, cleanup policy and provider/adapter behavior.

### H02 acceptance tests

-   Double consume: only one transaction succeeds.
-   Resend racing consume has deterministic serialized outcome.
-   Old token fails after resend.
-   Token fails after address change or disablement.
-   Delivery failure does not activate account or lose the pending
    state.
-   Registration/resend do not disclose account existence.
-   Per-account and per-source throttles work.
-   Verification POST requires CSRF.
-   Missing production email configuration disables registration safely.
-   Token is absent from logs, referrer, analytics and cache.

### H03 --- Admin bootstrap, recovery and last-Admin

Provisioning must be an explicit deployment-operator action consuming
managed deployment configuration. A one-time command may invoke this
same mechanism.

Within a serialized transaction: 1. Check durable bootstrap marker. 2.
Check whether an initial Admin already exists. 3. Check whether the
configured identity collides with any account, including PENDING
learner. 4. Create a new dedicated Admin identity and ADMIN membership
only if all checks pass. 5. Write durable completion marker. 6. Audit
action and outcome without recording secrets. 7. Retire/remove bootstrap
input after success.

Fail closed on collision. Never elevate an existing learner or pending
account and never overwrite password/profile fields. Replays after
success are no-ops.

Define: - authenticated operator recovery runbook; - auditable identity
reset/replacement; - revocation of old sessions during recovery; - no
restart-triggered rebootstrap; - last usable Admin protection across
demotion, disablement, deletion and grants; - how operator-provisioned
Admins satisfy account verification and login.

### H03 acceptance tests

-   Bootstrap creates exactly one initial Admin.
-   Existing active or pending email collision fails without mutation or
    elevation.
-   Concurrent bootstrap invocations cannot create multiple initial
    Admins.
-   Replay after success is a no-op.
-   Restart does not rebootstrap.
-   Secrets are not logged or stored in Git.
-   Recovery is authenticated, audited and revokes old sessions.
-   Last usable Admin cannot be accidentally removed/demoted/disabled.
-   Ordinary public registration cannot grant ADMIN.

------------------------------------------------------------------------

## 5. High corrections H04--H06 --- relational integrity, feedback and preview

### H04 --- exact composite key matrix

Restore and explicitly document matching unique targets, composite
foreign keys, nullability, parent ordering and restrictive deletion.

At minimum, include:

  ---------------------------------------------------------------------------------
  Child/reference                     Required composite membership
  ----------------------------------- ---------------------------------------------
  Passage current pointer             `(passage_id, passage_version_id)`

  Passage version identity            Unique `(passage_id, version_no)` and
                                      `(passage_id, version_id)`

  Exercise version identity           Unique `(exercise_id, version_no)` and
                                      `(exercise_id, version_id)`

  Exercise version to passage         `(exercise_id, passage_id)` and
                                      `(passage_id, passage_version_id)`

  Question to group                   `(exercise_version_id, group_id)`

  Evidence to question                `(exercise_version_id, question_id)`

  Evidence to passage version         `(exercise_version_id, passage_version_id)`

  Evidence to paragraph               `(passage_version_id, paragraph_id)`

  Correct option                      `(question_id, option_id)`

  Attempt item to attempt             `(attempt_id, exercise_version_id)`

  Attempt item to question            `(exercise_version_id, question_id)`

  Reading progress                    `(passage_id, passage_version_id)` and
                                      `(passage_version_id, last_paragraph_id)`

  Vocabulary source                   `(passage_version_id, paragraph_id)` plus
                                      learner ownership
  ---------------------------------------------------------------------------------

For every composite FK, show the exact referenced UNIQUE/PK target.
PostgreSQL needs a suitable unique target. Define nullability carefully:
only the version ID may be null in a nullable current pointer; do not
allow half-null composite references.

Also define: - unique role codes; - canonical slugs; - version numbers
and child positions; - parent-before-child ordering; - restrictive
deletion for published/referenced graphs; - FK indexes based on actual
access paths.

### H04 publication transaction

Use one transaction: 1. Lock stable passage, then exercise, in a
consistent global order. 2. Verify draft revision and author/publisher
scope. 3. Validate the complete graph, including question counts,
options, marking, evidence and score bounds. 4. Freeze all rows in the
published graph. 5. Switch eligible current pointers only after
validation. 6. Commit all changes atomically.

Only validated published versions become current. Public discovery and
new starts use one common eligibility predicate covering exercise,
parent passage and pinned passage version. Withdraw by removing
eligibility, not by mutating immutable versions. Existing attempt
behavior after ordinary withdrawal must be explicit. Exceptional
privacy/legal takedown remains separately governed.

### H04 acceptance tests

-   Invalid cross-version FK inserts fail.
-   Evidence cannot point to a paragraph from another passage version.
-   Correct option cannot point to another question's option.
-   Attempt item cannot point to a question from another exercise
    version.
-   Progress bookmark must belong to pinned passage version.
-   Invalid graph cannot partially publish.
-   Concurrent publish/start/withdraw obeys the lock protocol.
-   Public discovery and attempt start agree on eligibility.
-   Published content cannot be physically deleted while referenced.

### H05 --- Learning feedback reveal gates

Resolve apparent conflict between the general no-answer-key rule and
D09.

The only pre-submit reveal exception is: - mode = `LEARNING`; - pinned
exercise version policy = `AFTER_EACH_ANSWER`; - first valid nonblank
answer for that item has been committed and locked for scoring.

A clear, validation failure, stale write or mark-only update creates no
reveal.

If feedback is recoverable, use a distinct owner-only projection gated
by the same pinned mode, policy and persisted item lock. Generic
save/recovery DTOs remain answer-safe.

All other cases: - Exam: reveal only through authorized `GRADED`
review. - Learning + `AFTER_SUBMISSION`: reveal only through authorized
`GRADED` review. - Review requires both authorization/scope and reveal
state as independent mandatory predicates. - Cross-learner access
defaults to deny until separately specified. - Author DTOs use separate
scoped author routes. - Sensitive feedback/review responses must not
enter caches, SSR payloads or logs.

### H05 acceptance tests

-   First valid nonblank Learning answer under `AFTER_EACH_ANSWER` locks
    item and returns only that item's permitted feedback.
-   Re-answer after lock is rejected or follows a specifically approved
    rule; default is reject.
-   Blank/clear, invalid, stale and mark-only requests do not reveal.
-   Generic save/recovery response never includes marking fields.
-   Learning `AFTER_SUBMISSION` does not reveal before graded.
-   Exam never reveals before graded, regardless of exercise feedback
    setting.
-   Owner can review only after required reveal state.
-   Another learner cannot retrieve feedback/review.
-   Cache, SSR and log tests show no sensitive answer data.

### H06 --- fixed public sample and disclosure control

Use explicit versioned sample membership or deterministic version-scoped
count and order. Random per-request selection is prohibited because
repeated requests could cumulatively reveal the full exercise.

Define: - exact sample count; - rounding and cap behavior; -
deterministic order; - explicit membership FK to questions in the same
exercise version; - publish-time validation that sample fields contain
no editorial hints, marking, explanation or answer-revealing evidence; -
options remain in authored display order, independent of correctness.

Use one public DTO allowlist and one eligibility predicate across: -
public discovery; - passage/exercise detail; - slug and related
routes; - server-rendered payloads; - search/indexing; - cache behavior.

Withdrawal must invalidate public eligibility consistently; use no-store
where safe invalidation cannot be guaranteed.

### H06 acceptance tests

-   Repeated anonymous requests return the same version-scoped sample.
-   Non-sample questions never appear.
-   No answer keys, accepted variants, explanation, evidence hints or
    marking metadata appear.
-   Draft, withdrawn or restricted parent hides samples on every route.
-   Mismatched passage/exercise version is rejected.
-   All five question-type public projections are checked for leaks.
-   SSR hydration and search payloads are checked.
-   Exam is described as timed practice, not proctored closed-book
    enforcement; D02 and authorized historical reviews remain outside
    the attempt boundary.

------------------------------------------------------------------------

## 6. Medium corrections M01--M04

### M01 --- deterministic attempt/retry behavior

Specify and test: - canonical start identity includes exercise ID and
mode; - same learner/key/same canonical request returns same attempt; -
same key/different request returns 409; - authorized matching replay
returns original attempt after ordinary withdrawal or republishing; - a
different key creates a new attempt, preserving unlimited retakes; -
acquire common attempt lock before authoritative deadline check; -
reject saves at or after deadline, including concurrent saves; -
finalize every item outcome, including unanswered items, and aggregate
totals in one transaction; - sweep plus submit-driven finalization; -
overdue reads are read-only recovery or return a documented
pending-result response; - conditional save replay/no-op semantics; -
duplicate submit returns identical committed result; - record reason and
timestamps; - preserve autosave, refresh/network recovery, review flags,
navigation and manual-submit confirmation.

### M02 --- typed marking and persistence contracts

Restore per-question-type learner and protected DTO schemas. Define: -
exact enum domains and payload shapes; - option membership validation; -
public word limits and instructions; - normalization algorithm and
versioned fixtures; - counts for unanswered/incorrect/correct; - bounds
for points, total score, duration and progress; - unique role codes,
canonical slugs, version numbers and positions; - indexes for public
publication/topic lookups, FK joins, learner history and optional
deadline scans; - single source of truth for grader policy revision; -
historical explanation/marking snapshot semantics.

### M03 --- M1 acceptance and gates

Keep M1 limited to identity, authentication, minimal frontend and
identity-only migrations.

If M1 owns verification, include resend and confirmation UI/API.
Otherwise explicitly limit the development/test flow and keep production
registration disabled until the approved provider is configured.

Add: - cookie attributes and scope browser tests; - CSRF
bootstrap/refresh tests; - production-disabled pre-auth functionality
when required config is absent; - authenticated profile field
allowlists; - frontend states: registration, pending verification,
resend, verification confirmation, login, expired session, disabled
account; - negative tests for C01/H01--H03; - PostgreSQL concurrency and
migration tests; - ownership/role error and audit criteria; - separate
production deployment readiness from foundation acceptance.

### M04 --- document authority

Create a canonical authority index. Preserve historical documents and
mark them superseded, not deleted.

Explicitly distinguish: - PRD `D-01–D-08`; - Product Owner pack
decisions `D01–D11`; - ADR IDs; - open technical decision IDs.

Map old ADR/open-decision IDs. Explicitly supersede earlier: - JWT
alternatives; - optional Exam duration/modes; - card-only preview; -
after-submit-only Learning feedback proposals.

Update M1 prompt and links to real filenames. Do not claim open
technical choices are owner-approved.

------------------------------------------------------------------------

## 7. Low correction L01 --- examples and wireframes

-   Replace rendering-specific badge markup with plain Markdown in the
    canonical Markdown artifact.
-   Use password input placeholders only where a password is genuinely
    collected.
-   Remove password from initial registration example.
-   Separate public sample preview and authenticated active attempt
    wireframes.
-   Add pending-verification, confirmation, resend and error states.
-   Ensure GET verification is informational and POST verification
    consumes token and sets password.

------------------------------------------------------------------------

## 8. Required traceability matrix

Codex must fill exact section references and test identifiers after
updating documents.

  ----------------------------------------------------------------------------------
  Finding                 Required document section    Minimum acceptance evidence
  ----------------------- ---------------------------- -----------------------------
  C01                     Identity ERD,                Credential-binding and
                          registration/verification    no-preverification-password
                          API, ADR D08, security flow  tests

  H01                     Session model, login/logout  Concurrency, expiry, stale
                          flow, ADR D06/D07            logout, restart tests

  H02                     Token ERD, verification API, Lifecycle, CSRF, resend,
                          email operational decision   delivery and enumeration
                                                       tests

  H03                     Admin bootstrap, role        Collision, race, replay,
                          lifecycle, recovery          recovery, last-admin tests

  H04                     ERD, FK matrix,              Invalid FK and concurrent
                          publication/start/withdraw   publication tests
                          transaction                  

  H05                     D09, answer save/review      Reveal matrix and negative
                          DTOs, authorization          projection tests

  H06                     D10, public DTO,             Repeated sample and all-route
                          discovery/cache policy       disclosure tests

  M01                     Attempt state machine and    Save/submit/expiry race and
                          retry contract               idempotency tests

  M02                     Question DTOs and marking    Per-type schema/normalization
                          policy                       fixtures

  M03                     M1 scope and acceptance      Browser, PostgreSQL and
                          criteria                     negative security tests

  M04                     Authority index,             No conflicting active
                          ADR/decision mapping,        authority references
                          prompts                      

  L01                     API examples and UX          Examples consistent with
                          wireframes                   credential flow
  ----------------------------------------------------------------------------------

------------------------------------------------------------------------

## 9. Codex execution prompt

``` text
You are performing a documentation-only remediation of the IELTS Learning Platform Milestone 0 architecture.

Read completely:
- docs/MILESTONE-0-FINAL-REVIEW.md
- the current consolidated Milestone 0 final pack
- original PRD
- earlier review, design proposal, decisions/plan and v0.2 architecture pack
- this remediation plan

First inspect the actual repository tree, branch and git status. Do not assume filenames from prior chat. Identify exact current canonical and historical files.

Authority:
- D01–D11 are approved Product Owner decisions and must remain unchanged.
- Review corrections C01, H01–H06, M01–M04 and L01 are required design findings.
- Any technical choice not explicitly approved remains a proposal.
- The review report is historical evidence and must not be edited.

Strict scope:
- Documentation only.
- No source code, SQL migrations, framework initialization, dependencies or Milestone 1 implementation.
- Do not mark Milestone 0 closed.
- Do not treat Codex or reviewer recommendations as Product Owner approval.

Tasks:
1. Correct every finding in this plan.
2. Preserve D01–D11 exactly.
3. Make ERD composite keys enforceable by listing each unique target and composite FK.
4. Make API request/response projections explicit, especially registration, verification, save, feedback, review and public sample.
5. Make session, token, Admin, publication, grading and preview behavior atomic/testable.
6. Reconcile current and historical authority, ADR numbering and PRD decision IDs.
7. Add traceability from every finding to exact document sections and acceptance tests.
8. Update the Milestone 1 scope and prompt without authorizing its execution.

Before finishing, self-review each finding as CLOSED IN DESIGN, OPEN OWNER DECISION, or NOT ADDRESSED. Do not call a finding closed if its behavior or test is merely mentioned vaguely.

Deliver the files listed in Section 2 of this remediation plan.
Report:
- files created/modified;
- finding-by-finding disposition;
- decisions still requiring Product Owner approval;
- exact remaining blockers;
- confirmation that no code, migrations, frameworks or dependencies changed.

Stop and wait for Product Owner review. Do not start Milestone 1.
```

------------------------------------------------------------------------

## 10. Product Owner approvals still required after Codex remediation

Codex must not decide these silently. Present explicit recommendations
and consequences:

1.  Session storage and deployment boundary: single-process in-memory
    with restart logout, or shared store.
2.  Email delivery for M1: real provider or development/test adapter
    with production registration disabled.
3.  Verification token TTL, resend limits, rate limits and cleanup.
4.  Admin recovery operator model and last-Admin policy.
5.  Exact public sample count and deterministic selection.
6.  D09 feedback locking semantics: first valid nonblank answer locks
    for scoring; re-answer is rejected.
7.  Publication withdrawal behavior for existing attempts and
    exceptional takedown.
8.  Attempt expiry sweep cadence and overdue recovery.
9.  Exact compatible Java/Spring/Node/Next toolchain and same-origin/SSR
    deployment choice.

These are technical/operational decisions, not changes to D01--D11.

------------------------------------------------------------------------

## 11. Milestone 0 readiness gate

Milestone 0 may be recommended READY only when:

-   C01 and H01--H06 are fully corrected in the canonical pack.
-   M01--M04 and L01 are reconciled.
-   Each finding has concrete acceptance tests.
-   Required technical choices are approved or explicitly deferred with
    no M1 dependency.
-   Historical files remain preserved and clearly superseded.
-   Codex produces a final independent response with no unresolved
    Critical/High findings.
-   Product Owner explicitly signs off the corrected architecture and
    separately authorizes Milestone 1.

**Current status remains: NOT READY.** This plan does not grant
implementation authorization.
