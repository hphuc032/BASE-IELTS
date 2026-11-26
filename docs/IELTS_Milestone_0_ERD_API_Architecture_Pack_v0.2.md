# IELTS Learning Platform --- Milestone 0 Architecture Pack

**Document ID:** IELTS-M0-ARCH-001\
**Version:** 0.2 --- Updated after Product Owner decisions\
**Status:** Partially approved design; detailed schema/API remain
subject to technical review\
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

  -----------------------------------------------------------------------
  ID               Decision         Value                Status
  ---------------- ---------------- -------------------- ----------------
  C01              Repository       GitHub repository    Confirmed
                                    exists but is empty  

  C02              Coding agent     Codex                Confirmed

  C03              Development OS   Windows              Confirmed

  C04              Database         PostgreSQL           Confirmed

  C05              Backend          Spring Boot          Confirmed
                   preference                            

  C06              First target     IELTS learners       Confirmed

  C07              Learning model   Hybrid               Confirmed

  C08              Content workflow Teacher-authored +   Confirmed
                                    AI-assisted drafts   

  C09              Access model     Free/public content  Confirmed

  C10              First product    Reading              Confirmed
                   priority                              

  C11              Initial scale    Approximately 100    Confirmed
                                    learners             
  -----------------------------------------------------------------------

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

  Authentication          Spring Security +       Cookie/session
                          HttpOnly secure         approved;
                          cookie + server session implementation details
                          t                       o be specified
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

  ---------------------------------------------------------------------
  Status                             Meaning
  ---------------------------------- ----------------------------------
  200                                Successful read/update/action

  201                                Resource created

  204                                Successful delete/archive with no
                                     body, if used

  400                                Malformed request or invalid
                                     business input

  401                                Not authenticated

  403                                Authenticated but not authorized

  404                                Resource not found or
                                     intentionally concealed

  409                                State conflict, duplicate or
                                     invalid transition

  422                                Optional semantic validation
                                     convention; use consistently if
                                     selected

  429                                Rate limited

  500                                Unexpected server error
  ---------------------------------------------------------------------

Choose either 400 or 422 consistently for validation/business-rule
errors. This document does not freeze that choice.

------------------------------------------------------------------------

# 7. API Endpoint Inventory

## 7.1 Authentication and current user

  -------------------------------------------------------------------------
  Method          Endpoint                  Access          Purpose
  --------------- ------------------------- --------------- ---------------
  POST            `/auth/register`          Public          Register
                                                            learner

  POST            `/auth/login`             Public          Authenticate

  POST            `/auth/logout`            Authenticated   End
                                                            session/token

  POST            `/auth/forgot-password`   Public          Request reset

  POST            `/auth/reset-password`    Public with     Reset password
                                            reset token     

  GET             `/users/me`               Authenticated   Current profile

  PATCH           `/users/me`               Authenticated   Update own
                                                            profile
  -------------------------------------------------------------------------

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

  ----------------------------------------------------------------------------------
  Method        Endpoint                           Access             Purpose
  ------------- ---------------------------------- ------------------ --------------
  GET           `/reading`                         Public for public  Search/list
                                                   content            passages

  GET           `/reading/{passageId}`             Visibility-based   Passage
                                                                      metadata and
                                                                      paragraphs

  GET           `/reading/{passageId}/exercises`   Visibility-based   Learner-safe
                                                                      exercises

  GET           `/reading/{passageId}/progress`    Learner owner      Get personal
                                                                      progress

  PUT           `/reading/{passageId}/progress`    Learner owner      Save personal
                                                                      progress
  ----------------------------------------------------------------------------------

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

  -------------------------------------------------------------------------------
  Method         Endpoint                    Access              Purpose
  -------------- --------------------------- ------------------- ----------------
  GET            `/exercises`                Public/restricted   Browse available
                                             visibility rules    exercises

  GET            `/exercises/{exerciseId}`   Visibility-based    Get learner-safe
                                                                 exercise details
  -------------------------------------------------------------------------------

Learner-safe exercise details may include: - Exercise
title/description. - Mode availability. - Duration. - Question
prompts. - Options. - Instructions.

Must not include: - Correct answer keys. - Accepted answer variants. -
Private authoring notes. - Unreleased explanations.

## 7.5 Attempts

  ---------------------------------------------------------------------------------------------
  Method       Endpoint                                       Access             Purpose
  ------------ ---------------------------------------------- ------------------ --------------
  POST         `/attempts`                                    Learner            Start an
                                                                                 attempt

  GET          `/attempts/{attemptId}`                        Owner/authorized   Get own
                                                              teacher            attempt state

  PUT          `/attempts/{attemptId}/answers/{questionId}`   Attempt owner      Save/update
                                                                                 answer

  POST         `/attempts/{attemptId}/submit`                 Attempt owner      Submit and
                                                                                 grade

  GET          `/attempts/{attemptId}/result`                 Owner/review       Get result
                                                              permission         

  GET          `/attempts/{attemptId}/review`                 Owner/review       Review
                                                              permission         explanations

  GET          `/attempts`                                    Learner owner      List own
                                                                                 attempt
                                                                                 history
  ---------------------------------------------------------------------------------------------

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

  ----------------------------------------------------------------------------------------
  Method        Endpoint                             Access              Purpose
  ------------- ------------------------------------ ------------------- -----------------
  POST          `/teacher/reading`                   Teacher/Admin       Create draft
                                                                         passage

  PATCH         `/teacher/reading/{id}`              Author/authorized   Update draft
                                                     editor              

  PUT           `/teacher/reading/{id}/paragraphs`   Author/authorized   Replace/reorder
                                                     editor              paragraph
                                                                         collection

  POST          `/teacher/reading/{id}/publish`      Publisher           Publish
                                                     permission          

  POST          `/teacher/reading/{id}/archive`      Publisher           Archive
                                                     permission          

  POST          `/teacher/exercises`                 Teacher/Admin       Create exercise

  PATCH         `/teacher/exercises/{id}`            Author/authorized   Update draft
                                                     editor              

  POST          `/teacher/exercises/{id}/publish`    Publisher           Publish
                                                     permission          
  ----------------------------------------------------------------------------------------

Content authoring endpoints require explicit ownership and role checks.
Do not assume every teacher may edit every passage.

------------------------------------------------------------------------

# 8. Authorization Matrix

  --------------------------------------------------------------------------------
  Resource/action      Anonymous      Learner            Teacher             Admin
  ----------------- ------------ ------------ ------------------ -----------------
  Browse public              Yes          Yes                Yes               Yes
  passages                                                       

  Read public                Yes          Yes                Yes               Yes
  passage                                                        

  Read restricted             No   If granted                 If               Yes
  passage                                       assigned/granted 

  Save personal               No     Own only           Own only   Own only unless
  vocabulary                                                     support access is
                                                                           audited

  Start own attempt           No          Yes       Only if also      Only if also
                                                         learner           learner
                                                     role/policy       role/policy
                                                         permits           permits

  View own attempts           No     Own only           Own only   Own only unless
                                                                   audited support
                                                                            policy

  View another                No           No     Assigned scope  Authorized admin
  learner's attempt                                         only             scope

  Create passage              No           No                Yes               Yes
  draft                                                          

  Publish passage             No           No    Only if publish               Yes
                                              permission granted 

  Manage roles                No           No                 No               Yes

  Change own role             No           No                 No                No
                                                                   self-escalation
                                                                    through public
                                                                               API
  --------------------------------------------------------------------------------

Admin access to private learner data should be limited to operational
necessity and audited. Do not treat ADMIN as a reason to expose all
private data in every API.

------------------------------------------------------------------------

# 9. Architecture Decision Register

  ----------------------------------------------------------------------
  ADR              Topic            Proposed decision  Status
  ---------------- ---------------- ------------------ -----------------
  ADR-001          Application      Modular monolith   Proposed
                   architecture                        

  ADR-002          Database         PostgreSQL         Confirmed by
                                                       owner

  ADR-003          Backend          Spring Boot        Confirmed
                                                       preference;
                                                       version open

  ADR-004          Frontend         Next.js +          Proposed
                                    TypeScript         

  ADR-005          Persistence      Spring Data        Proposed
                                    JPA/Hibernate      

  ADR-006          Migrations       Flyway             Proposed

  ADR-007          API              REST/JSON          Proposed
                                    `/api/v1`          

  ADR-008          Authentication   Spring Security;   Open
                                    cookie/session vs  
                                    JWT undecided      

  ADR-009          IDs              UUID               Proposed

  ADR-010          Time handling    UTC `timestamptz`  Proposed

  ADR-011          Question         Type-specific      Proposed
                   payloads         validated JSON     
                                    structures         

  ADR-012          Content          Immutable attempt  Required
                   versioning       snapshot/version   principle;
                                                       mechanism open

  ADR-013          File storage     S3-compatible      Provider open
                                    storage later      

  ADR-014          Public access    Public published   Interpretation;
                                    reading can be     owner
                                    anonymous          confirmation
                                                       required

  ADR-015          MVP question     MCQ, T/F/NG,       Proposed; owner
                   types            Y/N/NG, Short      approval required
                                    Answer, Sentence   
                                    Completion         
  ----------------------------------------------------------------------

------------------------------------------------------------------------

# 10. Product Owner Decision Register --- Updated 2026-11-26

## 10.1 Approved decisions

  -----------------------------------------------------------------------
  Decision ID       Decision          Approved value    Consequence
  ----------------- ----------------- ----------------- -----------------
  D01               Authentication    Secure HttpOnly   Browser
                                      Cookie +          authentication
                                      server-managed    uses an HttpOnly
                                      session           session cookie;
                                                        CSRF protection
                                                        is required for
                                                        state-changing
                                                        requests.

  D02               Public Reading    Yes, anonymous    Public read
                    access            visitors can read endpoints do not
                                      published PUBLIC  require login.
                                      passages          Personalization
                                                        and learner
                                                        records require
                                                        authentication.

  D03               Reading question  Five types        Multiple Choice
                    types in MVP                        (single answer),
                                                        True/False/Not
                                                        Given, Yes/No/Not
                                                        Given, Short
                                                        Answer, Sentence
                                                        Completion.

  D04               Learning/Exam     Every MVP         Every published
                    modes             exercise supports MVP exercise
                                      both modes        exposes both
                                                        Learning and Exam
                                                        mode. No
                                                        per-exercise mode
                                                        toggle in MVP.

  D05               Retake policy     Unlimited         Learners may
                                                        start unlimited
                                                        attempts for
                                                        published public
                                                        practice
                                                        exercises. Every
                                                        attempt is stored
                                                        independently.
  -----------------------------------------------------------------------

These five decisions are approved by the Product Owner and must not be
silently changed by Codex or another agent.

## 10.2 Detailed interpretation of approved decisions

### D01 --- Cookie authentication

Approved direction: - Use a server-managed session. - Session identifier
is carried in a cookie marked `HttpOnly`. - In production, cookie must
be `Secure`. - Set an explicit `SameSite` policy based on deployment
topology. - Session cookie must not be readable by frontend
JavaScript. - Session creation, renewal, invalidation and expiration
must be documented. - State-changing requests must be protected against
CSRF. - Logout invalidates the server-side session and expires the
cookie. - Do not also introduce browser-stored bearer tokens unless the
Product Owner approves a later architecture change.

Still to specify technically: - Same-origin reverse proxy vs separate
frontend/backend origins. - `SameSite=Lax` vs `Strict` vs `None`. -
Session persistence strategy. - Idle timeout and absolute timeout. -
Session fixation protection and session rotation after login/privilege
changes. - CSRF token delivery pattern for the chosen frontend topology.

### D02 --- Public Reading

Anonymous visitors may: - List published passages where
`visibility=PUBLIC`. - Read published public passage metadata,
paragraphs and available translations. - View public exercise
descriptions and learner-safe question content if the exercise is also
published and public.

Anonymous visitors may not: - Save vocabulary to a personal notebook. -
Save reading progress. - Start or persist an attempt. - View learner
dashboards or personal history. - Access restricted content. - Access
answer keys or protected grading data.

Authenticated learners may use personalization features for their own
account.

### D03 --- Five MVP question types

The approved five types are: 1. `MULTIPLE_CHOICE_SINGLE` 2.
`TRUE_FALSE_NOT_GIVEN` 3. `YES_NO_NOT_GIVEN` 4. `SHORT_ANSWER` 5.
`SENTENCE_COMPLETION`

Matching question families and other completion formats are deferred
until a later release. Do not implement unsupported types as generic
unvalidated JSON.

### D04 --- Both modes on every exercise

Every published MVP Reading exercise supports: - `LEARNING` - `EXAM`

Mode behavior: - Learning Mode may expose translation and vocabulary
lookup, and may reveal feedback according to the approved learning
flow. - Exam Mode disables translation and dictionary assistance, uses a
timer when the exercise defines a duration, and reveals
answers/explanations only after submission. - Both modes use the same
published exercise content version and deterministic grading rules. -
The server enforces mode restrictions; frontend hiding alone is not
security.

### D05 --- Unlimited retakes

-   A learner may create multiple attempts for the same published public
    exercise.
-   A new attempt never overwrites an earlier attempt.
-   Each attempt stores its own start/submission timestamps, answers,
    score and review state.
-   No attempt-count limit is applied to public practice in MVP.
-   Rate limiting may protect abuse, but must not be used as a disguised
    pedagogical attempt limit.
-   Future assignments may introduce their own attempt policy only after
    owner approval.

## 10.3 Remaining open decisions

These decisions are not approved yet:

  -----------------------------------------------------------------------
  ID                      Open decision           Recommendation
  ----------------------- ----------------------- -----------------------
  O-01                    Java and Spring Boot    Verify supported Java
                          versions                LTS and compatible
                                                  Spring Boot release at
                                                  implementation time.

  O-02                    Frontend and backend    Prefer same-site
                          deployment topology     deployment/reverse
                                                  proxy if practical, to
                                                  simplify cookie and
                                                  CSRF configuration.

  O-03                    Session timeout and     Define idle + absolute
                          persistence             timeout; choose
                                                  persistence appropriate
                                                  to single-instance
                                                  initial deployment.

  O-04                    CSRF token delivery     Use Spring
                                                  Security-supported CSRF
                                                  mechanism compatible
                                                  with Next.js.

  O-05                    Exercise/question       Prefer immutable
                          versioning              published versions;
                                                  attempts reference
                                                  exact version.

  O-06                    Anonymous exercise      Public exercise prompts
                          access                  may be visible, but
                                                  confirm whether
                                                  anonymous users can
                                                  open a practice preview
                                                  without starting an
                                                  attempt.

  O-07                    Answer reveal within    Recommendation: allow
                          Learning Mode           per-question feedback
                                                  after answer, but
                                                  provide a final review
                                                  after submission.

  O-08                    Vocabulary duplicate    One learner vocabulary
                          policy                  entry per normalized
                                                  term, with source
                                                  context
                                                  retained/appendable.

  O-09                    Dictionary provider and Defer provider
                          licensing               selection; manual
                                                  vocabulary saving must
                                                  work without it.

  O-10                    Object storage provider Defer until image/audio
                                                  upload is required.

  O-11                    Email provider          Defer; decide whether
                                                  verification and
                                                  password reset are part
                                                  of first auth release.

  O-12                    AI provider/model and   AI output remains
                          privacy                 draft-only; provider
                                                  and data policy require
                                                  approval.

  O-13                    Teacher publication     Recommendation: teacher
                          rights                  authors drafts;
                                                  publishing requires
                                                  explicit publish
                                                  permission.

  O-14                    Progress calculation    Define completion and
                                                  progress rules before
                                                  dashboard metrics are
                                                  implemented.

  O-15                    Content                 Archive published
                          deletion/retention      content; define user
                                                  deletion and retention
                                                  policy before
                                                  production.

  O-16                    Admin bootstrap         Decide secure
                                                  first-admin creation
                                                  process; never allow
                                                  public self-assignment.

  O-17                    Full UI branding        Product name, logo,
                                                  colors and design
                                                  system remain open.
  -----------------------------------------------------------------------

## 10.4 Decision change control

If a later change conflicts with D01--D05: 1. Record the proposed change
and reason. 2. Identify affected database, API, security and frontend
behavior. 3. Obtain explicit Product Owner approval. 4. Update this
register and relevant ADRs. 5. Only then implement the change.

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

------------------------------------------------------------------------

# 14. Detailed API Contract Addendum --- Based on Approved D01--D05

This addendum refines the earlier endpoint inventory. It does not freeze
implementation details that remain in the open-decision register.

## 14.1 Cookie/session authentication contract

### Login

`POST /api/v1/auth/login`

Request:

``` json
{
  "email": "learner@example.com",
  "password": "StrongPassword123!"
}
```

Successful response body:

``` json
{
  "data": {
    "user": {
      "id": "uuid",
      "email": "learner@example.com",
      "displayName": "Alex Nguyen",
      "roles": ["LEARNER"]
    }
  }
}
```

The server also sets the session cookie through the `Set-Cookie`
response header. The session identifier must not be copied into the JSON
body.

Cookie requirements: - `HttpOnly` - `Secure` in production - Explicit
`SameSite` policy - Narrow `Path` and appropriate domain scope - No
unnecessary broad domain setting - Expiration aligned with server-side
session lifetime

The frontend must not read or store the session identifier in
localStorage/sessionStorage.

### Current session

`GET /api/v1/auth/me`

-   Returns the authenticated user's safe profile and roles.
-   Returns `401` when no valid session exists.
-   Must not expose session identifiers or password hashes.

### Logout

`POST /api/v1/auth/logout`

-   Requires a valid authenticated session.
-   Invalidates the server-side session.
-   Expires the cookie using matching cookie scope attributes.
-   Returns `204` or a documented success response.

### CSRF

Because cookie authentication automatically attaches credentials to
browser requests, state-changing operations require CSRF protection.

At minimum, protect: - POST - PUT - PATCH - DELETE

The chosen CSRF token delivery pattern must be compatible with the
frontend topology. Do not disable CSRF merely to make local frontend
requests work. Configure allowed origins and credentials narrowly.

### CORS and deployment

Preferred deployment if feasible: - Serve the frontend and API under the
same site/origin through a reverse proxy. - If origins differ, configure
exact allowed origins, credential support and CSRF token handling. -
Never combine wildcard allowed origins with credentialed requests. -
Local development origins must be explicit and environment-specific.

## 14.2 Public Reading endpoints

Anonymous: - `GET /api/v1/reading` - `GET /api/v1/reading/{passageId}` -
`GET /api/v1/reading/{passageId}/exercises` (only published public
exercises)

Visibility rules: - Passage must have `status=PUBLISHED` and
`visibility=PUBLIC`. - Exercise must have `status=PUBLISHED` and
`visibility=PUBLIC`. - Draft, in-review, archived or restricted content
must not leak through list, detail, search, sitemap or related-content
APIs. - Avoid exposing author-only fields, internal moderation notes,
answer keys or private storage paths.

Authenticated personalization: -
`GET /api/v1/reading/{passageId}/progress` -
`PUT /api/v1/reading/{passageId}/progress` - `GET /api/v1/vocabulary` -
`POST /api/v1/vocabulary`

For anonymous calls to personalized endpoints, return `401`.

## 14.3 Exercise mode contract

Every published MVP exercise supports both modes. The mode is selected
when an attempt starts.

`POST /api/v1/attempts`

Request:

``` json
{
  "exerciseId": "uuid",
  "mode": "EXAM"
}
```

Validation: - `mode` must be `LEARNING` or `EXAM`. - Exercise must be
published and visible to the learner. - Exercise must contain at least
one valid question. - Every question must have a valid answer key before
publication. - Unlimited attempts are permitted. - The server creates a
new attempt UUID for every accepted start request. - A client retry
should use an idempotency key if network retries may duplicate attempt
creation. Whether this header is mandatory remains an implementation
decision.

Exam restrictions: - Do not include translations in the exam response. -
Do not include dictionary lookup capability or protected answers. - Do
not return explanations before submission. - If duration is configured,
return `durationSeconds` and authoritative `startedAt`. - Server-side
expiration behavior must be defined before timed exam implementation;
client timer alone is insufficient.

Learning behavior: - Translation may be included when available. -
Vocabulary lookup/save may be used by an authenticated learner. -
Explanation reveal policy remains O-07 and must be decided before
implementing per-question feedback. - A learner's answer is not
automatically marked correct before the configured feedback point unless
the approved learning flow requires it.

## 14.4 Five MVP question payload schemas

These are the canonical logical payload shapes. Implement DTO validation
per type; do not accept arbitrary JSON.

### `MULTIPLE_CHOICE_SINGLE`

Learner answer:

``` json
{
  "selectedOptionId": "uuid"
}
```

Protected answer key:

``` json
{
  "correctOptionId": "uuid"
}
```

Validation: - Exactly one option ID. - Option must belong to the
question. - Unknown option IDs are rejected. - Correct option ID is
never returned before permitted review.

### `TRUE_FALSE_NOT_GIVEN`

Learner answer:

``` json
{
  "value": "TRUE"
}
```

Allowed values: - `TRUE` - `FALSE` - `NOT_GIVEN`

### `YES_NO_NOT_GIVEN`

Learner answer:

``` json
{
  "value": "YES"
}
```

Allowed values: - `YES` - `NO` - `NOT_GIVEN`

Do not accept `TRUE` in a `YES_NO_NOT_GIVEN` question or vice versa.

### `SHORT_ANSWER`

Learner answer:

``` json
{
  "text": "renewable energy"
}
```

Protected answer key:

``` json
{
  "acceptedAnswers": ["renewable energy", "clean energy"],
  "caseSensitive": false,
  "trimWhitespace": true,
  "maxWords": 2
}
```

### `SENTENCE_COMPLETION`

Learner answer:

``` json
{
  "text": "the local community"
}
```

Protected answer key:

``` json
{
  "acceptedAnswers": ["the local community"],
  "caseSensitive": false,
  "trimWhitespace": true,
  "maxWords": 4
}
```

For Short Answer and Sentence Completion, normalization must be
deterministic and documented. Initial recommendation: - Trim
leading/trailing whitespace. - Collapse repeated internal whitespace. -
Apply case-insensitive comparison by default. - Preserve meaningful
punctuation unless the owner approves a normalization rule. - Compare
against explicitly authored accepted variants. - Do not use
fuzzy/semantic matching in deterministic auto-grading.

The word-count convention (hyphenated words, contractions, punctuation)
must be specified in the question authoring rules before those settings
are relied on for scoring.

## 14.5 Attempt answer and submit contract

Save one answer: `PUT /api/v1/attempts/{attemptId}/answers/{questionId}`

Request:

``` json
{
  "answer": {
    "text": "renewable energy"
  }
}
```

The `answer` shape depends on the question type. The server loads the
question type from the attempt's immutable exercise version and
validates the payload against that type.

Submit: `POST /api/v1/attempts/{attemptId}/submit`

No learner-supplied score, correctness or answer key is accepted. The
server calculates all grading results.

Duplicate submission: - If the attempt is already graded, return the
existing result or a documented `409` response consistently. - Never
grade the same attempt twice into conflicting results. - Never permit
learner answer mutation after submission.

## 14.6 Review response

`GET /api/v1/attempts/{attemptId}/review`

Only allowed when: - The authenticated user owns the attempt, or - The
caller has an explicitly authorized teacher/admin scope, and - The
attempt has reached the configured review state.

Review may include: - Learner answer. - Correct answer. -
Correct/incorrect result. - Awarded/max points. - Explanation. -
Evidence paragraph/text.

Never expose answer keys through the normal exercise detail endpoint.

------------------------------------------------------------------------

# 15. Data Integrity and Versioning Design

## 15.1 Published content immutability

Recommendation: - Draft passages/exercises may be edited. - Publishing
creates a stable published version. - Editing published content creates
a new draft/version rather than mutating the version used by
active/historical attempts. - Attempts reference the exact exercise
version. - Grading uses the answer key from that version.

This is essential because D05 permits unlimited retakes and historical
scores must remain reproducible.

## 15.2 Recommended version model

Two implementation options remain:

**Option A --- Normalized version tables (recommended):** -
`exercise_versions` - `question_versions` - `question_option_versions` -
`question_explanation_versions` - `question_evidence_versions` -
`attempts.exercise_version_id`

Advantages: - Clear relational integrity. - Stable published content. -
Easier audit and historical review.

Trade-off: - More tables and authoring/publishing logic.

**Option B --- Snapshot JSON on attempt:** - Store a complete immutable
snapshot of questions, options and grading rules when an attempt starts.

Advantages: - Strong isolation from later content edits. - Easier to
reason about a single attempt snapshot.

Trade-offs: - Duplicated data. - More complex JSON schema evolution and
reporting.

Do not mix the two approaches casually. Select one in an ADR before
schema migrations. A practical hybrid is to maintain immutable published
exercise versions and store a small grading snapshot only where needed.

## 15.3 Constraints and indexes

Minimum proposed constraints: - Unique normalized user email. - Unique
role code. - Composite unique `(user_id, role_id)`. - Unique passage
slug. - Unique paragraph position per passage. - Unique translation
language per paragraph. - Unique question position per group. - Unique
option key and option position per question. - Unique attempt answer per
`(attempt_id, question_id)`. - Foreign keys for all parent/child
relationships. - Check constraints for positive points, nonnegative
durations and progress between 0 and 100.

Index candidates: -
`reading_passages(status, visibility, published_at)` -
`reading_passages(topic_id, status, visibility)` -
`reading_paragraphs(passage_id, position)` -
`exercises(passage_id, status, visibility)` -
`questions(group_id, position)` -
`attempts(learner_id, created_at DESC)` -
`attempts(exercise_id, learner_id)` -
`attempt_answers(attempt_id, question_id)` -
`user_vocabulary(user_id, saved_at DESC)` -
`reading_activity(user_id, last_opened_at DESC)`

Validate actual query plans before adding excessive indexes. Indexes
have write and storage costs.

## 15.4 Transaction boundaries

At minimum: - Register user + assign LEARNER role: one transaction. -
Publish exercise version: one transaction. - Start attempt + bind
immutable exercise version: one transaction. - Save answer: one
transaction. - Submit + freeze answers + grade + persist result: one
transaction where feasible. - Role change: one transaction plus audit
event.

------------------------------------------------------------------------

# 16. Authorization and Security Threat Review

  ------------------------------------------------------------------------
  Threat                  Risk                    Required control
  ----------------------- ----------------------- ------------------------
  IDOR on attempts        High                    Derive learner from
                                                  session; enforce attempt
                                                  ownership in service
                                                  layer

  Answer-key leakage      Critical                Separate authoring DTO
                                                  from learner DTO; never
                                                  serialize protected
                                                  fields

  CSRF                    High                    Spring Security CSRF
                                                  protection for
                                                  cookie-authenticated
                                                  state-changing requests

  Session theft           High                    HttpOnly, Secure,
                                                  SameSite, HTTPS, session
                                                  rotation and expiry

  Role self-escalation    Critical                Ignore role fields on
                                                  public registration;
                                                  admin-only role grant

  Draft content exposure  High                    Query-level
                                                  publication/visibility
                                                  filtering

  Duplicate attempt       Medium                  Idempotency strategy or
  creation                                        client request identity

  Duplicate submission    High                    State transition guard
                                                  and transactional
                                                  grading

  Malicious file upload   High                    Defer uploads or
                                                  validate
                                                  type/size/content and
                                                  store outside executable
                                                  web root

  XSS in rich content     High                    Sanitize authored rich
                                                  text; render plain text
                                                  safely by default

  Brute-force login       Medium                  Rate limiting, generic
                                                  errors and monitoring

  Sensitive logging       High                    Never log passwords,
                                                  cookies, session IDs,
                                                  answer keys or personal
                                                  data unnecessarily
  ------------------------------------------------------------------------

Security tests must include: - Anonymous access to restricted/draft
passage returns no content. - Learner A cannot read Learner B's attempt
or vocabulary. - Learner cannot submit an answer for another learner's
attempt. - Learner-facing exercise JSON contains no answer key. -
CSRF-less state-changing request is rejected under the selected CSRF
policy. - Public registration cannot create Teacher/Admin. - Exam mode
does not expose translation or explanation before submission.

------------------------------------------------------------------------

# 17. Updated Codex Prompt --- Architecture Review After Owner Approval

``` text
You are the solution architect and independent reviewer for the IELTS Learning Platform.

Read completely:
- docs/PRD.md
- docs/MILESTONE-0-ARCHITECTURE-PACK.md

The Product Owner has approved:
- D01: Cookie authentication using a server-managed session.
- D02: Published PUBLIC Reading content is readable anonymously.
- D03: Five MVP question types: Multiple Choice Single, True/False/Not Given, Yes/No/Not Given, Short Answer, Sentence Completion.
- D04: Every MVP exercise supports both Learning and Exam modes.
- D05: Unlimited retakes for public practice.

TASK:
Review and refine the Milestone 0 design only.

STRICT RULES:
1. Do not write application code.
2. Do not initialize Spring Boot or Next.js.
3. Do not create database migrations.
4. Do not add dependencies.
5. Do not reverse or reinterpret D01-D05.
6. Clearly distinguish approved decisions from open decisions.
7. Do not claim that remaining proposed technical choices are owner-approved.
8. Do not expose or invent secrets.
9. Do not copy copyrighted content from reference websites.

REVIEW:
- Validate the ERD, foreign keys, unique constraints and indexes.
- Review the cookie/session/CSRF design for the proposed frontend/backend topology.
- Validate public vs authenticated Reading access.
- Validate the five approved question payloads and deterministic grading rules.
- Validate both Learning and Exam mode behavior.
- Validate unlimited attempts and duplicate request behavior.
- Validate published exercise versioning and historical attempt reproducibility.
- Review API DTOs for answer-key leakage and IDOR.
- Identify contradictions between PRD and architecture pack.

OUTPUT:
1. Findings ranked Critical/High/Medium/Low.
2. Corrected ERD proposal, if needed.
3. Corrected endpoint/DTO proposal, if needed.
4. Updated open-decision list.
5. Proposed ADRs.
6. Milestone 1 implementation plan.
7. Exact files that should be added to docs/.
8. Explicit confirmation that no source code or migrations were changed.

Wait for Product Owner approval before implementation.
```

------------------------------------------------------------------------

# 18. Updated Milestone 0 Exit Criteria

## Approved by Product Owner

-   [x] D01 --- Cookie + server-managed session.
-   [x] D02 --- Anonymous reading of published PUBLIC passages.
-   [x] D03 --- Five MVP Reading question types.
-   [x] D04 --- Both Learning and Exam modes on every MVP exercise.
-   [x] D05 --- Unlimited retakes for public practice.

## Still required before Milestone 1 implementation

-   [ ] Resolve O-01: Java/Spring Boot version matrix.
-   [ ] Resolve O-02 to O-04: frontend/backend topology, session policy
    and CSRF integration.
-   [ ] Resolve O-05: immutable exercise version mechanism.
-   [ ] Resolve O-07: Learning Mode feedback reveal policy.
-   [ ] Resolve O-08: vocabulary duplicate behavior.
-   [ ] Resolve O-13: teacher publishing permissions.
-   [ ] Resolve O-16: secure first-admin bootstrap.
-   [ ] Review and approve final ERD.
-   [ ] Review and approve final API contract.
-   [ ] Run Codex design-only architecture review and resolve findings.
-   [ ] Approve Milestone 1 scope.

**Current state:** Product decisions D01-D05 are approved. The technical
design is refined but Milestone 0 is not yet closed. Do not create
migrations or implement authentication until the remaining blocking
design decisions and architecture review are complete.
