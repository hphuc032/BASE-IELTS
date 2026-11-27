# M2 implementation roadmap

**Planning only.** Release sequencing is conditional on explicit M2 authorization and decisions in the [register](M2-DECISION-REGISTER.md). Migration numbers below are relative, assigned after inspecting current Flyway history. No V1–V12 modifications.

| Stage | Scope, dependencies and deliverables | Exit tests and gate |
| --- | --- | --- |
| M2.0 Architecture and contract approval | Owner resolves P01–P06, validates v1.6 alignment, approves implementation scope. Review migrations, private/public DTOs, permissions and release flags. No source work. | Owner signs off design and authorizes implementation separately. |
| M2.1 Catalog foundation | If P01 approved, additive catalog tables, safe typed routines and staff catalog REST. Staff pages deferred to M2.6. Maintain root/child ordering and scopes. If rejected, omit migrations/routes/screens. | T01, T08, T09, T11. Catalog operations cannot update content pointers. |
| M2.2 Reading passage/lesson authoring | Depends on M2.0 and M2.1 only for placement. Add stable passage, draft edition, topic, paragraph and translation tables; restricted JDBC integration; author read/edit/preview API. | T02, T05, T08, T09, T11 with exact tuples, one editable draft and revision CAS. |
| M2.3 Question graph and protected answer data | Add exercise version, groups, questions, options, protected marking, explanation and evidence. Pin passage edition. Implement compound draft DTO saves. | T02–T03, T05, T07, T09, T11. Five types validated, no public answer projection. |
| M2.4 Publication and withdrawal | After M2.2–3 and P02/P04. Add freeze guards, pointers and audit. Typed publisher functions, bundle publish and staff history. Author role cannot publish. | T04–T05, T08, T11 race/failure cases. Coherent commit and immutable graph. |
| M2.5 Public projection contract | After M2.4. Safe views/read routines and public DTO tests isolated behind disabled M3 route wiring. No sample membership migration until TECH-06. | T06–T07, T12. Independent DB role denied all answer data. Release of live public discovery belongs to M3. |
| M2.6 Staff frontend | After staff APIs pass. Workspace, catalog if approved, editors, read-only preview, history and publish/withdraw dialogs. Reuse M1 UI patterns. No new migrations. | T08–T10 browser paths with session expiry, CSRF, access denial and keyboard/mobile preview. |
| M2.7 Public UI contract | Specify or fixture-render browse and reading screens after safe projection. No site navigation change or endpoint activation during M2. Live feature implementation/enablement belongs to M3. | T07/T10/T12 for isolated fixtures if authorized. M3 remains gated. |
| M2.8 Integrated acceptance | Run M1 regressions, fresh and upgrade PostgreSQL with least-privilege roles, backend and frontend builds, and Edge flows. | All applicable T01–T12 and existing M1 tests pass. Document deferred work and separate production gates. |

Content migrations are forward-only under separate ownership. A feature can land disabled only if explicitly in the authorized implementation scope, with public access denied by default and permission tests proving the disabled state. No assessment schema or execution in this roadmap.
