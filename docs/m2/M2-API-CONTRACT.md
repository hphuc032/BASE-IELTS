# M2 REST contract proposal

All paths below have prefix **/api/v1**. The management namespace follows v1.6 staff authoring (`/teacher`). This namespace does not imply a TEACHER grant. Public paths follow v1.6 and remain disabled until M3. Success uses {data} or {data,page:{number,size,totalElements,totalPages}}. Error envelope {error:{code,message,details,traceId}}. Never serialize database/JPA entities.

## DTO registry

- `CatalogInput`: slug/title/optional description/positive displayOrder for courses and lessons; code/name/optional description/displayOrder for levels. Parent comes from route, immutable after creation. `CatalogAdmin` adds id,parentId where applicable,status,revision,timestamps. `CatalogPublic` only id,slug or code,title or name,description,displayOrder.
- `PassageDraftInput`: title, summary?, instructions?, topicId?, visibility, ordered paragraphs [{heading?,body,translations?:[{language,text}]}]. `PassageAdmin` adds stableId,editionId,versionNumber,revision,state,stable slug and currentVersionId. `PassagePublic`: id,slug,title,summary,topic,wordCount,estimatedReadingMinutes, ordered {heading,body} paragraphs and permitted translations. No archived queries. PassageSummary has id,slug,title,summary,topic,wordCount,estimatedReadingMinutes only.
- `ExerciseDraftInput`: title,summary?,visibility,passageVersionId,examDurationSeconds, groups [{instructions?,questions:[QuestionDraft]}]. `QuestionDraft`: type,prompt,instructions?,points, options?,maxWords?,marking,explanation?,evidence?. Group/question/option positions are one-based array order. Marking shapes below. Options on input use unique local key and content; MCQ draft marking uses correctOptionKey, resolved server-side to baseline UUID-FK marking before save. Evidence uses a paragraph UUID of the pinned edition. `ExerciseAdmin` adds stable and edition identifiers, revision, status and normalized graph. No runtime learner DTO here.
- `ExercisePublic`: id,slug,title,summary,modes,examDurationSeconds. After TECH-06 only, sampleQuestions may additionally contain id,type,position,prompt,instructions, ordered {id,key,content} options for MCQ, and maxWords for text. No points, explanation, evidence, correct option, marking or accepted answers.
- `PublishInput`: draft edition UUID in route, If-Match draft revision; optional bundle entry in passage publication contains exercise stable ID, draft UUID and expected revision. `PublicationResult`: stable ID,edition ID, frozen timestamp and revision, and switched bundle members when present.
- `StaffPreview` uses safe rendering projection of a draft regardless of pointer status, with explicit draft badge and no cache. Answer editor accesses private marking only in scoped admin detail.

## Admin endpoints

Every row requires ACTIVE session plus listed capability. Unsafe calls require AUTH CSRF, strict JSON where a body is specified and explicit write transaction. Scope-conflicting IDs return concealed 404. A write is emitted only after commit.

| Method and path | Capability | Request → response | Transaction and replay |
| --- | --- | --- | --- |
| GET /teacher/courses; GET /teacher/courses/{courseId}/levels; GET /teacher/levels/{levelId}/lessons | CATALOG_VIEW | filters/page → CatalogAdmin list | Read snapshot |
| POST each preceding collection path | CATALOG_EDIT | CatalogInput → 201 CatalogAdmin, Location | Unique keys; duplicates 409, reconcile via list before retry |
| GET /teacher/{courses\|levels\|lessons}/{id} | CATALOG_VIEW | no body → CatalogAdmin, ETag | Snapshot |
| PATCH /teacher/{courses\|levels\|lessons}/{id} | CATALOG_EDIT | CatalogInput + If-Match → CatalogAdmin | Parent unchanged, revision CAS |
| POST /teacher/{courses\|levels\|lessons}/{id}/status | CATALOG_EDIT | {status} + If-Match → CatalogAdmin | Valid transition only; no direct status fields in PATCH |
| PUT /teacher/reading/{id}/placement | CATALOG_EDIT | {lessonId,position}, or {lessonId:null} to unassign + If-Match placement revision (0 when absent) → {lessonId,position?,revision} | Catalog locks, one assignment, revision CAS. Unassignment preserves a tombstone revision for replay/recreation fencing. |
| GET/POST /teacher/reading; GET/POST /teacher/exercises | CONTENT_VIEW on GET, CONTENT_EDIT on POST | GET filters/page → staff summary list. POST {slug,title} plus passageId for exercise → 201 stable identity and initial draft | Initial draft and stable creation atomic; duplicate slug 409. Incomplete draft is allowed. |
| POST /teacher/{reading\|exercises}/{id}/drafts | CONTENT_EDIT | {baseVersionId} referencing owned frozen edition → 201 draft summary | One open draft per aggregate. 409 existing draft. Copy and resolve graph references in transaction. |
| GET /teacher/{reading\|exercises}/{id}/drafts/{draftId}; GET same path with /preview | CONTENT_VIEW | no body → corresponding Admin DTO or StaffPreview, ETag | Graph obtained in one snapshot. |
| PUT /teacher/{reading\|exercises}/{id}/drafts/{draftId} | CONTENT_EDIT | corresponding DraftInput + If-Match → Admin DTO | Replace draft graph atomically, validate limits and allocate scoped IDs; stale retries 412. |
| POST /teacher/{reading\|exercises}/{id}/drafts/{draftId}/publish | CONTENT_PUBLISH | PublishInput + If-Match → 200 PublicationResult | Validate/freeze/switch/audit atomically; frozen replay 409, reconcile from staff publication list. |
| POST /teacher/{reading\|exercises}/{id}/withdraw; POST same path with /archive instead | CONTENT_PUBLISH | {expectedCurrentVersionId} plus explicit null when no current → 204 | Compare pointer under stable lock; mismatch 409; archive also disables future publish. |
| GET /teacher/{reading\|exercises}/{id}/publications | CONTENT_VIEW | page → {editionId,frozenAt,isCurrent} list | Scoped history only. No audit payload or credential fields. |

No review endpoint in the recommended workflow. Internal routines follow [security](M2-SECURITY-DESIGN.md).

## Public endpoints behind M3 gate

| Method and path | Input → output | Snapshot and exclusions |
| --- | --- | --- |
| GET /public/courses; GET /public/courses/{courseId}/levels; GET /public/levels/{levelId}/lessons | page/search → CatalogPublic list | P01 only; all ancestor ACTIVE checks in same snapshot |
| GET /public/lessons/{id} | page/size → {lesson: CatalogPublic, passages: PassageSummary[]} with top-level page metadata | Inactive ancestor/draft/private yields concealment |
| GET /reading; GET /reading/slug/{slug}; GET /reading/{id} | list page, q/topic filters; detail identifiers → PassagePublic | Same projector for IDs and slugs; list excludes full body |
| GET /exercises; GET /reading/{id}/exercises; GET /exercises/{id}; GET /exercises/slug/{slug} | page/q or ID/slug → ExercisePublic list/detail | Match exact current passage edition and exercise pointer; sample omitted until gate |

GETs are anonymous, idempotent, no session allocation or idle touch; eligible public reads use one read-only snapshot including total count. No public edition, arbitrary projection, or review endpoints. Return no-store throughout.

## Validation and resource bounds

Page 0-based, size default 20 max 50; reject invalid/unknown sort/filter keys. Allow `q` (1–100 chars, literal case-insensitive title/slug match), `topicId` for reading and `status` only for staff. Status filters use exact enums. Bind all values. Catalog orders by position then UUID, public content by frozen time DESC then UUID. Cap request and response at 768 KiB UTF-8 proposed; list summaries exclude full graphs. Full graph detail exceeding cap fails validation on draft save/freeze; never truncate safe detail silently. Error order follows v1.6 §6.1: media/size 415/413, auth 401, CSRF/capability 403, syntax 400, scope 404, state 409, missing If-Match 428, stale 412, overload 429, transient transaction failure 503. Return ETag with strong quoted revision on editor reads/writes.

| D03 type | Draft marking and freeze requirements |
| --- | --- |
| MULTIPLE_CHOICE_SINGLE | 2–6 distinct ordered options with unique keys; exactly one member correctOptionId after resolution; no maxWords or other marking fields |
| TRUE_FALSE_NOT_GIVEN | value exactly TRUE/FALSE/NOT_GIVEN; no options or text rules |
| YES_NO_NOT_GIVEN | value exactly YES/NO/NOT_GIVEN; no options or text rules |
| SHORT_ANSWER | positive integer maxWords; acceptedAnswers nonempty, ≤12 proposed with caseSensitive boolean; no options |
| SENTENCE_COMPLETION | same text rules; exactly one authored blank marker, no options |

Reject unknown/extra fields, contradictory discriminator fields and blank variants. Draft save may keep marking incomplete for editing but freeze requires complete marking, group membership and ordering, plus protected explanation when required by P03. Use bounded canonical duplicate detection for authoring only pending TECH-09. Proposed graph bounds per baseline: 20 groups, 100 questions, 100 total points, 512 KiB canonical graph, ≤2,000 Unicode scalars per answer variant. No scoring or learner answer API in M2.
