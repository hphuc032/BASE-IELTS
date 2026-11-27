# M2 UX specification

## Existing conventions

The App Router currently has Home, account pages and a small verification route. Reuse same-origin /api/v1 rewrites, responsive Tailwind styling, labels, field errors, explicit progress states and in-memory scoped CSRF. Keep the account flow and verification bootstrap intact. Session and author data use no-store. Expired sessions return to sign-in using only allowlisted same-origin destinations. Never persist answer-bearing drafts in local storage, URLs or service workers.

## Staff screens

| Proposed UI path | Components, actions and states |
| --- | --- |
| /teacher | Scoped workspace landing with catalog, passage and exercise links. Hide unavailable actions for usability; service checks still apply. |
| /teacher/courses and /teacher/courses/[id] | Catalog table/cards, search/status, create/edit dialog, ordering fields, level and lesson tabs. Explicit archive confirmation. Desktop tables reflow into labeled cards on mobile. P01 only. |
| /teacher/reading and /teacher/reading/[id]/drafts/[draftId] | Draft list and revision-aware editor with paragraphs, headings, topic, visibility and optional placement. Ordered keyboard controls as an alternative to drag and drop. Plain-text editing and preview. |
| /teacher/exercises and /teacher/exercises/[id]/drafts/[draftId] | Exercise list/editor with pinned passage edition, group navigator, five discriminator-specific question forms, option and protected answer panels, explanation, evidence picker. Switching type confirms clearing incompatible data. |
| .../preview and .../publications | Draft badge, safe preview component, missing validation summary, confirmation with draft revision and pointer impact. For a bundle show every switched edition. History shows current vs superseded frozen editions. |

Save is explicit. A successful save refreshes the ETag and draft status. Disable duplicate submission while pending. On 412 show reload/compare and retain unsaved form only in active page memory; never blindly replay. On 409 explain changed pointer/state and reload. On 503 offer safe read-before-retry for ambiguous publish. Dry preview never allocates or changes a pointer. Rejection or changes under P02 mean publisher leaves the draft editable; no separate approval state. On auth expiry clear protected panels and memory.

Use semantic landmarks, keyboard focus on first invalid field, summaries linked to fields, live status feedback, no color-only state, ≥44px touch targets where practical, and dialog focus return. Tests verify small viewport layout and keyboard operation.

## Public screen contracts for M3

Catalog /courses → level/lesson navigation (P01) → canonical /reading/[slug]. Reading library /reading has bound filters, paging, empty state and separate availability errors. Public view uses escaped paragraph and heading components. Exercise cards show eligible metadata and the disabled sample area remains absent from the DOM and network payload until TECH-06. Once approved, use a dedicated answer-free sample component and sign-in action; no submit or answer reveal in M2/M3 discovery.

Server-render from a single safe public DTO snapshot per response. Hydrate only documented fields. Admin forms may use client components for edit state and delegated same-origin fetch; private prefetch and shared caching are disabled. Reuse display-only typography components across preview and public screens without sharing privileged data loaders.
