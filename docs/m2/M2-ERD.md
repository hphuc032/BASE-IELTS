# M2 logical ERD

M2 Design v1.2 · 2026-11-27. Product Owner decisions and release authorization recorded. See the [sign-off package](M2-DESIGN-SIGNOFF.md) and [Decision Register](M2-DECISION-REGISTER.md).

**Proposed implementation schema.** v1.6 §5 remains authoritative. UUID PK `id`, timestamps UTC, columns NOT NULL unless marked optional. Every FK is RESTRICT for delete/update. Index uncovered FK leading columns. Versions, positions and revisions are positive integers. Stable content slugs match lower-case ASCII hyphenated form, unique per stable table. IDs and owners are immutable. No runtime hard deletes of published data.

~~~mermaid
erDiagram
  COURSE ||--o{ COURSE_LEVEL : contains
  COURSE_LEVEL ||--o{ READING_LESSON : contains
  READING_LESSON |o--o{ PLACEMENT : orders
  PASSAGE ||--o| PLACEMENT : linked
  PASSAGE ||--o{ PASSAGE_VERSION : editions
  PASSAGE_VERSION ||--|{ PARAGRAPH : contains
  PARAGRAPH ||--o{ TRANSLATION : translates
  PASSAGE ||--o{ EXERCISE : has
  EXERCISE ||--o{ EXERCISE_VERSION : editions
  PASSAGE_VERSION ||--o{ EXERCISE_VERSION : pinned_by
  EXERCISE_VERSION ||--|{ QUESTION_GROUP : contains
  QUESTION_GROUP ||--|{ QUESTION : contains
  QUESTION ||--o{ QUESTION_OPTION : offers
  QUESTION ||--o| MARKING : private
  QUESTION ||--o| ANSWER_EXPLANATION : private
  QUESTION ||--o{ EVIDENCE : private
~~~

| Table | Required columns and constraints |
| --- | --- |
| course | slug UNIQUE, title, optional description, positive display_order, status ACTIVE/INACTIVE/ARCHIVED, revision, timestamps. Sort by (display_order,id); ties permitted. |
| course_level | course_id→course, code unique within course, name, optional description, display_order, status, revision and timestamps. Index (course_id,status,display_order,id). |
| reading_lesson | level_id→course_level, slug unique within level, title, optional description, display_order, status, revision and timestamps. Reading-only type implicit. Index (level_id,status,display_order,id). |
| placement | passage_id PK→passage, optional lesson_id→reading_lesson, optional position, revision; (lesson_id,position,passage_id) index. Require lesson and position either both null or both present. Null pair is the retained unassignment revision. Optional placement metadata under approved P01. |
| passages | slug UNIQUE, author_id→identity.users, optional current_version_id, archived_at optional, created_at. |
| topics | slug UNIQUE, label. No topic taxonomies beyond this proposed catalog. |
| passage_versions | passage_id→passages, version_no, revision, state DRAFT/FROZEN, title, optional summary/instructions/topic_id→topics, visibility PUBLIC/PRIVATE, frozen_at nullable iff DRAFT. UNIQUE (passage_id,id), (passage_id,version_no), partial UNIQUE (passage_id) where DRAFT. |
| paragraphs | passage_version_id→passage_versions, position, optional heading, plain body. UNIQUE (passage_version_id,id) and (passage_version_id,position). Word count and estimate derive from frozen text, not client input. |
| translations | paragraph_id→paragraphs, canonical language, text; UNIQUE (paragraph_id,language). Omit from public projection until permitted. |
| exercises | slug UNIQUE, passage_id→passages, author_id→identity.users, optional current_version_id and archived_at; UNIQUE (id,passage_id). |
| exercise_versions | exercise_id→exercises, passage_id, passage_version_id, version_no, revision, state, visibility, title, optional summary, available_modes and nullable integer exam_duration_seconds as specified below, frozen_at as above. UNIQUE (exercise_id,id), (id,passage_version_id), (exercise_id,version_no); partial UNIQUE draft per exercise. Stable Exercise holds no mode or duration copy. |
| question_groups | exercise_version_id→exercise_versions, position, optional instructions. UNIQUE (exercise_version_id,id) and (exercise_version_id,position). |
| questions | exercise_version_id, group_id, position, exact D03 discriminator, prompt, optional instructions/max_words, positive points numeric(6,2). UNIQUE (exercise_version_id,id), (group_id,position). Conditional max_words only for text types. |
| options | question_id→questions, position, key, content. UNIQUE (question_id,id), (question_id,position), (question_id,key). |
| marking | question_id PK→questions; optional correct_option_id, enum_value or accepted_answers and case_sensitive according to type. Exact one marking row at freeze. Use bounded typed JSON array for variants only, structural checks and freeze validation. |
| explanations | question_id PK→questions, private plain text, required for every question at freeze. |
| evidence | exercise_version_id, question_id, passage_version_id, paragraph_id, optional locator; evidence belongs to exact pinned graph. |
| content_audit_events | event_id UUID PK, actor_id→identity.users, operation enum, entity kind, stable/edition references, request_id, UTC timestamp; append-only. Typed stable/edition FKs implemented with discriminator and exclusive nullable FK pairs for content or catalog events. Index target + timestamp + id; no answer/body payload. |

**Composite FKs.** passages(id,current_version_id)→passage_versions(passage_id,id); exercises(id,current_version_id)→exercise_versions(exercise_id,id); exercise_versions(exercise_id,passage_id)→exercises(id,passage_id); exercise_versions(passage_id,passage_version_id)→passage_versions(passage_id,id); questions(exercise_version_id,group_id)→question_groups(exercise_version_id,id); marking(question_id,correct_option_id)→options(question_id,id); evidence(exercise_version_id,question_id)→questions(exercise_version_id,id); evidence(exercise_version_id,passage_version_id)→exercise_versions(id,passage_version_id); evidence(passage_version_id,paragraph_id)→paragraphs(passage_version_id,id). Marking has its own question FK when the option is null. Pointer eligibility and cross-table type rules are enforced under locks at publication.

For public lists index current eligibility with current pointer and stable ID, and frozen publication date/ID plus optional topic. Migrations must verify plans against bounded fixture data. Mutable rows are navigation and drafts; frozen graphs, including protected marking, remain immutable; public views are synthetic DTO projections. Audit records cover publication history. No review table under approved P02. No sample membership writes or attempt tables until their gates. Future attempt FK pins exercise_versions.id and an item pins the matching (exercise_version_id,id) question. Translations remain relational and ordered through paragraphs. JSON passage blobs and rich text were considered; relational plain paragraphs provide membership FKs, deterministic ordering and simpler safe rendering.

## Reconciled persistence contracts

| Record | Keys and enforcement |
| --- | --- |
| publication_receipts | operation_id UUID global PK, actor_id→identity.users, entity_kind PASSAGE/EXERCISE, canonical intent digest (G01), committed_at and bounded sanitized result. Exactly one stable/edition FK pair matches the kind and composite membership. Bundle result lists switched stable/edition IDs and frozen revisions verified against the locked graph. Append-only; bind UUID and intent only on commit. No persistent operation ledger. |
| publication audit link | Optional operation_id UUID UNIQUE FK→publication_receipts, required for successful publication audit and null otherwise. The guarded writer inserts one receipt and linked success audit in the same transaction as freeze and pointer changes. The PK and UNIQUE FK prevent duplicates, and rollback leaves no partial graph, pointer, receipt or audit. |
| content_capability_grants | UUID PK, immutable grantee_id and grantor_id FKs→identity.users, scope GLOBAL only, capability CONTENT_EDIT/CONTENT_PUBLISH, granted_at, optional revoked_at ≥ granted_at and revoked_by FK. Require revoked_at/revoked_by both set or both absent. Partial UNIQUE (grantee_id,scope,capability) WHERE revoked_at IS NULL. Index grantee/grant activity. Provisioning events reference grant ID; append-only actor and timestamp. |
| draft lineage | Optional base_version_id plus composite (stable_id,base_version_id) FK to same family, verified FROZEN at clone and locked when checking. Initial draft has null base. Exercise pin uses its existing exact non-null composite FK, including bundle-preparation drafts. Evidence uses the exact edition/paragraph tuples already listed. |

## Exact mode checks

Use a NOT NULL enum-array with enum values LEARNING and EXAM. A structural CHECK admits only the canonical arrays {LEARNING}, {EXAM}, or {LEARNING,EXAM}, using exact equality against those three typed arrays (one dimension, ordinary lower bound 1). Reject null members, duplicates, empty, unknown or extra values; API canonicalizes order. Store exam_duration_seconds as nullable PostgreSQL integer. Its CHECK is (NOT ('EXAM' = ANY(available_modes)) AND exam_duration_seconds IS NULL) OR ('EXAM' = ANY(available_modes) AND exam_duration_seconds IS NOT NULL AND exam_duration_seconds BETWEEN 1 AND 86400). All three valid configurations may freeze. Publication also enforces frozen-or-in-bundle passage, required private explanation per question, cross-table discriminator constraints and all [field limits](M2-API-CONTRACT.md#proposed-field-validation-registry). Apply freeze guards to modes, references, lineage and immutable receipt records.

## Paragraph identity and restricted reads

Use paragraphs.id as the staff paragraphId and map plain body to staff content. Both existing IDs and positions remain scoped to the exact PassageVersion. Validate the parent root, draft, revision and each supplied ID under the stable Passage lock. Update retained rows in place, including for reorder. Allow an atomic position swap using deferrable per-edition position uniqueness or equivalent collision-safe resequencing inside the guarded transaction. The Evidence FK to (passage_version_id,id) restricts deletion even for a draft, and stays unchanged by edits to content or position. New UUIDs originate at the server.

Publication receipts record committed outcomes only. The primary projection filters by operation UUID, initiating actor, root kind and root ID under the approved [protected staff read boundary](M2-SECURITY-DESIGN.md#protected-staff-read-boundary). Use the M2 LOGIN principal content_staff_reader with schema USAGE and exact fence EXECUTE, plus approved scoped projector access. Table SELECT is restricted to the separate projector owners and the documented allowlist. No public receipt projection, GET-side write or change to frozen version relationships.
