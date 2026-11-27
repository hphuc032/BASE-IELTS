# M2 security design

## Authorization matrix

Capabilities below are proposed under TECH-10, granted only after approval. Anonymous and PENDING have the same gated public surface. ACTIVE learner has no authoring rights. Identity Admin bootstrap is not a content grant.

| Flow | Anonymous / learner | Scoped author | Scoped publisher | DB principal |
| --- | --- | --- | --- | --- |
| Current public list/detail after M3 gate | Allow only eligible PUBLIC | Same | Same | content_public_reader |
| Staff catalog view/edit | Deny | Explicit CATALOG_VIEW/EDIT only | Explicit CATALOG_VIEW/EDIT only | Separate scoped staff reader or content_author_writer |
| Draft list/detail/preview | Deny | CONTENT_VIEW, assigned scope | CONTENT_VIEW, assigned scope | content_staff_reader |
| Create/edit draft | Deny | CONTENT_EDIT, assigned scope | Only if separately given CONTENT_EDIT | content_author_writer |
| Freeze, withdraw, archive | Deny | Deny without CONTENT_PUBLISH | CONTENT_PUBLISH, assigned scope | content_publisher_writer |
| Public marking, older versions, audit | Deny | No through public routes | No through public routes | No public role grant |

Candidate P04: first release grants explicitly assigned scoped capabilities only to ACTIVE ADMIN. TEACHER grants and delegation held. Roles and author scopes remain deny-by-default. Proposed capability rows keyed by (user_id, capability, scope stable ID or global catalog scope), privately managed through a separately approved provisioning path. Grant changes take the account fence and advance security_revision where authority changes; content DB revalidates capability membership and revision in the transaction. No arbitrary actor ID from client. M1 runtime currently has account fence EXECUTE but content JDBC roles do not. Add exact EXECUTE on lockAndCheckActor for authorized content pools only; no lockForLogin, credential, recovery or lifecycle grants.

## Least privilege and threats

- Separate migration owner from writer logins and non-login routine owners. Author calls only typed draft/catalog edits. Publisher calls only publish/withdraw/archive. Staff read policy scopes every object/version tuple. Public SELECT only hardened views/projectors with explicit DTO columns; no direct base table, marking, evidence, explanation or audit access and no role assumption. Review effective grants, PUBLIC defaults, fixed search_path, schema ownership and prepared statements. Use writer-role pool same connection for the account fence and business command.
- Keep all answer-bearing content in separate protected tables. Staff rendering, Next hydration, shared caches, server errors, logs, tracing and search never fall back to a generic graph dump. Public queries, bundles and fixtures contain no secret answer values. Limit question exposure to approved fixed samples. Editorial checks also catch accidental answer hints inside visible text.
- Admit the exact new method/path combinations only. Reuse scoped security context and AUTH CSRF, add new admission limits deliberately, keep fallback deny-all. Unauthenticated unsafe calls never allocate a session. CSRF applies to editor, pointer operations and placement. Retain secure cookies, revision checks and proxy trust.
- Reject unknown body fields including authorId, status, revision, frozenAt and pointers. Strict projection and scope filters on both listing and nested resource reads deny IDOR. Use bounded JSON and parameterized criteria; allowlisted sorts and immutable ownership. Render content as escaped text; no arbitrary HTML, script URLs or unsafe Markdown rendering.
- Audit all successful status, grant, edit, publish, withdraw and archive decisions using authenticated trusted actor and opaque request references. Publish audit is atomic. Denial telemetry stores safe code only. Counters use fixed labels, not IDs. No write path purges frozen content. Exceptional takedown and retention beyond v1.6 deferred.
- Ingress/projector returns Cache-Control no-store and no shared SSR caching for author/private content. Public uses no-store per baseline. Sample-bearing pages additionally use noindex and sitemap omission after TECH-06. No content uploads in scope. Independent hostile tests are defined in [test plan](M2-TEST-PLAN.md).
