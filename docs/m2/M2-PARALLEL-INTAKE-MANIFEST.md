# M2 parallel development intake

Verified on 2026-11-27 before the two workstreams started.

| Item | Intake |
| --- | --- |
| Local branch | feature/m2-content-foundation |
| Local HEAD and live GitHub feature ref | `656207a312b9aaa68f7d8daeb460fe9824eef136` |
| Local main and live GitHub main ref | `35bd9b95e2d99098f8ed9477a7ed79d4763ad4f3` |
| Working tree | Clean at intake, with no staged, unstaged or untracked files |
| Flyway | V1–V14 tracked. V1–V12 match the accepted M1 baseline. |
| Identity source and config | Matched accepted M1 commit `a21337d2f447a6aaa13ce4c32f121230adaa8ea0` at intake. |
| Architecture | Milestone 0 v1.6 and M2 Design v1.2 govern the work. |

## Protected inputs

Raw SHA-256 values from the local checkout. Only the newly authorized staff integration may extend identity behavior for new consumers.

| Path | SHA-256 |
| --- | --- |
| `docs/MILESTONE-0-FINAL-v1.6.md` | `9a7e1e0812c8c26b8828e627289f183a1a9103955bd408a9ab7a76b8a1bff602` |
| `docs/m2/M2-DESIGN-SIGNOFF.md` | `4c183908c0779a4fc80e69142bcfa19dc6144b2197dbbd006516ef9c5544f14b` |
| `backend/src/main/java/com/baseielts/platform/identity/AuthenticationService.java` | `746aa00742eb34ea7abd375d43481a522d30488f504c48beded4a263889daa7f` |
| `backend/src/main/java/com/baseielts/platform/identity/AuthSessions.java` | `63da7572142168cef94859ce5c79671702377a7defdb0c5d11ba32a0900be9c2` |
| `backend/src/main/java/com/baseielts/platform/identity/BoundedRateLimit.java` | `c04e102c85bbac52c04b9f9eedc8f86a43f492efce26a08cc2e9fd1b0a6d6eaa` |
| `backend/src/main/java/com/baseielts/platform/identity/CookieSupport.java` | `72d19101193af31005a3aab14ee49503cd106954b1b2ef07483ed24e400f7b37` |
| `backend/src/main/java/com/baseielts/platform/identity/DevelopmentAdminCommand.java` | `9929e6af0889e8883a314d29254c5057a9e5ceaa2cf27946b58447a9ed74aa62` |
| `backend/src/main/java/com/baseielts/platform/identity/DevelopmentVerificationMailbox.java` | `cd0e2aa053385a457ab87bd91bac0f97f45809aaa1f03dfc33897b20c495b5b4` |
| `backend/src/main/java/com/baseielts/platform/identity/IdentityCapacityException.java` | `4b9cec47e2c016fb20ce46b1ab4dd94a73d8d878f14ba9e6cb3ccd066cd9f451` |
| `backend/src/main/java/com/baseielts/platform/identity/IdentityPasswords.java` | `db2e92c307d2af01b42fe9e2965c8c3aba16966aab73c25bf1745e99328cc90a` |
| `backend/src/main/java/com/baseielts/platform/identity/IdentitySecurityContextRepository.java` | `8abd7e807f3622fe36d5fb9ede514249bca1c23a2285cc3ee0f0312a488e5ab5` |
| `backend/src/main/java/com/baseielts/platform/identity/OpaqueTokens.java` | `2785cc4e28dcab0c22fbd37ca7ed3dfc96f60157dd3f6427268a3527fceacdef` |
| `backend/src/main/java/com/baseielts/platform/identity/PreauthStore.java` | `fec265068e6e927b63318f238dc3112b9947b06637c37507d9404feb1c264940` |
| `backend/src/main/java/com/baseielts/platform/identity/VerificationConnectionAdmission.java` | `add541cad3377e9630b7025cb6b318d9c1ae21ced8a82ce85e0b13ba1aaad2b0` |
| `backend/src/main/java/com/baseielts/platform/identity/VerificationController.java` | `766634020b3836c72508e750c41b0675cca80be0e79d18d48004a697dae9dfb5` |
| `backend/src/main/java/com/baseielts/platform/identity/VerificationService.java` | `ae308539fa9838545e9c87c21e54b25e6c129bd764c459c0aea6b339e5948466` |
| `backend/src/main/java/com/baseielts/platform/config/DatabaseSettings.java` | `755c4c28247f58b1c6631b6970295e2678e1150d77fac8806032707a7452c079` |
| `backend/src/main/java/com/baseielts/platform/config/IdentityRequestBoundary.java` | `2902f3c9a3df0731d2a30538ed4414855fbe5a3891fe7fede731ed31aa389c30` |
| `backend/src/main/java/com/baseielts/platform/config/SecurityConfiguration.java` | `342140397f42226ddaf65fcf7baf5161a1558e97f9d198474ac8ff80a2f8c461` |
| `backend/src/main/resources/db/migration/V1__identity_schema.sql` | `472c0e2ae59fd7af539c20f6b17ee20ba4d3acf7d0a8fa8621a6758fe2fe7e79` |
| `backend/src/main/resources/db/migration/V2__account_fencing.sql` | `5a77bf06aaf70d7edddd62d5b352ee2d587749c410361f0bbcedd07ed810ab11` |
| `backend/src/main/resources/db/migration/V3__verification_routines.sql` | `a1ce956b8119c79e460c645d3790b12db379809b1170b95fabf9c051dd0f3bcc` |
| `backend/src/main/resources/db/migration/V4__login_and_profile_routines.sql` | `9e3fffa7bc5fecfbb118336af7faf223f7bb913f14a663e96d77d36f12caed8f` |
| `backend/src/main/resources/db/migration/V5__verification_retention.sql` | `df89d107b8e740995ddf975cb94810c79791413172a154e75114b8953e1f11ea` |
| `backend/src/main/resources/db/migration/V6__governance_denial_audit.sql` | `26695756418ffa22f4d989bc6041c9c594f0d4f0ae353eed274a5cc84a186f5a` |
| `backend/src/main/resources/db/migration/V7__admin_bootstrap_and_approval_cancellation.sql` | `c591d5c92e129107927b2357c3bccd88ccd12dafe0294d79ce45fab508cab516` |
| `backend/src/main/resources/db/migration/V8__identity_lifecycle.sql` | `1bf920848d26cc74cee4628c53bdb5506ad303d3825469a2e1d093439c1e96d5` |
| `backend/src/main/resources/db/migration/V9__separate_approver_scope.sql` | `576a15375231d061e122609c9420ed003f42a7dd6b6d0e10a282c3d49f1ccbb3` |
| `backend/src/main/resources/db/migration/V10__idempotent_profile_display_name.sql` | `969f3799694a9db48dffaf8fd7f0edcfb3e500b809cd244aaef3a0cf153cf59d` |
| `backend/src/main/resources/db/migration/V11__management_reconciliation.sql` | `1f1838a19d23682097b76f61edfc8645fc6e6c3899fd365293c2c7d133a559c3` |
| `backend/src/main/resources/db/migration/V12__recovery_expiry_outcome.sql` | `7bf38e62d85fe0d455b212395abb47c3a4d18e00ee9df59c39d370e4ae370601` |
| `backend/src/main/resources/db/migration/V13__content_catalog_schema.sql` | `2b4807eba222c4b070f8601a7a4cbb574def651c422516bbdbc41a576192f078` |
| `backend/src/main/resources/db/migration/V14__catalog_authority_and_audit_storage.sql` | `645f97b4bd04fc6667cb7dd0f4bf2e3d0a0f41c3df5aa162e846156d7db8b3dd` |
