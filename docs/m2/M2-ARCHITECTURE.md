# M2 architecture blueprint

M2 Design v1.2 · 2026-11-27. Product Owner decisions and release authorization recorded. See the [sign-off package](M2-DESIGN-SIGNOFF.md) and [Decision Register](M2-DECISION-REGISTER.md).

## Repository and integration

Inspected clean M1 commit `a21337d2f447a6aaa13ce4c32f121230adaa8ea0` on main. Maven pins Java 21/Spring Boot 3.5.16 and includes JPA dependencies. The accepted identity write path actually uses JDBC, TransactionTemplate and privileged PostgreSQL routines. Compose pins PostgreSQL 17.12. Flyway V1–V12 covers identity, fencing, verification, profile, governance and management reconciliation. Next 16.4.6 rewrites /api/v1 to the private backend. React 19 and TypeScript 5.9 are pinned. PostgreSQL role separation, Shell SQL fixtures, MockMvc/JUnit and Edge Playwright are reusable. See [M1 acceptance](../MILESTONE-1-ACCEPTANCE-REPORT.md).

M1 has deny-all fallback, exact method/path admission, IdentitySecurityContextRepository and AUTH/PREAUTH scope. Adding routes requires explicit future wiring. A controller annotation alone cannot admit a route. AuthenticationService's identity authorization path currently obtains the fence and JVM stripe using its runtime connection. Content needs the same ordering on the selected writer connection and transaction. Do not fence on the identity pool then write on another. Extract the existing guard contract during implementation with regression coverage. Spring authorities use the ROLE_ prefix.

~~~mermaid
flowchart LR
  Browser --> Next["Next App Router and same-origin rewrite"]
  Next --> Security["Spring route, AUTH, CSRF and capability checks"]
  Security --> Author["Scoped author or publisher JDBC transaction"]
  Security --> Public["Public DTO projector gated for M3"]
  Author --> Fence["PostgreSQL account fence and shared JVM stripe"]
  Fence --> Routines["Narrow content routines"]
  Routines --> DB["Draft and frozen graphs; protected keys and audit"]
  Public --> Safe["Public views only, one snapshot"]
  Safe --> DB
~~~

## Protected staff reads

Apply the [staff read boundary](M2-SECURITY-DESIGN.md#protected-staff-read-boundary) to every protected GET, including catalog and content list/detail, exact Passage edition, drafts, preview, publication history and operation lookup. F04 approves the M2 content_staff_reader allowlist extension for the existing account fence. Authenticate the actor; use the fence to check ACTIVE state and expected revision, then acquire the shared JVM stripe and recheck AUTH generation and expiry. Check required capability and scope before the projection. On one bounded READ COMMITTED outer transaction on the primary, use the same checked-out staff connection for the fence and a coherent projection after it. Materialize all DTO data, including nested fields and any count, before transaction completion and stripe release. No deferred query, business DML or independent fence transaction. Provisioning is an implementation requirement under separate authorization.

## Publication and consistency

Use stable Passage and Exercise identities with pinned immutable editions per v1.6 §5. PostgreSQL guards protect every frozen descendant and pointer. Accepted publication sequence: account fence on writer connection → JVM stripe → stable passages sorted UUID → stable exercises sorted UUID → compare any existing receipt → validate graph → freeze → switch pointers → insert receipt → append linked audit → COMMIT. Recheck draft revision and eligibility under locks. For an explicitly bundled publish, freeze a passage and exercises referencing that same edition atomically. Standalone passage replacement hides stale exercise editions until republished. Withdrawal clears matching pointers and records audit, retaining history. Bounded lock/statement budgets are implementation candidates requiring measurement. Retry deadlocks only after rollback and only for safe replayable commands.

Approved P01 navigation uses one placement row per passage, with one nullable assignment to a lesson. Navigation updates run separately under catalog root → level → lesson → placement, with optimistic revisions. They never acquire passage/exercise write fences. A link to a stable passage does not pin or freeze content. Disablement hides navigation, not canonical PUBLIC Reading. The normalized public projector applies both gates when serving navigation, returning a materialized DTO in one PostgreSQL snapshot.

~~~mermaid
stateDiagram-v2
  [*] --> DRAFT
  DRAFT --> FROZEN: publish validated draft
  FROZEN --> FROZEN: immutable
  state "Current pointer (separate relationship)" as Pointer {
    [*] --> None
    None --> Current: publish commit
    Current --> Current: successor commit
    Current --> None: withdrawal
  }
~~~

**Approved workflow P02.** Draft and frozen states plus the pointer are sufficient. Publisher validation and audit provide a release checkpoint. Do not add IN_REVIEW, APPROVED or persisted reviewer notes in this release. No mandatory second reviewer. Administrative archive prevents new drafts or publish and withdraws the eligible pointer atomically. A frozen row never changes state. Revisions start at 1 and increment for each saved edit. Use a fresh successor draft from the current or last frozen edition. See [ERD](M2-ERD.md) and [security](M2-SECURITY-DESIGN.md).

## Edition reference and operation identity

An initial Passage draft is created with its new stable Passage. A successor clones an explicitly selected frozen edition of that same stable Passage; subsequent saves edit only the open draft. An Exercise stores an exact passageVersionId. Default source is an owned frozen edition under the correct stable Passage. An open Passage draft may be pinned only as explicit bundle preparation. Neither draft follows a current pointer. Reject a pin change unless the request supplies a complete replacement graph with all Evidence mapped within the new edition. Cloning creates new edition-owned child IDs and resolves internal membership explicitly. No temporary snapshots.

## Passage draft paragraph identity

The scoped staff editor reads UUIDs from the exact Passage edition. An existing paragraph PUT item names its own paragraphId; omission requests a server-generated UUID for a new row. Under the Passage root lock and checked draft revision, validate the complete input against the exact root and edition, then update retained rows in place and derive positive positions from array order. Reject duplicate and foreign IDs and deletion of any row still referenced by Evidence. All writers adding or moving Evidence to a Passage edition must hold its stable Passage lock before any Exercise lock or child change, so deletion and new references serialize. The restrictive Evidence FK is a database backstop. Position swaps must preserve uniqueness at commit without replacing retained row IDs. All changes, including translations and revision, commit or roll back together.

For standalone Exercise freeze, the pinned Passage must already be frozen. For a bundle under P06 it must be the exact Passage draft in the same command, frozen first under the same locks. Recheck stable IDs, exact drafts, revisions, all composite FKs, evidence membership, explanations and the modes/duration invariant. Failure anywhere aborts the entire bundle, including audit and receipt. After freeze no pin or descendant can move. Public eligibility separately requires the exact current matching passage edition.

## Publication outcome and replay

Each publish receives a client-generated operation UUID before the first request. The committed receipt binds UUID and canonical intent to the initiating actor and root. Intent covers route and draft IDs, expected revisions and a UUID-sorted bundle. Reject duplicate bundle members. Under the existing writer lock order compare an authorized committed receipt before draft staleness validation. An identical committed retry returns the immutable receipt. Changed intent against that receipt returns 409 OPERATION_CONFLICT. Neither receipt nor audit can commit twice. A collision across different lock sets hits the global receipt PK and rolls back the attempted effects before an authorized fresh read of the primary. A caller outside the receipt actor or root receives a concealed response.

F03 approves the receipt-only COMMITTED and UNKNOWN outcome model. The publications GET queries primary PostgreSQL and returns COMMITTED only for a durable receipt visible to the authorized actor and root. Absent and inaccessible receipts have the same concealed response. After an uncertain submission, the client interprets a concealed or temporarily unavailable lookup as UNKNOWN. UNKNOWN means a committed receipt cannot currently be established. It carries no conclusion about rollback, failure or whether an operation started. Only authoritative evidence from the original transaction can establish rollback.

Keep the original operation UUID and canonical request for every retry. If the first execution committed, return its receipt. If it rolled back, a valid identical retry may execute once. Rollback without a receipt leaves no durable original intent to compare, so the server cannot guarantee detection of a changed payload in that case. The client preserves intent and uses lookup only after reload if exact intent is unavailable. There is no persistent operation ledger or pre-commit global UUID-to-payload binding. G01 gates the canonical intent encoder and payload digest.

## Exercise edition modes

Persist modes and nullable integer duration at ExerciseVersion only. Draft create/save and freeze accept LEARNING, EXAM or both when valid. EXAM requires an integer duration from 1 to 86,400 seconds; without EXAM duration is NULL. Frozen configurations are immutable. The public projector reads both fields from the exact published edition and M2 does not run a countdown or practice.
