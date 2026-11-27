# AGENTS.md --- BASE IELTS Learning Platform

> Repository-wide instructions for Codex, Antigravity, Claude Code, and
> other coding agents.
>
> **Repository:** `D:/BASE-IELTS`\
> **Architecture baseline:** Approved Milestone 0 architecture v1.6\
> **Current milestone:** M1 --- Identity Foundation\
> **Current status:** M1.8 is not complete until all required acceptance
> gates pass.\
> **Product scope:** Identity-only M1. Do not start M2 without separate
> authorization.

## 1. Mission and operating principles

Build and maintain BASE IELTS as a secure, maintainable IELTS learning
platform.

Priorities: 1. Preserve approved product and security requirements. 2.
Protect accounts, credentials, sessions, audit records, and database
integrity. 3. Make small, reviewable, test-backed changes. 4. Prefer the
simplest maintainable solution within the approved architecture. 5. Keep
documentation and implementation evidence accurate. 6. Avoid unnecessary
redesign, infrastructure, dependencies, and documentation churn.

Never claim a feature, test, migration, security property, or release is
complete unless it was actually implemented or executed and evidence
exists.

## 2. Source of truth and document authority

Before changing code, inspect the repository and relevant authoritative
documents.

The approved architecture baseline is **Milestone 0 v1.6**, subject to
current authority records:

-   `docs/DOCUMENT-AUTHORITY.md`
-   `docs/MILESTONE-0-FINAL-v1.6.md`
-   `docs/MILESTONE-0-v1.6-MANIFEST.md`
-   `docs/DECISION-REGISTER.md`
-   `docs/ADR-REGISTER.md`
-   `docs/MILESTONE-0-TRACEABILITY.md`
-   `docs/MILESTONE-1-IMPLEMENTATION-PLAN.md`
-   `docs/MILESTONE-1-PROGRESS.md`

Read `docs/MILESTONE-1-PROGRESS.md` first for current M1 status. Check
document authority before treating any acceptance report or later
document as authoritative.

Rules: - Preserve approved architecture v1.6 and its manifest. -
Preserve D01--D11 exactly. - Treat explicit Product Owner decisions in
project records as binding. - A recommendation, draft, candidate, or
proposed ADR is not approved unless the authority records say so. -
Never fabricate historical reviews, hashes, approvals, test output, or
provenance. - Preserve historical artifacts and hashes; do not rewrite
them to hide discrepancies. - If authoritative documents conflict,
identify the exact conflict and propose the smallest resolution. Do not
silently change policy.

## 3. Product decisions and confirmed constraints

Preserve these approved decisions:

-   **D01 --- Authentication:** secure cookie and server-managed
    session.
-   **D02 --- Public Reading:** published PUBLIC Reading can be read
    without login.
-   **D03 --- Reading question types:** MCQ Single; True/False/Not
    Given; Yes/No/Not Given; Short Answer; Sentence Completion.
-   **D04 --- Modes:** every MVP exercise supports Learning and Exam
    modes.
-   **D05 --- Retakes:** unlimited independent public-practice attempts,
    subject to approved authorization rules.
-   **D06 --- Session lifetime:** 60-minute idle timeout and 7-day
    absolute lifetime.
-   **D07 --- Multi-device:** one active authenticated session per
    account.
-   **D08 --- Registration:** account remains PENDING until email
    verification.
-   **D09 --- Learning feedback:** configurable per exercise;
    implementation is deferred to its approved milestone.
-   **D10 --- Public exercise preview:** sample questions without
    answers; implementation is deferred to its approved milestone.
-   **D11 --- Admin bootstrap:** seeded/provisioned from deployment
    environment configuration.

Other confirmed Product Owner choices: - M1 email verification uses a
development/test adapter. No production SMTP or external paid email
provider is enabled. - Local PostgreSQL uses Docker Compose. - No hard
deletion of user accounts in M1. - Terminal verification tokens are
retained for at least the approved minimum of seven days, using the
exact approved terminal-state and retention contract. - Audit records
are retained; production retention policy remains separately gated. -
Development Admin bootstrap uses environment configuration. - Operator
and approver identities are distinct; development/test fixtures may be
used for their test identities. - E-01 reconstructed-review limitation
and E-02 historical digest discrepancies were accepted as disclosed
evidence limitations. Never represent them as recovered or resolved
historical facts. - E-03 promoted the manifested v1.6 candidate to the
approved baseline. Verify the manifest and authority record rather than
relying only on this summary.

## 4. Current scope: M1 Identity Foundation only

M1 includes: 1. M1.1 --- project and environment foundation. 2. M1.2 ---
identity database schema and Flyway migrations. 3. M1.3 --- PostgreSQL
privileges, governance routines, and account fencing. 4. M1.4 ---
registration and development/test email verification. 5. M1.5 ---
authentication, sessions, CSRF, and PREAUTH admission. 6. M1.6 --- Admin
bootstrap, recovery, and governance. 7. M1.7 --- minimal Next.js
authentication UI. 8. M1.8 --- integration, security, concurrency,
HTTPS, and browser acceptance.

Do not implement M2 or later functionality without separate Product
Owner authorization.

Explicitly out of scope for M1: - Content Management and authoring
workflows. - Reading passages, libraries, or question banks. - Public
exercise sample functionality. - Learning and Exam attempts. - Grading
and the Assessment expiry worker. - Vocabulary and progress tracking. -
Listening. - AI integrations. - Payments. - Production deployment and
production email enablement.

The Assessment expiry-worker scan/claim design remains an open M4
implementation gate. Keep it disabled and unimplemented in M1.

## 5. Security requirements

Security is part of implementation, not an optional enhancement.

### Authentication and sessions

-   Follow approved Spring Security and v1.6 session contracts.
-   Use secure, HttpOnly cookies with approved SameSite, Path, Domain,
    and Secure behavior.
-   Enforce CSRF on state-changing browser requests.
-   Keep PREAUTH and AUTH namespaces isolated.
-   Enforce one active authenticated session per account.
-   Enforce 60-minute idle and 7-day absolute session limits.
-   Preserve session generation and security-revision fencing.
-   A stale session must not regain authority after password reset,
    recovery, disablement, or another committed security change.
-   A stale logout must never revoke a newer session.
-   Fail closed for protected operations when authoritative session or
    database validation is unavailable.
-   Never log passwords, raw session identifiers, verification tokens,
    recovery tokens, or secrets.

### Passwords and verification tokens

-   Use the approved adaptive password hashing algorithm and parameters;
    do not invent or weaken policy.
-   Never store plaintext passwords or raw verification tokens.
-   Use cryptographically secure random tokens and store only the
    approved digest representation.
-   Enforce token expiry, single-use semantics, resend controls,
    retention, and atomic state transitions.
-   Account activation must follow the approved explicit verification
    flow; opening a URL alone must not activate an account.
-   Do not enable production registration or resend when production
    email delivery is not configured and approved.

### Authorization and Admin governance

-   Apply least privilege in the application and PostgreSQL.
-   Never allow a public learner to grant themselves ADMIN.
-   Preserve operator/approver separation and the last-usable-Admin
    invariant.
-   Recovery approval, cancellation, consumption, and account
    disablement must follow approved transaction and lock-order
    contracts.
-   Audit actor attribution must use the approved trusted database
    identity boundary, not caller-supplied identity claims.
-   Audit records must not be editable or deletable by ordinary runtime
    roles.

### PREAUTH and resource bounds

-   Enforce the approved atomic global PREAUTH capacity and admission
    behavior.
-   Bound rate-limit metadata, allocation concurrency, cleanup work, and
    queues.
-   Do not evict AUTH sessions to admit PREAUTH sessions.
-   Preserve authenticated-session capacity and responsiveness under
    anonymous bootstrap overload.
-   Do not increase approved limits merely to make a flaky test pass.
    Investigate and obtain approval for material policy changes.

### HTTPS and proxy trust

-   Trust forwarded headers only from explicitly trusted ingress
    proxies.
-   Reject Host-header and forwarded-header spoofing.
-   Verify actual Set-Cookie headers and browser behavior, not
    configuration alone.
-   Never expose authenticated session cookies over insecure HTTP.
-   Test process shutdown and cutover against the approved
    single-serving-process contract.

## 6. Database and migration rules

-   PostgreSQL is the authoritative database for M1.
-   Use Flyway for schema evolution.
-   Migrations are forward-only. Never edit an already-applied migration
    to repair a database; add a new migration.
-   Test migrations against a clean database and an existing database at
    the previous migration version.
-   Preserve migration checksums and history.
-   Use real PostgreSQL for PostgreSQL-specific privileges, row locks,
    transaction isolation, `SECURITY DEFINER`, and concurrency tests. H2
    or mocks are not substitutes.
-   Preserve approved database role separation and least privilege.
-   Use schema-qualified references and a safe fixed `search_path` in
    privileged routines.
-   Revoke unintended `PUBLIC` execution and schema creation privileges.
-   Do not grant broad table or schema privileges as a workaround.
-   Preserve the approved database-before-JVM account-fencing lock
    order.
-   Do not use destructive SQL, `DROP`, `TRUNCATE`, database reset, or
    volume deletion unless explicitly authorized.
-   Do not delete or recreate existing Docker volumes. Back up and
    obtain explicit approval before destructive data operations.
-   Never put production credentials in source code, migrations, Compose
    files, fixtures, or documentation.

## 7. Repository and secret safety

Before editing: 1. Run `git status --short --branch`. 2. Record branch
and HEAD. 3. Inspect relevant files and existing user changes. 4.
Identify generated files and local-only configuration. 5. Make the
smallest scoped change.

Protect: - `.env` files and secret stores. - Passwords, API keys, access
tokens, private keys, certificates. - Database dumps and user data. -
Existing Docker volumes. - Historical architecture and review
artifacts. - Unrelated user modifications.

Keep `.env.example` limited to safe placeholders. Ensure real `.env`
files, credentials, local databases, build output, and test artifacts
are ignored where appropriate.

Never stage or commit secrets. If a secret is found in tracked history,
report the exposure and recommend rotation and an approved
history-remediation process. Do not simply delete it in a new commit and
claim the exposure is fixed.

Do not overwrite unrelated changes or use destructive Git commands such
as `reset --hard`, `clean -fd`, or forced checkout.

## 8. Engineering workflow

For each task: 1. Inspect implementation and tests. 2. Read the
applicable architecture and acceptance contract. 3. State intended scope
briefly. 4. Implement the smallest coherent change. 5. Add or update
focused tests. 6. Run relevant tests. 7. Run broader regression tests
when shared security or persistence behavior changes. 8. Inspect the
complete diff. 9. Update progress documentation with actual evidence.
10. Report what changed, what ran, and what remains.

Prefer deterministic concurrency tests using barriers/latches and
independent real database connections. Avoid timing-only sleeps as proof
of serialization.

For database-disconnect and uncertain-commit scenarios, distinguish
confirmed commit, confirmed rollback, and unknown outcome. A network
failure does not prove a transaction rolled back.

For flaky tests: - Preserve failure evidence. - Identify the failing
resource, lock, connection, or timing boundary. - Reproduce with a
controlled test. - Fix the underlying cause. - Do not silently retry
until green or relax assertions without justification.

Never claim a test passed unless it actually executed successfully.
Record commands, exit codes, test counts, failures, errors, and skipped
tests where available.

## 9. Toolchain and dependency management

Follow versions approved in current preflight and architecture records.

-   Verify exact versions and compatibility from authoritative release
    documentation or package metadata.
-   Do not blindly use versions copied from previous AI-generated
    summaries.
-   Use Maven Wrapper for backend builds.
-   Use the repository's declared Node package manager and lockfile.
-   Do not casually regenerate or delete lockfiles.
-   Do not introduce dependencies without a clear need.
-   Do not add Redis, Kafka, RabbitMQ, Kubernetes, microservices, or
    other infrastructure without an approved requirement.
-   Keep development, test, and production configuration separate.
-   If package or Docker registry access fails, report the exact error.
    Do not fabricate successful dependency resolution or image digests.

## 10. Testing and acceptance

Run tests appropriate to each change. M1 acceptance must cover:

-   Clean and upgrade Flyway migrations.
-   PostgreSQL role and routine privileges.
-   Account status and credential-state constraints.
-   Registration and verification token lifecycle.
-   Authentication and authorization.
-   CSRF and cookie attributes.
-   Idle and absolute session expiry.
-   Single-session and stale-session behavior.
-   Account fencing and revision changes.
-   PREAUTH capacity, overload, and AUTH isolation.
-   Admin bootstrap, recovery, cancellation, and last-Admin protection.
-   Token cleanup and retention.
-   Cross-process concurrency.
-   Lost HTTP response after durable commit.
-   HTTPS ingress and trusted proxy behavior.
-   HTTP cookie exposure.
-   In-flight request behavior during shutdown and process cutover.
-   Frontend lint, production build, and browser acceptance.

Do not declare M1 complete while a required M1.8 gate is unexecuted or
failing. Do not begin M2 until M1 acceptance is complete and M2 has
separate Product Owner authorization.

## 11. Git commit and push policy

The Product Owner conditionally authorized commit and push for M1: -
Commit and push are permitted only after every required M1 acceptance
gate passes. - Before release, inspect the full diff, staged and
unstaged files, untracked files, and secret exposure. - Stage only
intended M1 source, tests, migrations, and relevant documentation. -
Never include unrelated user changes or secrets. - Never force-push. -
Never amend or rewrite unrelated history. - Push only to the verified
configured upstream. - Verify the remote branch and resulting remote
HEAD after push. - If any required test is failing or unexecuted, do not
stage, commit, or push. - If upstream is missing or ambiguous, ask the
Product Owner rather than guessing.

A successful build alone does not authorize release.

## 12. Documentation and reporting

Keep `docs/MILESTONE-1-PROGRESS.md` accurate during implementation.

Create `docs/MILESTONE-1-ACCEPTANCE-REPORT.md` only when M1 acceptance
is genuinely complete.

The report must include: - Approved architecture baseline and digest. -
Completed M1.1--M1.8 work packages. - Actual toolchain versions. -
Migrations and database evidence. - Implemented endpoints and frontend
routes. - Exact commands and test results. - Failed and skipped tests. -
Concurrency, lost-response, HTTPS, and shutdown evidence. - Known
limitations and production-readiness gates. - Local setup, startup, and
shutdown instructions. - Git commit and push evidence, if released.

Preserve historical evidence and clearly label limitations.

## 13. Decision and escalation policy

Do not ask the Product Owner about routine, reversible implementation
details. Choose the simplest secure option within the approved
architecture and document it.

Escalate only when a decision materially affects security, privacy,
product behavior, data retention or destructive operations, financial
cost, major architecture, or an unresolved approval gate.

When escalating, provide concise options, trade-offs, and a
recommendation. Do not silently change approved requirements.

## 14. Final status discipline

Use only evidence-backed status statements:

-   **M1 COMPLETE --- COMMITTED AND PUSHED** only after all M1
    acceptance gates pass and remote verification succeeds.
-   **M1 COMPLETE --- COMMITTED, PUSH BLOCKED** only when acceptance
    passes and a verified upstream configuration issue prevents push.
-   **M1 NOT COMPLETE --- BLOCKERS REMAIN** whenever a required gate is
    failing, skipped, or unverified.

Do not start M2 or production deployment without separate authorization.
