# M2 owner review checklist

Use the [decision register](M2-DECISION-REGISTER.md) for alternatives and consequences. These items are intentionally open. No earlier decision is reopened.

| Priority and deadline | Owner confirmation | Default recommendation pending approval |
| --- | --- | --- |
| Before M2 authorization | P01. Should the first content release include course/level/lesson navigation? | Optional hierarchy, single placement per passage and canonical Reading independent of catalog availability. |
| Before authoring implementation | P02/P04. Is publisher validation sufficient, and may an explicitly granted ADMIN both edit and publish? Who provisions scopes? | No persisted review workflow, deny by default, named grants through a separately controlled provisioning path. |
| Before publication acceptance | P03/P06. Require explanations, accept plain text, and accept atomic bundles plus temporary hiding of stale pinned exercises after standalone passage replacement? | Private required explanations, plain paragraphs, atomic bundle or coherent pointer eligibility. |
| Before public activation | P05/TECH-06. Confirm M3 rollout and fixed sample policy. | Keep all sample contracts disabled until approved; no sample exposure through a generic question endpoint. |

Design package completion means the proposal is ready for a decision session. Code, migrations, fixtures, role changes, public route activation and production rollout require separate authorization. M4 expiry remains gated.
