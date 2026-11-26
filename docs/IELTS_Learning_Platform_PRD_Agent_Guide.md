# IELTS Learning Platform

## Product Requirements, System Architecture & AI Agent Implementation Guide

**Document ID:** IELTS-PLATFORM-PRD-001\
**Version:** 1.0\
**Status:** Baseline specification for product/architecture planning\
**Audience:** Product owner, developer, technical reviewer, Claude Code,
Codex and other AI coding agents\
**Initial scale:** Approximately 100 learners\
**Primary language:** English learning content; Vietnamese support in
explanations and translations\
**Initial program:** IELTS\
**Learning model:** Hybrid (teacher-led live classes + independent
online practice)\
**Access model:** Free/public learning content, with accounts for
personalized features\
**Initial product priority:** Reading\
**Backend preference:** Java Spring Boot\
**Frontend preference:** Next.js + TypeScript\
**Database preference:** PostgreSQL

------------------------------------------------------------------------

# 0. Instructions for Every Human and AI Agent

This document is the baseline product and engineering specification.
Read it completely before proposing or changing implementation.

## 0.1 Mandatory operating rules

1.  **Do not silently change product decisions.** If a requirement
    conflicts with the implementation, report the conflict and propose
    options. Do not choose a materially different product direction
    without owner approval.
2.  **Do not implement the entire platform in one large step.** Work in
    small, reviewable milestones.
3.  **Do not start feature implementation before the relevant
    architecture, data model and API contract are agreed.** The owner
    may explicitly authorize a limited prototype.
4.  **Do not invent requirements.** Distinguish:
    -   Confirmed decisions in this document.
    -   Recommendations that still need owner approval.
    -   Open decisions that must be resolved.
5.  **Do not assume external services are configured.** Zoom, Google
    Meet, email, AI, dictionary, TTS, object storage and payment
    integrations require credentials, configuration, cost review and
    owner approval.
6.  **Do not expose secrets.** Never commit `.env`, passwords, API keys,
    private tokens, signing keys, database credentials or production
    data.
7.  **Do not commit generated or copied copyrighted learning material
    without confirming usage rights.** Reference websites are
    inspiration for product patterns, not sources to scrape or
    reproduce.
8.  **Do not return JPA entities directly from REST endpoints.** Use
    request/response DTOs and explicit mapping.
9.  **Do not bypass authorization in service methods.** Every
    learner-specific operation must enforce ownership or an authorized
    teaching/admin relationship on the server.
10. **Do not use frontend-only checks as security.** Backend
    authorization is authoritative.
11. **Do not edit an existing database schema or migration that has
    already been applied in a shared environment.** Create a new forward
    migration. Never rewrite migration history without explicit owner
    approval and a safe migration plan.
12. **Do not add dependencies, infrastructure, microservices, queues,
    caches or AI providers merely because they may be useful.** Explain
    the need, cost and operational impact first.
13. **Do not claim a feature is complete because its UI exists.**
    Completion requires backend behavior, validation, authorization,
    persistence, tests and documented acceptance criteria.
14. **Do not move to the next milestone while critical tests or
    acceptance criteria are failing.**
15. **Keep documentation synchronized.** When an approved design
    changes, update the relevant PRD, ERD, API contract, architecture
    notes and tests.

## 0.2 Required workflow for an AI coding agent

For each task:

1.  Read this document and inspect the current repository before
    editing.
2.  Report the existing branch, project structure, relevant modules,
    migrations and tests.
3.  Identify the exact requirement IDs being implemented.
4.  List assumptions, dependencies, risks and files expected to change.
5.  If an important decision is open, ask the owner instead of guessing.
6.  Propose a small implementation plan.
7.  Implement only the approved scope.
8.  Add or update tests.
9.  Run relevant tests, formatting, linting and build commands.
10. Review authorization, validation, error handling, logging and data
    migration impacts.
11. Summarize changed files, commands run, test results, known
    limitations and follow-up decisions.
12. Do not mark the task complete if tests could not be run; state why.

## 0.3 Definition of Done

A feature is complete only when all applicable conditions are met:

-   Acceptance criteria pass.
-   API behavior is documented.
-   Input validation and error responses are implemented.
-   Authorization is enforced server-side.
-   Data is persisted correctly.
-   Database migrations are safe and reproducible.
-   Automated tests cover success and important failure paths.
-   UI states include loading, empty, success and error states where
    applicable.
-   Responsive behavior is checked for relevant screens.
-   No secrets or private user data are exposed.
-   Documentation is updated.
-   Build and relevant test suites pass.

------------------------------------------------------------------------

# 1. Product Vision

## 1.1 Product statement

Build a free IELTS learning platform that combines teacher-led online
classes with independent learning and practice.

The platform should help learners:

-   Read IELTS-oriented English content.
-   Understand texts through optional Vietnamese translations.
-   look up words in context and save vocabulary.
-   Practice IELTS Reading question types.
-   Review correct answers and understand mistakes.
-   Track learning activity and progress.
-   Participate in teacher-managed classes and assignments.

The first release prioritizes Reading. The architecture should not
prevent later expansion to Listening, Speaking, Writing, TOEIC, Kids or
general Communication programs, but those future programs must not
inflate the first release unnecessarily.

## 1.2 Product principle

The platform is not merely a content website or a class-management
system. It should create a repeatable learning loop:

**Learn → Practice → Review → Remember → Improve**

1.  **Learn:** Read lessons and understand vocabulary and concepts.
2.  **Practice:** Answer questions in learning or exam mode.
3.  **Review:** Inspect results, explanations and evidence.
4.  **Remember:** Save vocabulary and review it later.
5.  **Improve:** Use progress information to decide what to practice
    next.

## 1.3 Initial audience and scale

-   Primary audience: IELTS learners.
-   Initial expected scale: approximately 100 learners.
-   Other roles: teachers and administrators.
-   Initial deployment should favor simplicity, maintainability and low
    operational overhead.
-   Do not design for internet-scale traffic or introduce distributed
    infrastructure without evidence.

## 1.4 Learning model: Hybrid

Hybrid means the platform supports both:

-   Teacher-led live learning, initially using external meeting tools
    such as Zoom or Google Meet.
-   Independent learning on the website, including reading, practice,
    vocabulary and progress tracking.

The platform does not need to implement its own video-conferencing
system in the MVP.

## 1.5 Access model: Free

The user selected a free access model.

Interpretation for the initial product:

-   Published content designated **Public** can be accessed without
    payment.
-   Visitors may be allowed to read public content without signing in.
-   Account registration is needed for personalized features such as
    saving vocabulary, recording attempts, tracking progress and
    receiving assignments.
-   The product must not introduce paid subscriptions, payment
    processing or paywalls unless the owner later approves a
    business-model change.

Whether every public feature is available anonymously or some require an
account is an implementation detail to confirm in the access-control
design. Do not assume all user-specific data can be used anonymously.

------------------------------------------------------------------------

# 2. Confirmed Decisions, Recommendations and Open Decisions

## 2.1 Confirmed by the owner

  -----------------------------------------------------------------------
  ID                      Decision                Confirmed value
  ----------------------- ----------------------- -----------------------
  D-01                    Initial learner program IELTS

  D-02                    Learning model          Hybrid

  D-03                    Content production      Both teacher-authored
                                                  and AI-assisted

  D-04                    Access/business model   Free

  D-05                    First feature priority  Reading

  D-06                    Backend preference      Spring Boot

  D-07                    Initial scale           Approximately 100
                                                  learners

  D-08                    Product direction       A dedicated learning
                                                  platform, not only a
                                                  center-management
                                                  website
  -----------------------------------------------------------------------

## 2.2 Recommended technical baseline (not all choices are frozen)

  -----------------------------------------------------------------------
  Area                    Recommendation          Status
  ----------------------- ----------------------- -----------------------
  Frontend                Next.js + TypeScript    Recommended

  UI styling              Tailwind CSS            Recommended

  UI components           shadcn/ui, if suitable  Optional recommendation

  Backend                 Spring Boot modular     Strong recommendation
                          monolith                

  Database                PostgreSQL              Recommended

  ORM                     Spring Data JPA /       Recommended
                          Hibernate               

  Migration               Flyway                  Strong recommendation

  API style               REST + JSON             Recommended

  API documentation       OpenAPI / Swagger       Recommended

  Authentication          Spring Security; exact  Open
                          token/session design    
                          TBD                     

  File storage            S3-compatible storage / Recommended, provider
                          Cloudflare R2           TBD

  Tests                   JUnit 5, Mockito;       Recommended
                          Playwright for          
                          end-to-end              

  Deployment              Render or a managed VPS Provider TBD

  CI/CD                   GitHub Actions          Recommended
  -----------------------------------------------------------------------

The owner has selected Spring Boot, but the exact Spring Boot version,
Java version and compatible dependency versions must be verified against
the target environment before project initialization. Do not assume a
version without checking compatibility.

## 2.3 Open decisions requiring owner approval

The following are not settled by this document:

1.  Exact Java and Spring Boot versions.
2.  Authentication approach: secure cookie/session or bearer JWT; token
    rotation and refresh behavior.
3.  Whether public reading pages require an account for any interaction
    beyond reading.
4.  Exact public/private visibility rules for lessons, exercises,
    explanations and answer keys.
5.  Dictionary data provider and licensing.
6.  Audio/TTS provider and whether audio is generated, uploaded or both.
7.  Object-storage provider, region, retention and access policy.
8.  Email provider and whether email verification/reset is in MVP.
9.  Zoom/Google Meet integration depth; initial release may simply store
    meeting links.
10. Whether teachers can publish content directly or admin approval is
    mandatory.
11. Initial CEFR/IELTS difficulty taxonomy and how levels are assigned.
12. Exact IELTS score conversion policy and supported question types in
    the first release.
13. Whether Reading MVP includes all listed IELTS question types or a
    smaller initial subset.
14. Whether Listening is included in MVP or the next release. Current
    roadmap places it after the Reading-first MVP.
15. Whether a learner can retake a submitted attempt and whether
    question order/options are randomized.
16. Privacy, consent, data retention and deletion policy.
17. Branding, product name, logo, colors and final visual design.

Agents must not quietly resolve these open decisions. For a task that
depends on one, surface the decision and wait for approval or use a
clearly isolated temporary implementation that does not lock the
architecture.

------------------------------------------------------------------------

# 3. Product Scope and Release Boundaries

## 3.1 MVP: IELTS Reading Learning Platform

The MVP should prove the core learning loop.

### Included

-   Authentication and role-based authorization.
-   Learner, Teacher and Admin roles.
-   Learner dashboard (basic).
-   Public Reading Library.
-   Reading content management.
-   Reading Player.
-   Optional paragraph translations.
-   Vocabulary lookup interface and learner vocabulary notebook.
-   IELTS Reading exercises and question bank.
-   Learning Mode and Exam Mode.
-   Attempt persistence and automatic grading for supported question
    types.
-   Answer explanations and evidence references.
-   Reading history and basic progress.
-   Basic content publishing workflow.
-   Responsive desktop/mobile experience.
-   API documentation and automated tests.

### Not required for MVP

-   Native mobile apps.
-   Microservices.
-   Kubernetes.
-   Real-time chat.
-   In-house video conferencing.
-   Payment gateway or subscriptions.
-   AI auto-publishing.
-   Fully automated AI band scoring.
-   AI Speaking assessment.
-   AI Writing assessment.
-   Complex gamification.
-   Advanced adaptive learning.
-   Full-featured parent portal.
-   TOEIC, Kids and Communication program experiences.
-   Large-scale content scraping or automatic importing from reference
    sites.

## 3.2 Later releases

### Release 1.1 --- Listening

-   Listening library.
-   Audio player and playback controls.
-   Transcript where licensed/available.
-   IELTS Listening question types.
-   Attempts, grading and explanations.
-   Listening history and progress.

### Release 1.2 --- Teacher operations and hybrid learning

-   Class management.
-   Teacher-to-class assignments.
-   Schedule and attendance.
-   Meeting links.
-   Teacher feedback.
-   Writing submission and Speaking recording workflows.
-   More complete teacher dashboard.

### Release 2.0 --- Smart learning

-   AI-assisted Writing feedback.
-   AI-assisted Speaking practice.
-   Spaced repetition.
-   Personalized recommendations.
-   Advanced analytics.
-   Carefully scoped gamification.

------------------------------------------------------------------------

# 4. User Roles and Permissions

## 4.1 Learner

Can:

-   View public published content.
-   Register and manage their own profile.
-   Read published lessons.
-   Use permitted dictionary/translation features.
-   Save and manage their own vocabulary.
-   Start and submit permitted exercises.
-   View their own attempts, answers and progress.
-   View assignments and schedules for classes they belong to.
-   Submit work when assigned.

Cannot:

-   View another learner's private data.
-   View protected answer keys before submission.
-   Edit published course content.
-   Change their own role.
-   Access teacher/admin APIs.

## 4.2 Teacher

Can, within assigned scope:

-   View assigned classes and learners.
-   Create and edit permitted learning content.
-   Create exercises and questions.
-   Assign lessons/exercises to assigned classes or learners.
-   Review submissions.
-   Give feedback.
-   View learner progress for assigned learners.
-   Manage class learning activities if granted permission.

Cannot:

-   Access unrelated classes by changing IDs.
-   Assign themselves arbitrary admin privileges.
-   View private learner data outside their authorized scope.
-   Change system-wide roles or security settings.

## 4.3 Admin

Can:

-   Manage users and roles.
-   Manage programs, courses and classes.
-   Manage content and publishing.
-   Manage teacher assignments.
-   View operational reports.
-   Configure permitted platform settings.

Admin operations must be audited where they affect accounts, roles,
content publication or important data.

## 4.4 Authorization rules

-   Enforce permissions on the backend.
-   Validate both role and resource scope.
-   Never rely only on hidden frontend buttons.
-   Use ownership checks for learner-owned records.
-   Use class/teacher assignment checks for teacher operations.
-   Prevent insecure direct object reference (IDOR).
-   Return consistent 401/403 behavior without leaking private resource
    details.

------------------------------------------------------------------------

# 5. Functional Requirements

Priority labels:

-   **P0:** Required for MVP.
-   **P1:** Important next release.
-   **P2:** Later enhancement.

## FR-01 --- Authentication and Identity (P0)

-   Registration.
-   Login and logout.
-   Password hashing.
-   Password reset workflow, subject to email-provider decision.
-   Profile view/update.
-   Role assignment by authorized admin.
-   Authentication state and session/token lifecycle.
-   Account status handling (active/disabled).
-   Server-side authorization.

Security requirements:

-   Never store plaintext passwords.
-   Do not put long-lived secrets in frontend code.
-   Use secure cookie attributes if cookie-based authentication is
    selected.
-   If bearer tokens are selected, define expiry, refresh, revocation
    and storage strategy before implementation.
-   Rate-limit sensitive authentication endpoints.
-   Avoid revealing whether an email exists in password-reset responses.

## FR-02 --- Learner Dashboard (P0 basic, P1 expanded)

Basic MVP dashboard:

-   Recently opened reading content.
-   Recent attempts and scores.
-   Vocabulary count.
-   Assigned exercises, if assignment module is enabled.
-   Quick links to Reading Library and continue reading.

Later:

-   Weekly/monthly activity.
-   Skill-level progress.
-   Weak question types.
-   Review recommendations.
-   Upcoming class schedule.

Do not display fabricated progress or estimated IELTS bands without a
defined calculation method.

## FR-03 --- Reading Library (P0)

Learners can:

-   Browse published reading passages.
-   Search by title or supported metadata.
-   Filter by topic and difficulty.
-   View estimated reading time and word count.
-   See content visibility and availability.
-   Continue an unfinished reading activity where supported.

Content metadata should include:

-   Title.
-   Slug.
-   Summary/description.
-   Cover image (optional).
-   Topic/category.
-   Difficulty label.
-   CEFR level (optional and carefully defined).
-   IELTS relevance/type (if applicable).
-   Estimated reading time.
-   Word count.
-   Publication status.
-   Visibility (public or restricted).
-   Author/creator.
-   Created/updated timestamps.

Difficulty and CEFR are not automatically equivalent to IELTS band
score. Do not label them as interchangeable.

## FR-04 --- Reading Content Model (P0)

A reading passage should support:

-   Title and metadata.
-   Ordered paragraphs.
-   English text.
-   Optional Vietnamese translation per paragraph.
-   Optional inline vocabulary annotations.
-   Optional audio resource.
-   Related exercise groups.
-   Publication status and visibility.
-   Content versioning or a safe update strategy.

Content must be stored in a structured format that supports
paragraph-level rendering, translation alignment and evidence
references.

Avoid storing the entire lesson as one unstructured HTML blob if it
prevents reliable paragraph mapping, text selection or question evidence
linking.

## FR-05 --- Reading Player (P0)

The Reading Player should support:

-   Clear, readable article layout.
-   Paragraph structure.
-   Optional show/hide translation.
-   Text-size adjustment.
-   Word selection/click lookup.
-   Vocabulary save action.
-   Notes or bookmarks if included in the approved MVP scope.
-   Reading progress.
-   Continue reading.
-   Responsive behavior.

Vocabulary lookup should be graceful when an external dictionary
provider is unavailable. The user must still be able to save a word
manually.

Do not promise pronunciation audio, IPA or dictionary definitions unless
the data provider or content model actually supplies them.

## FR-06 --- IELTS Reading Question Engine (P0)

Potential IELTS question types:

-   Multiple Choice.
-   True / False / Not Given.
-   Yes / No / Not Given.
-   Matching Headings.
-   Matching Information.
-   Matching Features.
-   Matching Sentence Endings.
-   Sentence Completion.
-   Summary Completion.
-   Note Completion.
-   Table Completion.
-   Diagram Label Completion.
-   Short Answer Questions.

**Important:** The list is the desired supported family, not a
requirement to implement all types in the first coding sprint. The owner
must approve the exact MVP subset. Implement question types
incrementally and test grading rules for each.

Question engine design must support:

-   Question type.
-   Prompt/instructions.
-   Ordered question items.
-   Options or matching pools when relevant.
-   Correct answer representation.
-   Accepted answer variants where appropriate.
-   Case/whitespace rules.
-   Word-limit rules where appropriate.
-   Explanation.
-   Evidence paragraph/range.
-   Points/marking rule.
-   Order and grouping.

Do not use a single simplistic answer field for every question type.
Define validated type-specific answer structures.

## FR-07 --- Learning Mode and Exam Mode (P0)

### Learning Mode

-   Translation may be available.
-   Vocabulary lookup may be available.
-   No strict timer by default.
-   Explanations may be shown after an answer or after submission,
    according to exercise settings.
-   Learner can focus on understanding.

### Exam Mode

-   Timer.
-   No translation.
-   No dictionary lookup.
-   No answer/explanation disclosure before submission.
-   Answer navigation.
-   Mark-for-review.
-   Autosave answers.
-   Submit confirmation.
-   Result and review after submission.
-   Attempt recovery after refresh or temporary network loss.

The server must enforce mode restrictions. Hiding a button in the
frontend is not sufficient.

## FR-08 --- Attempts and Grading (P0)

Each attempt must record:

-   Learner.
-   Exercise/version.
-   Mode.
-   Started time.
-   Submitted time.
-   Status.
-   Answers.
-   Score.
-   Maximum score.
-   Grading result.
-   Relevant timing data.
-   Version/snapshot reference for the questions used.

Important rules:

1.  A new attempt must not overwrite an old attempt.
2.  Submitted attempts must be immutable from the learner's perspective.
3.  Draft/in-progress attempts may be updated according to policy.
4.  Grading must be deterministic for the same question version and
    answer.
5.  Accepted answer variants must be explicit and testable.
6.  Do not expose answer keys through public APIs before submission.
7.  Autosave must be idempotent or otherwise protected against duplicate
    writes.
8.  The system must handle duplicate submission requests safely.
9.  Question edits after an attempt starts must not silently change that
    attempt's grading basis.

## FR-09 --- Answer Explanation and Evidence (P0)

For each supported question, content authors should be able to provide:

-   Correct answer.
-   Explanation.
-   Relevant passage/paragraph.
-   Evidence text or reference.
-   Keywords.
-   Paraphrasing notes.
-   Common distractor/mistake notes.

After submission, learners should be able to see:

-   Their answer.
-   Correct answer.
-   Correct/incorrect status.
-   Explanation.
-   Evidence location.
-   Relevant vocabulary where supplied.

Explanations should be written/reviewed by teachers. AI may draft them,
but must not publish unreviewed explanations.

## FR-10 --- Vocabulary Notebook (P0)

Learners can:

-   Save a word from a reading passage.
-   Add a word manually.
-   Store meaning and pronunciation when available.
-   Store part of speech and example when available.
-   Keep source lesson/context.
-   Search and filter saved vocabulary.
-   Update personal notes.
-   Remove a word from their own notebook.

Vocabulary is user-specific. A saved word from one learner must not
become visible in another learner's notebook unless a separate shared
vocabulary feature is explicitly designed.

Suggested vocabulary fields:

-   Term.
-   Normalized term.
-   IPA (optional).
-   Part of speech (optional).
-   Meaning/definition.
-   Vietnamese meaning (optional).
-   Example sentence (optional).
-   Source passage and context (optional).
-   Created/saved date.
-   Review status.

## FR-11 --- Flashcards and Review (P1)

-   Generate flashcards from saved vocabulary.
-   Show term first, then reveal meaning.
-   Let learner rate recall.
-   Track review history.
-   Suggest words for review.

Spaced repetition algorithm is not frozen. Do not implement a complex
algorithm without defining its schedule, rating model and test cases.

## FR-12 --- Listening (P1)

-   Listening library.
-   Audio player.
-   Playback speed.
-   Replay/seek controls.
-   Question groups.
-   Transcript where permitted.
-   Automatic grading for supported question types.
-   Explanation and evidence.
-   Attempt history.

Audio must be stored or streamed through a suitable service. Do not
store large audio binaries directly in PostgreSQL.

## FR-13 --- Assignments (P1)

Teacher can:

-   Select a published lesson or exercise.
-   Assign to an authorized class or learner.
-   Set start/due dates.
-   View completion status.
-   Review submissions.

Learner can:

-   View assigned work.
-   Complete and submit it.
-   See status and teacher feedback where released.

## FR-14 --- Hybrid Class Operations (P1)

-   Classes and enrollments.
-   Teacher-class assignment.
-   Schedule.
-   Meeting URL.
-   Attendance.
-   Class-level assignments.
-   Basic class announcements if approved.

Initial implementation may store an external meeting URL and display it
to authorized class members. Do not build a video-conferencing service.

## FR-15 --- Progress Tracking (P0 basic, P1 expanded)

MVP metrics:

-   Reading passages opened/completed.
-   Attempts and scores.
-   Vocabulary saved.
-   Recent activity.

Later metrics:

-   Score trend by question type.
-   Practice frequency.
-   Time spent.
-   Assignment completion.
-   Listening progress.
-   Skill-level trend.

Do not infer official IELTS band scores from arbitrary practice
exercises. Any band conversion must be based on an explicitly defined
and reviewed conversion table.

## FR-16 --- Content Management (P0)

Teacher/Admin content workflow:

-   Create draft.
-   Edit draft.
-   Preview.
-   Validate required fields.
-   Add paragraphs and translations.
-   Add question groups.
-   Add questions, answers and explanations.
-   Submit for review if approval workflow is enabled.
-   Publish/unpublish.
-   Archive content where appropriate.

Only authorized users can publish. Content changes must not corrupt
historical attempts.

## FR-17 --- AI-Assisted Content (P1; manual content remains possible in MVP)

AI may assist with:

-   Drafting reading passages for a specified level/topic.
-   Suggesting questions.
-   Suggesting answer explanations.
-   Suggesting vocabulary.
-   Drafting translations.
-   Creating practice variations.

Required safeguards:

-   AI output is a draft.
-   Teacher review is mandatory before publication.
-   Correct answers must be validated.
-   Content must be checked for factual and linguistic quality.
-   Do not reproduce copyrighted passages or official test content
    without permission.
-   Store provider/model metadata only if useful and approved.
-   Do not send personal learner data to AI services without a privacy
    and data-processing decision.

------------------------------------------------------------------------

# 6. User Journeys

## 6.1 Anonymous visitor

1.  Opens the public website.
2.  Browses public Reading Library.
3.  Opens a public article.
4.  Reads content and permitted translations.
5.  Is prompted to register/sign in when attempting to save vocabulary
    or track personal progress.

The exact anonymous capabilities must be finalized in access-control
design.

## 6.2 Learner Reading journey

1.  Registers/signs in.
2.  Opens Dashboard.
3.  Selects Reading Library.
4.  Filters by topic/difficulty.
5.  Opens a passage.
6.  Reads paragraphs.
7.  Looks up and saves vocabulary.
8.  Starts a related exercise.
9.  Chooses Learning Mode or Exam Mode if the exercise permits both.
10. Answers questions; the system autosaves.
11. Submits.
12. Sees score and answer explanations.
13. Reviews mistakes.
14. Returns to Dashboard and sees updated activity.

## 6.3 Teacher content journey

1.  Signs in.
2.  Opens Content Management.
3.  Creates a Reading Passage draft.
4.  Adds metadata and ordered paragraphs.
5.  Adds translations if available.
6.  Creates question groups and questions.
7.  Adds answer keys and explanations.
8.  Previews the learner experience.
9.  Validates content.
10. Publishes or submits for approval according to policy.

## 6.4 Admin journey

1.  Signs in.
2.  Manages users and roles.
3.  Creates or manages courses/classes.
4.  Assigns teachers.
5.  Reviews content and publication status.
6.  Monitors basic usage and system health.

------------------------------------------------------------------------

# 7. Recommended System Architecture

## 7.1 Architecture style

Use a **Modular Monolith** for the initial release.

Reasons:

-   Approximately 100 initial learners.
-   Smaller development and operational burden.
-   Easier local development and deployment.
-   Easier transactional consistency for attempts and grading.
-   Modules can be separated later if evidence justifies it.

Do not use microservices, Kubernetes or distributed messaging in the
initial release without a specific requirement.

## 7.2 High-level architecture

``` text
Browser / Mobile Browser
        |
        v
Next.js Frontend
        |
        | HTTPS / REST / JSON
        v
Spring Boot Application
        |
        +-- Identity & Access
        +-- Content Management
        +-- Reading
        +-- Question Engine
        +-- Assessment / Attempts
        +-- Vocabulary
        +-- Progress
        +-- Assignments (later)
        +-- Scheduling (later)
        |
        +--------------------+
        |                    |
        v                    v
   PostgreSQL          Object Storage
                       (images/audio/files)
        |
        v
   Backup / Recovery

External integrations (only when approved):
- Zoom / Google Meet
- Email provider
- Dictionary API
- TTS provider
- AI provider
```

## 7.3 Backend module boundaries

Suggested package-by-feature structure:

``` text
com.example.ieltsplatform
├── IeltsPlatformApplication.java
├── common
│   ├── config
│   ├── exception
│   ├── response
│   ├── validation
│   └── security
├── identity
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
├── content
├── reading
├── question
├── assessment
├── vocabulary
├── progress
├── assignment
└── scheduling
```

Each feature module should own its business rules and persistence
access. Avoid arbitrary cross-module repository access. Use explicit
service interfaces or application-level methods where modules need to
collaborate.

Within a module, use clear layers:

``` text
Controller -> Application/Service -> Repository -> Database
                  |
                  +-> Domain/business rules
                  +-> DTO mapping
```

Do not create an overengineered domain framework. Keep boundaries
understandable and testable.

## 7.4 Frontend module boundaries

Suggested structure:

``` text
frontend/
├── src/
│   ├── app/
│   ├── components/
│   │   ├── ui/
│   │   └── layout/
│   ├── features/
│   │   ├── auth/
│   │   ├── dashboard/
│   │   ├── reading/
│   │   ├── assessment/
│   │   ├── vocabulary/
│   │   ├── teacher/
│   │   └── admin/
│   ├── lib/
│   ├── hooks/
│   └── types/
└── public/
```

-   Keep API calls in a consistent data-access layer.
-   Keep reusable UI components separate from feature-specific
    components.
-   Use typed API contracts.
-   Do not duplicate business rules in frontend as the only enforcement.
-   Handle loading, empty, error and unauthorized states.
-   Keep exam-mode restrictions synchronized with backend enforcement.

------------------------------------------------------------------------

# 8. Database Design --- Conceptual Model

The following is a conceptual entity inventory, not a final approved SQL
schema. Confirm cardinalities, constraints, indexes, deletion policies
and versioning before creating migrations.

## 8.1 Identity

-   `users`
-   `roles`
-   `user_roles`
-   `user_profiles`

Potential user fields:

-   id (UUID recommended).
-   email (unique, normalized).
-   password_hash.
-   display_name.
-   status.
-   created_at.
-   updated_at.

Do not store plaintext passwords.

## 8.2 Learning structure

-   `programs`
-   `courses`
-   `lessons`
-   `topics`
-   `classes`
-   `enrollments`
-   `class_teachers`

Although IELTS is the only initial program, keeping a `program` concept
may help future expansion. Avoid adding unnecessary complexity if it
does not support a real current requirement.

## 8.3 Reading content

-   `reading_passages`
-   `reading_paragraphs`
-   `paragraph_translations`
-   `content_versions` (if versioning is implemented)
-   `media_assets`
-   `topics`

Use ordered paragraph records. Store language and translation
relationships explicitly.

## 8.4 Question engine

-   `exercises`
-   `question_groups`
-   `questions`
-   `question_options` (where relevant)
-   `question_explanations`
-   `question_evidence`

Question types may require type-specific validated payloads. A JSONB
payload can be considered for variable question structures, but only
with schema validation and clear versioning. Do not use unvalidated
arbitrary JSON as a substitute for a data model.

## 8.5 Assessment

-   `attempts`
-   `attempt_answers`
-   `attempt_events` (optional, if required for recovery/audit)
-   `grading_results`

Attempt records should refer to a stable exercise/question version or
snapshot so content edits do not silently alter historical grading.

## 8.6 Vocabulary

-   `vocabulary_entries` (shared dictionary/content records if needed)
-   `user_vocabulary`
-   `vocabulary_sources`
-   `flashcard_decks`
-   `flashcards`
-   `vocabulary_reviews`

Separate shared vocabulary definitions from learner-specific saved
vocabulary when that distinction is useful.

## 8.7 Operations

-   `assignments`
-   `assignment_targets`
-   `submissions`
-   `teacher_feedback`
-   `schedules`
-   `attendance`

These are primarily later-release modules unless the owner explicitly
includes them in MVP.

## 8.8 Data integrity principles

-   Use primary keys and foreign keys.
-   Add unique constraints for identities and natural uniqueness
    requirements.
-   Index common lookup and filtering paths.
-   Use timestamps consistently; define timezone handling.
-   Use transactions for submission and grading workflows.
-   Define deletion/archival behavior for content and users.
-   Avoid hard deletion when it would break historical attempts or
    auditability.
-   Consider optimistic locking for editable content where concurrent
    editing matters.
-   Use database constraints in addition to application validation.

------------------------------------------------------------------------

# 9. Assessment and Grading Design

## 9.1 Core concepts

-   **Exercise:** a learning or exam activity.
-   **Question Group:** a group of related questions, potentially linked
    to a passage.
-   **Question:** an individual question with a type and marking rule.
-   **Attempt:** one learner's run through an exercise.
-   **Attempt Answer:** the learner's submitted/current answer for a
    question.
-   **Grading Result:** the deterministic result of applying the
    question's marking rule.

## 9.2 Grading requirements

For each question type, define:

-   Input format.
-   Correct answer format.
-   Accepted alternatives.
-   Normalization rules.
-   Case sensitivity.
-   Whitespace/punctuation rules.
-   Word limits.
-   Partial credit, if any.
-   Maximum points.
-   Explanation/evidence mapping.

Examples:

-   Multiple Choice: compare selected option IDs against correct option
    IDs.
-   Completion: normalize text according to an explicitly defined rule
    and compare against accepted alternatives.
-   Matching: validate mapping keys and values and score each pair.
-   True/False/Not Given: use explicit enumerated values, not arbitrary
    strings.

Do not assume all IELTS questions can be graded using a single string
comparison.

## 9.3 Attempt lifecycle

Suggested states:

-   `IN_PROGRESS`
-   `SUBMITTED`
-   `GRADED`
-   `CANCELLED` (only if cancellation is a supported business rule)

A submission operation should be safe against duplicate requests. The
server must reject or safely handle attempts to modify a submitted
attempt.

------------------------------------------------------------------------

# 10. Reading Experience and UX Requirements

## 10.1 Reading Library

Provide:

-   Search.
-   Topic filters.
-   Difficulty filters.
-   Reading cards.
-   Estimated reading time.
-   Word count.
-   Completion state for signed-in users.
-   Clear distinction between public and restricted content.

## 10.2 Reading Workspace

Suggested layout:

-   Main column: English passage and paragraph structure.
-   Optional side panel: vocabulary and notes.
-   Translation display: inline, expandable or side-by-side depending on
    responsive layout.
-   Optional audio controls when audio exists.
-   Clear reading progress.

The UI must prioritize readability and concentration over decorative
animation.

## 10.3 Learning Mode versus Exam Mode

  Capability          Learning Mode                    Exam Mode
  ------------------- -------------------------------- ------------------
  Translation         Optional                         Hidden
  Dictionary lookup   Allowed if configured            Disabled
  Timer               Optional                         Enabled
  Explanation         According to exercise settings   After submission
  Answer review       Learning flow                    After submission
  Autosave            Yes                              Yes
  Mark for review     Optional                         Recommended

The backend must know the selected mode and enforce the permitted
behavior.

------------------------------------------------------------------------

# 11. Content Quality, AI and Copyright

## 11.1 Content pipeline

``` text
Teacher chooses topic and target level
            |
            v
AI may draft content (optional)
            |
            v
Teacher edits and verifies content
            |
            v
Teacher creates/validates questions
            |
            v
Answer and explanation review
            |
            v
Content approval / publication
            |
            v
Learner access
```

## 11.2 Quality checks

Before publication, check:

-   Grammar and naturalness.
-   Difficulty suitability.
-   Factual accuracy.
-   Translation accuracy.
-   Question clarity.
-   Correct answer correctness.
-   Explanation consistency.
-   Evidence paragraph accuracy.
-   Copyright/license status.
-   Accessibility of images/audio.

## 11.3 Copyright

Read to Lead and YouPass are reference products for user experience and
feature inspiration.

Do not:

-   Copy their website source code.
-   Scrape and republish their articles, questions, answer explanations
    or audio.
-   Reproduce official IELTS/Cambridge material without permission.
-   Remove attribution or license notices.

Use original teacher-authored content, properly licensed open content or
AI-assisted original drafts reviewed by teachers.

------------------------------------------------------------------------

# 12. Security, Privacy and Reliability

## 12.1 Security baseline

-   Password hashing using a suitable adaptive password hash such as
    BCrypt or Argon2.
-   Spring Security authorization.
-   Input validation.
-   Parameterized database access through ORM/repositories.
-   CORS restricted to approved origins.
-   CSRF protection appropriate to the selected authentication model.
-   Secure cookies if cookies are used.
-   Rate limiting for sensitive endpoints.
-   Safe file upload validation.
-   File size/type restrictions.
-   Avoid exposing stack traces and secrets.
-   Audit important admin actions.
-   Dependency and vulnerability review.

## 12.2 Privacy

-   Collect only necessary learner information.
-   Define why each personal-data field is collected.
-   Restrict access to learner records.
-   Define retention and deletion behavior.
-   Do not send personal data to AI providers without explicit review.
-   Do not put learner data or credentials in logs.
-   Provide a privacy notice appropriate to the deployment jurisdiction.

## 12.3 Reliability

-   Database backups.
-   Restore procedure testing.
-   Flyway migration discipline.
-   Consistent error handling.
-   Structured logs.
-   Health checks.
-   Autosave for attempts.
-   Idempotent submission handling.
-   Monitoring of application and database health.

------------------------------------------------------------------------

# 13. API Design Principles

The final API contract must be created before implementation of each
module.

General conventions:

-   REST resource-oriented endpoints.
-   JSON request/response bodies.
-   Consistent naming.
-   DTOs for requests and responses.
-   Validation errors with field-level information.
-   Pagination for lists.
-   Consistent status codes.
-   Authentication and authorization documented per endpoint.
-   Versioning strategy decided before public API stability is required.

Illustrative endpoint groups (not a frozen API contract):

``` text
/api/v1/auth/*
/api/v1/users/me
/api/v1/reading
/api/v1/reading/{id}
/api/v1/reading/{id}/progress
/api/v1/vocabulary
/api/v1/exercises
/api/v1/exercises/{id}
/api/v1/attempts
/api/v1/attempts/{id}
/api/v1/attempts/{id}/answers
/api/v1/attempts/{id}/submit
/api/v1/attempts/{id}/review
/api/v1/teacher/content/*
/api/v1/admin/*
```

Do not implement these exact paths blindly. Confirm naming, ownership,
request schemas and response schemas in the API contract.

------------------------------------------------------------------------

# 14. Testing Strategy

## 14.1 Backend unit tests

Test:

-   Authentication and authorization rules.
-   Reading publication rules.
-   Vocabulary ownership.
-   Question validation.
-   Grading rules by question type.
-   Attempt state transitions.
-   Duplicate submissions.
-   Content version behavior.
-   Progress calculation.

## 14.2 Backend integration tests

Test:

-   Repository/database behavior.
-   Flyway migration from a clean database.
-   API authentication.
-   API authorization.
-   Reading and question retrieval.
-   Attempt persistence and grading.
-   Transaction rollback on failure.

Use a real PostgreSQL test environment or a compatible test setup for
database-specific behavior. Do not assume an in-memory database proves
PostgreSQL compatibility.

## 14.3 Frontend tests

Test:

-   Navigation.
-   Reading rendering.
-   Translation toggle.
-   Vocabulary save flow.
-   Question interaction.
-   Exam timer and submission flow.
-   Error/empty/loading states.
-   Responsive layouts.

## 14.4 End-to-end critical path

At minimum, automate this flow:

1.  Admin/Teacher creates a passage.
2.  Teacher adds paragraphs and a question set.
3.  Authorized user publishes content.
4.  Learner opens the public/published passage.
5.  Learner signs in if required for personalization.
6.  Learner saves vocabulary.
7.  Learner starts an attempt.
8.  Learner answers and submits.
9.  System grades the attempt.
10. Learner reviews explanations.
11. Learner sees updated history/progress.
12. Another learner cannot access the first learner's private data.

------------------------------------------------------------------------

# 15. Performance and Scale

Initial target: approximately 100 learners.

Recommended principles:

-   Start with one Spring Boot application and one PostgreSQL database.
-   Use database indexes for common filters and foreign keys.
-   Paginate lists.
-   Avoid N+1 queries.
-   Avoid loading large audio/image binaries through application memory
    unnecessarily.
-   Store media in object storage.
-   Use CDN delivery if justified.
-   Keep reading content payloads reasonably sized.
-   Add caching only after measuring a bottleneck.
-   Load-test important endpoints before production release.

A target such as sub-500ms for ordinary API responses may be used as an
initial engineering goal under normal conditions, but it is not a
guarantee. Measure actual performance and define endpoint-specific
service-level objectives.

------------------------------------------------------------------------

# 16. Development Roadmap

Use milestone-based delivery. Exact calendar estimates should be created
only after repository, team capacity and scope are known.

## Milestone 0 --- Discovery and Architecture

Deliverables:

-   Approved PRD.
-   User-role and permission matrix.
-   ERD.
-   API contract.
-   Architecture diagram.
-   Authentication decision.
-   Migration strategy.
-   UI/UX wireframes.
-   MVP question-type list.

Exit criteria:

-   No unresolved blocking decision for the first implementation
    milestone.
-   Owner approves the architecture and scope.

## Milestone 1 --- Project Foundation

Backend:

-   Spring Boot project.
-   PostgreSQL connection.
-   Flyway.
-   Common API response/error model.
-   Validation.
-   Security foundation.
-   User and role model.
-   Initial tests.

Frontend:

-   Next.js project.
-   TypeScript.
-   UI foundation.
-   API client.
-   Authentication screens.
-   Shared layout.

Exit criteria:

-   Clean build.
-   Database migrations run from empty database.
-   Authentication and role checks tested.

## Milestone 2 --- Reading Content Management

-   Reading metadata.
-   Paragraph management.
-   Translation management.
-   Topic management.
-   Draft/publish workflow.
-   Teacher/admin content screens.
-   Content APIs.

Exit criteria:

-   Authorized content editor can create, preview and publish a valid
    passage.
-   Unauthorized users cannot edit or publish.

## Milestone 3 --- Reading Learner Experience

-   Reading Library.
-   Reading Player.
-   Responsive article view.
-   Translation toggle.
-   Reading progress.
-   Vocabulary lookup integration boundary.
-   Vocabulary Notebook.

Exit criteria:

-   Learner can read and save vocabulary.
-   Ownership and visibility rules are tested.

## Milestone 4 --- Question and Assessment Engine

-   Approved MVP question types.
-   Exercise and question authoring.
-   Learning Mode.
-   Exam Mode.
-   Attempt lifecycle.
-   Autosave.
-   Grading.
-   Answer explanations.
-   Attempt history.

Exit criteria:

-   Grading tests pass for every implemented question type.
-   Historical attempts remain stable after content edits.
-   Duplicate submission is handled safely.

## Milestone 5 --- Dashboard and MVP Release

-   Basic dashboard.
-   Reading history.
-   Basic progress.
-   Admin/teacher workflows.
-   Security review.
-   End-to-end testing.
-   Deployment.
-   Backup and restore check.
-   User documentation.

Exit criteria:

-   Critical end-to-end path passes.
-   Production configuration is secure.
-   Known limitations are documented.
-   Owner accepts release.

## Later milestones

-   Listening.
-   Assignments.
-   Class and schedule management.
-   Hybrid meeting links and attendance.
-   Speaking and Writing workflows.
-   AI-assisted learning.
-   Advanced analytics.

------------------------------------------------------------------------

# 17. Acceptance Criteria for MVP

  -----------------------------------------------------------------------
  ID                      Requirement             Acceptance condition
  ----------------------- ----------------------- -----------------------
  AC-01                   Authentication          User can register/sign
                                                  in using the approved
                                                  auth design

  AC-02                   Roles                   Learner, Teacher and
                                                  Admin permissions are
                                                  enforced by backend

  AC-03                   Content authoring       Authorized
                                                  teacher/admin can
                                                  create a reading
                                                  passage

  AC-04                   Publishing              Draft content is not
                                                  exposed as published
                                                  public content

  AC-05                   Reading library         Learner/visitor can
                                                  browse content allowed
                                                  by visibility rules

  AC-06                   Reading player          Paragraphs render in
                                                  correct order and are
                                                  readable on mobile

  AC-07                   Translation             Optional translation
                                                  can be shown/hidden
                                                  where available

  AC-08                   Vocabulary              Signed-in learner can
                                                  save and manage their
                                                  own vocabulary

  AC-09                   Question engine         Approved MVP question
                                                  types can be authored
                                                  and answered

  AC-10                   Exam mode               Restricted assistance
                                                  is not available during
                                                  exam mode

  AC-11                   Autosave                In-progress answers
                                                  survive refresh/network
                                                  interruption within
                                                  defined limits

  AC-12                   Grading                 Supported question
                                                  types are graded
                                                  deterministically

  AC-13                   Explanation             Learner can see answer
                                                  and explanation after
                                                  permitted review point

  AC-14                   History                 Attempts are persisted
                                                  independently

  AC-15                   Authorization           Learner cannot access
                                                  another learner's
                                                  private data

  AC-16                   Migration               Fresh database can be
                                                  created through Flyway
                                                  migrations

  AC-17                   Tests                   Critical unit,
                                                  integration and E2E
                                                  tests pass

  AC-18                   Deployment              Application can be
                                                  deployed using
                                                  documented
                                                  configuration
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 18. Initial Project Documentation Structure

Keep these documents in the repository:

``` text
docs/
├── PRD.md
├── ARCHITECTURE.md
├── ERD.md
├── API-CONTRACT.md
├── AUTHORIZATION-MATRIX.md
├── DATABASE-MIGRATIONS.md
├── QUESTION-TYPES.md
├── TEST-PLAN.md
├── DEPLOYMENT.md
├── ADR/
│   ├── ADR-001-modular-monolith.md
│   ├── ADR-002-database.md
│   ├── ADR-003-authentication.md
│   └── ADR-004-content-versioning.md
└── CHANGELOG.md
```

Use Architecture Decision Records (ADRs) for decisions that materially
affect architecture. Each ADR should include:

-   Context.
-   Decision.
-   Alternatives considered.
-   Consequences.
-   Status (Proposed / Accepted / Superseded).
-   Date and owner.

------------------------------------------------------------------------

# 19. First Task Prompt for an AI Coding Agent

Use this prompt after placing this document in the repository as
`docs/PRD.md`.

``` text
You are the senior software architect and implementation agent for the IELTS Learning Platform.

MANDATORY:
1. Read docs/PRD.md completely before doing anything.
2. Treat confirmed owner decisions in the PRD as authoritative.
3. Do not silently resolve open decisions.
4. Do not start feature implementation yet.
5. Do not create or modify database migrations yet.
6. Do not add dependencies or external services without justification.
7. Inspect the current repository and report its actual state.

TASK:
Prepare Milestone 0 — Discovery and Architecture.

DELIVER:
1. Current repository and environment assessment.
2. Proposed final technology/version matrix, including Java and Spring Boot compatibility.
3. User-role and authorization matrix.
4. Conceptual ERD with entities, relationships and ownership rules.
5. Reading question-type data model for the approved MVP subset.
6. Attempt lifecycle and grading design.
7. Authentication options with security trade-offs; request owner decision.
8. API Contract draft for the MVP.
9. Database migration strategy.
10. Backend and frontend package/module structure.
11. Security and privacy threat checklist.
12. UI/UX screen inventory and learner/teacher/admin user flows.
13. Milestone implementation plan and acceptance criteria.
14. List of all open decisions that require owner approval.

IMPORTANT:
- Clearly distinguish confirmed requirements, recommendations and open decisions.
- Do not claim the architecture is approved.
- Do not invent credentials, integrations, content or business rules.
- Do not copy content from Read to Lead or YouPass.
- Keep the solution as a modular monolith unless the owner approves a different architecture.
- Make no code or migration changes during this task.

At the end, provide:
A. Proposed files to create under docs/.
B. Decisions requiring owner approval.
C. Risks and unresolved dependencies.
D. A concise recommendation for the next implementation milestone.
```

------------------------------------------------------------------------

# 20. Reference Products and Product Inspiration

The owner identified these products as references:

## Read to Lead

https://readtoolead.com/

Reference purpose: - Reading content presentation. - Reading level/topic
organization. - Bilingual reading inspiration. - Vocabulary
presentation.

## YouPass

https://youpass.vn/

Reference purpose: - IELTS practice workflow. - Listening/Reading
practice patterns. - Question formats. - Answer review and
explanations. - Vocabulary review ideas.

These are product references, not implementation specifications. Do not
assume every proposed feature exists on the reference websites. Do not
scrape or reproduce their copyrighted content.

------------------------------------------------------------------------

# 21. Final Product Direction

The initial release is:

**A free, hybrid IELTS learning platform with Reading as its first major
learning experience.**

The first product milestone should deliver a reliable and complete
Reading learning loop:

**Published Reading → Read → Look up/save vocabulary → Practice → Submit
→ Grade → Review explanations → Track progress.**

The platform should be simple enough to operate for approximately 100
learners, but structured enough to add Listening, teacher operations,
Speaking, Writing and other English programs later.

The most important engineering priorities are:

1.  Correct data model and content versioning.
2.  Secure role/resource authorization.
3.  Reliable attempts and deterministic grading.
4.  A high-quality Reading experience.
5.  Teacher-controlled content quality.
6.  Safe migrations and automated tests.
7.  Clear documentation and owner approval at architecture gates.

**Do not optimize for the number of features. Optimize for a trustworthy
learning experience that works end to end.**
