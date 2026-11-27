# IELTS Learning Platform · Milestone 0 architecture v1.1

**Status** Remediated proposal for Product Owner review. Milestone 0 remains open. Milestone 1 requires separate explicit authorization.

## 1. Authority and architecture

[Authority index](DOCUMENT-AUTHORITY.md) governs precedence. The [decision register](DECISION-REGISTER.md) preserves D01–D11 verbatim from v1.0. Technical contracts below are proposed, with choices and gates in the decision and [ADR registers](ADR-REGISTER.md). [Traceability](MILESTONE-0-TRACEABILITY.md) defines acceptance cases. The original PRD retains authority over product requirements outside explicit supersessions.

Proposed deployment is one HTTPS ingress routing UI to Next.js/TypeScript and /api/v1 to a private Spring Boot modular monolith backed by PostgreSQL. Backend modules are identity, content/Reading, question, assessment, vocabulary and progress. Flyway, JPA, OpenAPI and exact runtimes await approval. For M1, browser-fetch private data and avoid personalized SSR. Use host-only Secure, HttpOnly, SameSite=Lax cookie scoped to /api/v1. Match cookie scope on clear. Preserve the Spring Boot and PostgreSQL directions already recorded. Keep other provider integrations and later-release features deferred. Email verification is governed separately by §2 and TECH-03.

## 2. Registration and verification

### 2.1 Identity and tokens

Users have UUID PK, unique nonnull canonical email, display name, status PENDING/ACTIVE/DISABLED, nullable password hash, nullable verified timestamp, credential/role revision, email revision and UTC created/updated timestamps. Use one canonical normalization path without provider alias folding. PENDING has no usable hash or verification timestamp. ACTIVE requires both. DISABLED retains credential/verification material if previously activated, but can hold the original uncredentialed state when disabled while pending. Enforce matching nullness of hash and verified timestamp and record verification method separately for operator-attested accounts. Roles have UUID PK and unique code LEARNER/TEACHER/ADMIN. User roles have composite PK (user_id, role_id), FKs to users and roles and nullable assigned_by FK to users. Public registration inserts PENDING plus LEARNER in one transaction. Public and disabled accounts have no authenticated private access.

Verification tokens have UUID PK, owner FK, email revision, unique digest, issue/expiry times, consumed_at and invalidated_at. Generate 256 random bits and retain only a digest in persistent token storage. A partial unique index on user_id for rows with neither consumed_at nor invalidated_at enforces at most one outstanding token, even if expired. Invalidate the old row before inserting any replacement. Issue, resend, consume, disable and change address under the same user lock and then lock the token row if applicable. A successful activation marks the token consumed and invalidates siblings. Tokens fail at expiry or on status/address revision mismatch. Address change or disablement invalidates outstanding tokens in the same transaction.

### 2.2 Flow and delivery

New registration accepts no password. A duplicate canonical email in any state returns the same response without changing any account fields or reissuing a token. For valid requests, canonical-email uniqueness handles concurrent registration. Resend only rotates the token for an eligible PENDING account, with no profile mutation. Registration and resend return the same generic acknowledgment for existing, nonexistent, disabled and account-throttled targets. Apply per-source throttles uniformly. Compare externally visible response shape, status and timing behavior across account states.

Commit token and pending-account state before handing the token to the mail adapter. Retry transient delivery failures at most once in the same process using the same token while it remains current and live. Provider retries may deliver duplicates of that same link. On permanent failure, process loss or exhaustion, leave the account pending and permit throttled resend. Keep raw tokens out of persistent queues, logs and analytics. Use the configured HTTPS origin with the token in the link fragment. The UI GET /auth/verify-email serves an informational confirmation page, extracts and clears the fragment in the browser, and never activates, consumes or authenticates. Use no-store, no-referrer and no third-party scripts or assets on the page. After CSRF bootstrap, POST verifies token and newly chosen password. In one transaction set the adaptive hash and verified timestamp, activate, consume and invalidate siblings. Rollback leaves credentials, state and token unchanged. Successful activation issues no authenticated session. Login is separate.

Proposed M1 uses a nonproduction mail adapter accessible only to controlled dev/test operators. Production registration and resend return a uniform unavailable response if verified delivery is unconfigured. Production never uses the dev adapter or auto-activates accounts. Select real delivery before enabling those routes. Token TTL, throttles, sender configuration and cleanup proposal are in TECH-03/04.

## 3. Session lifecycle

### 3.1 Authoritative binding

Propose a single application process with in-memory authoritative account-to-session bindings and restart logout. Deploy exactly one live process handling auth, with no rolling overlap. A shared-store deployment requires shared authoritative serialization and equivalent tests before approval. Binding fields are account ID, opaque session reference digest, generation, authenticated_at, last qualifying activity, absolute expiry, revocation state and credential/role revision. Pre-auth CSRF sessions are separate from the one authenticated binding. Neither raw cookie values nor tokens enter logs or app-owned persistence.

### 3.2 Replacement, expiry and revocation

Verify password, then acquire the account serialization boundary. Recheck ACTIVE status, credential and role revision. Prepare a fresh server session ID with fresh CSRF state. Atomically replace the binding, advance generation and make the new reference authoritative before issuing the cookie. Roll back an incomplete replacement or revoke the failed new binding without resurrecting an old one. An old cookie ceases to authorize immediately after replacement. Every private request compares the presented session to the authoritative generation and account revision. Reject at now >= last_activity + 60 minutes or now >= authenticated_at + 7 days, whichever comes first. Use authoritative server time. Successful authenticated application calls qualify except public reads, CSRF bootstrap, static assets, health checks and background polling. Update last activity monotonically under the same guard. No touch changes absolute expiry. Logout, expiry and cleanup compare the exact generation; stale operations cannot touch or revoke a replacement.

Account disablement, password or role change revokes existing authentication and requires fresh login. Use the account guard across the status/role transaction and revision update. For sensitive writes, validate again inside the serialization boundary and hold it through transaction commit, so the write commits before revocation or is denied. Where multiple accounts are needed, lock them in stable ID order, after the security governance guard if applicable. A single-process crash invalidates all bindings. Do not serve authenticated requests during a durability failure that leaves account revision uncertain.

### 3.3 Browser flow

GET /api/v1/auth/csrf issues a session-backed synchronizer token and header name. Public Reading never creates a session. Require X-CSRF-TOKEN on all browser writes, including verify, register, resend, login and logout. Rotate ID and invalidate the old CSRF token on login. Fetch fresh CSRF after login/logout before the next write. Missing or stale CSRF gives 403. Same-origin needs no credentialed CORS. An alternate topology needs an exact origin allowlist and browser tests.

## 4. Admin lifecycle

Only an authenticated deployment operator can invoke bootstrap via an explicit command consuming managed deployment configuration, with no automatic startup path. Commit the dedicated account, ADMIN grant, success audit and durable singleton completion marker in one transaction under a shared database security-governance lock. Lock before checking the marker, existing Admin and canonical email collision. A collision with any account fails without mutation. A completed marker makes repeats no-ops. Audit rejections separately without secrets. Retire bootstrap credentials after success. Account/role locks follow this governance lock for Admin lifecycle operations. Only role definitions may be seeded in migrations.

Define a usable Admin as ACTIVE with verified evidence, usable credential and ADMIN grant. For dedicated operator-provisioned identities, propose independently verified control of the configured mailbox attested by the operator and captured as verification evidence in the audit record. This path does not apply to public registration. Use normal session/CSRF and revocation for operator-provisioned Admin login. Keep role-management capability scoped and audited, with no self-escalation path. Reject any ordinary demotion, disablement, deletion or credential invalidation leaving zero usable Admins, checked under the same lock. Recovery requires authenticated deployment operator access with independent approval, a managed replacement credential, identity evidence and an audit record. Reset an existing dedicated Admin or atomically create a new dedicated, collision-free verified Admin before revoking/replacing the old one. Revoke affected sessions and advance credential/role revision. Recovery neither clears the bootstrap marker nor promotes public accounts. TECH-05 governs owner approval and runbook prerequisites.

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

### 5.2 Keys, checks and indexes

Unique canonical slugs on passages and topics; unique (passage_id, version_no) and (exercise_id, version_no). Unique positions per passage edition, exercise edition, group and question for paragraphs, groups, questions and options respectively. Unique option key per question and canonical language per paragraph. One marking row and up to one explanation per question. Attempt start key unique per learner. Reading activity unique per (user_id, passage_id); old bookmarks do not transfer to newer editions. Use positive positions and version numbers. Proposed points numeric(6,2), total numeric(8,2), positive integral Exam duration in seconds bounded at 86,400, progress 0–100, score 0–max and item awards 0–item max. Reject total overflow at publish. State checks bind mode to deadline and terminal status to result/timestamps. Index candidate paths are current public publication date/id, topic/date/id, exercise by passage, attempt by version and (learner, started_at, id), progress/vocabulary owner/time and partial in-progress Exam deadline. Add uncovered FK lookup indexes as query plans warrant; reuse indexes backing keys. Store verification expiry lookup as a cleanup index candidate.

### 5.3 Publish, withdraw and retain

Create stable parent with null pointer, insert version and its children in FK order, then set pointer. All draft edits and publish/withdraw/start operations respect passage-first then exercise locks, sorted by stable ID if multiple. Under those locks, recheck draft revision and publisher scope. Validate required children, type and mark, evidence, score bounds, explicit preview members and their disclosure review. Freeze the validated graph and switch pointers in one transaction. No current pointer may reference a draft. A new publication clones into a new version with a separate draft revision. Published rows are immutable, including keys, evidence, explanations, sample membership, mode settings and grader policy. Restrict physical deletion of published graphs and referenced identities; only delete unreferenced drafts. Retention and exceptional takedown need distinct approval.

One eligibility predicate for all public routes and fresh public starts: current published PUBLIC exercise with a current published PUBLIC parent, whose exact current passage edition is the one pinned by that exercise. Parent replacement may hide stale child editions until republished; coordinated changes may switch all pointers atomically. Withdrawal clears eligibility without editing history. Under the proposal, ordinary withdrawal prevents discovery/new starts but existing owners retain pinned recovery, submission and review. Older matching start replays use existing ownership and bypass fresh eligibility. Exceptional takedown policy stays gated by TECH-08.

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
| POST /auth/logout | Current cookie + CSRF | 204 and matching cookie clear on valid request |
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

## 8. Reading, sample and author projections

GET /reading, /reading/{id} and slug equivalents expose only eligible PUBLIC titles, summaries, published dates, topic/level, timing/word count and safe media. Detail includes version ID, ordered English paragraphs and permitted translations. No account-specific information or session creation. GET /exercises, /exercises/{id} and /reading/{id}/exercises use §5.3 and the same public card shape {id,title,summary,modes,examDurationSeconds,sampleQuestions}. Pending users have the same public access as anonymous visitors. Private reading progress and vocabulary CRUD derive owner from session, require verified learner and enforce §5 membership/current eligibility on submitted source or bookmark. Restricted delivery and support access remain default-deny pending approval.

Propose explicit immutable sample membership of exactly max(1,min(2,floor(questionCount/10))) for valid nonempty exercises. The publisher selects that number of question IDs. Return them in group order then question position. FK enforces same version and PK prevents duplicates. Reject wrong count, editorial hints, explanations, marked options and answer-revealing evidence at publication. Use the base shape in §7.1, without points, and preserve authored option order independent of markings. Use fixed membership on every request. Freeze count and selected IDs with the version, and review the visible prompt, instructions and option text in author preview before approving publication. No keys, variants, correctness, feedback, marking settings or evidence. Apply the same predicate and projection to lists, detail, slug, related content, SSR and search. Public responses and rendered/index-facing payloads use no-store/no stale shared copies initially. Search reads eligible projections; exclude question payloads from external indexing. Withdrawal removes all eligible public representations.

Scoped teacher/admin authoring stays under /teacher/reading and /teacher/exercises, with draft create, preview, revision-checked edits to paragraph/group/question collections, publish, withdraw and archive. Require publisher capability for publication changes. Only author projections contain protected marking objects and internal notes. They have no route or serializer shared with public or attempt projections. Private, auth, attempt, author, review and feedback responses are no-store and excluded from SSR/hydration, search, telemetry and log bodies. Staff capability never implies cross-learner review.

## 9. Attempts, feedback and review

### 9.1 Lifecycle and races

POST /attempts accepts {exerciseId,mode}, CSRF and mandatory Idempotency-Key. Require ACTIVE verified learner. Persist canonical exercise ID and mode with the key. Same learner/key/request returns the original attempt with 200 even after ordinary withdrawal/republication. Different request under that key returns 409. A new key on eligible exercise creates a distinct attempt with 201 and Location, under §5.3 locks. Unique key handles concurrent same-key writes. Serialize or retry a unique conflict by rereading the committed winner. Public new keys have no attempt count cap. Exam pins server deadline at start from positive duration. Learning has no enforced deadline.

Save and finalization lock the same attempt row. Resolve owner first, then version membership and authoritative time after taking the lock. A save at or after deadline or on GRADED returns 409 with no changes. Use current If-Match revision or 428. Exact full-state replay with current revision or the immediately preceding revision is a no-op, with unchanged timestamp/revision, when payload matches the stored state. Any different stale payload returns 412. This includes markForReview in the comparison. Validate question type; normalized blank is represented as no answer for scoring. New writes increment item revision. Persist mark-for-review separately from the score lock.

Submit is bodyless and uses the same attempt lock. Finalize all items, including unanswered, and persist totals, reason, effective submission and grading timestamps and GRADED in one transaction. Before deadline use MANUAL and decision time; at/after deadline use TIME_EXPIRED and deadline as effective submission time. A failure rolls everything back. Duplicate submit returns the identical stored summary. Propose one-minute backend expiry sweep plus submit-driven expiry finalization. GET does not finalize. Overdue reads are read-only and result/review return 409 RESULT_PENDING until committed. Polling cadence, bounds and exact operational behavior await TECH-09. Refresh and network recovery preserve deadline and answers. UI supports navigation, autosave, mark-for-review and manual confirmation.

### 9.2 Reveal matrix

The only learner pre-submit exception is Learning with pinned AFTER_EACH_ANSWER and a committed valid nonblank answer locked for scoring. A syntactically valid over-limit answer can lock and receive incorrect feedback. Reject answer changes or clearing after lock, allow mark-only changes with normal concurrency control. Invalid, stale, blank, clear and mark-only requests cause no new reveal. Learning AFTER_SUBMISSION and Exam reveal only via authorized GRADED review. An AFTER_EACH_ANSWER exercise never changes Exam behavior. Authorization and reveal state are independent predicates. Cross-learner access stays denied.

| Endpoint | Gate/request | Response allowlist |
| --- | --- | --- |
| POST /attempts | §9.1 | {attemptId,exerciseVersionId,mode,status,startedAt,deadlineAt} |
| GET /attempts and /attempts/{id} | Owner | History identity/status/score summaries. Detail adds serverTime, readOnly, English passage, ordered groups and safe questions, own item state, and persisted review flags. Learning may include permitted translation and authorized lookup capability if enabled; Exam omits both. |
| PUT /attempts/{id}/answers/{questionId} | Owner + CSRF + If-Match; {answer: typed object or null,markedForReview: boolean} | {questionId,answer,markedForReview,revision,savedAt,answerLocked} and ETag. Only a fresh qualifying first-answer commit may additionally return a separately mapped itemFeedback object containing that item's feedback. Replays and mark-only responses use the generic projection. |
| GET /attempts/{id}/answers/{questionId}/feedback | Owner + exact membership + pinned Learning AFTER_EACH_ANSWER + item lock | One itemFeedback object {questionId,outcome,correctAnswer,explanation}. Otherwise 404. No collection or evidence in this projection. |
| POST /attempts/{id}/submit and GET /attempts/{id}/result | Owner + CSRF for POST; GRADED for GET | {attemptId,status,score,maxScore,correctCount,incorrectCount,unansweredCount,submissionReason,submittedAt,gradedAt} |
| GET /attempts/{id}/review | Owner AND GRADED | {attemptId,items:[{questionId,prompt,options,yourAnswer,outcome,correctAnswer,awardedPoints,maxPoints,explanation,evidence}]} with type-appropriate fields only. Evidence contains paragraph ID and reviewed excerpt. |

CorrectAnswer uses one designated accepted variant for text, option ID for MCQ and enum value for selection types. No private marking rules are serialized. Exam is timed practice. Public Reading and authorized historical reviews remain independently accessible. Exam attempt routes serve no translation, dictionary, pre-grade feedback or answer key, regardless of client presentation.

## 10. UX blueprint

- Public Reading library → passage and permitted translations → public exercise card with fixed read-only sample and sign-in action.
- Registration form (email, display name) → generic pending notice with resend → informational email confirmation page → password-entry POST → success and sign-in, or expired/superseded/error state with resend.
- Sign-in → signed-in shell/profile → expired or disabled-session state → sign-in. Distinguish signed-in disabled/revoked UI state using generic session error flow; public login errors do not disclose account status.
- Verified learner → choose Learning or Exam → active attempt workspace with navigation, autosave status and mark-for-review. Learning presents configured aids; Exam displays server deadline and English-only workspace. Confirm manual submit → result and authorized review.

## 11. Milestone 1 boundary and authorization hold

M1 proposal includes compatible Spring/Java and Next/Node baseline, PostgreSQL identity persistence and identity-only forward migrations, session/CSRF, security headers, PENDING registration and verification tokens, register/resend/confirmation/login/expired-session screens and minimal profile shell, audited operator bootstrap/recovery, health and safe errors. In-memory session proposal needs no session-registry migration. Public production registration depends on separately approved mail readiness. Reading, exercises, attempts, grading, vocabulary, Listening, class operations and integrations beyond approved identity delivery remain later work.

Foundation acceptance requires verified toolchain and topology, clean builds, fresh PostgreSQL migration and repeat startup, and passing M1 tests in the [traceability matrix](MILESTONE-0-TRACEABILITY.md). Production readiness separately requires approved email configuration, environment security, retention and operator recovery readiness before enabling relevant facilities. Technical choices in the decision register require acceptance or explicit deferral with a disabled dependent capability. Independent reassessment and explicit owner sign-off are still pending.

**Future M1 instruction, inactive until separately authorized.** Read [authority index](DOCUMENT-AUTHORITY.md), this pack, the [decision register](DECISION-REGISTER.md), [ADR register](ADR-REGISTER.md), linked PRD and [traceability tests](MILESTONE-0-TRACEABILITY.md). Inspect actual branch, workspace and versions. Apply only accepted contracts and identity scope. Run clean builds, identity migrations from empty PostgreSQL, concurrency, security and browser acceptance. Stop after M1 acceptance and await separate M2 approval. This document grants no execution permission.
