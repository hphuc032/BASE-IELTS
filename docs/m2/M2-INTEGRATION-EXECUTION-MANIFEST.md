# M2 integration execution manifest

Intake verified on 2026-11-27. M2 Design v1.2 and Milestone 0 v1.6 govern this work. The M1 acceptance commit is `a21337d2f447a6aaa13ce4c32f121230adaa8ea0`.

| Worktree | Branch | Local and directly verified remote HEAD | State | Flyway |
| --- | --- | --- | --- | --- |
| `D:/BASE-IELTS` | `feature/m2-content-foundation` | `860fbea3a510c125bda94012faf9a73bbe100935` | Clean | V1–V15 |
| Managed Reading worktree | `feature/m2.2-reading` | `1657d2bb798258cab17e8c005c0768497f8f23d4` | Clean | V1–V12 |
| Design release | `main` | `35bd9b95e2d99098f8ed9477a7ed79d4763ad4f3` | Remote ref verified | V1–V12 |

The reported catalog checkpoint `656207a312b9aaa68f7d8daeb460fe9824eef136` exists. The catalog branch already contains private Passage V15 and the narrow Decision A staff AUTH seam. Each branch preserves the V1–V12 Git blobs from M1. The Milestone 0 v1.6 Git blob on both branches is `bdd4c544e354d50f49daae15a2e29d314f848165`, with approved SHA-256 `9a7e1e0812c8c26b8828e627289f183a1a9103955bd408a9ab7a76b8a1bff602`. The Reading checkout uses CRLF conversion for that file. Its committed bytes match the approved digest. The manifest file itself has committed SHA-256 `5f7c77d55a6bacd81159a8463abc239a1a59452811081ee697f03a6f78bbd425`. Catalog security configuration and account fence SQL match M1. The only tracked change to an existing identity source is the Decision A extension to `AuthenticationService`. The prior protected input manifest is at `docs/m2/M2-PARALLEL-INTAKE-MANIFEST.md` on the catalog branch at `860fbea`.

| Owner | Scope | Execution and release gate |
| --- | --- | --- |
| Agent A | Catalog and staff AUTH on `feature/m2-content-foundation` in `D:/BASE-IELTS` | Prepare the next catalog application or REST boundary behind fixture-only activation. Exercise the existing fence, authorization, CAS and audit as far as the isolated contract permits. Record measured G03 evidence separately from live readiness. |
| Agent B | Reading domain and isolated tests on `feature/m2.2-reading` in the managed worktree | Build stable identity and versioned draft behavior against the ERD and document the V15 persistence dependency. Keep question graph preparation independent. Staff UI remains gated by M2.6. |
| Integration owner | This manifest, cross-branch review, protected file verification, shared integration and migration sequence | Preserve V13 catalog, V14 authority/audit and V15 private Passage. Further versions require a reviewed schema proposal. Verify each branch independently before any integration checkpoint. |

Separate PostgreSQL fixtures are required for any database execution. G02 continues to block real provisioning and affected runtime activation. G03 must be measured per operation before activation. G01 continues to block publication. Public discovery remains gated to M3. Only tested, scoped checkpoints can be published to their respective feature branches.

## Coordination outcome

- **G02.** The Product Owner confirmed environment-bootstrap synthetic development and test fixtures with separate operator and approver identities. Production provisioning remains blocked. The deployment/security owner must control the credential in an approved secrets store and jointly approve the exact principal and identity mapping with the Product Owner. Independent approval, rotation, recovery and audited handover are required.
- **Catalog.** Agent A paused after overlapping edits appeared in the Catalog worktree, including `CatalogRoutines.java`, `CatalogStaffApi.java`, a new Catalog security configuration and `V16__staff_catalog_routines.sql`. This integration pass assigned no V16. The 15-test isolated attempt reported 11 passed, three failed and one error with none skipped. The changed repository called routines missing from the fixture, and the privilege test queried the wrong login fence signature. No Catalog changes were staged or pushed. Resolve ownership and validate one routine contract before resuming. G03 acceptance remains open.
- **Reading.** Agent B pushed the tested domain checkpoint `445a6aa624ed60d123ef3338d5fbc48a23a2d052`. Additional verification used the committed Catalog V15 and independent databases rather than the changing Catalog checkout. The verification found no need for a new migration at this checkpoint. [Reading evidence](M2.2-ACCEPTANCE-REPORT.md)
