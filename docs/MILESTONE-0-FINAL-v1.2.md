# IELTS Learning Platform · Milestone 0 architecture v1.2

**Status** Remediated proposal for Product Owner review. Milestone 0 remains open. Milestone 1 requires separate explicit authorization.

## 1. Authority and architecture

[Authority index](DOCUMENT-AUTHORITY.md) governs precedence. The [decision register](DECISION-REGISTER.md) preserves D01–D11 verbatim from v1.0. Technical contracts below are proposed, with choices and gates in the decision and [ADR registers](ADR-REGISTER.md). [Traceability](MILESTONE-0-TRACEABILITY.md) defines acceptance cases. The original PRD retains authority over product requirements outside explicit supersessions.

Proposed deployment is one HTTPS ingress routing UI to Next.js/TypeScript and /api/v1 to a private Spring Boot modular monolith backed by PostgreSQL. Backend modules are identity, content/Reading, question, assessment, vocabulary and progress. Flyway, JPA, OpenAPI and exact runtimes await approval. For M1, browser-fetch private data and avoid personalized SSR. Use host-only Secure, HttpOnly, SameSite=Lax cookie scoped to /api/v1. Match cookie scope on clear. Preserve the Spring Boot and PostgreSQL directions already recorded. Keep other provider integrations and later-release features deferred. Email verification is governed separately by §2 and TECH-03.

## 2. Registration and verification

### 2.1 Identity and tokens

Users have UUID PK, unique nonnull canonical email, display name, PENDING/ACTIVE/DISABLED status, password hash, verified timestamp/method, monotonic security and email revisions, and UTC timestamps. Normalize email consistently without provider alias folding. PENDING has neither hash nor verification timestamp. ACTIVE requires both. A disabled formerly pending identity may have neither. Enforce matching nullness, and require verification method whenever verified. Roles have UUID PK and unique LEARNER/TEACHER/ADMIN codes. user_roles has PK (user_id, role_id), required FKs to users and roles and nullable assigned_by FK to users. Public registration inserts PENDING plus LEARNER atomically, without a password.

Verification tokens have UUID PK, required user FK, email revision, unique digest, issued/expires timestamps and nullable consumed/invalidated timestamps. Generate 256 random bits and persist only the digest. A partial unique index on user_id where neither consumed nor invalidated permits one outstanding row, even after expiry. Under the user-row fence, invalidate an outstanding row before inserting a replacement. Issue, resend, consume, disable and address change serialize against the same user row. For token consumption, use digest lookup to identify the candidate user, lock user then token, and recheck digest, state, email revision, expiry and one-use state. Activation atomically sets the new adaptive password hash and verified timestamp, transitions to ACTIVE, consumes token and invalidates siblings. Rollback restores all state. Initial or duplicate registration and resend cannot modify an existing profile or password. Activation issues no authenticated session.

### 2.2 Delivery, timing and browser boundary

A valid request from an eligible source returns a generic 202 independent of account status, issuance throttle or delivery outcome. Count source limits before account lookup, and account buckets by canonical address in equivalent fashion for existing and nonexistent addresses. Source throttling is uniform. Use a common minimum response window after parsing/rate checks (proposed 300 ms), with bounded admission and database work and no synchronous SMTP dependency. Measure observable timing distributions under load before enabling production. Do not claim exact indistinguishability. Report delivery outcome through bounded internal metrics only.

Before token issuance reserve bounded volatile mail-queue capacity or leave the request on the generic no-issue path. Commit token state before handing raw token to the reserved in-process queue. Release capacity on rollback. Queue loss on crash does not activate the user; a later resend supersedes outstanding tokens. Retry a transient send failure at most once after rechecking live generation and expiry. Delivery may still race resend after the eligibility check. Only the current token can activate. Apply TECH-04 cooldown, resend and source limits. Unauthenticated resend can supersede a valid earlier link; use throttling and clear expired/superseded recovery UX. Provision real delivery only when approved. Unconfigured production register/resend return uniform 503 before mutation; dev/test adapter never runs in production.

Use configured HTTPS origin and put the token in the fragment. GET /auth/verify-email is an inert confirmation page with no auth creation, consumption or activation. Use no-store, no-referrer, restrictive CSP (only the designated first-party bootstrap, no third-party scripts, framing, analytics or worker registration) and no URL/token logging. The first script reads then removes fragment via history replacement before other work, keeps it only in memory, and clears it when leaving or restoring the document from a browser history cache. Disable link tracking/rewrites for verification mail. No token echo, storage, telemetry or cached hydration. User explicitly submits token and new password with session-backed CSRF. On stale CSRF obtain a fresh pre-auth token and allow a deliberate retry while the verification token remains in memory. Reload or lost fragment leads to reopen-email/resend UI. The link may be visible to the recipient or their browser tooling before removal; publish the limits of the guarantee.

## 3. Session and concurrency

### 3.1 Sole authentication gate and Spring integration

Propose one serving application process, process-local account bindings, and PostgreSQL account revisions/fences shared with operator processes. No sticky multi-instance deployment or automatic takeover of an unconfirmed old process. At cutover, stop new ingress to the old process, drain or terminate and confirm it has exited before admitting traffic to the replacement. Fail closed with the route unavailable if exit cannot be confirmed. Crash loses all app sessions; restart always requires login. Test this operating constraint on the selected deployment before enabling authentication.

The authoritative binding is keyed by account and records a server boot identifier, unique generation, digest of opaque session ID, account security revision, authentication time, last qualifying activity and absolute expiry. Only a request whose binding and authoritative ACTIVE database row match may establish authentication. A stored Spring Security context is transport/cache material. Use a version-pinned JSON login integration with explicit SecurityContextRepository persistence and a gate after context load and before authorization. For each protected REQUEST, ERROR, FORWARD and ASYNC dispatch, revalidate or deny. Do not resume a protected asynchronous task from inherited context alone. Recreate authorities from state pinned to the matching revision. Clear rejected context; no fallback bearer, cache-only or container-only authentication.

Use server time with a nondecreasing elapsed-time source for process-local expiry. Reject at idle >= 60 minutes or absolute >= 7 days. Qualifying successful protected application work advances last activity monotonically, capped by the lifetime cutoff. Public reads, failed calls, health, static assets, polling and CSRF bootstrap do not touch the authoritative idle clock. Configure backing authenticated session retention to cover the entire 7-day absolute window and prevent container TTL/touch from granting extra time or ending an otherwise valid binding early. Cleanup retires all expired/revoked storage under exact generation checks. Bound anonymous CSRF sessions separately (proposed 15-minute lifetime, source admission limits and bounded cleanup). No raw cookies or synchronizer tokens enter logs.

### 3.2 Login, revocation and failure ordering

Verify the password outside the fence, then take the DB account row lock and process-local account guard in §5.3 order. Recheck status, security revision and password version used for verification. Create a fresh server session ID and fresh CSRF state without emitting a cookie yet. Persist the new Spring context as provisional. Stage the new generation under both locks after context preparation and durable recheck. Hold the local guard through DB completion and expose the binding and cookie only after successful commit. On definitive rollback discard provisional state and restore the unchanged prior binding before unlocking. On uncertain commit invalidate the affected bindings and require re-login. If a durable mutation commits between login commit and cookie delivery, request-time revision checks reject the stale cookie. If publication succeeds but response issuance reports failure, compare-and-revoke only that generation; never resurrect the prior one. A lost response after it was handed to the network is indistinguishable from delivery: only the current binding is valid, and the user may log in again to replace it. No provisional context can authorize. No unbounded local lock registry; use bounded striped guards in deterministic order when more than one is needed.

Credential changes, role changes, disablement and recovery take the same DB fence and advance security revision in the transaction. Protected writes take account row and local guard, revalidate generation/revision and expiry before mutation, and hold through commit. The write is ordered before revocation or rejected. A write authorized before expiry may commit under its bounded transaction budget; new admissions at the boundary fail. For supported protected reads materialize the permitted projection under the fence before release. External operator mutations use the database fence even when no local guard is available. A revision mismatch denies the next request immediately after the durable change regardless of local eviction. Logout and cleanup compare exact binding, never delete a replacement, and never depend on browser state for revocation. A DB outage fails private authentication closed. Transaction cancellation/crash has no speculative success response.

### 3.3 Cookie and CSRF ordering

Same-origin HTTPS, private API, host-only Secure HttpOnly SameSite=Lax cookie scoped to /api/v1. Use separate pre-auth cookie and authenticated cookie names so late token bootstrap cannot overwrite authentication. GET /api/v1/auth/csrf selects the current valid authenticated session when present or a bounded anonymous session; response has token and header name, no-store. All browser writes require X-CSRF-TOKEN in the expected session, including public registration, verification, resend and login. Successful login rotates session and CSRF; client refreshes token after login/logout. Verify that login clears old CSRF before publishing the authenticated replacement. Public Reading never creates a session. Stale CSRF returns generic 403; refresh without silently retrying a non-idempotent command.

Coordinate login/logout across tabs using a same-origin browser coordination lock where supported. Logout revokes the exact server binding and returns 204; under this proposal it sends no asynchronous authenticated-cookie clear that could erase a later login. The revoked cookie is inert until replacement or its bounded expiry. Logout clears the app's private UI/token state. Browsers can still reorder two login responses. If a stale cookie wins delivery, protected access returns 401 and the UI clears private state and offers one bounded reconciliation followed by explicit re-login. It never retries stale authenticated writes or promises latest-login cookie preservation. The server retains at most one valid binding throughout.

## 4. Admin lifecycle

Provision only through an explicit authenticated deployment operator command reading managed deployment configuration. No startup hook or public API. Identity foundation seeds a singleton governance row before any bootstrap. Under its DB row lock, test durable bootstrap completion marker, existing Admin and canonical-email collision. Create the dedicated ACTIVE account, ADMIN grant, marker and success audit in one transaction. Existing ACTIVE or PENDING identity collisions fail without mutation. A completed marker makes replays no-ops. Record denied outcomes separately, with a correlation ID and no secrets. Retire bootstrap configuration after success. Role definitions and the governance lock row are identity foundation data; no credential is seeded.

A usable Admin is ACTIVE with a valid credential, verified evidence and ADMIN membership. For a dedicated operator identity, require independently verified control of the configured mailbox, recorded as operator-attested evidence with actor IDs and timestamp. Public users follow normal mailbox-token verification. Compute the post-change usable Admin count under the governance lock for every Admin eligibility mutation, including cleanup, deletion, role changes, verification changes and credential invalidation. Ordinary changes must leave one usable Admin once initialized. Runtime DB role cannot directly write roles, state or credential columns; reviewed identity routines are the only write boundary, with admin eligibility operations always taking governance first. M1 has no public role or recovery management routes.

Recovery requires separate authenticated operator and approver identities with distinct deployment capabilities. Store a scoped, expiring one-use approval tied to target and allowed action; consume under governance lock in the recovery transaction. Reject self-approval, missing/expired approvals, wrong targets, public account promotion or restart-triggered bootstrap. Reset a dedicated account, or create its verified replacement before revoking the old usable Admin inside the same transaction. Advance affected security revisions and use §3.2 fencing; deliver replacement credentials only through an approved managed channel. Audit success with the transition and one-use approval, and failed attempts separately. Audit append interfaces prohibit app update/delete; operational retention and access follow TECH-12. Recovery never resets bootstrap completion.

## 5. Logical ERD and publication

All entity IDs are UUID PK unless specified. Use UTC instants and required FKs unless explicitly optional. Users own attempts, vocabulary and progress. Authors reference users; topics optionally categorize passage versions. Stable passages and exercises each point to their own current edition. Each exercise belongs to one stable passage and every exercise edition pins an exact passage edition. Passage editions contain paragraphs and optional language translations. Exercise editions contain groups, questions, ordered options, protected marking, explanation, evidence and public sample membership. An attempt pins one exercise edition and initializes one item per question.

### 5.1 Composite FK matrix

Every target tuple below is explicitly UNIQUE unless denoted PK. Nonnull is the default, including every constituent of a required composite. Use normal nullable FK matching only as described below.

| Referencing columns | Exact referenced target |
| --- | --- |
| passages(id, current_version_id) | passage_versions(passage_id, id) |
| exercises(id, current_version_id) | exercise_versions(exercise_id, id) |
| exercise_versions(exercise_id, passage_id) | exercises(id, passage_id) |
| exercise_versions(passage_id, passage_version_id) | passage_versions(passage_id, id) |
| questions(exercise_version_id, group_id) | question_groups(exercise_version_id, id) |
| marking(question_id, correct_option_id) | options(question_id, id) |
| evidence(exercise_version_id, question_id) | questions(exercise_version_id, id) |
| evidence(exercise_version_id, passage_version_id) | exercise_versions(id, passage_version_id) |
| evidence(passage_version_id, paragraph_id) | paragraphs(passage_version_id, id) |
| preview_members(exercise_version_id, question_id) | questions(exercise_version_id, id) |
| attempt_items(attempt_id, exercise_version_id) | attempts(id, exercise_version_id) |
| attempt_items(exercise_version_id, question_id) | questions(exercise_version_id, id) |
| reading_activity(passage_id, passage_version_id) | passage_versions(passage_id, id) |
| reading_activity(passage_version_id, last_paragraph_id) | paragraphs(passage_version_id, id) |
| vocabulary_sources(passage_version_id, paragraph_id) | paragraphs(passage_version_id, id) |
| vocabulary_sources(user_id, vocabulary_id) | user_vocabulary(user_id, id) |

Only the pointer version is nullable for current pointers. Correct option is required for single-choice and null otherwise; marking.question_id is also a standalone FK and PK. Last paragraph and source paragraph are optional, but their version is required and has its own FK. A manual vocabulary entry may have no source row. Vocabulary source owner must match the session and the vocabulary owner. Evidence always has a paragraph. Missing parent targets or incomplete required tuples fail. Single-column FKs include version to stable identity, group to exercise version, option/explanation to question, paragraph to passage version, translation to paragraph, attempt to learner and exercise version, and progress to user. Preview members and attempt items use PK (exercise_version_id, question_id) and (attempt_id, question_id), respectively.

#### Standalone FK dictionary

Each id target is the named table's PK. The only nullable relationships are explicitly identified. Composite references above cover remaining identity/order relationships without redundant standalone FKs.

| Referencing column | Target | Nullability |
| --- | --- | --- |
| user_roles.user_id; user_roles.assigned_by | users.id | user_id required; assigned_by optional |
| user_roles.role_id | roles.id | Required |
| verification_tokens.user_id | users.id | Required |
| admin_audit.governance_id; recovery_approvals.governance_id | admin_governance.id | Required |
| recovery_approvals.target_user_id | users.id | Required |
| passages.author_id; exercises.author_id | users.id | Required |
| passage_versions.passage_id; exercises.passage_id; reading_activity.passage_id | passages.id | Required |
| passage_versions.topic_id | topics.id | Optional |
| exercise_versions.exercise_id | exercises.id | Required |
| paragraphs.passage_version_id; vocabulary_sources.passage_version_id | passage_versions.id | Required even with no source paragraph |
| translations.paragraph_id | paragraphs.id | Required |
| question_groups.exercise_version_id; attempts.exercise_version_id | exercise_versions.id | Required |
| options.question_id; marking.question_id; explanations.question_id | questions.id | Required |
| attempts.learner_id; reading_activity.user_id; user_vocabulary.user_id | users.id | Required |

admin_governance uses a singleton PK with a fixed allowed value. admin_audit and recovery_approvals use UUID PKs. Deployment operator and approver identifiers are immutable external principals, not learner FKs. Bootstrap/recovery audit uses externally assigned immutable actor identifiers and logical target references retained across identity retention. Verification revision is validated under the user fence and does not reference a mutable revision as an FK. The attempt's canonical requested exercise ID is the stable exercise associated with its edition (persist it as part of the immutable request identity and verify equality at creation under the publication lock). Deadline job state is on attempts, not a separate relation.

Marking has exactly one row per published question. Freeze reads the referenced question in the same graph and enforces its discriminator and corresponding exact §7.1 marking schema, including MCQ correct_option_id nonnull and null for all other types. The conditional relationship is enforced at the privileged freeze boundary, not via a row-local check pretending to see another table.

### 5.2 Keys, checks and indexes

Unique canonical slugs on passages and topics; unique (passage_id, version_no) and (exercise_id, version_no). Unique positions per passage edition, exercise edition, group and question for paragraphs, groups, questions and options respectively. Unique option key per question and canonical language per paragraph. One marking row and up to one explanation per question. Attempt start key unique per learner. Reading activity unique per (user_id, passage_id); old bookmarks do not transfer to newer editions. Use positive positions and version numbers. Proposed points numeric(6,2), total numeric(8,2), positive integral Exam duration in seconds bounded at 86,400, progress 0–100, score 0–max and item awards 0–item max. Reject total overflow at publish. State checks bind mode to deadline and terminal status to result/timestamps. Index candidate paths are current public publication date/id, topic/date/id, exercise by passage, attempt by version and (learner, started_at, id), progress/vocabulary owner/time and partial in-progress Exam deadline. Add uncovered FK lookup indexes as query plans warrant; reuse indexes backing keys. Store verification expiry lookup as a cleanup index candidate.

### 5.3 Global locks, publication and snapshots

All writers use this order and acquire their entire footprint before mutation: (1) singleton Admin governance row when Admin eligibility is affected, (2) DB account rows sorted by UUID, (3) process-local account guard stripes sorted by stripe index, (4) token/approval rows sorted by ID when applicable, (5) stable passage rows sorted by ID, (6) stable exercise rows sorted by ID, (7) attempt rows sorted by ID, (8) owned child mutations. Skip unused levels, never acquire an earlier level while holding a later one. No child-specific prelocking or lazy ORM writes before parent fences. Cross-process identity writers take the DB prefix. System expiry workers take an attempt lock only and never acquire account/content locks. Protected attempt writers acquire the identity prefix, then attempt; fresh starts acquire the identity and content prefixes, then initialize the new attempt/items. An owner-scoped replay can be looked up without first taking an attempt lock or acquiring content locks. Foreign-key checks target existing, non-deletable parents for published references. Draft deletion holds its stable parent locks. Operational operations touching multiple resources must predeclare and sort them; if the set changes, discard and retry the transaction. Enforce bounded lock/statement/transaction budgets. Abort on deadlock/serialization error, and retry a bounded number from outside the transaction only for safe replayable operations. Never emit success before commit.

Application, worker and authoring DB roles have no direct content-table DML. Narrow database write routines validate required draft state, current pointer targets and revision under the stable parent locks. Give the migration/maintenance identity separate, restricted ownership without runtime credentials. The database write boundary enforces: freeze is one-way; insert into a frozen version, move into/out of one, update/delete a frozen row or descendant all fail; updates of published pointers only accept validated frozen versions belonging to the stable parent or null. Make guards apply to every graph table, including options, translations, marking, evidence and samples. Protect stable identity ownership/parent assignment from mutation that changes historic eligibility/pinned membership. Verify at the service boundary author/publisher principal and role using the revision fence before invoking the restricted writer. No general-purpose draft/admin bypass for past editions.

In one transaction, lock passage(s) then exercise(s), validate scope, current draft revision, complete graph and §7.3 limits; freeze children and version, then switch eligible pointers. Start holds the same relevant locks, rechecks eligibility and pins that committed version. Parent republish may hide stale exercise editions until coordinated republish. Withdrawal clears eligible pointer(s), leaving the frozen graph intact. Published content is retained and physically undeletable through supported runtime paths. Ordinary withdrawal leaves prior owners' pinned recovery, submission and review under the proposed policy. Exceptional takedown requires separately approved policy. Failure at any freeze or pointer step rolls back the whole change.

Public eligibility is current frozen PUBLIC passage; an exercise additionally needs a current frozen PUBLIC exercise edition pinned to that exact passage edition. Each public response, including eligibility, count, paging, membership and projection, uses one database snapshot (single statement or read-only repeatable-read transaction). Materialize DTOs before releasing it; no lazy fetch from a newer snapshot. A request whose snapshot precedes withdrawal may return the coherent previous edition. A fresh snapshot after commit cannot. SSR must use that same materialized projection; no mixing fragments from independent editions.

## 6. API conventions and identity projections

API paths are relative to /api/v1 unless explicitly labeled UI. JSON requests reject unknown fields, and responses use explicit allowlists. A single result uses {data}; collections use {data,page} with page number, size, totalElements and totalPages. Errors use {error:{code,message,details,traceId}} with safe field errors and no protected values. Use 400 invalid input, 401 missing/expired private authentication, 403 invalid CSRF or forbidden capability, 404 scoped resource absent/concealed, 409 state conflict, 412 stale precondition, 428 missing precondition, 429 source throttling, and 503 unavailable verification delivery configuration. Private endpoints require an ACTIVE session and applicable ownership and role, regardless of identifier format.

| Endpoint | Request and gate | Response allowlist |
| --- | --- | --- |
| GET /auth/csrf | Public synchronizer bootstrap | 200 {token,headerName}; no-store |
| POST /auth/register | Public + CSRF; {email,displayName} only | 202 {message}; generic |
| POST /auth/resend-verification | Public + CSRF; {email} only | Same 202 {message} as registration |
| GET /auth/verify-email (UI) | Informational confirmation route | HTML form/status only; no token echo; no auth change |
| POST /auth/verify-email | Public + CSRF; {token,newPassword}; newPassword is user-entered | 204, no authenticated session; invalid token has a uniform error |
| POST /auth/login | Public + CSRF; {email,password}; password is user-entered | 200 safe profile and session cookie; generic failure |
| POST /auth/logout | Exact current binding + CSRF | 204 after revocation; no authenticated Set-Cookie clear, per §3.3 |
| GET /users/me | ACTIVE | 200 {id,email,displayName,roles,status} |
| PATCH /users/me | ACTIVE + CSRF; {displayName} | Same safe profile |

Registration example accepts only email and displayName. Password fields appear only in confirmation and login. Password reset and profile email change endpoints are deferred. No DTO contains a password hash, raw session identifier, token digest or account revision.

## 7. Question contracts and grading

### 7.1 Typed shapes

| Type | Learner answer object | Protected marking object |
| --- | --- | --- |
| MULTIPLE_CHOICE_SINGLE | {selectedOptionId: UUID} | {correctOptionId: UUID}, membership FK |
| TRUE_FALSE_NOT_GIVEN | {value: TRUE, FALSE or NOT_GIVEN} | {value: same enum} |
| YES_NO_NOT_GIVEN | {value: YES, NO or NOT_GIVEN} | {value: same enum} |
| SHORT_ANSWER | {text: string} | {acceptedAnswers: nonempty string array, caseSensitive: boolean} |
| SENTENCE_COMPLETION | {text: string}, one blank per item | Same protected shape as SHORT_ANSWER |

Every public/learner question shape has id, type, position, prompt and instructions. Include ordered options {id,key,content} only for MCQ and public maxWords for text types. Attempt questions also have points, outside the sample projection. An exercise version requires at least one group and each group at least one valid question. MCQ requires at least two ordered choices, and the other types have no option rows. Null answer uses the outer answer field only. Reject malformed and extra fields, unknown options and wrong enums. Proposed text cap is 2,000 Unicode scalar values. Require positive maxWords and consistent instructions; validate nonempty, unique normalized variants under the same cap and word limit at publish.

### 7.2 Determinism and history

Normalize text using NFC, trim and collapse Unicode White_Space to ASCII space, and use locale-independent lowercase for case-insensitive comparisons. Preserve punctuation, accents and digits. Count space-delimited tokens containing a Unicode letter or number. Null/normalized empty is UNANSWERED. Valid text above the word limit is INCORRECT. An exact normalized match within limits, exact enum match or correct member option earns full fixed-precision points. INCORRECT and UNANSWERED earn zero. Aggregate one outcome per item, an exact fixed-precision sum and max, and separate correct/incorrect/unanswered counts summing to the question count. No inferred IELTS band.

The frozen exercise version is the sole grader revision reference; retain each revision and its fixture corpus. Persist original item outcomes and result totals. Resolve keys/explanations using the immutable version during authorized review. Changes require a new version and do not rewrite historical outcomes.

### 7.3 Enforceable workload limits

Proposed ceilings, pending TECH-09 performance validation: 20 groups, 100 total questions, 6 choices per MCQ, 12 variants per text item, 2,000 Unicode scalars and 8 KiB UTF-8 per answer or variant, 100 total points, and 512 KiB UTF-8 for the complete canonical versioned grading graph (including text, explanations and feedback inputs). Positive fixed-precision points apply. Cap incoming assessment authoring bodies before parsing at 768 KiB, answer/mark request bodies at 16 KiB, and start at 4 KiB. Bound nesting to the explicit shapes. Count/size checks cover complete drafts on edits and again atomically at publication. Grade at most 100 items and at most 1,200 variant comparisons. Pre-normalize accepted variants at freeze under the pinned revision; never call external services in a grading transaction. Reject over-limit bodies without mutation, and reject invalid graphs without pointer changes.

## 8. Public and author access

Public practice means a published PUBLIC exercise available for anonymous discovery/sample; creating, saving and submitting attempts requires an ACTIVE verified learner, and eligible public exercises have no lifetime or pedagogical retake cap. Temporary fair request throttles may defer starts without limiting eventual count.

| Principal | Reading and sample | Persisted practice |
| --- | --- | --- |
| Anonymous | Eligible PUBLIC pages, discovery and answer-free samples | Deny |
| PENDING | Same public surface, plus separate verification flow | Deny |
| ACTIVE verified learner | Same public surface and own authorized history | New independent attempts on eligible public exercises; owner-only save, submit and review |
| Restricted/private exercise | Conceal from public and ordinary learner discovery; no fresh attempt | Deny unless an approved explicit grant defines access |

Public route inventory: GET /reading, /reading/{id}, Reading slug detail, GET /exercises, /exercises/{id}, exercise slug detail, GET /reading/{id}/exercises and related modules, search, permitted SSR and hydration. All use the centralized §5.3 eligibility and snapshot projector. No public edition-addressable or archive query, author DTO, personal field or session creation. Reading projects safe metadata, ordered text and permitted translations. Exercise card exposes {id,title,summary,modes,examDurationSeconds,sampleQuestions}. Sample uses §7.1 safe shapes without points, keys, accepted variants, feedback, marking rules or evidence. Publish validation and editorial review prohibit answer indicators in visible text or authored options. Option order is authored, independent of correctness.

Propose exactly max(1,min(2,floor(questionCount/10))) fixed members for each nonempty immutable edition, selected by publisher and emitted in group/question position order. Membership is FK-bound to the same edition. A successor can select different samples after approval; stability is per edition, without a cross-edition cumulative disclosure guarantee. This is timed practice and public Reading and authorized older reviews remain accessible independently. Keep public responses no-store at browser/ingress/SSR, and exclude sample-bearing pages from external indexing with noindex and sitemap omission. Internal search operates on current eligible safe metadata and may attach sample only via the same snapshot projector. Indexing, cache policy and separately approved media access are distinct controls; withdrawal cannot retract previously retained public copies.

Authoring routes under /teacher/reading and /teacher/exercises use scoped draft/preview/edit/publish/withdraw/archive operations, independent DTOs, and publisher capability for publication. Private, auth, author, attempt, review and feedback payloads never enter shared caches, SSR, indexes, telemetry or logs. Private progress and vocabulary CRUD derive owner from authenticated verified learner, and recheck eligible source membership under §5.1. Staff role does not confer cross-learner review.

## 9. Attempts, commands and reveal

### 9.1 Start and finalization

POST /attempts takes {exerciseId,mode}, CSRF and a required opaque Idempotency-Key (16–128 URL-safe ASCII characters). It requires ACTIVE verified learner and stores the canonical pair. Same learner/key/pair replays 200 with original identity even after ordinary withdrawal, without creating an attempt. Different pair yields 409. One created attempt per key via the unique constraint; handle a conflict by checking committed winner after rollback. Return 201 with Location only after commit. Fresh start checks §5.3 and initializes one item per question. New keys on eligible exercises remain independent. Use temporary fair throttles (proposed burst 5 starts and 10 starts/min per account, refill continuously); never impose a lifetime quota or cap simultaneous historical records. Exam pins a server deadline at start; Learning has no deadline.

All updates and finalization lock the attempt row in §5.3 order. For saves/submit take server time after locking; no save/mark is accepted at or after deadline or in terminal state. Finalization freezes every item, computes deterministic outcomes (§7), persists immutable totals, GRADED, reason and timestamps in one transaction. If manual submit takes the lock before deadline use MANUAL and decision time. At/after deadline use TIME_EXPIRED and deadline as effective submitted time. Duplicate submits return stored identical summary. Rollback leaves all item/result states unchanged. GET never finalizes; overdue recovery is read-only and result/review return RESULT_PENDING for authorized owners until finalization. Refresh and reconnect do not extend time.

Propose one sweep worker per deployment, every minute, scanning at most 100 ready IDs per cycle ordered by next eligible time, deadline and ID and processing at most 20 claims. Each attempt is processed in its own short transaction with a nonblocking attempt claim (skip locked) and deadline/state recheck. Keep scanning past a contended candidate within the scan cap. No batch-wide locks or account dependency. On failure, rollback then record bounded retry state in a separate guarded transaction only if still overdue and ungraded; exponential 1–15 minute backoff prevents queue starvation, alert after five consecutive failures. A crash between rollback and retry update leaves it eligible next cycle. Owner submit bypasses sweep backoff. Proposed budgets: 250 ms lock wait, 2 s DB statement and 5 s transaction deadline for assessment mutations, including computation checks. On transient contention return retryable 503 after rollback with bounded client jitter and reuse the start key or submit endpoint; no background retry of ambiguous answer edits. Benchmark maxima and approve capacity, budgets and backoff before M4 (TECH-09).

### 9.2 Disjoint editing commands

Both commands require owner, verified ACTIVE session, matching question in pinned edition, CSRF and a shared item If-Match. After locking, validate state/deadline, command shape and type, then check revision. Missing precondition returns 428, stale returns 412 regardless of matching payload. Equality at the current revision is a state no-op, not a retrievable command replay; no timestamp/revision change or newly generated feedback. On timeout or 412 reconcile with GET before deliberately resending. Concurrent answer, mark or submit serialize on the attempt. A successful mutation increments the item revision. Terminal or overdue updates are 409.

- PUT /attempts/{id}/answers/{questionId} accepts exactly {answer: typed object or null}. Reject mark fields. It changes answer only. In Learning with pinned AFTER_EACH_ANSWER, the first valid nonblank answer commits a score lock; syntactically valid over-word-limit answers count as incorrect and lock. Once locked, different answer or clear is 409. Blank, unchanged, invalid, stale and rolled-back requests never trigger feedback.
- PUT /attempts/{id}/marks/{questionId} accepts exactly {markedForReview: boolean}. Reject any answer field, including null. This command cannot mutate the score lock or answer and never emits feedback. A mark remains editable during IN_PROGRESS, before deadline, even with a scoring lock.

The winning answer write computes the permitted item projection from the exact committed answer and immutable version, captures it with that revision, and emits it only after successful commit on that request. A subsequent mark or submit cannot alter the locked input. A lost winning response can recover through the feedback GET using the persisted score lock. A failed commit emits nothing.

### 9.3 DTOs and independent reveal gates

| Endpoint | Required gate | Allowlist |
| --- | --- | --- |
| POST /attempts | §9.1 | {attemptId,exerciseVersionId,mode,status,startedAt,deadlineAt} |
| GET /attempts and /attempts/{id} | Owner | Safe history; detail includes serverTime, readOnly, English passage, groups, safe questions, and own item states/review flags. Learning may include authorized translation/lookup; Exam omits both. |
| PUT answers or marks | §9.2 | {questionId,answer,markedForReview,revision,savedAt,answerLocked}, ETag. Only the qualifying committed answer mutation additionally returns itemFeedback. |
| GET /attempts/{id}/answers/{questionId}/feedback | Owner + exact membership + Learning + pinned AFTER_EACH_ANSWER + persisted score lock | {questionId,outcome,correctAnswer,explanation}; otherwise concealed. No other items or evidence. |
| POST /attempts/{id}/submit or GET /attempts/{id}/result | Owner; CSRF for POST; GRADED for GET | {attemptId,status,score,maxScore,correctCount,incorrectCount,unansweredCount,submissionReason,submittedAt,gradedAt} |
| GET /attempts/{id}/review | Owner AND GRADED | {attemptId,items:[{questionId,prompt,options,yourAnswer,outcome,correctAnswer,awardedPoints,maxPoints,explanation,evidence}]} with applicable fields only. Evidence is paragraph ID and reviewed excerpt. |

CorrectAnswer is {correctOptionId} for MCQ, {value} for each enum type, and {text} for text types, using the first explicitly authored accepted variant in immutable order. Persist variant order and keep it separate from normalized lookup computation. Generic responses carry no correctAnswer, outcome, explanation or evidence. Exam and Learning AFTER_SUBMISSION may reveal only through owner-authorized GRADED review. A privileged author's dedicated DTO does not change attempt reveal authorization. No cross-learner access by default. UI supports navigation, offline/reconnect reconciliation, autosave, per-item mark and manual submit confirmation.

## 10. UX blueprint

- Public Reading library → passage and permitted translations → public exercise card with fixed read-only sample and sign-in action.
- Registration form (email, display name) → generic pending notice with resend → informational email confirmation page → password-entry POST → success and sign-in, or expired/superseded/error state with resend.
- Sign-in → signed-in shell/profile → expired or disabled-session state → sign-in. Distinguish signed-in disabled/revoked UI state using generic session error flow; public login errors do not disclose account status.
- Verified learner → choose Learning or Exam → active attempt workspace with navigation, autosave status and mark-for-review. Learning presents configured aids; Exam displays server deadline and English-only workspace. Confirm manual submit → result and authorized review.

## 11. M1 foundation and production gates

### 11.1 Dependencies and foundation acceptance

Before M1 authorization, obtain verified toolchain/topology (TECH-01), supported session/deployment boundary (TECH-02), M1 mail mode and limits (TECH-03/04), Admin identities/approval/governance (TECH-05), and identity password, CSRF, role and retention policies (TECH-12). Obtain architecture sign-off and separate M1 authorization. Exact runtime versions and build compatibility remain unverified. The identity foundation needs an approved private backend/HTTPS ingress, PostgreSQL with identity migration role and least-privilege runtime roles, process-exclusion procedure, controlled mail adapter and operator/test identities. Use test fixtures for deployment secrets. Production services can stay disabled.

M1 includes identity-only forward migrations (users, roles/memberships, verification tokens, governance singleton, recovery approvals and audit), secured internal/test identity service entry points for credential, role and disablement revocation, the scoped deployment operator bootstrap/recovery runner, session/CSRF, identity API, minimal auth UI, health and safe errors. No public role, recovery or password reset management route. The proposed in-memory session design requires no session table. M1 uses public health or an isolated test fixture to prove absence of session allocation. Public Reading, real exercise eligibility, draft/publication, grading, sample, user vocabulary and progress belong to later milestones. No M1 acceptance depends on their endpoints or tables.

Validate clean builds, forward migration from an empty PostgreSQL database and repeat startup. Run M1 acceptance and failure cases in the traceability matrix against the version-pinned deployment/test boundary. Demonstrate old-cookie rejection, concurrent/external revocation, production-disabled registration without mail config, safe field/error projections, operator-only recovery and bounded pre-auth state independently of future modules.

### 11.2 Production readiness

Real registration/resend require approved sender/provider, configured verification origin and measured delivery/throttling behavior. Enable production Admin bootstrap/recovery only after operator and independent approver identities, managed credential delivery, protected audit and recovery drill are approved. Approve deployment fencing, cookie behavior, privacy/retention and security configuration before enabling affected production features. Passing M1 foundation does not enable them.

Future milestone dependencies: TECH-08 version graph/write boundary needs architecture approval. Resolve TECH-06/10 for public discovery and authoring, TECH-07/09 for assessment, plus feature-specific taxonomy, retention and provider items in TECH-11/12. Record an explicit deferral with disabled affected functionality when appropriate.

### 11.3 Implementation hold

After separate authorization only, read this pack and the current authority, decisions, ADR and traceability files. Inspect actual versions, scope the identity-only changes, run the accepted M1 checks and stop after M1 acceptance. No code work, test execution, commit authorization or subsequent milestone is granted by this document. Milestone 0 remains open pending independent re-review and explicit Product Owner sign-off.

## 12. Diagnostic evidence

Proposed bounded-cardinality counters: session replacement/rejection reason, revocation and crash restart, verification enqueue/retry/failure, governance outcome, grading rollback/retry and overdue queue lag. Use fixed reason enums and opaque request correlation IDs. No per-user labels, credentials, raw cookie/token/fragment, answer data or unnecessary personal information. Append-only security audit is distinct from metrics. Validate redaction and access under TECH-12.
