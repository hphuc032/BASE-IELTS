# M2 architecture blueprint

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

## Publication and consistency

Use stable Passage and Exercise identities with pinned immutable editions per v1.6 §5. PostgreSQL guards protect every frozen descendant and pointer. Accepted publication sequence: account fence on writer connection → JVM stripe → stable passages sorted UUID → stable exercises sorted UUID → validate graph and construct audit → freeze → switch pointers → COMMIT. Recheck draft revision and eligibility under locks. For an explicitly bundled publish, freeze a passage and exercises referencing that same edition atomically. Standalone passage replacement hides stale exercise editions until republished. Withdrawal clears matching pointers and records audit, retaining history. Bounded lock/statement budgets are implementation candidates requiring measurement. Retry deadlocks only after rollback and only for safe replayable commands.

Optional P01 navigation uses one placement row per passage, with one nullable assignment to a lesson. Navigation updates run separately under catalog root → level → lesson → placement, with optimistic revisions. They never acquire passage/exercise write fences. A link to a stable passage does not pin or freeze content. Disablement hides navigation, not canonical PUBLIC Reading. The normalized public projector applies both gates when serving navigation, returning a materialized DTO in one PostgreSQL snapshot.

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

**Proposed workflow P02.** Draft and frozen states plus the pointer are sufficient. Publisher validation and audit provide a release checkpoint. Do not add IN_REVIEW, APPROVED or persisted reviewer notes unless a separate moderation decision requires them. Administrative archive prevents new drafts or publish and withdraws the eligible pointer atomically. A frozen row never changes state. Revisions start at 1 and increment for each saved edit. Use a fresh successor draft from the current or last frozen edition. See [ERD](M2-ERD.md) and [security](M2-SECURITY-DESIGN.md).
