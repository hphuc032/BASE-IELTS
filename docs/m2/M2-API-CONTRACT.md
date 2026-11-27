# M2 REST contract proposal

M2 Design v1.2 · 2026-11-27. Product Owner decisions and release authorization recorded. See the [sign-off package](M2-DESIGN-SIGNOFF.md) and [Decision Register](M2-DECISION-REGISTER.md).

All paths below have prefix **/api/v1**. The management namespace follows v1.6 staff authoring (`/teacher`). This namespace does not imply a TEACHER grant. Public paths follow v1.6 and remain disabled until M3. Success uses {data} or {data,page:{number,size,totalElements,totalPages}}. Error envelope {error:{code,message,details,traceId}}. Never serialize database/JPA entities.

## DTO registry

- `CatalogInput`: slug/title/optional description/positive displayOrder for courses and lessons; code/name/optional description/displayOrder for levels. Parent comes from route, immutable after creation. `CatalogAdmin` adds id,parentId where applicable,status,revision,timestamps. `CatalogPublic` only id,slug or code,title or name,description,displayOrder.
- `PassageDraftInput`: title, summary?, instructions?, topicId?, visibility, ordered paragraphs [{paragraphId?, heading?, content, translations?:[{language,text}]}]. Existing paragraphs require their own UUID; omit paragraphId for a new paragraph. `PassageAdmin`: stableId,editionId,versionNumber,revision,state,stable slug,currentVersionId and the authoring fields, with paragraphs [{paragraphId: UUID, content: string, position: integer, heading?, translations?}]. Positions are one-based array order. `PassagePublic`: id,slug,title,summary,topic,wordCount,estimatedReadingMinutes, ordered {heading,body} paragraphs and permitted translations. No archived queries. PassageSummary has id,slug,title,summary,topic,wordCount,estimatedReadingMinutes only.
- `ExerciseDraftInput`: title,summary?,visibility,passageVersionId,availableModes,examDurationSeconds?, groups [{instructions?,questions:[QuestionDraft]}]. `QuestionDraft`: type,prompt,instructions?,points, options?,maxWords?,marking,explanation?,evidence?. Group/question/option positions are one-based array order. Marking shapes below. Options on input use unique local key and content; MCQ draft marking uses correctOptionKey, resolved server-side to baseline UUID-FK marking before save. Evidence uses a paragraph UUID of the pinned edition. `ExerciseAdmin` adds stable and edition identifiers, revision, status and normalized graph. No runtime learner DTO here.
- `ExercisePublic`: id,slug,title,summary,modes,examDurationSeconds. After TECH-06 only, sampleQuestions may additionally contain id,type,position,prompt,instructions, ordered {id,key,content} options for MCQ, and maxWords for text. No points, explanation, evidence, correct option, marking or accepted answers.
- `PublishInput`: draft edition UUID in route, strong If-Match draft revision and required Publication-Operation-Id UUID; optional bundle entry in passage publication contains exercise stable ID, draft UUID and expected revision. `PublicationResult`: operation ID, stable ID, edition ID, frozen timestamp and revision, and switched bundle members when present.
- `StaffPreview` uses safe rendering projection of a draft regardless of pointer status, with explicit draft badge and no cache. Answer editor accesses private marking only in scoped admin detail.

## Admin endpoints

Every row requires an AUTH session for ACTIVE ADMIN plus the listed capability. Unsafe calls require AUTH CSRF, strict JSON where a body is specified and an explicit write transaction. A publisher without CONTENT_EDIT reads through derived CONTENT_VIEW. Scope-conflicting IDs return concealed 404. A write response is emitted only after commit. All protected GETs, including catalog, Passage/Exercise, draft/preview, publication history and reconciliation, use the approved [F04 staff read boundary](M2-SECURITY-DESIGN.md#protected-staff-read-boundary). Derive actor from AUTH, check ACTIVE state/revision with the fence and validate capability and scope before reading. Hold the fence on the same primary connection and outer transaction through full DTO materialization. No business writes or deferred database reads.

| Method and path | Capability | Request → response | Transaction and replay |
| --- | --- | --- | --- |
| GET /teacher/courses; GET /teacher/courses/{courseId}/levels; GET /teacher/levels/{levelId}/lessons | CATALOG_VIEW | filters/page → CatalogAdmin list | Read snapshot |
| POST each preceding collection path | CATALOG_EDIT | CatalogInput → 201 CatalogAdmin, Location | Unique keys; duplicates 409, reconcile via list before retry |
| GET /teacher/{courses\|levels\|lessons}/{id} | CATALOG_VIEW | no body → CatalogAdmin, ETag | Snapshot |
| PATCH /teacher/{courses\|levels\|lessons}/{id} | CATALOG_EDIT | CatalogInput + If-Match → CatalogAdmin | Parent unchanged, revision CAS |
| POST /teacher/{courses\|levels\|lessons}/{id}/status | CATALOG_EDIT | {status} + If-Match → CatalogAdmin | Valid transition only; no direct status fields in PATCH |
| PUT /teacher/reading/{id}/placement | CATALOG_EDIT | {lessonId,position}, or {lessonId:null} to unassign + If-Match placement revision (0 when absent) → {lessonId,position?,revision} | Catalog locks, one assignment, revision CAS. Unassignment preserves a tombstone revision for replay/recreation fencing. |
| GET/POST /teacher/reading; GET/POST /teacher/exercises | CONTENT_VIEW on GET, CONTENT_EDIT on POST | GET filters/page → staff summary list. POST {slug,title} plus passageId, passageVersionId, availableModes and conditional examDurationSeconds for exercise → 201 stable identity and initial draft | Initial draft and stable creation atomic; duplicate slug 409. Incomplete draft is allowed. |
| POST /teacher/{reading\|exercises}/{id}/drafts | CONTENT_EDIT | {baseVersionId} referencing owned frozen edition → 201 draft summary | One open draft per aggregate. 409 existing draft. Copy and resolve graph references in transaction. |
| GET /teacher/{reading\|exercises}/{id}/drafts/{draftId}; GET same path with /preview | CONTENT_VIEW | no body → corresponding Admin DTO or StaffPreview, ETag | Graph obtained in one snapshot. |
| GET /teacher/reading/{id}/editions/{editionId} | CONTENT_VIEW | Exact scoped draft or frozen edition → PassageAdmin, ETag | Fence and verify root/edition membership. Reuse the staff Passage projection to select Evidence paragraphs. No public equivalent. |
| PUT /teacher/{reading\|exercises}/{id}/drafts/{draftId} | CONTENT_EDIT | corresponding DraftInput + If-Match → Admin DTO | Atomic graph edit. For Passage, patch retained paragraphs in place and allocate new UUIDs only for ID omissions. Stale retries 412. |
| POST /teacher/{reading\|exercises}/{id}/drafts/{draftId}/publish | CONTENT_PUBLISH | PublishInput + If-Match → 200 PublicationResult | Atomic freeze/switch/audit/receipt. Matching committed replay returns the stored result, even after later pointer changes. A different intent returns 409 OPERATION_CONFLICT only when the UUID is bound by a committed receipt visible to this actor/root. |
| POST /teacher/{reading\|exercises}/{id}/withdraw; POST same path with /archive instead | CONTENT_PUBLISH | {expectedCurrentVersionId} plus explicit null when no current → 204 | Compare pointer under stable lock; mismatch 409; archive also disables future publish. |
| GET /teacher/{reading\|exercises}/{id}/publications | CONTENT_VIEW | page → {editionId,frozenAt,isCurrent} list. Exact operationId → authorized {operationId,outcome:COMMITTED,result} or concealed 404 | Fenced history or read-only reconciliation. No audit payload or credential fields. |

No persisted review endpoint under approved P02. Internal routines follow [security](M2-SECURITY-DESIGN.md).

## Public endpoints behind M3 gate

| Method and path | Input → output | Snapshot and exclusions |
| --- | --- | --- |
| GET /public/courses; GET /public/courses/{courseId}/levels; GET /public/levels/{levelId}/lessons | page/search → CatalogPublic list | Approved catalog; all ancestor ACTIVE checks in same snapshot |
| GET /public/lessons/{id} | page/size → {lesson: CatalogPublic, passages: PassageSummary[]} with top-level page metadata | Inactive ancestor/draft/private yields concealment |
| GET /reading; GET /reading/slug/{slug}; GET /reading/{id} | list page, q/topic filters; detail identifiers → PassagePublic | Same projector for IDs and slugs; list excludes full body |
| GET /exercises; GET /reading/{id}/exercises; GET /exercises/{id}; GET /exercises/slug/{slug} | page/q or ID/slug → ExercisePublic list/detail | Match exact current passage edition and exercise pointer; sample omitted until gate |

GETs are anonymous and idempotent. They allocate no session and do not touch idle expiry. Eligible public reads use one read-only snapshot including total count. Public endpoints expose no internal publication receipts, history, audit, arbitrary edition, projection or review endpoints. Return no-store throughout.

## Validation and resource bounds

Page 0-based, size default 20 max 50; reject invalid/unknown sort/filter keys. Allow `q` (1–100 chars, literal case-insensitive title/slug match), `topicId` for reading and `status` only for staff. Status filters use exact enums. Bind all values. Catalog orders by position then UUID, public content by frozen time DESC then UUID. Cap request and response at 768 KiB UTF-8 proposed; list summaries exclude full graphs. Full graph detail exceeding cap fails validation on draft save/freeze; never truncate safe detail silently. Error order follows v1.6 §6.1: media/size 415/413, auth 401, CSRF/capability 403, syntax 400, scope 404, state 409, missing If-Match 428, stale 412, overload 429, transient transaction failure 503. Return ETag with strong quoted revision on editor reads/writes.

| D03 type | Draft marking and freeze requirements |
| --- | --- |
| MULTIPLE_CHOICE_SINGLE | 2–6 distinct ordered options with unique keys; exactly one member correctOptionId after resolution; no maxWords or other marking fields |
| TRUE_FALSE_NOT_GIVEN | value exactly TRUE/FALSE/NOT_GIVEN; no options or text rules |
| YES_NO_NOT_GIVEN | value exactly YES/NO/NOT_GIVEN; no options or text rules |
| SHORT_ANSWER | positive integer maxWords; acceptedAnswers nonempty, ≤12 proposed with caseSensitive boolean; no options |
| SENTENCE_COMPLETION | same text rules; exactly one authored blank marker, no options |

Reject unknown/extra fields, contradictory discriminator fields and blank variants. Draft save may keep marking incomplete for editing but freeze requires complete marking, group membership and ordering, plus protected explanation when required under approved P03. Use the bounded canonical duplicate detection in the field registry for authoring only. TECH-09 remains gated. Proposed graph bounds per baseline: 20 groups, 100 questions, 100 total points, 512 KiB canonical graph, ≤2,000 Unicode scalars per answer variant. No scoring or learner answer API in M2.

## Publication reconciliation

Send a canonical UUID in Publication-Operation-Id before first publish. Reject a missing or malformed UUID with 400 INVALID_PUBLICATION_OPERATION. Perform receipt lookup on primary PostgreSQL after current authorization and object scope checks. Match a committed receipt to actor, root and canonical intent before testing draft staleness. Identical replay returns 200 with the original result. A changed intent against a committed receipt visible to the same actor/root returns 409 OPERATION_CONFLICT. Conceal wrong actor or root with 404. Resolve a concurrent uniqueness conflict only after rollback and with a fresh authorized primary read.

GET /teacher/{reading or exercises}/{id}/publications?operationId={uuid} is the existing endpoint's exact lookup alternative to paged history. Do not combine operationId with page, size or filters. Apply AUTH, capability and root authorization before scoped receipt lookup on the authoritative primary. Query for operation UUID, initiating actor, root kind and root ID together. A visible committed receipt returns 200 with {data:{operationId,outcome:COMMITTED,result:PublicationResult}}. The result excludes marking, explanation, audit rows, credentials and actor details. Older committed results survive withdrawal.

An inaccessible root, absent receipt, or receipt belonging to another actor/root returns the same 404 envelope with code NOT_FOUND, constant generic message, identical empty details, and an opaque traceId with the same format and length independent of lookup result. Use the same Content-Type and Cache-Control: no-store. Omit ETag, Last-Modified, Location and other receipt-dependent headers on each concealed response. Never echo operation UUID, actor, timestamps or receipt metadata in body or headers. An authorized evaluation blocked by primary failure or lock timeout follows generic 503, with no inference of absence and no alternate cache/replica read.

Under approved F03, UNKNOWN is the client outcome after an uncertain submission without an authoritative committed result, including a concealed 404 or temporary lookup failure. Lookup does not return a separate 200 UNKNOWN or establish rollback, failure or whether the operation ever started. A busy account fence may wait within budget or return generic 503. Report rollback only with authoritative evidence from the original execution. Retry with the original operation UUID, canonical payload and expected revisions. Rollback without a surviving receipt leaves no durable prior intent, so a changed payload cannot be guaranteed detectable in that case. After reload without the original payload, offer reconciliation only. Do not generate a new UUID in response to uncertainty.

## Exercise mode validation

Exercise draft create and PUT require availableModes with LEARNING, EXAM or both. Reject empty, duplicate, unknown or null elements with 400 INVALID_AVAILABLE_MODES. Normalize to canonical order LEARNING then EXAM. If EXAM is present, duration must be an integer in 1–86400. Otherwise omit or send null, persisted as SQL NULL. Validate before numeric coercion. Violations are 400 INVALID_EXAM_DURATION on edits and 409 INVALID_EXAM_DURATION if discovered at publication. Freeze accepts all three valid configurations and rechecks mode shape (409 INVALID_AVAILABLE_MODES for invalid stored shape). Public modes derive from the exact current frozen ExerciseVersion.available_modes and duration from that same edition. An explicit draft-to-draft pin is bundle preparation only. Standalone Exercise freeze on a draft Passage returns 409 PASSAGE_EDITION_NOT_FROZEN. Evidence outside the pinned edition fails validation (400 on save, 409 on freeze). No implicit repinning. Missing protected explanation at freeze returns 409 EXPLANATION_REQUIRED.

## Passage paragraph updates

For a Passage draft PUT, validate root, draft, current revision and all supplied paragraph IDs in one transaction under the Passage root lock. Omitted paragraphId means create with a server-generated UUID. A supplied ID must identify a retained paragraph of this exact version. Reject malformed, null or duplicate IDs with 400 INVALID_PARAGRAPH_ID. Treat an absent or foreign supplied ID uniformly as concealed 404, including same-root other-edition and cross-root IDs. Deleting a referenced paragraph returns 409 PARAGRAPH_REFERENCED. Existing rows keep their IDs when content or order changes. Any invalid member rolls back the entire PUT, including translations and the revision. The response returns generated IDs and positions. Public shape remains unchanged.

## Proposed field validation registry

All limits apply after normalization. Count lengths in Unicode scalar values (PostgreSQL UTF-8 char_length, backend code points, frontend code-point iteration), never UTF-16 units. Normalize NFC, trim Unicode White_Space at edges of single-line fields and accepted answers, preserve internal whitespace. Multiline plain text canonicalizes CRLF to LF and trims outer whitespace only; preserve interior newlines and spaces. Reject NUL, unpaired surrogates and control characters except LF/tab in multiline text. Optional blank maps to null; required blank rejects. DB columns use NOT NULL where marked required and length CHECKs. DTO/service canonicalizes before writes; guarded routines enforce canonical shape/size and reject invalid cross-field data; freeze rechecks the complete graph. Frontend implements matching limits for feedback; backend remains authoritative. These are proposed measurable technical bounds, not new product approvals.

| Field | Length/range | Requiredness and additional rules |
| --- | --- | --- |
| Course title, Level name, Lesson title | 1–160 | Required single line |
| Course slug, Lesson slug, stable Passage/Exercise or Topic slug | 1–80 ASCII | Required; trim, lower-case ASCII, only alphanumerics separated by single hyphens; reject explicit duplicates in applicable scope with 409; no Unicode transliteration |
| Level code | 1–32 ASCII | Required; same normalization and format as slug; unique within course |
| Course, Level or Lesson description | 1–2,000 | Optional plain multiline |
| Passage or Exercise title, Topic label | 1–160 | Required single line; Topic reference optional and valid UUID in scope; unknown target 404 |
| Passage or Exercise summary | 1–1,000 | Optional multiline |
| Passage, group or question instructions | 1–2,000 | Optional multiline |
| Paragraph heading | 1–160 | Optional single line |
| Paragraph content, translation text | 1–8,000 | Required per row, multiline; translation language canonicalized and unique per paragraph. Staff content maps to plain body in persistence. |
| Question prompt | 1–2,000 | Required multiline; one explicit blank for SENTENCE_COMPLETION |
| Option key | 1–24 ASCII | Required MCQ; trim/lower-case, slug pattern, unique within question |
| Option content | 1–1,000 | Required MCQ plain text; normalized duplicate content rejected within question |
| Explanation | 1–4,000 | Plain multiline; draft omission allowed, exactly one valid private explanation at freeze |
| Evidence locator | 1–240 | Optional plain single line; locator does not replace required pinned-graph UUIDs |
| Accepted answers, maxWords | count 1–12; text 1–2,000 scalars each; maxWords integer 1–50 | Required text marking at freeze; variants ≤ maxWords using Unicode White_Space-delimited nonempty tokens; canonical duplicates rejected using NFC, collapsed token spacing and locale-independent lower-case if caseSensitive=false. This authoring rule does not settle TECH-09 scoring. |
| Points and duration | 0.01–100.00 points, scale ≤2; duration integer 1–86,400 seconds | Require points, total ≤100.00. Duration conditional on EXAM; null without EXAM. |

Overall JSON 768 KiB, graph 512 KiB, bounds for groups/questions/options still apply before accepting edits and at freeze. Oversized body 413, field invalid 400, invalid complete graph at publish 409. Do not store arbitrary HTML.
