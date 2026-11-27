-- Isolated test contract, loaded only by test-m2-staff-auth.ps1 -IncludeCatalog.
-- Provisioner and role grants here are confined to the test container.
GRANT USAGE ON SCHEMA catalog_staff TO content_catalog_reader_owner, content_catalog_writer_owner,
    content_staff_reader, content_author_writer;
GRANT USAGE ON SCHEMA identity, content_catalog, account_fence_routines
    TO content_catalog_reader_owner, content_catalog_writer_owner;
GRANT EXECUTE ON FUNCTION account_fence_routines."lockAndCheckActor"(uuid,bigint)
    TO content_catalog_reader_owner, content_catalog_writer_owner;
GRANT SELECT (id, status, security_revision) ON identity.users
    TO content_catalog_reader_owner, content_catalog_writer_owner;
GRANT SELECT (user_id, role_id) ON identity.user_roles
    TO content_catalog_reader_owner, content_catalog_writer_owner;
GRANT SELECT (id, code) ON identity.roles
    TO content_catalog_reader_owner, content_catalog_writer_owner;
GRANT SELECT (grantee_id, capability, scope, revoked_at) ON content_catalog.content_capability_grants
    TO content_catalog_reader_owner, content_catalog_writer_owner;
GRANT USAGE ON TYPE content_catalog.catalog_status, content_catalog.capability
    TO content_catalog_reader_owner, content_catalog_writer_owner;
GRANT USAGE ON TYPE content_catalog.catalog_action, content_catalog.catalog_entity TO content_catalog_writer_owner;
GRANT INSERT (actor_id, operation, entity_kind, course_id, level_id, lesson_id, revision, request_id)
    ON content_catalog.content_audit_events TO content_catalog_writer_owner;

GRANT USAGE ON TYPE catalog_staff.item, catalog_staff.page_item TO content_catalog_reader_owner, content_staff_reader;
GRANT USAGE ON TYPE catalog_staff.item TO content_catalog_writer_owner, content_author_writer;
GRANT EXECUTE ON FUNCTION catalog_staff.require_access(uuid,bigint,boolean), catalog_staff.items(text)
    TO content_catalog_reader_owner, content_catalog_writer_owner;

GRANT SELECT (id, slug, title, description, display_order, status, revision, created_at, updated_at)
    ON content_catalog.course TO content_catalog_reader_owner, content_catalog_writer_owner;
GRANT SELECT (id, course_id, code, name, description, display_order, status, revision, created_at, updated_at)
    ON content_catalog.course_level TO content_catalog_reader_owner, content_catalog_writer_owner;
GRANT SELECT (id, level_id, slug, title, description, display_order, status, revision, created_at, updated_at)
    ON content_catalog.reading_lesson TO content_catalog_reader_owner, content_catalog_writer_owner;
GRANT INSERT (slug,title,description,display_order), UPDATE (slug,title,description,display_order,status,revision,updated_at)
    ON content_catalog.course TO content_catalog_writer_owner;
GRANT INSERT (course_id,code,name,description,display_order), UPDATE (code,name,description,display_order,status,revision,updated_at)
    ON content_catalog.course_level TO content_catalog_writer_owner;
GRANT INSERT (level_id,slug,title,description,display_order), UPDATE (slug,title,description,display_order,status,revision,updated_at)
    ON content_catalog.reading_lesson TO content_catalog_writer_owner;

ALTER FUNCTION catalog_staff.get_item(text,uuid,bigint,uuid) OWNER TO content_catalog_reader_owner;
ALTER FUNCTION catalog_staff.list_items(text,uuid,bigint,uuid,integer,integer,text,text) OWNER TO content_catalog_reader_owner;
ALTER FUNCTION catalog_staff.mutate_item(text,text,uuid,bigint,uuid,text,text,text,integer,text,bigint,uuid)
    OWNER TO content_catalog_writer_owner;
GRANT EXECUTE ON FUNCTION catalog_staff.get_item(text,uuid,bigint,uuid),
    catalog_staff.list_items(text,uuid,bigint,uuid,integer,integer,text,text) TO content_staff_reader;
GRANT EXECUTE ON FUNCTION catalog_staff.mutate_item(text,text,uuid,bigint,uuid,text,text,text,integer,text,bigint,uuid)
    TO content_author_writer;
