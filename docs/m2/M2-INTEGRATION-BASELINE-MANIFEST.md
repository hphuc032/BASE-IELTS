# M2.1 and M2.2 integration baseline manifest

2026-11-27 · Integration Owner · local codex/m2-integration

| Baseline | Verified Git SHA |
| --- | --- |
| Integration HEAD and Agent A Catalog source | 860fbea3a510c125bda94012faf9a73bbe100935 |
| Agent B Reading source | 483b9d0d27a36d6e6d0c58fd239bd2dbfd6ab87b |
| Common baseline and current GitHub main | 35bd9b95e2d99098f8ed9477a7ed79d4763ad4f3 |
| Accepted M1 ancestor | a21337d2f447a6aaa13ce4c32f121230adaa8ea0 |

GitHub returned both feature heads and main at the exact SHAs above. It returned no integration branch. The candidate includes the uncommitted, checksum-pinned integrations in the file inventory below. The approved v1.6 Git blob and M1 production sources are unchanged. V16 Flyway checksum 957144442 and V17.3 checksum -574889560 remain asserted by the runner. V18 and V19 are reserved.

Toolchain observed or pinned by the build includes Java 21, Maven Wrapper 3.9.11, Node 24.14.0, npm 11.9.0, Next.js 16.4.6 and Playwright 1.61.1. The read-only local JDBC probe returned PostgreSQL 17.12. The exact isolated PostgreSQL fixture image is pinned in compose.yaml.

The PO-11 combined run did not reach fixture creation because the Docker engine API did not respond. The earlier 57-pass V17.3 combined run used container base-ielts-staff-test-f927e03852e344c1a3d3dcf8ee31c9c8. Full fixture identities and commands are in the integration checkpoint. The earlier 10,000-Course results are separate from PO-11 validation.

## Raw SHA-256 inventory

| Path | SHA-256 |
| --- | --- |
| `.gitattributes` | 61171cb1510ada0aa2994017ad1bc1da553bf16507c780e07163116fcf42bb39 |
| `backend/src/main/java/com/baseielts/platform/content/CatalogConnectionAdmission.java` | 933f2f9863ff08f01a4ddefcae001ef21a61fa5f14539692c1c361ce131ebf91 |
| `backend/src/main/java/com/baseielts/platform/content/CatalogHttpInput.java` | 68d4d0d14e7775980bd7c254e347d9142778415baf70d951acd34e651faf81b2 |
| `backend/src/main/java/com/baseielts/platform/content/CatalogInput.java` | a13d8facd5816f034b96cbab1b46a104e7bfebc241be8d41bf421c71fe9547e5 |
| `backend/src/main/java/com/baseielts/platform/content/CatalogRoutines.java` | 29bc3593a2045b75fa78b6a6f7713a3c54cd8df6468ba0d18c62d3c5c2f13edd |
| `backend/src/main/java/com/baseielts/platform/content/CatalogStaffApi.java` | b9dbbe88bd05af538a34e84eabf84fd90ce63b3f2150ae3c540c6dcd910d8f7e |
| `backend/src/main/java/com/baseielts/platform/content/CatalogStaffSecurity.java` | 080ad97c36d624c3a41a06bbac79cd85b9de10b3fd1cd5ea337c9d34b5534ce5 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingDraftInput.java` | 4a5b93cbf272df80460fd8681d85481a3ef949c37395f068c4df79ae4809addf |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingRoutines.java` | 1352e0f8b242b563aa974f8f15ba3d2f89b304af33f6d4e99415dc28ebae08a5 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingStaffApi.java` | f6a195b7928a37301a4e1cc3f0e6ddbed95df376309f181f0b63c7718f28dea2 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingStaffProjection.java` | 44b357760c670687c4eabad092bb82c536166768c63eb737e3e85f9a28cb7474 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingStaffSecurity.java` | 273bc91f3f2dd43c6da0d442bee685f4e478cd1053423340b430942bc99917bf |
| `backend/src/main/resources/db/migration/V16__staff_catalog_routines.sql` | 2e488fc22aba5df1af34fb73de0118d46e155d0fd4bd90a85f0f4b0a12810c81 |
| `backend/src/main/resources/db/migration/V17__catalog_point_lookups.sql` | e76b48517190d1e8e76d5bf83e429ab62f16b279245cf7986c920c1511a8445c |
| `backend/src/main/resources/db/migration/V17_1__catalog_parent_lookups.sql` | ca40cc120dfb8a157638e702c2bf5c10bc54b9e67742fd268c3e04398fd66945 |
| `backend/src/main/resources/db/migration/V17_2__reading_lesson_placement.sql` | 72cae4c69f59638be913a3b85152a52ad0af0cfcd750160a6d1fce1a74b609e4 |
| `backend/src/main/resources/db/migration/V17_3__reading_staff_authoring.sql` | 2d32eafaf4a7889c8d1add753e9f24224296295606be0b8dcf056667575c31e1 |
| `backend/src/test/java/com/baseielts/platform/content/CatalogConnectionAdmissionTest.java` | 6c49d110cd0b0216021d654be6304563e62e20262b022e4a059a6e2febd6d78b |
| `backend/src/test/java/com/baseielts/platform/content/CatalogHttpInputTest.java` | 3021220fbd0b061eccc747635993fd4e3aa9ba8a476e1c471114466567d5ce59 |
| `backend/src/test/java/com/baseielts/platform/content/CatalogPostgresTest.java` | d01091de918d3f0240b3de65d9240df805d0b6a21ff6d4e80a7b5f43b6db2ac3 |
| `backend/src/test/java/com/baseielts/platform/content/reading/PassageAuthoringContractTest.java` | 04f24029c18929b4a4974b83fc1455046fa996b3d0c3635b835690db31f1055f |
| `backend/src/test/java/com/baseielts/platform/content/reading/ReadingRouteBoundaryTest.java` | 505f827d4504e281219a3c4659b82092e8bdc7cd606f906a95dd1a9b478c1632 |
| `backend/src/test/java/com/baseielts/platform/content/StaffFixtureMigrationTest.java` | d4eb0c6885ab3ccac6b155eb11f40699717f1abac0eecb7351328165c7634765 |
| `backend/src/test/java/com/baseielts/platform/identity/CatalogG03PostgresTest.java` | 755b12268c88042d7c7eb1670e07940a814359bdb6b6d8df6e6c8d09eebe4d74 |
| `backend/src/test/java/com/baseielts/platform/identity/CatalogRouteBoundaryProofTest.java` | e4fae6af9ce18ca4b0f13853f2e14e7fe39bcc381add3ed234fd11639a53c56c |
| `backend/src/test/java/com/baseielts/platform/identity/CatalogStaffApiPostgresTest.java` | c0cb1897141b535d6da9064a4e2d2af618fe01209fa332cbd48916a19e0bf831 |
| `backend/src/test/java/com/baseielts/platform/identity/ReadingStaffPersistencePostgresTest.java` | 680687d43c393e51b98ae494bdd77fb62761a1c226ae49725f87c14769fa71cc |
| `backend/src/test/java/com/baseielts/platform/identity/VerificationPostgresTest.java` | 11e753c952897e71704ab14ad6ea280cb928ddf72f0aa830961debd7f23bce2a |
| `backend/src/test/postgresql/catalog-staff.sql` | b1c9f7b1999748194c21062a55cfa4f5348fd9b8fbb35b8f1d9d06053c5e672e |
| `backend/src/test/postgresql/reading-staff.sql` | 6213837b29d0f234ad3a32d54d70420d5a3a8e3a4df00a74a0ed09b88219ce85 |
| `scripts/initialize-dev-db.ps1` | 5f694c9dca9e8f71693fa0e210098053f3783982ba37514bdc9e33b5c0e6d09a |
| `scripts/test-m2-catalog.ps1` | c27ef32770a3d76dbe98ce003459b474ec29739624ec445caafb059a4245ec31 |
| `scripts/test-m2-staff-auth.ps1` | 641a264345048a697790b377d762df56fb4493f4525ce6982f08775a676224bd |

## Final isolated fixture validation

The isolated WSL source-build run supersedes the earlier Docker startup blocker. PostgreSQL 17.12 was verified on the original server by read-only JDBC and on the dedicated fixture through both the startup probe and Flyway. See [final fixture identities and exact command](M2-INTEGRATION-CHECKPOINT.md#dedicated-wsl-postgresql-fixture-and-reading-limits). The inventory below supersedes the earlier working-copy snapshot for subsequently changed test, transport and launcher files.

| Path | Final tested raw working-copy SHA-256 |
| --- | --- |
| `.gitattributes` | 61171cb1510ada0aa2994017ad1bc1da553bf16507c780e07163116fcf42bb39 |
| `backend/src/main/java/com/baseielts/platform/content/CatalogConnectionAdmission.java` | 933f2f9863ff08f01a4ddefcae001ef21a61fa5f14539692c1c361ce131ebf91 |
| `backend/src/main/java/com/baseielts/platform/content/CatalogHttpInput.java` | 68d4d0d14e7775980bd7c254e347d9142778415baf70d951acd34e651faf81b2 |
| `backend/src/main/java/com/baseielts/platform/content/CatalogInput.java` | a13d8facd5816f034b96cbab1b46a104e7bfebc241be8d41bf421c71fe9547e5 |
| `backend/src/main/java/com/baseielts/platform/content/CatalogRoutines.java` | 29bc3593a2045b75fa78b6a6f7713a3c54cd8df6468ba0d18c62d3c5c2f13edd |
| `backend/src/main/java/com/baseielts/platform/content/CatalogStaffApi.java` | b9dbbe88bd05af538a34e84eabf84fd90ce63b3f2150ae3c540c6dcd910d8f7e |
| `backend/src/main/java/com/baseielts/platform/content/CatalogStaffSecurity.java` | 080ad97c36d624c3a41a06bbac79cd85b9de10b3fd1cd5ea337c9d34b5534ce5 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingDraftInput.java` | ff5702c4cd06a0049790bc776a8e74ab4989c6ab18f28c606086c74ef6f4a255 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingRoutines.java` | 1352e0f8b242b563aa974f8f15ba3d2f89b304af33f6d4e99415dc28ebae08a5 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingStaffApi.java` | f6a195b7928a37301a4e1cc3f0e6ddbed95df376309f181f0b63c7718f28dea2 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingStaffProjection.java` | 44b357760c670687c4eabad092bb82c536166768c63eb737e3e85f9a28cb7474 |
| `backend/src/main/java/com/baseielts/platform/content/reading/ReadingStaffSecurity.java` | 273bc91f3f2dd43c6da0d442bee685f4e478cd1053423340b430942bc99917bf |
| `backend/src/main/resources/db/migration/V16__staff_catalog_routines.sql` | 2e488fc22aba5df1af34fb73de0118d46e155d0fd4bd90a85f0f4b0a12810c81 |
| `backend/src/main/resources/db/migration/V17__catalog_point_lookups.sql` | e76b48517190d1e8e76d5bf83e429ab62f16b279245cf7986c920c1511a8445c |
| `backend/src/main/resources/db/migration/V17_1__catalog_parent_lookups.sql` | ca40cc120dfb8a157638e702c2bf5c10bc54b9e67742fd268c3e04398fd66945 |
| `backend/src/main/resources/db/migration/V17_2__reading_lesson_placement.sql` | 72cae4c69f59638be913a3b85152a52ad0af0cfcd750160a6d1fce1a74b609e4 |
| `backend/src/main/resources/db/migration/V17_3__reading_staff_authoring.sql` | 2d32eafaf4a7889c8d1add753e9f24224296295606be0b8dcf056667575c31e1 |
| `backend/src/test/java/com/baseielts/platform/content/CatalogConnectionAdmissionTest.java` | 6c49d110cd0b0216021d654be6304563e62e20262b022e4a059a6e2febd6d78b |
| `backend/src/test/java/com/baseielts/platform/content/CatalogHttpInputTest.java` | 3021220fbd0b061eccc747635993fd4e3aa9ba8a476e1c471114466567d5ce59 |
| `backend/src/test/java/com/baseielts/platform/content/CatalogPostgresTest.java` | d01091de918d3f0240b3de65d9240df805d0b6a21ff6d4e80a7b5f43b6db2ac3 |
| `backend/src/test/java/com/baseielts/platform/content/reading/PassageAuthoringContractTest.java` | 04f24029c18929b4a4974b83fc1455046fa996b3d0c3635b835690db31f1055f |
| `backend/src/test/java/com/baseielts/platform/content/reading/ReadingRouteBoundaryTest.java` | 505f827d4504e281219a3c4659b82092e8bdc7cd606f906a95dd1a9b478c1632 |
| `backend/src/test/java/com/baseielts/platform/content/StaffFixtureMigrationTest.java` | 7c1b432e38c556c6e378c2e67cc89efd70f56d6aa804f4b680625e294db0d85e |
| `backend/src/test/java/com/baseielts/platform/identity/CatalogG03PostgresTest.java` | 755b12268c88042d7c7eb1670e07940a814359bdb6b6d8df6e6c8d09eebe4d74 |
| `backend/src/test/java/com/baseielts/platform/identity/CatalogRouteBoundaryProofTest.java` | e4fae6af9ce18ca4b0f13853f2e14e7fe39bcc381add3ed234fd11639a53c56c |
| `backend/src/test/java/com/baseielts/platform/identity/CatalogStaffApiPostgresTest.java` | c0cb1897141b535d6da9064a4e2d2af618fe01209fa332cbd48916a19e0bf831 |
| `backend/src/test/java/com/baseielts/platform/identity/ReadingStaffPersistencePostgresTest.java` | 95d1bbdfbee916f6746a9bf71f7c7a11f692a5997b94730abf7c61e0ba80d8ef |
| `backend/src/test/java/com/baseielts/platform/identity/VerificationPostgresTest.java` | 1a7fc00f41ff9dc0be6fa7bc1e476ab2144bef70acec2adeb8c94d85c44c09ba |
| `backend/src/test/postgresql/catalog-staff.sql` | b1c9f7b1999748194c21062a55cfa4f5348fd9b8fbb35b8f1d9d06053c5e672e |
| `backend/src/test/postgresql/reading-staff.sql` | 6213837b29d0f234ad3a32d54d70420d5a3a8e3a4df00a74a0ed09b88219ce85 |
| `frontend/tests/identity.spec.ts` | 52bf8d4a7bf37517064745565193281d26bd2b2a2337512f73c005dc0e723882 |
| `scripts/initialize-dev-db.ps1` | 18684e585450d15b442c585e19a3c7a6b76348aaadf5ff2ab28f1a8744c4f152 |
| `scripts/m1-fixture-psql.ps1` | 6fd21b38514075f57d3d5ef03c98d51fa57aefe5a7b764d2f4db918df779d1ce |
| `scripts/test-admin-recovery.ps1` | 2dff44f0a8363e75b5bc1914c16e3911e4de04473321541942e1882c53f73c18 |
| `scripts/test-auth-browser.ps1` | 031d73a98e7c2e84aa688cd589150c69bcc9ec893e639fe719839607334d5b55 |
| `scripts/test-m1-on-wsl-fixture.ps1` | e1b62d74acbf1d0e07d7092c5f82eacd44e60fd59f7b274c475c7b14b0e2704d |
| `scripts/test-m2-acceptance-on-wsl.ps1` | 08cd5d5df6e20397b263ec98f763c220e63b2d50791b0dbfd34c744ac61594f9 |
| `scripts/test-m2-catalog.ps1` | c27ef32770a3d76dbe98ce003459b474ec29739624ec445caafb059a4245ec31 |
| `scripts/test-m2-staff-auth.ps1` | f679001727a40248316558fbcb0ed45bd868089572cd528fc15aee47f6dda5a2 |

Git treats the pre-existing terminal CRLF in the pinned V17.2 file as a line ending for whitespace checks. The V17.2 bytes remain intact. The final .gitattributes SHA-256 is 76aa5c692a9e73eec7fafb826092a2d32aee0a8fea951504059d7cfe85cbc88d.
