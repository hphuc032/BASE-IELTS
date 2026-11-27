-- Fixture only. The migration itself does not provision roles or runtime grants.
GRANT USAGE ON SCHEMA reading_staff TO content_reading_reader_owner, content_reading_writer_owner,
    content_staff_reader, content_author_writer;
GRANT USAGE ON SCHEMA identity, content_reading, content_catalog, account_fence_routines
    TO content_reading_reader_owner, content_reading_writer_owner;
GRANT USAGE ON SCHEMA account_fence_routines TO content_staff_reader, content_author_writer;
GRANT EXECUTE ON FUNCTION account_fence_routines."lockAndCheckActor"(uuid,bigint)
    TO content_reading_reader_owner, content_reading_writer_owner, content_staff_reader, content_author_writer;
GRANT SELECT (user_id, role_id) ON identity.user_roles TO content_reading_reader_owner, content_reading_writer_owner;
GRANT SELECT (id, code) ON identity.roles TO content_reading_reader_owner, content_reading_writer_owner;
GRANT SELECT (grantee_id, capability, scope, revoked_at) ON content_catalog.content_capability_grants
    TO content_reading_reader_owner, content_reading_writer_owner;
GRANT USAGE ON TYPE content_catalog.capability, content_reading.version_state, content_reading.visibility
    TO content_reading_reader_owner, content_reading_writer_owner;
GRANT USAGE ON TYPE content_catalog.catalog_action, content_catalog.catalog_entity TO content_reading_writer_owner;

GRANT SELECT (id, slug, current_version_id, archived_at) ON content_reading.passages
    TO content_reading_reader_owner, content_reading_writer_owner;
GRANT SELECT (id, passage_id, version_no, revision, state, title, summary, instructions, topic_id, visibility)
    ON content_reading.passage_versions TO content_reading_reader_owner, content_reading_writer_owner;
GRANT SELECT (id, passage_version_id, position, heading, body) ON content_reading.paragraphs
    TO content_reading_reader_owner, content_reading_writer_owner;
GRANT SELECT (paragraph_id, language, text) ON content_reading.translations
    TO content_reading_reader_owner, content_reading_writer_owner;
GRANT SELECT (id) ON content_reading.topics TO content_reading_writer_owner;
GRANT SELECT (id), UPDATE (revision) ON content_catalog.course TO content_reading_writer_owner;
GRANT SELECT (id, course_id), UPDATE (revision) ON content_catalog.course_level TO content_reading_writer_owner;
GRANT SELECT (id, level_id), UPDATE (revision) ON content_catalog.reading_lesson TO content_reading_writer_owner;
GRANT SELECT (passage_id, lesson_id, revision) ON content_reading.placement TO content_reading_writer_owner;
GRANT INSERT (slug, author_id), UPDATE (id) ON content_reading.passages TO content_reading_writer_owner;
GRANT INSERT (passage_id, version_no, title, summary, instructions, topic_id, visibility, base_version_id),
    UPDATE (revision, title, summary, instructions, topic_id, visibility)
    ON content_reading.passage_versions TO content_reading_writer_owner;
GRANT INSERT (id, passage_version_id, position, heading, body), UPDATE (position, heading, body), DELETE
    ON content_reading.paragraphs TO content_reading_writer_owner;
GRANT INSERT (paragraph_id, language, text), UPDATE (text), DELETE ON content_reading.translations
    TO content_reading_writer_owner;
GRANT INSERT (passage_id, lesson_id, position), UPDATE (lesson_id, position, revision)
    ON content_reading.placement TO content_reading_writer_owner;
GRANT INSERT (actor_id, operation, entity_kind, passage_id, passage_version_id, placement_passage_id, revision, request_id)
    ON content_catalog.content_audit_events TO content_reading_writer_owner;

GRANT EXECUTE ON FUNCTION reading_staff.require_access(uuid,bigint,boolean),
    reading_staff.materialize_edition(uuid,uuid,boolean,boolean)
    TO content_reading_reader_owner, content_reading_writer_owner;
GRANT EXECUTE ON FUNCTION reading_staff.require_revision(bigint,bigint),
    reading_staff.valid_text(jsonb,integer,boolean,boolean), reading_staff.valid_paragraphs(jsonb),
    reading_staff.replace_paragraphs(uuid,jsonb) TO content_reading_writer_owner;
ALTER FUNCTION reading_staff.check_reader(uuid,bigint) OWNER TO content_reading_reader_owner;
ALTER FUNCTION reading_staff.check_writer(uuid,bigint) OWNER TO content_reading_writer_owner;
ALTER FUNCTION reading_staff.get_edition(uuid,bigint,uuid,uuid,boolean) OWNER TO content_reading_reader_owner;
ALTER FUNCTION reading_staff.get_preview(uuid,bigint,uuid,uuid) OWNER TO content_reading_reader_owner;
ALTER FUNCTION reading_staff.list_passages(uuid,bigint,integer,integer,text,uuid,text) OWNER TO content_reading_reader_owner;
ALTER FUNCTION reading_staff.mutate_passage(text,uuid,bigint,uuid,uuid,jsonb,bigint,uuid)
    OWNER TO content_reading_writer_owner;
ALTER FUNCTION reading_staff.set_placement(uuid,bigint,uuid,uuid,integer,bigint,uuid)
    OWNER TO content_reading_writer_owner;
GRANT EXECUTE ON FUNCTION reading_staff.check_access(uuid,bigint,boolean) TO content_staff_reader, content_author_writer;
GRANT EXECUTE ON FUNCTION reading_staff.check_reader(uuid,bigint), reading_staff.get_edition(uuid,bigint,uuid,uuid,boolean),
    reading_staff.get_preview(uuid,bigint,uuid,uuid), reading_staff.list_passages(uuid,bigint,integer,integer,text,uuid,text)
    TO content_staff_reader;
GRANT EXECUTE ON FUNCTION reading_staff.check_writer(uuid,bigint),
    reading_staff.mutate_passage(text,uuid,bigint,uuid,uuid,jsonb,bigint,uuid),
    reading_staff.set_placement(uuid,bigint,uuid,uuid,integer,bigint,uuid) TO content_author_writer;
