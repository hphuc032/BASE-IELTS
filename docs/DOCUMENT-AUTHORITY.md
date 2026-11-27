# Documentation authority

Milestone 0 architecture is approved and closed. The current approved baseline is v1.6. M1 implementation was separately authorized. M1 acceptance is recorded in [the acceptance report](MILESTONE-1-ACCEPTANCE-REPORT.md).

## Current references

- [Architecture v1.4](MILESTONE-0-FINAL-v1.4.md) was the standing proposal before the approved v1.6 promotion. [Architecture v1.5](MILESTONE-0-FINAL-v1.5.md) is a remediation candidate only. [Architecture v1.3](MILESTONE-0-FINAL-v1.3.md) is superseded as a technical proposal and remains immutable historical evidence alongside the [Independent v1.3 review](MILESTONE-0-INDEPENDENT-REVIEW-v1.3.md).
- [Decision register](DECISION-REGISTER.md) records approved pack D01–D11 and open TECH IDs. [ADR register](ADR-REGISTER.md) defines M0-ADR IDs.
- [Original PRD](IELTS_Learning_Platform_PRD_Agent_Guide.md) remains the product requirements baseline, subject to later explicit owner decisions. PRD D-01 through D-08 mean IELTS, hybrid, teacher/AI authorship, free, Reading, Spring Boot, initial scale and dedicated learning platform. These IDs do not alias pack D01–D11.
- [Traceability](MILESTONE-0-TRACEABILITY.md) and [review response](MILESTONE-0-REVIEW-RESPONSE.md) preserve earlier v1.4/v1.5 acceptance records and identify the effective v1.6 acceptance tests. See the approval record below for the separate M1 authorization.

## Historical sources and filename mapping

- [Initial architecture pack](IELTS_Milestone_0_ERD_API_Architecture_Pack.md), [v0.2 pack](IELTS_Milestone_0_ERD_API_Architecture_Pack_v0.2.md) and [v1.0 consolidation](IELTS_MILESTONE_0_FINAL.md) are superseded as technical drafts.
- [Initial independent review](MILESTONE-0-REVIEW.md), [design proposal](MILESTONE-0-DESIGN-PROPOSAL.md) and [decisions and plan](MILESTONE-0-DECISIONS-AND-PLAN.md) are historical review inputs. [Final independent review](MILESTONE-0-FINAL-REVIEW.md) is immutable evidence. [Remediation plan](IELTS_MILESTONE_0_REMEDIATION_PLAN.md) defines the requested correction scope.
- Historical references to docs/PRD.md resolve to the actual original PRD above. Historical docs/MILESTONE-0-ARCHITECTURE-PACK.md references resolve by version to the appropriate pack above. Historical docs/MILESTONE-0-FINAL.md means the v1.0 consolidation. Current baseline references use approved v1.6 and its registers. v1.5 remained a review candidate and is preserved for provenance. [Architecture v1.1](MILESTONE-0-FINAL-v1.1.md) is the superseded prior proposal. [Independent v1.1 review](MILESTONE-0-v1.1-INDEPENDENT-REVIEW.md) is immutable evidence. Mutable register links in older documents now resolve to current registers; the baseline digests below identify the exact prior versions.

JWT/browser bearer alternatives are superseded by D01. D04 supersedes optional modes, and the proposed v1.4 baseline contract requires a positive Exam duration. D10 supersedes card-only preview. D09 supersedes the earlier proposed after-submit-only Learning behavior. D06–D08 and D11 supersede conflicting session, registration and bootstrap drafts. Historical prompts confer no authority to initialize M1.

## Open-decision crosswalk

For v0.2 and v1.0 O-01 through O-17, respectively, the current destinations are TECH-01, 01, 02, 12, 08, 06, 07, 11, 11, 11, 03/04, 11, 10, 11, 08/12, 05, 11. Use the same mapping for the earlier independent plan. O-18 through O-22 in v1.0 and the earlier independent plan map to TECH-09, 09, 10, 11, 11. Historical proposals and provisional statuses do not carry over as acceptance.

PRD §2.3 open items, by ordinal, map as follows: 1 → TECH-01, 2 → D01/D06/D07 and TECH-02/12, 3 → D02/D10 and TECH-06/10, 4 → TECH-07/08/10, 5–7 → TECH-11, 8 → D08 and TECH-03/04, 9 → TECH-11, 10 → TECH-10, 11 → TECH-11, 12 → D03 and TECH-09/11, 13 → D03, 14 → TECH-11, 15 → D05 and TECH-09, 16 → TECH-12, 17 → TECH-11.

## Reviewed v1.1 input bundle

SHA-256 over raw bytes at remediation start. The working tree inputs were untracked; HEAD bb205ad did not contain this bundle. Record the exact v1.2 bundle at independent re-review by hash or approved Git revision. No commit is implied.

| File | SHA-256 |
| --- | --- |
| ADR-REGISTER.md | 04B55C0A551C3B4A611AD7F1A7C8A4AFF4F15DEE4BB53CED22EB95C302E23F01 |
| DECISION-REGISTER.md | 768328F500AEE63A39D2800EB80BB5799D71EE1AE267199ABDE911813408DCAC |
| DOCUMENT-AUTHORITY.md | E0786A67CDE4D0E279F653A29861F3D87B2820D606F969730691EDB697EC2D57 |
| IELTS_Learning_Platform_PRD_Agent_Guide.md | 4D31788EC49793004F31A146C169E663025EB034549D17BB1CCEEDA73E5E9EB6 |
| IELTS_Milestone_0_ERD_API_Architecture_Pack_v0.2.md | 48B7E3A8627BCA3EF5FB965AA171BB5884664D8C7ABC03B4734BB6FEE2D9DAF4 |
| IELTS_Milestone_0_ERD_API_Architecture_Pack.md | 487751864EB0AD1FABE776200695D10494B59D634082DC9A07464BBB9D9110B1 |
| IELTS_MILESTONE_0_FINAL.md | 8C719D63F5D6CBD46499DD0B8EC89EADDE75A365240AD5093B57F3E425FA2B0A |
| IELTS_MILESTONE_0_REMEDIATION_PLAN.md | D90E196DB08136891D29DC417AC3CD38B8A41101975E4009F17BB907A02C9241 |
| MILESTONE-0-DECISIONS-AND-PLAN.md | E51F6EA2423A269C32F892E854F28A71FDC8B409A03AE8E34DD45FCA307057A9 |
| MILESTONE-0-DESIGN-PROPOSAL.md | 50BC16459435661823AA7186EDE2C10982B278F5E6F908774C48F81EC75045B7 |
| MILESTONE-0-FINAL-REVIEW.md | A004F19960235AFFDA3957CD7ACBB8C81B9C8F037A12FC1DFBCC847AD709DC81 |
| MILESTONE-0-FINAL-v1.1.md | 145AE2AE06BFF83B58487F3AFAF5FF66B8279D9712B426EF0BCEFCFAEC9EFE4C |
| MILESTONE-0-REVIEW-RESPONSE.md | CA0BDBE72A84E13963B5026486974D14DD29C4C9F8C407A4D0C86B6FEC84E8E5 |
| MILESTONE-0-REVIEW.md | 2A2FE9810E99642FB5AED154F0FD5BABB4725EA46A39C9F3A28BEDFCB158B29F |
| MILESTONE-0-TRACEABILITY.md | 211EEE9F7D50F752675C868219C3422046C8F97C53A10E23E37F63017426F7C1 |
| MILESTONE-0-v1.1-INDEPENDENT-REVIEW.md | A9DF8CDC4CE7533E011554B05469FA5D61CEB9619196259DEBB8F319A4CF230D |

## v1.2 baseline and v1.3 review handoff

[Architecture v1.2](MILESTONE-0-FINAL-v1.2.md) is superseded as a technical proposal. [Independent v1.2 review](MILESTONE-0-v1.2-INDEPENDENT-REVIEW.md) remains immutable evidence. Historical links to mutable registers now resolve to current files. These SHA-256 values identify the v1.2 review inputs before remediation. The updated [Review Response](MILESTONE-0-REVIEW-RESPONSE.md) pins v1.3 companion digests. No working-tree draft is represented as part of HEAD.

| File | SHA-256 |
| --- | --- |
| ADR-REGISTER.md | 1D9BABFF7D1F3095242FD6AA465A2FC39140A1F51203271FA7540A887BE3B1F5 |
| DECISION-REGISTER.md | 094F25A5734400D1EBFA7B887511202182717A4E2D585F13503EAAF4B3F41031 |
| DOCUMENT-AUTHORITY.md | 5C11AE63D46BD9A3867B10763D65810D7960DDF86ABC7CCFFB51860715389AB7 |
| MILESTONE-0-FINAL-v1.2.md | 5A31F8BCAA0900B51B7CBFCEE628A2295774F9A209E27A54B7C3557A1C26EA5B |
| MILESTONE-0-REVIEW-RESPONSE.md | C104E2376AA41622126C6446C438B6F9A548EAE8F40C5E68C6176BD1BFFBF27F |
| MILESTONE-0-TRACEABILITY.md | 2BE00116EB7FE62EA9DD41654DED1FBA8D80CCB36FAE5994A0DEDE6D3ED1F17E |
| MILESTONE-0-v1.2-INDEPENDENT-REVIEW.md | 015AA33B8E561D2FBC7729386C9275B79D5F10081A70831D4C9E122EEF588B01 |

## v1.3 baseline and v1.4 review handoff

[Architecture v1.3](MILESTONE-0-FINAL-v1.3.md) is superseded as a technical proposal. [Independent v1.3 review](MILESTONE-0-INDEPENDENT-REVIEW-v1.3.md) remains immutable evidence. These SHA-256 values identify the v1.3 review inputs before v1.4 remediation. The updated [Review Response](MILESTONE-0-REVIEW-RESPONSE.md) pins v1.4 companion digests. No working-tree draft is represented as part of HEAD.

| File | SHA-256 |
| --- | --- |
| ADR-REGISTER.md | 5530F37AF1D399448F881FCBDF6F02391905D0929E98F20F43A1375788157660 |
| DECISION-REGISTER.md | B64EF97CC1642A4BD2384EF3E17F6C6E60ADBB6262A10657C54F0D57F4F73FBB |
| DOCUMENT-AUTHORITY.md | 816E77DEA4F0DD9BF7277DEAF6F363C574006A140CA4C76AA69704358D29335F |
| MILESTONE-0-FINAL-v1.3.md | A321708932D0A96D41439A911F0D1ADEBE453DB0E93E2E4A35A2DB02B1E3776D |
| MILESTONE-0-INDEPENDENT-REVIEW-v1.3.md | 941318771E4DF0FA4E35176117B426F218302968E4F8DA0FABD5CE76CD21E652 |
| MILESTONE-0-REVIEW-RESPONSE.md | 17B5027A5686320328D529FE1D37F349616B008F4EE15E36057AEFA0807DADAA |
| MILESTONE-0-TRACEABILITY.md | DC3600EB834E501E510608399F375A60033106A8634A61570A7032D6242238A2 |

The expected v1.4 independent review file was absent at v1.5 remediation start. R14-01–R14-03 are sourced from the user-supplied remediation request; no source review was reconstructed. Historical review reports and architecture packs present in the checkout remain immutable evidence.

## v1.6 targeted candidate

[Architecture v1.6](MILESTONE-0-FINAL-v1.6.md) was the remediation candidate for the three M1 gaps, preserving [v1.5](MILESTONE-0-FINAL-v1.5.md) byte-for-byte. v1.4 was standing at that time. Candidate tests now apply as identified in the effective acceptance authority section of traceability. The [v1.6 manifest](MILESTONE-0-v1.6-MANIFEST.md) pins exact bytes; E-01/E-02 were accepted with disclosed limitations in the [Product Owner decision record](MILESTONE-1-PREFLIGHT.md). These were the preapproval conditions. The current approval and separate M1 authorization are recorded below.

## Current approved architecture and separate implementation authorization

The standing architecture is [v1.6](MILESTONE-0-FINAL-v1.6.md) under explicit E-03 approval. Its raw SHA-256 is 9A7E1E0812C8C26B8828E627289F183A1A9103955BD408A9AB7A76B8A1BFF602, verified against the [original manifest](MILESTONE-0-v1.6-MANIFEST.md). Prior status entries describe history. v1.4 and v1.5 are preserved and superseded. Milestone 0 architecture closure is recorded after manifest and decision verification. Separately, the same owner message authorizes identity-only M1.1–M1.8; it does not certify acceptance or production readiness. E-01/E-02 limitations remain disclosed. See the [decision record](DECISION-REGISTER.md) and [progress](MILESTONE-1-PROGRESS.md).

## Authorized M1.8 reconciliation clarification

The Product Owner's M1.8 instruction authorizes the V11 scoped operator and lifecycle outcome lookups and the atomic reset receipt. This implementation clarification does not change D01–D11, PO-01–PO-06 or the approved v1.6 baseline. V1–V10 and historical test records retain their original authority.
