# IELTS Learning Platform --- Milestone 0 Architecture Pack

**Document ID:** IELTS-M0-ARCH-001\
**Version:** 0.1 --- Draft for Product Owner Review\
**Status:** Proposed design; NOT approved for implementation\
**Owner context:** Empty GitHub repository; Codex agent; Windows
development; PostgreSQL; Spring Boot backend\
**Related baseline:** `IELTS_Learning_Platform_PRD_Agent_Guide.md`

> This document is a design proposal. Confirmed owner decisions are
> separated from recommendations and unresolved decisions. Codex must
> not treat proposed choices as approved until the owner accepts them.

------------------------------------------------------------------------

# 1. Milestone 0 Outcome

Milestone 0 prepares the technical foundation without implementing
application features.

## 1.1 Confirmed project inputs

  --------------------------------------------------------------------------
  ID                Decision          Value                Status
  ----------------- ----------------- -------------------- -----------------
  C01               Repository        GitHub repository    Confirmed
                                      exists but is empty  

  C02               Coding agent      Codex                Confirmed

  C03               Development OS    Windows              Confirmed

  C04               Database          PostgreSQL           Confirmed

  C05               Backend           Spring Boot          Confirmed
                    preference                             

  C06               First target      IELTS learners       Confirmed

  C07               Learning model    Hybrid               Confirmed

  C08               Content workflow  Teacher-authored +   Confirmed
                                      AI-assisted drafts   

  C09               Access model      Free/public content  Confirmed

  C10               First product     Reading              Confirmed
                    priority                               

  C11               Initial scale     Approximately 100    Confirmed
                                      learners             
  --------------------------------------------------------------------------

## 1.2 Recommended baseline

  -----------------------------------------------------------------------
  Area                    Recommendation          Approval
  ----------------------- ----------------------- -----------------------
  Architecture            Modular monolith        Owner approval required

  Frontend                Next.js + TypeScript    Owner approval required

  Backend                 Spring Boot + Java      Spring Boot confirmed;
                                                  versions pending

  Database                PostgreSQL              Confirmed

  ORM                     Spring Data JPA /       Owner approval required
                          Hibernate               

  Migration               Flyway                  Owner approval required

  API                     REST/JSON under         Owner approval required
                          `/api/v1`               

  API documentation       OpenAPI                 Owner approval required

  Testing                 JUnit 5, Spring         Owner approval required
                          integration tests,      
                          Playwright              

  File storage            S3-compatible object    Provider and timing
                          storage later           open

  Authentication          Spring Security         Security framework
                                                  recommended;
                                                  session/token decision
                                                  open
  -----------------------------------------------------------------------

## 1.3 Milestone 0 deliverables

1.  Conceptual ERD.
2.  Entity dictionary and ownership rules.
3.  Reading question type model.
4.  Attempt lifecycle and grading contract.
5.  API endpoint inventory with request/response examples.
6.  Authorization matrix.
7.  Architecture decisions and open-decision register.
8.  Milestone 1 implementation boundary.

No source code, database migration or infrastructure should be created
as part of this design-only milestone.

------------------------------------------------------------------------

# 2. Domain Model Overview

The MVP domain is intentionally limited to:

-   Identity and roles.
-   Public/restricted learning content.
-   Reading passages and paragraphs.
-   Question groups and questions.
-   Exercises.
-   Attempts and submitted answers.
-   Vocabulary saved by a learner.
-   Basic reading activity/progress.

Classes, schedules, attendance, assignments, Listening, Speaking,
Writing and AI provider integration are later modules. The model should
not prematurely implement them.

## 2.1 Main relationships

``` mermaid
erDiagram
    USER ||--o{ USER_ROLE : has
    ROLE ||--o{ USER_ROLE : assigned
    USER ||--o{ USER_VOCABULARY : saves
    VOCABULARY_ENTRY ||--o{ USER_VOCABULARY : referenced_by

    TOPIC ||--o{ READING_PASSAGE : categorizes
    USER ||--o{ READING_PASSAGE : authors
    READING_PASSAGE ||--|{ READING_PARAGRAPH : contains
    READING_PARAGRAPH ||--o{ PARAGRAPH_TRANSLATION : has
    READING_PASSAGE ||--o{ EXERCISE : provides

    EXERCISE ||--|{ QUESTION_GROUP : contains
    QUESTION_GROUP ||--|{ QUESTION : contains
    QUESTION ||--o{ QUESTION_OPTION : offers
    QUESTION ||--o| QUESTION_EXPLANATION : explains
    QUESTION ||--o{ QUESTION_EVIDENCE : supported_by

    USER ||--o{ ATTEMPT : starts
    EXERCISE ||--o{ ATTEMPT : attempted_as
    ATTEMPT ||--o{ ATTEMPT_ANSWER : contains
    QUESTION ||--o{ ATTEMPT_ANSWER : answered

    USER ||--o{ READING_ACTIVITY : records
    READING_PASSAGE ||--o{ READING_ACTIVITY : viewed
```

`USER` means an authenticated account. Public anonymous reading does not
create a `USER` row or a learner-owned record.

## 2.2 Entity ownership and boundaries

  -----------------------------------------------------------------------
  Entity                  Owner/scope             Notes
  ----------------------- ----------------------- -----------------------
  User                    Account                 Private profile and
                                                  authentication identity

  Role                    Platform                Admin-managed role
                                                  definitions

  UserRole                Account/platform        Role assignment

  Topic                   Shared content          Public taxonomy

  ReadingPassage          Content author/editor   Visibility and
                                                  publication state

  ReadingParagraph        Parent passage          Ordered content unit

  ParagraphTranslation    Parent paragraph        Language-specific
                                                  translation

  Exercise                Content author/editor   Learning or exam
                                                  activity

  QuestionGroup           Parent exercise         Shared
                                                  instructions/passage
                                                  grouping

  Question                Parent group            Typed question
                                                  definition

  QuestionOption          Parent question         Only for question types
                                                  with options

  QuestionExplanation     Parent question         Revealed only when
                                                  review is allowed

  QuestionEvidence        Parent question         Passage paragraph/text
                                                  reference

  Attempt                 Learner                 Private learner-owned
                                                  record

  AttemptAnswer           Attempt + question      Private learner-owned
                                                  response

  VocabularyEntry         Shared                  Only if the platform
                          dictionary/content,     maintains canonical
                          optional                entries

  UserVocabulary          Learner                 Private saved
                                                  vocabulary

  ReadingActivity         Learner                 Private reading
                                                  activity/progress
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 3. Detailed ERD --- Proposed Logical Schema

All IDs should use UUIDs unless a strong reason requires otherwise.
PostgreSQL `uuid` is suitable. Timestamps should be stored consistently
in UTC using `timestamptz`; render in the user's local timezone.

## 3.1 Identity

### `users`

  Column              Type           Rules
  ------------------- -------------- ------------------------------
  id                  UUID           PK
  email               varchar(320)   unique, normalized, not null
  password_hash       varchar(255)   not null for password login
  display_name        varchar(120)   not null
  status              varchar(20)    ACTIVE, DISABLED, PENDING
  email_verified_at   timestamptz    nullable
  created_at          timestamptz    not null
  updated_at          timestamptz    not null

Do not store plaintext passwords. Do not expose `password_hash` in any
API response.

### `roles`

  Column   Type          Rules
  -------- ------------- ---------------------------------
  id       UUID          PK
  code     varchar(30)   unique; LEARNER, TEACHER, ADMIN
  name     varchar(80)   not null

### `user_roles`

  Column        Type          Rules
  ------------- ------------- -------------------
  user_id       UUID          FK users
  role_id       UUID          FK roles
  assigned_at   timestamptz   not null
  assigned_by   UUID          nullable FK users

Composite primary key: `(user_id, role_id)`.

**Rule:** Public registration must never allow a caller to assign ADMIN
or TEACHER to themselves. New public registrations receive LEARNER only.
Elevated roles require an authorized admin operation or controlled
bootstrap procedure.

## 3.2 Topics and reading content

### `topics`

  Column        Type           Rules
  ------------- -------------- -----------------------
  id            UUID           PK
  slug          varchar(100)   unique
  name          varchar(120)   not null
  description   text           nullable
  is_active     boolean        not null default true
  created_at    timestamptz    not null

### `reading_passages`

  Column                   Type           Rules
  ------------------------ -------------- ---------------------------------------
  id                       UUID           PK
  topic_id                 UUID           FK topics, nullable
  author_id                UUID           FK users
  slug                     varchar(180)   unique
  title                    varchar(240)   not null
  summary                  text           nullable
  cover_image_url          text           nullable
  difficulty_label         varchar(30)    nullable
  cefr_level               varchar(5)     nullable; validated enum/check
  estimated_read_minutes   smallint       nullable
  word_count               integer        computed/stored with clear policy
  visibility               varchar(20)    PUBLIC, RESTRICTED
  status                   varchar(20)    DRAFT, IN_REVIEW, PUBLISHED, ARCHIVED
  published_at             timestamptz    nullable
  created_at               timestamptz    not null
  updated_at               timestamptz    not null
  version                  integer        optimistic-lock/version field

Rules: - `PUBLISHED` is required before public access. - `PUBLIC`
controls anonymous visibility; `RESTRICTED` requires an authorized
account/relationship. - `difficulty_label` and CEFR are not IELTS band
scores. - Do not hard-delete passages referenced by attempts. Archive or
create a new content version.

### `reading_paragraphs`

  Column       Type           Rules
  ------------ -------------- ---------------------
  id           UUID           PK
  passage_id   UUID           FK reading_passages
  position     integer        not null, \>= 1
  heading      varchar(240)   nullable
  content      text           not null
  created_at   timestamptz    not null
  updated_at   timestamptz    not null

Unique `(passage_id, position)`.

### `paragraph_translations`

  Column               Type          Rules
  -------------------- ------------- -----------------------
  id                   UUID          PK
  paragraph_id         UUID          FK reading_paragraphs
  language_code        varchar(10)   e.g. `vi`
  translated_content   text          not null
  reviewed_by          UUID          nullable FK users
  reviewed_at          timestamptz   nullable
  created_at           timestamptz   not null
  updated_at           timestamptz   not null

Unique `(paragraph_id, language_code)`.

Translation must not be assumed to exist. Frontend should hide
translation controls when none is available.

## 3.3 Exercises and questions

### `exercises`

  -----------------------------------------------------------------------
  Column                  Type                    Rules
  ----------------------- ----------------------- -----------------------
  id                      UUID                    PK

  passage_id              UUID                    FK reading_passages,
                                                  nullable for future
                                                  standalone exercises

  created_by              UUID                    FK users

  title                   varchar(240)            not null

  description             text                    nullable

  exercise_type           varchar(30)             READING initially

  allowed_modes           jsonb or normalized     decision pending
                          relation                

  duration_seconds        integer                 nullable

  status                  varchar(20)             DRAFT, PUBLISHED,
                                                  ARCHIVED

  visibility              varchar(20)             PUBLIC, RESTRICTED

  version                 integer                 version/snapshot
                                                  strategy pending

  published_at            timestamptz             nullable

  created_at              timestamptz             not null

  updated_at              timestamptz             not null
  -----------------------------------------------------------------------

The owner must approve whether `allowed_modes` is represented as a JSON
array or a separate relation. Do not finalize SQL before that decision.

### `question_groups`

  Column              Type           Rules
  ------------------- -------------- --------------
  id                  UUID           PK
  exercise_id         UUID           FK exercises
  position            integer        not null
  title               varchar(240)   nullable
  instructions        text           nullable
  passage_reference   text           nullable
  created_at          timestamptz    not null

Unique `(exercise_id, position)`.

### `questions`

  Column                  Type           Rules
  ----------------------- -------------- --------------------------------------------
  id                      UUID           PK
  group_id                UUID           FK question_groups
  position                integer        not null
  question_type           varchar(40)    controlled enum
  prompt                  text           not null
  points                  numeric(6,2)   not null default 1, \> 0
  answer_schema_version   integer        not null
  answer_key              jsonb          protected; never in learner pre-submit DTO
  accepted_answers        jsonb          optional, validated by type
  settings                jsonb          type-specific validated settings
  created_at              timestamptz    not null
  updated_at              timestamptz    not null

Unique `(group_id, position)`.

**Important:** `answer_key` and `accepted_answers` are sensitive
assessment data. Do not return them through learner-facing exercise
endpoints before submission. Consider a separate authoring DTO and
learner DTO. JSONB is a proposal for typed question payloads, not
permission to accept arbitrary unvalidated JSON.

### `question_options`

  Column        Type          Rules
  ------------- ------------- --------------
  id            UUID          PK
  question_id   UUID          FK questions
  option_key    varchar(20)   not null
  content       text          not null
  position      integer       not null

Unique `(question_id, option_key)` and `(question_id, position)`.
Correctness should remain in protected answer data, not exposed in
learner option records.

### `question_explanations`

  Column                 Type          Rules
  ---------------------- ------------- ---------------------
  id                     UUID          PK
  question_id            UUID          unique FK questions
  explanation            text          not null
  paraphrasing_notes     text          nullable
  common_mistake_notes   text          nullable
  reviewed_by            UUID          nullable FK users
  reviewed_at            timestamptz   nullable

### `question_evidence`

  Column          Type      Rules
  --------------- --------- ---------------------------------
  id              UUID      PK
  question_id     UUID      FK questions
  paragraph_id    UUID      FK reading_paragraphs, nullable
  evidence_text   text      nullable
  start_offset    integer   nullable
  end_offset      integer   nullable
  note            text      nullable

Evidence offsets must be defined against a stable content version and a
consistent character-offset convention. If reliable offsets are not
feasible in MVP, use paragraph reference + evidence text first.

## 3.4 Attempts and answers

### `attempts`

  Column             Type           Rules
  ------------------ -------------- -------------------------------------------
  id                 UUID           PK
  learner_id         UUID           FK users
  exercise_id        UUID           FK exercises
  exercise_version   integer        not null
  mode               varchar(20)    LEARNING, EXAM
  status             varchar(20)    IN_PROGRESS, SUBMITTED, GRADED, CANCELLED
  started_at         timestamptz    not null
  submitted_at       timestamptz    nullable
  graded_at          timestamptz    nullable
  score              numeric(8,2)   nullable
  max_score          numeric(8,2)   nullable
  duration_seconds   integer        nullable
  created_at         timestamptz    not null
  updated_at         timestamptz    not null

Indexes: - `(learner_id, created_at desc)` -
`(exercise_id, learner_id)` - `(status, updated_at)` for operational
recovery if needed.

### `attempt_answers`

  Column              Type           Rules
  ------------------- -------------- -------------------------------
  id                  UUID           PK
  attempt_id          UUID           FK attempts
  question_id         UUID           FK questions
  answer_payload      jsonb          validated by question type
  is_correct          boolean        nullable until graded
  awarded_points      numeric(6,2)   nullable until graded
  max_points          numeric(6,2)   not null
  feedback_snapshot   jsonb          nullable; post-grade feedback
  saved_at            timestamptz    not null

Unique `(attempt_id, question_id)`.

Rules: - Attempt owner must equal authenticated learner for learner
operations. - Submitted attempts are immutable to learners. - Repeated
submit requests must not create duplicate grading outcomes. - Attempts
must refer to a stable exercise/question version or immutable
snapshot. - A content edit must not silently change historical answers
or scores.

## 3.5 Vocabulary

### `vocabulary_entries` (optional shared canonical dictionary)

  Column             Type           Rules
  ------------------ -------------- ----------
  id                 UUID           PK
  term               varchar(160)   not null
  normalized_term    varchar(160)   indexed
  ipa                varchar(120)   nullable
  part_of_speech     varchar(40)    nullable
  definition_en      text           nullable
  meaning_vi         text           nullable
  example_sentence   text           nullable
  source_provider    varchar(80)    nullable
  created_at         timestamptz    not null

This table should only be introduced if the product needs reusable
canonical dictionary entries. A provider lookup result may instead be
cached separately under an approved policy.

### `user_vocabulary`

  Column                Type           Rules
  --------------------- -------------- --------------------------------
  id                    UUID           PK
  user_id               UUID           FK users
  vocabulary_entry_id   UUID           nullable FK vocabulary_entries
  term                  varchar(160)   not null
  ipa                   varchar(120)   nullable
  part_of_speech        varchar(40)    nullable
  meaning               text           nullable
  example_sentence      text           nullable
  source_passage_id     UUID           nullable FK reading_passages
  source_paragraph_id   UUID           nullable FK reading_paragraphs
  context_sentence      text           nullable
  personal_note         text           nullable
  review_status         varchar(20)    NEW, LEARNING, KNOWN
  saved_at              timestamptz    not null
  updated_at            timestamptz    not null

Suggested uniqueness: `(user_id, normalized_term)` only if the owner
approves one saved record per normalized word. Otherwise allow multiple
contexts for the same term.

## 3.6 Reading activity

### `reading_activity`

  Column              Type           Rules
  ------------------- -------------- --------------------------------
  id                  UUID           PK
  user_id             UUID           FK users
  passage_id          UUID           FK reading_passages
  last_paragraph_id   UUID           nullable FK reading_paragraphs
  progress_percent    numeric(5,2)   0--100
  last_opened_at      timestamptz    not null
  completed_at        timestamptz    nullable

Unique `(user_id, passage_id)` if only one resumable progress record is
required. Detailed reading events can be added later if analytics
requires them.

------------------------------------------------------------------------

# 4. Question Type Contract

## 4.1 MVP recommendation

To avoid building an untestable generic engine, implement a small set
first:

**Recommended MVP subset:** 1. Multiple Choice --- single answer. 2.
True / False / Not Given. 3. Yes / No / Not Given. 4. Short Answer. 5.
Sentence Completion.

This subset is a recommendation and requires owner approval. Matching
Headings, Matching Information, Matching Features, Matching Sentence
Endings, Summary/Note/Table/Diagram Completion should be added in later
increments after the data contract is validated.

## 4.2 Type-specific payload examples

Examples below are conceptual JSON structures, not final API schemas.

### Multiple Choice

Learner answer:

``` json
{ "selectedOptionId": "uuid" }
```

Protected answer key:

``` json
{ "correctOptionId": "uuid" }
```

### True / False / Not Given

Learner answer:

``` json
{ "value": "TRUE" }
```

Allowed values: `TRUE`, `FALSE`, `NOT_GIVEN`.

### Yes / No / Not Given

Allowed values: `YES`, `NO`, `NOT_GIVEN`.

Do not mix the True/False and Yes/No variants in one question unless the
exercise explicitly defines the variant.

### Short Answer / Completion

Learner answer:

``` json
{ "text": "renewable energy" }
```

Protected answer key:

``` json
{
  "acceptedAnswers": ["renewable energy", "renewable energies"],
  "caseSensitive": false,
  "trimWhitespace": true,
  "maxWords": 2
}
```

The exact normalization and punctuation policy must be approved and
tested. Do not use fuzzy matching in the initial deterministic grader
unless explicitly specified.

## 4.3 Matching question family (later)

Potential answer representation:

``` json
{
  "matches": [
    { "promptKey": "1", "optionKey": "C" },
    { "promptKey": "2", "optionKey": "A" }
  ]
}
```

Need explicit rules for: - Reusable options. - Distractors. - One-to-one
vs reusable matches. - Partial credit. - Duplicate mappings. -
Unanswered items.

## 4.4 Question authoring validation

Before publishing: - Type is supported. - Prompt/instructions are
present. - Required options exist. - Correct answer is valid for the
type. - Points are positive. - Accepted answers follow the type
schema. - Explanation exists if required by publication policy. -
Evidence references valid passage content. - Question order is unique
within group. - No answer key is exposed in learner-facing responses.

------------------------------------------------------------------------

# 5. Attempt Lifecycle and API Behavior

## 5.1 State machine

``` mermaid
stateDiagram-v2
    [*] --> IN_PROGRESS: start
    IN_PROGRESS --> IN_PROGRESS: save answer
    IN_PROGRESS --> SUBMITTED: submit
    SUBMITTED --> GRADED: grading succeeds
    IN_PROGRESS --> CANCELLED: permitted cancellation
    GRADED --> [*]
    CANCELLED --> [*]
```

`CANCELLED` is optional and should not be implemented until cancellation
policy is approved.

## 5.2 Start attempt

Server validates: - Authenticated user is a learner. - Exercise is
published and visible to the learner. - Selected mode is allowed. -
Exercise version is available. - User is permitted to start another
attempt under the retry policy.

Server creates an `IN_PROGRESS` attempt and returns a learner-safe
exercise representation. It must not return answer keys.

## 5.3 Save answer

-   Only attempt owner may save.
-   Attempt must be `IN_PROGRESS`.
-   Question must belong to the exercise version used by the attempt.
-   Payload must validate against question type.
-   Repeating the same save should be safe.
-   Return saved state and timestamp.

## 5.4 Submit

-   Only attempt owner may submit.
-   Attempt must be `IN_PROGRESS`.
-   Validate required submission state.
-   Freeze the attempt's answers.
-   Grade deterministically.
-   Persist score and per-question results transactionally where
    practical.
-   Make duplicate submit requests safe.
-   Return result only after grading is complete.

## 5.5 Review

-   Only attempt owner or authorized teacher/admin may review.
-   Learner review availability depends on exercise settings and attempt
    status.
-   Do not expose explanations before the configured reveal point.
-   Teacher access must be limited to assigned learners/classes if that
    module exists.

------------------------------------------------------------------------

# 6. API Contract --- Conventions

This section is a proposed contract for owner review, not an
implementation-ready frozen specification.

## 6.1 Base

-   Base path: `/api/v1`
-   Content type: `application/json`
-   IDs: UUID strings
-   Timestamps: ISO 8601 UTC, e.g. `2026-11-26T05:00:00Z`
-   Pagination: `page`, `size`, optional `sort`
-   Default page size: 20; maximum: 100 (proposed)
-   All endpoints use HTTPS outside local development.

## 6.2 Standard success response

Single resource:

``` json
{
  "data": {
    "id": "uuid",
    "title": "Example"
  }
}
```

Paged response:

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

## 6.3 Standard error response

``` json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed.",
    "details": [
      {
        "field": "title",
        "code": "NOT_BLANK",
        "message": "Title is required."
      }
    ],
    "traceId": "correlation-id"
  }
}
```

Never return stack traces, SQL, secrets or internal implementation
details.

## 6.4 Proposed HTTP status usage

  -----------------------------------------------------------------------
  Status                              Meaning
  ----------------------------------- -----------------------------------
  200                                 Successful read/update/action

  201                                 Resource created

  204                                 Successful delete/archive with no
                                      body, if used

  400                                 Malformed request or invalid
                                      business input

  401                                 Not authenticated

  403                                 Authenticated but not authorized

  404                                 Resource not found or intentionally
                                      concealed

  409                                 State conflict, duplicate or
                                      invalid transition

  422                                 Optional semantic validation
                                      convention; use consistently if
                                      selected

  429                                 Rate limited

  500                                 Unexpected server error
  -----------------------------------------------------------------------

Choose either 400 or 422 consistently for validation/business-rule
errors. This document does not freeze that choice.

------------------------------------------------------------------------

# 7. API Endpoint Inventory

## 7.1 Authentication and current user

  -------------------------------------------------------------------------------
  Method            Endpoint                  Access            Purpose
  ----------------- ------------------------- ----------------- -----------------
  POST              `/auth/register`          Public            Register learner

  POST              `/auth/login`             Public            Authenticate

  POST              `/auth/logout`            Authenticated     End session/token

  POST              `/auth/forgot-password`   Public            Request reset

  POST              `/auth/reset-password`    Public with reset Reset password
                                              token             

  GET               `/users/me`               Authenticated     Current profile

  PATCH             `/users/me`               Authenticated     Update own
                                                                profile
  -------------------------------------------------------------------------------

**Open decision:** Cookie/session vs bearer JWT. Do not implement auth
endpoints until this is approved.

### Register request (proposed)

``` json
{
  "email": "learner@example.com",
  "password": "StrongPassword123!",
  "displayName": "Alex Nguyen"
}
```

Response:

``` json
{
  "data": {
    "id": "uuid",
    "email": "learner@example.com",
    "displayName": "Alex Nguyen",
    "roles": ["LEARNER"],
    "status": "PENDING"
  }
}
```

Registration must not accept a role field from the public caller.

## 7.2 Reading library

  -----------------------------------------------------------------------------------------
  Method            Endpoint                           Access             Purpose
  ----------------- ---------------------------------- ------------------ -----------------
  GET               `/reading`                         Public for public  Search/list
                                                       content            passages

  GET               `/reading/{passageId}`             Visibility-based   Passage metadata
                                                                          and paragraphs

  GET               `/reading/{passageId}/exercises`   Visibility-based   Learner-safe
                                                                          exercises

  GET               `/reading/{passageId}/progress`    Learner owner      Get personal
                                                                          progress

  PUT               `/reading/{passageId}/progress`    Learner owner      Save personal
                                                                          progress
  -----------------------------------------------------------------------------------------

List query proposal:
`?q=technology&topic=uuid&level=B2&page=0&size=20&sort=publishedAt,desc`

List response example:

``` json
{
  "data": [
    {
      "id": "uuid",
      "slug": "technology-and-society",
      "title": "Technology and Society",
      "summary": "A reading passage about technology.",
      "topic": { "id": "uuid", "name": "Technology" },
      "difficultyLabel": "Upper Intermediate",
      "cefrLevel": "B2",
      "estimatedReadMinutes": 8,
      "wordCount": 720,
      "visibility": "PUBLIC",
      "publishedAt": "2026-11-26T05:00:00Z"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

Passage response must contain learner-safe paragraph data and available
translations, but never protected answer keys.

## 7.3 Vocabulary

  Method   Endpoint             Access          Purpose
  -------- -------------------- --------------- -----------------------
  GET      `/vocabulary`        Learner owner   List own saved words
  POST     `/vocabulary`        Learner owner   Save a word
  GET      `/vocabulary/{id}`   Learner owner   Get own saved word
  PATCH    `/vocabulary/{id}`   Learner owner   Update note/status
  DELETE   `/vocabulary/{id}`   Learner owner   Remove own saved word

Create request:

``` json
{
  "term": "emerge",
  "ipa": "/ɪˈmɜːdʒ/",
  "partOfSpeech": "verb",
  "meaning": "to become known or begin to exist",
  "sourcePassageId": "uuid",
  "sourceParagraphId": "uuid",
  "contextSentence": "New opportunities emerge."
}
```

The server derives `userId` from the authenticated principal. Do not
accept `userId` from the learner request body.

## 7.4 Exercise discovery

  -----------------------------------------------------------------------------------
  Method            Endpoint                    Access              Purpose
  ----------------- --------------------------- ------------------- -----------------
  GET               `/exercises`                Public/restricted   Browse available
                                                visibility rules    exercises

  GET               `/exercises/{exerciseId}`   Visibility-based    Get learner-safe
                                                                    exercise details
  -----------------------------------------------------------------------------------

Learner-safe exercise details may include: - Exercise
title/description. - Mode availability. - Duration. - Question
prompts. - Options. - Instructions.

Must not include: - Correct answer keys. - Accepted answer variants. -
Private authoring notes. - Unreleased explanations.

## 7.5 Attempts

  -----------------------------------------------------------------------------------------------------
  Method            Endpoint                                       Access             Purpose
  ----------------- ---------------------------------------------- ------------------ -----------------
  POST              `/attempts`                                    Learner            Start an attempt

  GET               `/attempts/{attemptId}`                        Owner/authorized   Get own attempt
                                                                   teacher            state

  PUT               `/attempts/{attemptId}/answers/{questionId}`   Attempt owner      Save/update
                                                                                      answer

  POST              `/attempts/{attemptId}/submit`                 Attempt owner      Submit and grade

  GET               `/attempts/{attemptId}/result`                 Owner/review       Get result
                                                                   permission         

  GET               `/attempts/{attemptId}/review`                 Owner/review       Review
                                                                   permission         explanations

  GET               `/attempts`                                    Learner owner      List own attempt
                                                                                      history
  -----------------------------------------------------------------------------------------------------

Start request:

``` json
{
  "exerciseId": "uuid",
  "mode": "LEARNING"
}
```

Start response:

``` json
{
  "data": {
    "attemptId": "uuid",
    "exerciseId": "uuid",
    "exerciseVersion": 1,
    "mode": "LEARNING",
    "status": "IN_PROGRESS",
    "startedAt": "2026-11-26T05:00:00Z",
    "durationSeconds": null,
    "questions": [
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

Save answer request:

``` json
{
  "answer": {
    "selectedOptionId": "uuid"
  }
}
```

Save answer response:

``` json
{
  "data": {
    "attemptId": "uuid",
    "questionId": "uuid",
    "saved": true,
    "savedAt": "2026-11-26T05:05:00Z"
  }
}
```

Submit response:

``` json
{
  "data": {
    "attemptId": "uuid",
    "status": "GRADED",
    "score": 8,
    "maxScore": 10,
    "submittedAt": "2026-11-26T05:15:00Z",
    "gradedAt": "2026-11-26T05:15:01Z",
    "correctCount": 8,
    "incorrectCount": 2,
    "unansweredCount": 0
  }
}
```

Review response:

``` json
{
  "data": {
    "attemptId": "uuid",
    "questions": [
      {
        "questionId": "uuid",
        "yourAnswer": { "selectedOptionId": "uuid" },
        "correct": false,
        "correctAnswer": { "correctOptionId": "uuid" },
        "awardedPoints": 0,
        "maxPoints": 1,
        "explanation": "The passage indicates ...",
        "evidence": [
          {
            "paragraphId": "uuid",
            "text": "Evidence from the passage."
          }
        ]
      }
    ]
  }
}
```

**Security note:** `correctAnswer` and explanation are returned only
after the applicable review permission is satisfied.

## 7.6 Content authoring (Teacher/Admin)

  --------------------------------------------------------------------------------------------
  Method            Endpoint                             Access              Purpose
  ----------------- ------------------------------------ ------------------- -----------------
  POST              `/teacher/reading`                   Teacher/Admin       Create draft
                                                                             passage

  PATCH             `/teacher/reading/{id}`              Author/authorized   Update draft
                                                         editor              

  PUT               `/teacher/reading/{id}/paragraphs`   Author/authorized   Replace/reorder
                                                         editor              paragraph
                                                                             collection

  POST              `/teacher/reading/{id}/publish`      Publisher           Publish
                                                         permission          

  POST              `/teacher/reading/{id}/archive`      Publisher           Archive
                                                         permission          

  POST              `/teacher/exercises`                 Teacher/Admin       Create exercise

  PATCH             `/teacher/exercises/{id}`            Author/authorized   Update draft
                                                         editor              

  POST              `/teacher/exercises/{id}/publish`    Publisher           Publish
                                                         permission          
  --------------------------------------------------------------------------------------------

Content authoring endpoints require explicit ownership and role checks.
Do not assume every teacher may edit every passage.

------------------------------------------------------------------------

# 8. Authorization Matrix

  ------------------------------------------------------------------------------------
  Resource/action        Anonymous        Learner            Teacher             Admin
  ----------------- -------------- -------------- ------------------ -----------------
  Browse public                Yes            Yes                Yes               Yes
  passages                                                           

  Read public                  Yes            Yes                Yes               Yes
  passage                                                            

  Read restricted               No     If granted                 If               Yes
  passage                                           assigned/granted 

  Save personal                 No       Own only           Own only   Own only unless
  vocabulary                                                         support access is
                                                                               audited

  Start own attempt             No            Yes       Only if also      Only if also
                                                             learner           learner
                                                         role/policy       role/policy
                                                             permits           permits

  View own attempts             No       Own only           Own only   Own only unless
                                                                       audited support
                                                                                policy

  View another                  No             No     Assigned scope  Authorized admin
  learner's attempt                                             only             scope

  Create passage                No             No                Yes               Yes
  draft                                                              

  Publish passage               No             No    Only if publish               Yes
                                                  permission granted 

  Manage roles                  No             No                 No               Yes

  Change own role               No             No                 No                No
                                                                       self-escalation
                                                                        through public
                                                                                   API
  ------------------------------------------------------------------------------------

Admin access to private learner data should be limited to operational
necessity and audited. Do not treat ADMIN as a reason to expose all
private data in every API.

------------------------------------------------------------------------

# 9. Architecture Decision Register

  ------------------------------------------------------------------------
  ADR               Topic             Proposed decision  Status
  ----------------- ----------------- ------------------ -----------------
  ADR-001           Application       Modular monolith   Proposed
                    architecture                         

  ADR-002           Database          PostgreSQL         Confirmed by
                                                         owner

  ADR-003           Backend           Spring Boot        Confirmed
                                                         preference;
                                                         version open

  ADR-004           Frontend          Next.js +          Proposed
                                      TypeScript         

  ADR-005           Persistence       Spring Data        Proposed
                                      JPA/Hibernate      

  ADR-006           Migrations        Flyway             Proposed

  ADR-007           API               REST/JSON          Proposed
                                      `/api/v1`          

  ADR-008           Authentication    Spring Security;   Open
                                      cookie/session vs  
                                      JWT undecided      

  ADR-009           IDs               UUID               Proposed

  ADR-010           Time handling     UTC `timestamptz`  Proposed

  ADR-011           Question payloads Type-specific      Proposed
                                      validated JSON     
                                      structures         

  ADR-012           Content           Immutable attempt  Required
                    versioning        snapshot/version   principle;
                                                         mechanism open

  ADR-013           File storage      S3-compatible      Provider open
                                      storage later      

  ADR-014           Public access     Public published   Interpretation;
                                      reading can be     owner
                                      anonymous          confirmation
                                                         required

  ADR-015           MVP question      MCQ, T/F/NG,       Proposed; owner
                    types             Y/N/NG, Short      approval required
                                      Answer, Sentence   
                                      Completion         
  ------------------------------------------------------------------------

------------------------------------------------------------------------

# 10. Questions Requiring Product Owner Approval

These are blocking decisions before implementation of the corresponding
areas.

## P0 --- Must decide before Milestone 1 authentication/security implementation

**Q1. Authentication model** - A. Secure HttpOnly cookie +
server-managed session. - B. Access token + refresh token using secure
cookies. - C. Bearer JWT. - Recommendation: choose a browser-first
secure cookie design; final choice depends on deployment topology and
frontend/backend origins.

**Q2. Public content access** - A. Anyone can read published public
passages without login; login required for saving
progress/vocabulary/attempts. - B. Login required even to read. -
Recommendation: A, consistent with free/public access.

**Q3. Role provisioning** - Recommendation: public registration creates
LEARNER only. Admin is provisioned through a controlled bootstrap
process; teacher roles are granted by admin.

**Q4. Java/Spring versions** - Choose a supported Java LTS and
compatible Spring Boot release after verifying current compatibility. Do
not pin an unverified version.

## P0 --- Must decide before Reading/Assessment implementation

**Q5. Initial Reading question types** - A. Recommended five types: MCQ,
T/F/NG, Y/N/NG, Short Answer, Sentence Completion. - B. Implement all
listed IELTS Reading question types in MVP. - Recommendation: A, then
expand.

**Q6. Exercise modes** - A. Every exercise supports both Learning and
Exam modes. - B. Teacher selects allowed modes per exercise. -
Recommendation: B, with a default selected by the author.

**Q7. Retake policy** - A. Unlimited attempts. - B. Teacher-configurable
attempt limit. - C. One attempt per assignment, unlimited for public
practice. - Recommendation: C for a future assignment-aware product; for
public MVP, unlimited practice attempts.

**Q8. Answer reveal** - A. Learning Mode can reveal explanations
immediately after each answer. - B. All explanations appear only after
full submission. - Recommendation: make this an exercise setting; Exam
Mode always reveals only after submission.

**Q9. Content versioning** - A. Snapshot all question and answer content
into each attempt. - B. Keep immutable published exercise versions and
link attempts to version. - Recommendation: B if versioning can be
implemented reliably; otherwise snapshot the required grading data. Do
not allow historical attempts to be silently changed.

**Q10. Vocabulary duplicates** - A. One saved entry per normalized word
per learner. - B. Allow multiple entries for the same word from
different contexts. - Recommendation: one word entry with multiple
source/context references, if needed.

## P1 --- Can be decided before relevant later milestone

-   Dictionary provider and license.
-   IPA/pronunciation source.
-   TTS provider.
-   Object storage provider.
-   Email provider.
-   AI provider/model and data privacy terms.
-   Zoom/Google Meet integration depth.
-   Teacher publication approval workflow.
-   Full progress metrics and band conversion.
-   Assignment/class/schedule scope.

------------------------------------------------------------------------

# 11. Milestone 1 Boundary

After owner approval of the P0 decisions, Milestone 1 may implement
only:

1.  Repository baseline and project documentation.
2.  Java/Spring Boot application skeleton.
3.  PostgreSQL local development connection.
4.  Flyway baseline migration for identity only.
5.  User and role persistence.
6.  Approved authentication flow.
7.  Authorization foundation.
8.  Global error response and validation.
9.  Health endpoint.
10. Unit/integration tests.

Milestone 1 must **not** implement Reading, Question Bank, Attempts,
Vocabulary, AI, Listening or class management yet.

Suggested first migrations after approval:

-   `V1__create_users.sql`
-   `V2__create_roles.sql`
-   `V3__create_user_roles.sql`

The exact migration split is subject to the agreed schema and project
conventions. Do not create these files until the owner approves the
identity schema and authentication decision.

------------------------------------------------------------------------

# 12. Codex Prompt --- Milestone 0 Review Only

Use this prompt in Codex after adding both the original PRD and this
document to the empty repository.

``` text
You are the solution architect for the IELTS Learning Platform.

READ THESE FILES COMPLETELY:
- docs/PRD.md
- docs/MILESTONE-0-ARCHITECTURE-PACK.md

The repository is currently expected to be empty. Verify this rather than assuming it.

TASK:
Perform a Milestone 0 architecture review. This is a DESIGN-ONLY task.

STRICT RULES:
1. Do not implement application code.
2. Do not initialize Spring Boot or Next.js yet.
3. Do not create database migrations or schema files.
4. Do not add dependencies.
5. Do not choose answers to open Product Owner questions.
6. Do not silently change confirmed requirements.
7. Do not claim the proposed ERD/API is approved or frozen.
8. If you identify a conflict, report it with document section and impact.

REVIEW:
- Validate the ERD relationships and ownership rules.
- Review PostgreSQL data types, constraints and indexes.
- Review question-type payloads and grading determinism.
- Review attempt lifecycle, autosave and duplicate submission behavior.
- Review content versioning and historical attempt integrity.
- Review public vs authenticated access.
- Review authorization and IDOR risks.
- Review API request/response consistency.
- Identify missing requirements and overengineering.
- Verify that the proposal is appropriate for approximately 100 initial learners.

OUTPUT:
1. Architecture review summary.
2. ERD corrections (if any), with rationale.
3. API contract corrections (if any), with examples.
4. Security findings ranked Critical/High/Medium/Low.
5. Data integrity and migration risks.
6. Open decisions that must be answered by the Product Owner.
7. A proposed Milestone 1 implementation plan.
8. Explicit statement that no code or migrations were changed.

Do not implement anything. Wait for Product Owner approval.
```

------------------------------------------------------------------------

# 13. Milestone 0 Exit Criteria

Milestone 0 is complete only when:

-   [ ] Owner confirms the P0 open decisions.
-   [ ] ERD is reviewed and approved.
-   [ ] Question type MVP subset is approved.
-   [ ] Attempt lifecycle and grading rules are approved.
-   [ ] Authentication design is approved.
-   [ ] Public/private access rules are approved.
-   [ ] API contract is reviewed and approved.
-   [ ] Authorization matrix is approved.
-   [ ] Java/Spring Boot version compatibility is verified.
-   [ ] Codex architecture review findings are resolved or explicitly
    accepted.
-   [ ] Milestone 1 scope is approved.

**Until these criteria are met, the design remains a proposal and
implementation must not proceed beyond explicitly authorized
scaffolding.**
