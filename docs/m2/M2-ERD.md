# M2 logical ERD

**Proposed implementation schema.** v1.6 §5 remains authoritative. UUID PK `id`, timestamps UTC, columns NOT NULL unless marked optional. Every FK is RESTRICT for delete/update. Index uncovered FK leading columns. Versions, positions and revisions are positive integers. Stable content slugs match lower-case ASCII hyphenated form, unique per stable table. IDs and owners are immutable. No runtime hard deletes of published data.

~~~mermaid
erDiagram
  COURSE ||--o{ COURSE_LEVEL : contains
  COURSE_LEVEL ||--o{ READING_LESSON : contains
  READING_LESSON o|--o{ PLACEMENT : orders
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
| placement | passage_id PK→passage, optional lesson_id→reading_lesson, optional position, revision; (lesson_id,position,passage_id) index. Require lesson and position either both null or both present. Null pair is the retained unassignment revision. Metadata only under P01. |
| passages | slug UNIQUE, author_id→identity.users, optional current_version_id, archived_at optional, created_at. |
| topics | slug UNIQUE, label. No topic taxonomies beyond this proposed catalog. |
| passage_versions | passage_id→passages, version_no, revision, state DRAFT/FROZEN, title, optional summary/instructions/topic_id→topics, visibility PUBLIC/PRIVATE, frozen_at nullable iff DRAFT. UNIQUE (passage_id,id), (passage_id,version_no), partial UNIQUE (passage_id) where DRAFT. |
| paragraphs | passage_version_id→passage_versions, position, optional heading, plain body. UNIQUE (passage_version_id,id) and (passage_version_id,position). Word count and estimate derive from frozen text, not client input. |
| translations | paragraph_id→paragraphs, canonical language, text; UNIQUE (paragraph_id,language). Omit from public projection until permitted. |
| exercises | slug UNIQUE, passage_id→passages, author_id→identity.users, optional current_version_id and archived_at; UNIQUE (id,passage_id). |
| exercise_versions | exercise_id→exercises, passage_id, passage_version_id, version_no, revision, state, visibility, title, optional summary, exam_duration_seconds positive ≤86,400, frozen_at as above. UNIQUE (exercise_id,id), (id,passage_version_id), (exercise_id,version_no); partial UNIQUE draft per exercise. Learning and Exam both supported by invariant D04. |
| question_groups | exercise_version_id→exercise_versions, position, optional instructions. UNIQUE (exercise_version_id,id) and (exercise_version_id,position). |
| questions | exercise_version_id, group_id, position, exact D03 discriminator, prompt, optional instructions/max_words, positive points numeric(6,2). UNIQUE (exercise_version_id,id), (group_id,position). Conditional max_words only for text types. |
| options | question_id→questions, position, key, content. UNIQUE (question_id,id), (question_id,position), (question_id,key). |
| marking | question_id PK→questions; optional correct_option_id, enum_value or accepted_answers and case_sensitive according to type. Exact one marking row at freeze. Use bounded typed JSON array for variants only, structural checks and freeze validation. |
| explanations | question_id PK→questions, private plain text. |
| evidence | exercise_version_id, question_id, passage_version_id, paragraph_id, optional locator; evidence belongs to exact pinned graph. |
| content_audit_events | event_id UUID PK, actor_id→identity.users, operation enum, entity kind, stable/edition references, request_id, UTC timestamp; append-only. Typed stable/edition FKs implemented with discriminator and exclusive nullable FK pairs for content or catalog events. Index target + timestamp + id; no answer/body payload. |

**Composite FKs.** passages(id,current_version_id)→passage_versions(passage_id,id); exercises(id,current_version_id)→exercise_versions(exercise_id,id); exercise_versions(exercise_id,passage_id)→exercises(id,passage_id); exercise_versions(passage_id,passage_version_id)→passage_versions(passage_id,id); questions(exercise_version_id,group_id)→question_groups(exercise_version_id,id); marking(question_id,correct_option_id)→options(question_id,id); evidence(exercise_version_id,question_id)→questions(exercise_version_id,id); evidence(exercise_version_id,passage_version_id)→exercise_versions(id,passage_version_id); evidence(passage_version_id,paragraph_id)→paragraphs(passage_version_id,id). Marking has its own question FK when the option is null. Pointer eligibility and cross-table type rules are enforced under locks at publication.

For public lists index current eligibility with current pointer and stable ID, and frozen publication date/ID plus optional topic. Migrations must verify plans against bounded fixture data. Mutable rows are navigation and drafts; frozen graphs, including protected marking, remain immutable; public views are synthetic DTO projections. Audit records cover publication history. No review table in recommended P02. No sample membership writes or attempt tables until their gates. Future attempt FK pins exercise_versions.id and an item pins the matching (exercise_version_id,id) question. Translations remain relational and ordered through paragraphs. JSON passage blobs and rich text were considered; relational plain paragraphs provide membership FKs, deterministic ordering and simpler safe rendering.
