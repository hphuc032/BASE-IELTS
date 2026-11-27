# M2 UX specification

M2 Design v1.2 · 2026-11-27. Product Owner decisions and release authorization recorded. See the [sign-off package](M2-DESIGN-SIGNOFF.md) and [Decision Register](M2-DECISION-REGISTER.md).

## Existing conventions

The App Router currently has Home, account pages and a small verification route. Reuse same-origin /api/v1 rewrites, responsive Tailwind styling, labels, field errors, explicit progress states and in-memory scoped CSRF. Keep the account flow and verification bootstrap intact. Session and author data use no-store. Expired sessions return to sign-in using only allowlisted same-origin destinations. Never persist answer-bearing drafts in local storage, URLs or service workers.

## Staff screens

| Proposed UI path | Components, actions and states |
| --- | --- |
| /teacher | Scoped workspace landing with catalog, passage and exercise links. Hide unavailable actions for usability; service checks still apply. |
| /teacher/courses and /teacher/courses/[id] | Catalog table/cards, search/status, create/edit dialog, ordering fields, level and lesson tabs. Explicit archive confirmation. Desktop tables reflow into labeled cards on mobile. Approved P01. |
| /teacher/reading and /teacher/reading/[id]/drafts/[draftId] | Draft list and revision-aware editor with paragraphs, headings, topic, visibility and optional placement. Ordered keyboard controls as an alternative to drag and drop. Plain-text editing and preview. |
| /teacher/exercises and /teacher/exercises/[id]/drafts/[draftId] | Exercise list/editor with pinned passage edition, group navigator, five discriminator-specific question forms, option and protected answer panels, explanation, evidence picker. Switching type confirms clearing incompatible data. |
| .../preview and .../publications | Draft badge, safe preview component, missing validation summary, confirmation with draft revision and pointer impact. For a bundle show every switched edition. History shows current vs superseded frozen editions. |

Save is explicit. A successful save refreshes the ETag and draft status. Disable duplicate submission while pending. On 412 show reload/compare and retain unsaved form only in active page memory. On 409 explain changed pointer/state and reload. The publication dialog follows [Publication recovery](#publication-recovery) after lost response or transient failure. Dry preview never allocates or changes a pointer. Under P02, publisher feedback leaves an editable draft with no separate approval state. On auth expiry clear protected panels and memory.

Use semantic landmarks, keyboard focus on first invalid field, summaries linked to fields, live status feedback, no color-only state, ≥44px touch targets where practical, and dialog focus return. Tests verify small viewport layout and keyboard operation.

## Public screen contracts for M3

Catalog /courses → level/lesson navigation (P01) → canonical /reading/[slug]. Reading library /reading has bound filters, paging, empty state and separate availability errors. Public view uses escaped paragraph and heading components. Exercise cards show eligible metadata and the disabled sample area remains absent from the DOM and network payload until TECH-06. Once approved, use a dedicated answer-free sample component and sign-in action; no submit or answer reveal in M2/M3 discovery.

Server-render from a single safe public DTO snapshot per response. Hydrate only documented fields. Admin forms may use client components for edit state and delegated same-origin fetch. Private prefetch and shared caching are disabled. Reuse display-only typography components across preview and public screens without sharing privileged data loaders. Each protected staff GET revalidates AUTH, capability and scope under the F04 account fence and completes DTO materialization before release. A later request with a stale authorization state is denied after revocation commits. Clear protected UI state when authorization is rejected.

## Exercise and Passage editor behavior

Catalog is approved, placement optional. An inactive hierarchy only hides navigation links. Authoring works without catalog assignment. Use the [field registry](M2-API-CONTRACT.md#proposed-field-validation-registry) for validation hints. Offer LEARNING, EXAM or both. Hide and clear duration with EXAM off and require an explicitly entered integer duration with EXAM on. Never generate a duration for Learning-only content. Permit all three valid mode configurations at publish.

Keep paragraphId on every existing staff paragraph while editing or moving it. Omit the ID only for a new paragraph. Bind the Evidence picker to the exact edition, with paragraphId as selection identity and position/content as display data. Use the scoped staff edition projection. On 409 PARAGRAPH_REFERENCED, retain the unsaved edit in memory and explain that the paragraph still has Evidence references. Read a new server-generated UUID from the successful save response. Clear protected state on authorization expiry.

Selecting a Passage draft shows a bundle-only indicator with exact edition and revision. Publishing requires a private explanation on every question. Keep private explanation out of preview/shared UI data loaders and all public responses.

## Publication recovery

Generate one operation UUID before the first submit and keep it with the exact original request and revisions in active page memory. If that request is lost on reload, offer lookup only. A tab-scoped recovery reference may retain the root and operation UUID for lookup. Clear it on acknowledgment, logout or actor change. Do not persist the command, auth data or answer-bearing content in the recovery reference.

After a lost response, use the authorized publications lookup backed by primary PostgreSQL. A visible receipt yields COMMITTED. A concealed 404 or a temporary failure without authoritative result leaves the dialog in UNKNOWN. Explain that lookup could not establish a committed result and cannot tell whether the operation failed, rolled back or ever started. Allow recheck, or retry the original operation UUID and exact intent after revalidation. After reload with no original payload, offer reconciliation and explain why retry is unavailable. Never substitute a payload or automatically create a new UUID while the outcome is unresolved. Report rollback only with authoritative evidence from the original execution.
