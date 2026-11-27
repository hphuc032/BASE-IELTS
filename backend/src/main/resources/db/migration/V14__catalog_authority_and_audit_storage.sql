-- Storage only. G02 provisioning, fencing routines and runtime grants are separate.
CREATE TYPE content_catalog.capability AS ENUM ('CONTENT_EDIT', 'CONTENT_PUBLISH');
CREATE TYPE content_catalog.catalog_action AS ENUM ('CREATE', 'EDIT', 'CHANGE_STATUS');
CREATE TYPE content_catalog.catalog_entity AS ENUM ('COURSE', 'LEVEL', 'LESSON');
REVOKE ALL ON TYPE content_catalog.capability, content_catalog.catalog_action, content_catalog.catalog_entity FROM PUBLIC;

CREATE TABLE content_catalog.content_capability_grants (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    grantee_id uuid NOT NULL REFERENCES identity.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    grantor_id uuid NOT NULL REFERENCES identity.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    scope text NOT NULL DEFAULT 'GLOBAL' CHECK (scope = 'GLOBAL'),
    capability content_catalog.capability NOT NULL,
    granted_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    revoked_at timestamptz,
    revoked_by uuid REFERENCES identity.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CHECK (grantor_id <> grantee_id),
    CHECK ((revoked_at IS NULL) = (revoked_by IS NULL)),
    CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);

CREATE UNIQUE INDEX content_capability_active_idx
    ON content_catalog.content_capability_grants (grantee_id, scope, capability) WHERE revoked_at IS NULL;
CREATE INDEX capability_grantee_history_idx
    ON content_catalog.content_capability_grants (grantee_id, granted_at, id);
CREATE INDEX capability_grantor_idx ON content_catalog.content_capability_grants (grantor_id);
CREATE INDEX capability_revoker_idx ON content_catalog.content_capability_grants (revoked_by) WHERE revoked_by IS NOT NULL;

CREATE TABLE content_catalog.content_audit_events (
    event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id uuid NOT NULL REFERENCES identity.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    operation content_catalog.catalog_action NOT NULL,
    entity_kind content_catalog.catalog_entity NOT NULL,
    course_id uuid REFERENCES content_catalog.course(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    level_id uuid REFERENCES content_catalog.course_level(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    lesson_id uuid REFERENCES content_catalog.reading_lesson(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    revision bigint NOT NULL CHECK (revision > 0),
    request_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CHECK (
        (entity_kind = 'COURSE' AND course_id IS NOT NULL AND level_id IS NULL AND lesson_id IS NULL)
        OR (entity_kind = 'LEVEL' AND course_id IS NULL AND level_id IS NOT NULL AND lesson_id IS NULL)
        OR (entity_kind = 'LESSON' AND course_id IS NULL AND level_id IS NULL AND lesson_id IS NOT NULL)
    )
);

CREATE INDEX catalog_audit_actor_idx ON content_catalog.content_audit_events (actor_id);
CREATE INDEX catalog_audit_course_idx ON content_catalog.content_audit_events (course_id, occurred_at, event_id) WHERE course_id IS NOT NULL;
CREATE INDEX catalog_audit_level_idx ON content_catalog.content_audit_events (level_id, occurred_at, event_id) WHERE level_id IS NOT NULL;
CREATE INDEX catalog_audit_lesson_idx ON content_catalog.content_audit_events (lesson_id, occurred_at, event_id) WHERE lesson_id IS NOT NULL;

REVOKE ALL ON content_catalog.content_capability_grants, content_catalog.content_audit_events FROM PUBLIC;
