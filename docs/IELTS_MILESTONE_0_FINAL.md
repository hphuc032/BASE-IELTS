# IELTS Learning Platform --- Milestone 0 Final Architecture Pack

**ID:** IELTS-M0-FINAL-001\
**Version:** 1.0 · **Date:** 2026-11-26\
**Status:** Consolidated proposal for Product Owner final sign-off. This
document alone does not authorize implementation.

## 0. Authority and agent instructions

This document consolidates the PRD, approved D01--D05, Codex's
independent review, corrected ERD/API proposal, decision/ADR plan, and
the additional owner-approved D06--D11.

**Rules for every developer and AI agent** 1. D01--D11 below are
approved Product Owner decisions. 2. Technical details are proposed
implementation contracts until this consolidated document is signed off.
3. Do not silently change approved decisions. 4. Do not initialize
frameworks, add dependencies, create migrations or implement features
until the Product Owner explicitly closes Milestone 0 and authorizes
Milestone 1. 5. Never expose answer keys in public, sample, attempt or
pre-submit DTOs. 6. Published content versions and grading rules are
immutable. Historical attempts must remain reproducible. 7. Enforce
identity, authorization, ownership, visibility and exam rules on the
server. 8. Never edit applied Flyway migrations; use forward migrations.
9. Keep email, dictionary, TTS, AI, media storage and meeting
integrations out of scope until separately approved.

## 1. Confirmed product baseline

  -----------------------------------------------------------------------
  ID                      Decision                Value
  ----------------------- ----------------------- -----------------------
  C01                     Repository              Reported empty; Codex
                                                  later observed
                                                  documentation-only
                                                  checkout on `main`

  C02                     Coding agent            Codex

  C03                     Development OS          Windows

  C04                     Database                PostgreSQL

  C05                     Backend                 Spring Boot

  C06                     Initial program         IELTS

  C07                     Learning model          Hybrid

  C08                     Content                 Teacher-authored +
                                                  AI-assisted drafts;
                                                  human review before
                                                  publication

  C09                     Access                  Free; published PUBLIC
                                                  Reading anonymously
                                                  accessible

  C10                     Priority                Reading

  C11                     Initial scale           Approximately 100
                                                  learners
  -----------------------------------------------------------------------

Exact Java, Spring Boot, Node, Next.js and dependency versions must be
verified for compatibility before initialization.

## 2. Product Owner Decision Register

`<badge color="success">`{=html}D01--D11 approved`</badge>`{=html}

  -----------------------------------------------------------------------
  ID                      Decision                Approved value
  ----------------------- ----------------------- -----------------------
  D01                     Authentication          Secure cookie +
                                                  server-managed session

  D02                     Public Reading          Published PUBLIC
                                                  Reading can be read
                                                  without login

  D03                     MVP question types      MCQ Single; T/F/NG;
                                                  Y/N/NG; Short Answer;
                                                  Sentence Completion

  D04                     Modes                   Every MVP exercise
                                                  supports Learning and
                                                  Exam

  D05                     Retakes                 Unlimited independent
                                                  public-practice
                                                  attempts

  D06                     Session timeout         60-minute idle; 7-day
                                                  absolute lifetime

  D07                     Multi-device            One active
                                                  authenticated session
                                                  per account

  D08                     Registration            PENDING until email
                                                  verification

  D09                     Learning feedback       Configurable per
                                                  exercise

  D10                     Public exercise preview Sample questions,
                                                  without answers

  D11                     First Admin             Seed/provision from
                                                  deployment environment
                                                  configuration
  -----------------------------------------------------------------------

### 2.1 Exact interpretation of D06--D11

**D06 --- 60--7** - Idle timeout is 60 minutes since qualifying
authenticated activity. - Absolute lifetime is 7 days from
authentication; activity never extends it. - Backend is authoritative.
Expired sessions are invalidated. - Recommended idle refresh rule:
authenticated application requests only, excluding health checks/static
assets.

**D07 --- Single active session** - A successful login invalidates the
previous active session before the new one becomes active. - Concurrent
login races must result in one active session only. - Logout,
disablement, password change and privilege change revoke the active
session. - Do not rely on a browser-side device identifier. - A unique
account-to-active-session constraint or equivalent serialized session
registry is required.

**D08 --- Pending registration** - Public registration creates a
`PENDING` account. - Pending accounts cannot access learner-only
personalized APIs. - Verification token is random, single-use,
time-limited, stored hashed, invalidated after use/resend. - Public
registration cannot assign roles. New accounts receive LEARNER only. -
Email provider, sender domain, token expiry, resend limits and
verification URL remain operational decisions before production. - Local
development may use a development-only verification mechanism;
production must never auto-activate accounts because email is
unavailable.

**D09 --- Configurable Learning feedback** - `AFTER_EACH_ANSWER`: after
a valid answer save, return an item-feedback projection with
correctness/answer/explanation. - `AFTER_SUBMISSION`: do not reveal
correctness or explanation until the attempt is submitted and graded. -
Recommended default: `AFTER_SUBMISSION`. - Exam always uses
`AFTER_SUBMISSION`. - Policy is part of immutable exercise version and
pinned by each attempt. - Changing policy requires a new published
exercise version. - Never return a complete answer key and rely on
frontend hiding.

**D10 --- Public sample preview** - Anonymous users see public exercise
cards and a limited sample of questions for published PUBLIC exercises
attached to the current public passage version. - Sample excludes
correct answers, accepted variants, explanations, revealing evidence,
grading metadata and author notes. - Sample selection/count is
configured per exercise version. - Full attempt requires a verified
learner. - Preview creates no attempt or personal progress. - Proposed
default: 2 questions or 10% of exercise, whichever is smaller; minimum 1
only if questions exist. Exact rule can be finalized before public
discovery implementation.

**D11 --- First Admin seed** - Use deployment environment configuration
or a one-time operator bootstrap command. - Never commit Admin
credentials or production password hashes in source or migrations. - Do
not log the bootstrap secret. - Provisioning is idempotent and must not
silently overwrite an existing Admin. - Rotate/remove bootstrap secret
after use. - Define recovery and last-Admin policy. No public
Admin-grant endpoint. - A migration may seed role definitions, never a
production Admin credential.

## 3. Consolidated architecture

### 3.1 Proposed architecture

Use a modular monolith: - Next.js + TypeScript frontend. - Spring Boot
REST backend. - PostgreSQL. - Flyway. - Spring Security. - Spring Data
JPA/Hibernate (proposed). - OpenAPI (proposed). - One HTTPS browser
origin with reverse proxy routing `/api/v1` to backend (proposed).

Microservices, Kubernetes, brokers and distributed caches are not
justified for the initial scale.

### 3.2 Browser/API topology

``` mermaid
flowchart TB
    B[Browser / Mobile Browser]
    P[HTTPS Ingress / Reverse Proxy]
    N[Next.js Frontend]
    S[Spring Boot REST Application]
    I[Identity and Session]
    C[Content and Reading]
    A[Question and Assessment]
    V[Vocabulary and Progress]
    DB[(PostgreSQL)]
    E[Email Provider - production verification]

    B --> P
    P -->|UI routes| N
    P -->|/api/v1| S
    S --> I
    S --> C
    S --> A
    S --> V
    I --> DB
    C --> DB
    A --> DB
    V --> DB
    I -. verification .-> E
```

Proposed same-origin behavior: - Browser calls same public origin for UI
and `/api/v1`. - Backend is not exposed to arbitrary browser origins. -
Host-only `Secure`, `HttpOnly`, `SameSite=Lax` session cookie. - Cookie
path `/api/v1` for client-fetched API auth; revisit if personalized SSR
needs UI-route cookie access. - Distinct origins require explicitly
configured credentialed CORS and real-browser testing.

### 3.3 Backend package boundaries

``` text
com.example.ieltsplatform
├── common/{config,exception,response,validation,security}
├── identity/{controller,service,repository,entity,dto}
├── content
├── reading
├── question
├── assessment
├── vocabulary
└── progress
```

Each module owns its rules. Cross-module operations use explicit
application services, not arbitrary repository access.

Frontend:

``` text
frontend/src/
├── app/
├── components/{ui,layout}
├── features/{auth,dashboard,reading,assessment,vocabulary,teacher}
├── lib/
├── hooks/
└── types/
```

## 4. Consolidated logical ERD

UUID identifiers and UTC `timestamptz` are proposed. Composite foreign
keys should enforce membership of versioned child records.

``` mermaid
erDiagram
    USERS ||--o{ USER_ROLES : has
    ROLES ||--o{ USER_ROLES : assigned
    USERS ||--o{ EMAIL_VERIFICATION_TOKENS : verifies
    USERS ||--o{ USER_SESSIONS : owns
    TOPICS ||--o{ READING_PASSAGES : categorizes
    USERS ||--o{ READING_PASSAGES : authors
    READING_PASSAGES ||--o{ PASSAGE_VERSIONS : versions
    PASSAGE_VERSIONS ||--|{ PASSAGE_PARAGRAPHS : contains
    PASSAGE_PARAGRAPHS ||--o{ PARAGRAPH_TRANSLATIONS : translated
    READING_PASSAGES ||--o{ EXERCISES : provides
    EXERCISES ||--o{ EXERCISE_VERSIONS : versions
    PASSAGE_VERSIONS ||--o{ EXERCISE_VERSIONS : pinned_by
    EXERCISE_VERSIONS ||--|{ QUESTION_GROUPS : contains
    QUESTION_GROUPS ||--|{ QUESTIONS : contains
    QUESTIONS ||--o{ QUESTION_OPTIONS : offers
    QUESTIONS ||--o| QUESTION_MARKING : marks
    QUESTIONS ||--o| QUESTION_EXPLANATIONS : explains
    QUESTIONS ||--o{ QUESTION_EVIDENCE : supported_by
    USERS ||--o{ ATTEMPTS : starts
    EXERCISE_VERSIONS ||--o{ ATTEMPTS : attempted_as
    ATTEMPTS ||--|{ ATTEMPT_ITEMS : stores
    QUESTIONS ||--o{ ATTEMPT_ITEMS : answered
    USERS ||--o{ USER_VOCABULARY : saves
    USERS ||--o{ READING_ACTIVITY : tracks
    PASSAGE_VERSIONS ||--o{ READING_ACTIVITY : read
```

### 4.1 Identity tables

**`users`** - `id UUID PK` -
`email_canonical VARCHAR(320) UNIQUE NOT NULL` -
`password_hash VARCHAR(255) NOT NULL` -
`display_name VARCHAR(120) NOT NULL` - `status`: `PENDING`, `ACTIVE`,
`DISABLED` - `email_verified_at TIMESTAMPTZ NULL` - `created_at`,
`updated_at`

Canonical email normalization must be consistent; do not apply
provider-specific alias folding.

**`roles`, `user_roles`** - Role codes: `LEARNER`, `TEACHER`, `ADMIN`. -
Composite PK `(user_id, role_id)`. - `assigned_by` nullable FK to
users. - Public registration cannot assign elevated roles.

**`email_verification_tokens`** - `id UUID PK`, `user_id FK`,
`token_hash UNIQUE`, `expires_at`, `used_at NULL`, `created_at`. - Store
only token hash. - Single-use and expiry enforced transactionally.

**`user_sessions` logical registry** - `id`, `user_id`, opaque
`session_reference` or hash, `created_at`, `last_seen_at`,
`absolute_expires_at`, `revoked_at`. - Unique active session per user
enforced transactionally. - Exact storage depends on selected Spring
Session/session-store approach. - Never store raw cookie values. -
In-memory session registry can be acceptable for one instance if restart
forces re-login. Multi-instance or durable revocation needs shared
storage such as Spring Session JDBC/Redis after operational review.
Finalize before M1.

### 4.2 Immutable Reading content

**`reading_passages`**: stable identity, slug, author and pointer to
current published version.

**`passage_versions`**: passage ID/version, topic, title, summary,
visibility, difficulty, CEFR, estimated time, word count and publication
metadata.

**`passage_paragraphs`**: belong to passage version, unique
`(passage_version_id, position)`.

**`paragraph_translations`**: belong to paragraph, unique
`(paragraph_id, language_code)`.

Draft versions may be edited. Published versions and child graphs are
immutable. Evidence and learner progress reference exact passage
version/paragraph.

### 4.3 Immutable exercise/question content

**`exercises`**: stable identity, author and current published version
pointer.

**`exercise_versions`** - Exact parent passage version. -
Title/instructions/visibility. - Positive `exam_duration_seconds`. -
`learning_feedback_policy`: `AFTER_EACH_ANSWER` or `AFTER_SUBMISSION`. -
`preview_question_count` or explicit preview membership. - Grader policy
revision. - Both Learning and Exam modes always enabled (D04). -
Published version and children immutable.

**`question_groups`, `questions`** - Belong to exact exercise version. -
Types fixed by D03. - Positive fixed-precision points. - Type-specific
validated payloads and constraints. - Unique order within group.

**`question_options`, `question_marking`, `question_explanations`,
`question_evidence`** - Option records do not expose correctness. -
Protected marking data separate from learner projections. - Correct
option must belong to same question. - Explanation/evidence part of
immutable exercise version. - Evidence references same pinned passage
version. - Defer character offsets; use paragraph reference and reviewed
excerpt initially.

### 4.4 Attempts

**`attempts`** - `id`, `learner_id`, exact `exercise_version_id` -
`mode`: `LEARNING` or `EXAM` - `status`: `IN_PROGRESS`, `GRADED`
(CANCELLED only if separately approved) - mandatory learner-scoped
`start_key` - `started_at`, `deadline_at`, `submitted_at`, `graded_at` -
`submission_reason`: `MANUAL`, `TIME_EXPIRED` - score/max score and
pinned grader policy revision - unique `(learner_id, start_key)`

**`attempt_items`** - Composite PK `(attempt_id, question_id)`. -
Composite FK ensures question belongs to attempt's exact exercise
version. - `answer_payload JSONB`, validated by question type. -
`marked_for_review`, `revision`, `saved_at`. - Final `is_correct`,
`awarded_points`, `max_points`, feedback snapshot. - Initialize every
question unanswered, revision 0. - Persist outcomes for unanswered items
during finalization.

### 4.5 Vocabulary and progress

`user_vocabulary` is learner-owned: term, meaning, optional IPA/part of
speech, context, notes, review status and optional versioned source.
Manual save works without dictionary integration. Duplicate policy
remains open.

`reading_activity` is unique per `(user_id, passage_id)` for resumable
progress, stores exact `passage_version_id`, and validates bookmark
membership. Old bookmark is not applied to a new passage version.

------------------------------------------------------------------------

# 5. Question types and deterministic grading

Approved MVP: 1. `MULTIPLE_CHOICE_SINGLE` 2. `TRUE_FALSE_NOT_GIVEN` 3.
`YES_NO_NOT_GIVEN` 4. `SHORT_ANSWER` 5. `SENTENCE_COMPLETION`

Proposed v1 grading: - MCQ: validate selected option belongs to
question, compare UUID. - T/F/NG and Y/N/NG: exact enum equality. -
Short Answer/Sentence Completion: explicit accepted variants,
deterministic normalization, no fuzzy matching. - Full points or zero;
no partial credit. - Null/empty normalized answer is unanswered. -
Malformed payload, unknown fields, invalid options and oversized input
rejected without mutation. - Valid over-word-limit answer is saved but
graded incorrect.

Proposed text normalization: - Unicode NFC. - Trim and collapse Unicode
whitespace to ASCII spaces. - Locale-independent lowercase when
case-insensitive. - Preserve punctuation, accents and digits for
equality. - Count space-delimited nonempty tokens containing at least
one Unicode letter or number. - Pin Unicode/runtime behavior and grader
policy revision. - Accepted variants must be unique after normalization
and obey word limit.

Do not calculate IELTS band from raw practice score without a separately
approved conversion table.

------------------------------------------------------------------------

# 6. Attempt protocol and Exam Mode

**Start** - Verified LEARNER only. - Accept `exerciseId`, `mode`,
mandatory `Idempotency-Key`. - Resolve current published
exercise/passage and access inside transaction. - Same learner/key/same
request returns original attempt. - Same key/different request returns
409. - Concurrent same-key starts yield one attempt via unique
constraint. - New key starts a new attempt; unlimited public retakes
remain (D05).

**Save** - Owner only; attempt must be `IN_PROGRESS`. - Save and
finalization share attempt row-lock boundary. - Per-item ETag/revision;
stale revision returns 412; missing precondition returns 428. - Validate
against pinned question type. - Exam mode has no dictionary, translation
or feedback endpoints within the attempt.

**Submit/deadline** - Learning has no enforced deadline. - Exam has
positive duration and server-generated deadline. - Refresh never extends
deadline. - At/after deadline, freeze last committed answers and grade
blanks as unanswered. - Manual submit and expiry share finalization
transaction. - Duplicate submit returns committed result. -
Grading/persistence failure rolls back and can retry. - Proposed expiry
sweep plus on-demand finalization; cadence/recovery policy must be
decided before M4.

**Integrity limitation:** D02 keeps public Reading anonymously
accessible and D05 allows prior reviews. Exam Mode can restrict
assistance within the attempt experience but cannot guarantee
closed-book integrity outside it. Describe it as timed practice, not
proctored/secure examination.

------------------------------------------------------------------------

# 7. API Contract

Base `/api/v1`; JSON DTOs; UUID IDs; UTC ISO-8601 timestamps. Pagination
default 20, max 100 (proposed).

## 7.1 Envelopes and status

Success: `{ "data": { ... } }`

Paged:

``` json
{
  "data": [],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

Error:

``` json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed.",
    "details": [
      { "field": "title", "code": "NOT_BLANK", "message": "Title is required." }
    ],
    "traceId": "correlation-id"
  }
}
```

-   200 success; 201 created; 204 no-content.
-   400 malformed/invalid input; 401 missing/expired auth; 403 CSRF/role
    denial.
-   404 absent or intentionally concealed; 409 state/idempotency
    conflict.
-   412 stale item revision; 428 missing revision precondition; 429
    throttled; 500 generic unexpected error.

## 7.2 Authentication

  -----------------------------------------------------------------------------------
  Method            Endpoint                      Access            Purpose
  ----------------- ----------------------------- ----------------- -----------------
  GET               `/auth/csrf`                  Public            Return
                                                                    synchronizer
                                                                    token/header
                                                                    name; may create
                                                                    pre-auth session

  POST              `/auth/register`              Public + CSRF     Create PENDING
                                                                    account and send
                                                                    verification

  POST              `/auth/verify-email`          Public token      Verify single-use
                                                                    token

  POST              `/auth/resend-verification`   Public + CSRF +   Resend without
                                                  rate limit        account
                                                                    enumeration

  POST              `/auth/login`                 Public + CSRF     Authenticate
                                                                    ACTIVE account,
                                                                    invalidate old
                                                                    session, rotate
                                                                    ID

  POST              `/auth/logout`                Authenticated +   Revoke and clear
                                                  CSRF              cookie; 204

  GET               `/users/me`                   Active            Safe current
                                                  authenticated     profile

  PATCH             `/users/me`                   Active            Update
                                                  authenticated +   allowlisted
                                                  CSRF              profile

  POST              `/auth/forgot-password`       Deferred          Requires email
                                                                    reset approval

  POST              `/auth/reset-password`        Deferred          Requires email
                                                                    reset approval
  -----------------------------------------------------------------------------------

Register request:

``` json
{
  "email": "learner@example.com",
  "password": "StrongPassword123!",
  "displayName": "Alex Nguyen"
}
```

Response should avoid account enumeration:

``` json
{
  "data": {
    "message": "If the address can be registered, verification instructions will be sent."
  }
}
```

Verification:

``` json
{ "token": "one-time-opaque-token" }
```

Login accepts email/password. Server sets cookie; never return session
cookie as JSON.

## 7.3 Reading and public sample preview

  Method   Endpoint                           Access
  -------- ---------------------------------- ----------------------------------
  GET      `/reading`                         Public published PUBLIC passages
  GET      `/reading/{passageId}`             Public published PUBLIC detail
  GET      `/reading/{passageId}/exercises`   Public cards + sample
  GET      `/exercises`                       Public exercise discovery
  GET      `/exercises/{exerciseId}`          Public card + sample only
  GET      `/reading/{passageId}/progress`    Verified learner, own
  PUT      `/reading/{passageId}/progress`    Verified learner, own + CSRF

Sample DTO:

``` json
{
  "data": {
    "id": "uuid",
    "title": "Technology and Society",
    "summary": "Practice understanding arguments in a reading passage.",
    "modes": ["LEARNING", "EXAM"],
    "examDurationSeconds": 1200,
    "sampleQuestions": [
      {
        "id": "uuid",
        "type": "MULTIPLE_CHOICE_SINGLE",
        "position": 1,
        "prompt": "What is the main idea?",
        "options": [
          { "id": "uuid", "key": "A", "content": "Option A" },
          { "id": "uuid", "key": "B", "content": "Option B" }
        ]
      }
    ]
  }
}
```

No answer key, accepted variants, explanation, answer-revealing evidence
or marking data in sample DTO.

## 7.4 Vocabulary

  Method   Endpoint             Access
  -------- -------------------- ------------------------------
  GET      `/vocabulary`        Verified learner, own
  POST     `/vocabulary`        Verified learner, own + CSRF
  GET      `/vocabulary/{id}`   Owner
  PATCH    `/vocabulary/{id}`   Owner + CSRF
  DELETE   `/vocabulary/{id}`   Owner + CSRF

Derive user ID from authenticated principal; never accept ownership from
request body.

## 7.5 Attempts

  ---------------------------------------------------------------------------------------
  Method                  Endpoint                                Access
  ----------------------- --------------------------------------- -----------------------
  POST                    `/attempts`                             Verified learner +
                                                                  CSRF + Idempotency-Key

  GET                     `/attempts`                             Own history

  GET                     `/attempts/{id}`                        Owner, recovery
                                                                  projection

  PUT                     `/attempts/{id}/answers/{questionId}`   Owner + CSRF + If-Match

  POST                    `/attempts/{id}/submit`                 Owner + CSRF

  GET                     `/attempts/{id}/result`                 Owner and GRADED

  GET                     `/attempts/{id}/review`                 Owner and GRADED/reveal
                                                                  policy
  ---------------------------------------------------------------------------------------

Start request:

``` json
{ "exerciseId": "uuid", "mode": "EXAM" }
```

Header: `Idempotency-Key: <opaque-unique-key>`

Save request:

``` json
{
  "answer": { "selectedOptionId": "uuid" },
  "markedForReview": false
}
```

Header: `If-Match: "item-revision-3"`

Review requires both authorized ownership/scope and `GRADED` plus
applicable reveal policy.

-   `AFTER_EACH_ANSWER`: separate item-feedback projection after valid
    save.
-   `AFTER_SUBMISSION`: no correctness/answer/explanation until graded.
-   Exam always reveals only after submission.
-   Review returns pinned prompt/options, learner answer, outcome,
    intended answer/approved variants, points, explanation and evidence.

## 7.6 Teacher authoring

  ------------------------------------------------------------------------------------
  Method                  Endpoint                             Access
  ----------------------- ------------------------------------ -----------------------
  POST                    `/teacher/reading`                   Teacher/Admin author
                                                               scope; create draft

  GET                     `/teacher/reading/{id}`              Scoped author preview

  PATCH                   `/teacher/reading/{id}`              Scoped author +
                                                               revision

  PUT                     `/teacher/reading/{id}/paragraphs`   Scoped author

  POST                    `/teacher/reading/{id}/publish`      Publisher permission

  POST                    `/teacher/reading/{id}/withdraw`     Publisher permission

  POST                    `/teacher/reading/{id}/archive`      Publisher permission

  POST                    `/teacher/exercises`                 Teacher/Admin author
                                                               scope

  GET                     `/teacher/exercises/{id}`            Scoped author preview
                                                               including keys

  PATCH                   `/teacher/exercises/{id}`            Scoped author +
                                                               revision

  POST                    `/teacher/exercises/{id}/publish`    Publisher permission

  POST                    `/teacher/exercises/{id}/withdraw`   Publisher permission
  ------------------------------------------------------------------------------------

Teacher role alone does not grant permission to publish all content.
O-13 defines final authoring/publishing scope.

------------------------------------------------------------------------

# 8. Authorization matrix

  -------------------------------------------------------------------------------------------------
  Action           Anonymous      Pending        Verified              Teacher                Admin
                                                  learner                      
  ------------- ------------ ------------ --------------- -------------------- --------------------
  Read                   Yes          Yes             Yes                  Yes                  Yes
  published                                                                    
  PUBLIC                                                                       
  passage                                                                      

  View public            Yes          Yes             Yes                  Yes                  Yes
  card/sample                                                                  

  Save                    No           No             Own               Own if               Own if
  vocabulary                                                   learner-capable      learner-capable

  Start attempt           No           No             Own   Only if separately   Only if separately
                                                            learner-authorized   learner-authorized

  View own                No           No           Own +  Own + graded/reveal  Own + graded/reveal
  review                                    graded/reveal                      

  View another            No           No              No  Assigned scope only Audited support only
  learner                                                           if enabled 
  attempt                                                                      

  Edit scoped             No           No              No                  Yes                  Yes
  draft                                                                        

  Publish                 No           No              No   Explicit publisher                  Yes
                                                                         scope 

  Assign roles            No           No              No                   No         Yes, audited

  First Admin             No           No              No                   No  Deployment operator
  bootstrap                                                                                    only
  -------------------------------------------------------------------------------------------------

Admin does not automatically gain unrestricted access to private learner
data. Support access is a separately audited capability.

------------------------------------------------------------------------

# 9. Security and privacy contracts

### Cookie/CSRF flow

1.  Browser calls `GET /auth/csrf`.
2.  Server returns synchronizer token and header name; may create
    pre-auth session.
3.  Browser sends `X-CSRF-TOKEN` on unsafe requests, including
    register/login/logout.
4.  Successful login rotates session ID and CSRF token.
5.  Browser fetches a fresh CSRF token after login.
6.  Logout invalidates session and clears CSRF/cookie with matching
    scope.
7.  Missing/stale CSRF returns consistent 403.
8.  CSRF is required for all browser state-changing requests.
    CORS/SameSite do not replace CSRF.

### Session rules

-   60-minute idle; 7-day absolute.
-   One active session/account; new login invalidates old.
-   Disablement/password/privilege changes revoke session.
-   Rotate ID at login and privilege elevation.
-   Never log cookie/session values.
-   Decide session store/restart behavior before M1.
-   In-memory session storage implies restart-driven reauthentication;
    multi-instance consistency needs shared store.

### Answer confidentiality

-   Separate public, sample, attempt, feedback, review and author DTOs.
-   Explicit field allowlists; reject unknown/mass-assignment fields.
-   Review requires authorization AND reveal state.
-   No answer keys in SSR hydration, cache, logs, search indexes or
    error bodies.
-   No shared caching for private, CSRF, authoring, attempt, feedback or
    review responses.
-   Public sample questions are answer-free.

### Privacy

-   Minimize learner data.
-   Define deletion and retention before production.
-   No personal learner data to AI providers without privacy approval.
-   No credentials/Admin secrets in Git.
-   Admin support access scoped and audited.

------------------------------------------------------------------------

# 10. ADR register

  -----------------------------------------------------------------------------------------
  ADR               Topic             Proposed resolution                 Status
  ----------------- ----------------- ----------------------------------- -----------------
  ADR-001           Architecture      Modular monolith                    Proposed

  ADR-002           Database          PostgreSQL                          Owner-approved

  ADR-003           Backend           Spring Boot; verify exact version   Owner-approved
                                                                          direction

  ADR-004           Frontend          Next.js + TypeScript                Proposed

  ADR-005           Persistence       Spring Data JPA/Hibernate           Proposed

  ADR-006           Migrations        Flyway                              Proposed

  ADR-007           API               REST/JSON `/api/v1`, typed DTOs     Proposed

  ADR-008           Authentication    Server-managed session cookie       D01 approved

  ADR-009           Browser topology  Same HTTPS origin/reverse proxy     Proposed

  ADR-010           Session lifetime  60-minute idle, 7-day absolute      D06 approved

  ADR-011           Concurrent        Single active session/account       D07 approved
                    sessions                                              

  ADR-012           Registration      Pending email verification          D08 approved;
                                                                          provider open

  ADR-013           Admin bootstrap   Environment-driven seed/one-time    D11 approved;
                                      command                             operational
                                                                          controls required

  ADR-014           Content versions  Immutable passage/exercise graph    Proposed

  ADR-015           Attempt           Idempotency, row lock, per-item     Proposed
                    consistency       revision, atomic grading            

  ADR-016           Grading           Five types, deterministic pinned    D03 approved;
                                      policy                              detailed policy
                                                                          proposed

  ADR-017           Modes             Both Learning and Exam              D04 approved

  ADR-018           Retakes           Unlimited public practice           D05 approved

  ADR-019           Learning feedback Per-exercise immutable policy       D09 approved

  ADR-020           Public preview    Sample without answer keys          D10 approved

  ADR-021           Public Reading    Anonymous published PUBLIC content  D02 approved

  ADR-022           Restricted access Default deny pending grant model    Proposed

  ADR-023           Providers         Defer                               Proposed
                                      email/dictionary/media/AI/meeting   
                                      provider selection                  
  -----------------------------------------------------------------------------------------

Older PRD/Pack ADR numbering may overlap. Preserve historical files; use
this consolidated register as the canonical proposed numbering after
sign-off.

------------------------------------------------------------------------

# 11. Remaining open decisions and deferrals

D01--D11 are approved. These items remain open and must be resolved
before their dependent implementation:

  ------------------------------------------------------------------------------
  ID                      Open item                      Gate
  ----------------------- ------------------------------ -----------------------
  O-01                    Exact Java/Spring/Security,    Before M1
                          Node/Next and dependency       initialization
                          compatibility; JPA, Flyway,    
                          OpenAPI and test stack         

  O-02                    Same-origin deployment and SSR Before M1 routing/auth
                          decision                       shell

  O-03                    Session store/restart behavior Before M1 auth
                          and concurrency implementation 

  O-04                    CSRF integration tests and     Before M1 auth
                          token rotation details         

  O-05                    Immutable version              Design approval now;
                          schema/publication transaction M2/M4 implementation

  O-06                    Exact sample preview           Before public discovery
                          count/selection                

  O-07                    D09 feedback DTO and item      Before M4
                          locking                        

  O-08                    Vocabulary duplicate policy    Before M3

  O-09                    Dictionary provider/licensing  Before dictionary
                                                         lookup

  O-10                    Media                          Before media
                          storage/region/retention/TTS   integration

  O-11                    Email provider, token expiry,  Before production
                          resend limits, sender domain   registration

  O-12                    AI provider/model and privacy  Before AI integration
                          terms                          

  O-13                    Teacher authoring scope and    Before M2
                          publication approval workflow  

  O-14                    Progress                       Before M3/M5
                          completion/resume/dashboard    
                          metrics                        

  O-15                    Account deletion, retention,   Before production;
                          audit retention, exceptional   identity implications
                          takedown                       before M1

  O-16                    Admin recovery and last-Admin  Before M1 production
                          procedure                      bootstrap

  O-17                    Branding and accessibility     UX review before M0
                          wireframes                     exit

  O-18                    Exam expiry sweep cadence,     Before M4
                          recovery, cancellation/grace   
                          policy                         

  O-19                    Grading limits, retry details, Before M4
                          question order/randomization   

  O-20                    Restricted grants and private  Before restricted
                          support access                 content/teacher review

  O-21                    Difficulty taxonomy, CEFR and  Before M2; band
                          score conversion               conversion separate
                                                         approval

  O-22                    Listening and class/meeting    Later release
                          integration scope              
  ------------------------------------------------------------------------------

Deferral is permitted only when it does not block the next milestone and
the dependent feature remains disabled/default-deny.

------------------------------------------------------------------------

# 12. UX wireframe blueprint

These are structural wireframes, not final visual design. Branding,
colors and typography remain open.

### 12.1 Public Reading Library

``` text
┌─────────────────────────────────────────────────────────────┐
│ LOGO      Reading   Vocabulary   About            Sign in    │
├─────────────────────────────────────────────────────────────┤
│ IELTS READING                                                │
│ Improve your reading, one passage at a time.                 │
│ [ Search passages... ] [Search]                               │
│ Topic [All]   Level [All]   Sort [Latest]                    │
│ ┌──────────────────┐ ┌──────────────────┐ ┌────────────────┐ │
│ │ Passage cover    │ │ Passage cover    │ │ Passage cover  │ │
│ │ Title            │ │ Title            │ │ Title          │ │
│ │ Topic · Level    │ │ Topic · Level    │ │ Topic · Level  │ │
│ │ 8 min · 720 words│ │ 6 min · 540 words│ │ 9 min · 800    │ │
│ │ [Read passage]   │ │ [Read passage]   │ │ [Read passage] │ │
│ └──────────────────┘ └──────────────────┘ └────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 12.2 Reading Player

``` text
┌─────────────────────────────────────────────────────────────┐
│ ← Library     Technology and Society              Sign in    │
├───────────────────────────────────────┬─────────────────────┤
│ Topic · Level · Estimated time        │ Reading tools       │
│ Passage title                         │ Translation [Show]  │
│ Paragraph 1: English text...          │ Vocabulary          │
│ Paragraph 2: English text...          │ Saved words         │
│ [Save progress]                       │                     │
│ Related exercises                     │                     │
└───────────────────────────────────────┴─────────────────────┘
```

### 12.3 Exercise preview and attempt

``` text
┌─────────────────────────────────────────────────────────────┐
│ Exercise title                         LEARNING | EXAM       │
│ Passage · 20 minutes (Exam)                                  │
├─────────────────────────────────────────────────────────────┤
│ Public sample question preview — no answers                  │
│ Verified learner: [Start Learning] [Start Exam]              │
├─────────────────────────────────────────────────────────────┤
│ Question 1  ○ A  ○ B  ○ C  ○ D                               │
│ Question 2  TRUE / FALSE / NOT GIVEN                         │
│ [Mark for review]                                             │
│ Exam timer: backed by server deadline                         │
│ [Submit attempt]                                              │
└─────────────────────────────────────────────────────────────┘
```

### 12.4 Result and review

``` text
┌─────────────────────────────────────────────────────────────┐
│ Result: 8 / 10                         Completed             │
│ Correct 8 · Incorrect 2 · Unanswered 0                       │
├─────────────────────────────────────────────────────────────┤
│ Question 1 · Correct                                         │
│ Your answer / Correct answer / Explanation / Evidence        │
│ Question 2 · Incorrect                                       │
│ Your answer / Correct answer / Explanation / Evidence        │
│ [Try again] [Back to Reading]                                │
└─────────────────────────────────────────────────────────────┘
```

------------------------------------------------------------------------

# 13. Milestone 1 plan

## 13.1 Scope

M1 is Identity + Security Foundation and minimal frontend authentication
shell.

Included: 1. Repository baseline and docs. 2. Verified Java/Spring
compatibility matrix. 3. Spring Boot skeleton. 4. PostgreSQL local
connection. 5. Flyway identity migrations only. 6. Users, roles,
user-role membership. 7. Pending registration and verification-token
model. 8. Cookie session + CSRF. 9. Single active session/account. 10.
60-minute idle and 7-day absolute timeout. 11. Admin bootstrap. 12.
Register, verify, login, logout, profile APIs. 13. Next.js auth pages
and minimal signed-in shell. 14. Global errors, validation, health and
structured logs. 15. Unit/integration/browser tests.

Excluded: - Reading CRUD/player. - Question Bank. - Attempts/grading. -
Vocabulary. - AI, dictionary, TTS. - Listening. - Classes, assignments,
schedules, attendance. - Payment/subscriptions.

## 13.2 Proposed identity migration sequence

Only after sign-off: - `V1__create_users.sql` - `V2__create_roles.sql` -
`V3__create_user_roles.sql` -
`V4__create_email_verification_tokens.sql` -
`V5__create_session_registry.sql` only if selected session design
requires an app-owned registry.

Never store Admin secrets/password hashes in migration scripts.

## 13.3 M1 acceptance criteria

-   Registration creates PENDING account.
-   Pending account cannot call learner-only APIs.
-   Valid verification activates account.
-   Invalid, expired, used or replayed token fails safely.
-   Public registration cannot assign TEACHER/ADMIN.
-   Login creates server-side session and rotates identifier.
-   Second login invalidates first session.
-   Idle timeout is 60 minutes; absolute lifetime 7 days, not extended
    by activity.
-   CSRF required for unsafe requests including pre-auth register/login.
-   Logout revokes session and clears cookie.
-   Disablement/password/privilege change revokes session.
-   First Admin bootstrap is secure, idempotent and not public.
-   Fresh PostgreSQL database migrates successfully; repeat startup is
    safe.
-   Backend/frontend builds and critical tests pass.

------------------------------------------------------------------------

# 14. Codex prompt --- only after explicit sign-off

``` text
You are the implementation agent for the IELTS Learning Platform.

Read completely:
- docs/PRD.md
- docs/MILESTONE-0-FINAL.md
- Existing M0 review artifacts in docs/

Authority:
- D01–D11 in MILESTONE-0-FINAL.md are approved owner decisions.
- Do not silently change them.
- Follow accepted ADRs in the signed-off document.
- Inspect actual repository state before editing.

Task:
Implement Milestone 1 only: Identity + Security Foundation and minimal frontend authentication shell.

Strict rules:
1. Do not implement Reading, Question Engine, Attempts, Vocabulary, Listening, AI or classes.
2. Do not create migrations for future modules.
3. Never commit credentials, tokens, Admin secrets or production personal data.
4. Never seed production Admin password in SQL.
5. Pending accounts cannot access learner-only APIs.
6. Enforce one active session/account and D06 timeouts.
7. Enforce CSRF for unsafe browser requests including pre-auth registration/login.
8. Use DTO allowlists; never return JPA entities.
9. Use forward-only Flyway migrations.
10. Add tests for every M1 acceptance criterion.
11. Do not claim completion if tests were not run.

Workflow:
A. Inspect repository, branch, runtime and tools.
B. Verify Java/Spring/Node compatibility before pinning versions.
C. Present file/module plan and blockers.
D. Implement in small reviewable steps.
E. Run backend, frontend, PostgreSQL migration and browser auth tests.
F. Review security and migration diffs.
G. Report files, tests, results and limitations.

Stop after M1 acceptance. Do not start M2 without explicit owner approval.
```

------------------------------------------------------------------------

# 15. Milestone 0 exit checklist

-   [x] D01--D05 approved.
-   [x] D06--D11 approved.
-   [x] Critical answer-key projection and review gate addressed.
-   [x] Immutable passage/exercise version graph proposed.
-   [x] Session, CSRF and single-device contract documented.
-   [x] Pending registration and verification-token model documented.
-   [x] Learning feedback policy documented.
-   [x] Public sample preview documented.
-   [x] Admin bootstrap approach documented.
-   [x] Corrected ERD and API consolidated.
-   [x] Authorization matrix consolidated.
-   [x] Milestone 1 scope and acceptance criteria consolidated.
-   [x] Architecture diagram included.
-   [x] Reading and Attempt UX wireframe blueprint included.
-   [ ] Exact toolchain compatibility matrix verified.
-   [ ] Session store/restart implementation selected.
-   [ ] Email provider and production verification configuration
    selected.
-   [ ] Admin recovery/last-Admin procedure defined.
-   [ ] Codex reviews this consolidated version.
-   [ ] Product Owner signs off final architecture and explicitly
    authorizes M1.

**Conclusion:** The D01--D11 decisions and corrected design are
consolidated. Milestone 0 is prepared for final review, not yet declared
closed. No code or migration is authorized by this document alone.
