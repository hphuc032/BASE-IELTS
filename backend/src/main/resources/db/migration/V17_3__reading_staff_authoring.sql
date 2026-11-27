-- V17.3: scoped staff Passage authoring. Runtime roles and grants are deployment-gated.
CREATE SCHEMA reading_staff;
REVOKE ALL ON SCHEMA reading_staff FROM PUBLIC;

CREATE FUNCTION reading_staff.require_access(actor uuid, expected bigint, editing boolean) RETURNS void
LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF editing IS NULL OR
       (editing AND (session_user <> 'content_author_writer' OR current_user <> 'content_reading_writer_owner')) OR
       (NOT editing AND (session_user <> 'content_staff_reader' OR current_user <> 'content_reading_reader_owner')) THEN
        RAISE EXCEPTION 'Reading role denied' USING ERRCODE = 'RD002';
    END IF;
    IF NOT coalesce(account_fence_routines."lockAndCheckActor"(actor, expected), false) THEN
        RAISE EXCEPTION 'Reading session inactive' USING ERRCODE = 'RD008';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM identity.user_roles ur JOIN identity.roles r ON r.id = ur.role_id
                   WHERE ur.user_id = actor AND r.code = 'ADMIN')
       OR NOT EXISTS (SELECT 1 FROM content_catalog.content_capability_grants g
                      WHERE g.grantee_id = actor AND g.scope = 'GLOBAL' AND g.revoked_at IS NULL
                        AND (g.capability = 'CONTENT_EDIT' OR (NOT editing AND g.capability = 'CONTENT_PUBLISH'))) THEN
        RAISE EXCEPTION 'Reading capability denied' USING ERRCODE = 'RD002';
    END IF;
END;
$$;

CREATE FUNCTION reading_staff.check_access(actor uuid, expected bigint, editing boolean) RETURNS boolean
LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    -- The selected definer independently enforces the exact login.
    IF editing IS NULL THEN
        RAISE EXCEPTION 'Reading role denied' USING ERRCODE = 'RD002';
    END IF;
    IF editing THEN
        RETURN reading_staff.check_writer(actor, expected);
    END IF;
    RETURN reading_staff.check_reader(actor, expected);
END;
$$;

CREATE FUNCTION reading_staff.check_reader(actor uuid, expected bigint) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    PERFORM reading_staff.require_access(actor, expected, false);
    RETURN true;
END;
$$;

CREATE FUNCTION reading_staff.check_writer(actor uuid, expected bigint) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    PERFORM reading_staff.require_access(actor, expected, true);
    RETURN true;
END;
$$;

-- Internal, invoker-only projection. Exact family and edition selection happens here.
CREATE FUNCTION reading_staff.materialize_edition(passage uuid, edition uuid, draft_only boolean, preview boolean)
RETURNS jsonb LANGUAGE plpgsql STABLE SET search_path = pg_catalog, pg_temp AS $$
DECLARE result jsonb;
BEGIN
    SELECT CASE WHEN preview THEN jsonb_build_object('revision', v.revision, 'preview',
                    jsonb_build_object('draft', true, 'title', v.title, 'summary', v.summary,
                                       'paragraphs', fields.paragraphs))
                ELSE jsonb_build_object(
                    'stableId', p.id, 'editionId', v.id, 'versionNumber', v.version_no, 'revision', v.revision,
                    'state', v.state, 'slug', p.slug, 'currentVersionId', p.current_version_id,
                    'title', v.title, 'summary', v.summary, 'instructions', v.instructions,
                    'topicId', v.topic_id, 'visibility', v.visibility, 'paragraphs', fields.paragraphs)
                END
      INTO result
      FROM content_reading.passages p
      JOIN content_reading.passage_versions v ON v.passage_id = p.id
      CROSS JOIN LATERAL (
        SELECT coalesce(jsonb_agg(
            CASE WHEN preview THEN jsonb_build_object('heading', b.heading, 'body', b.body, 'translations', t.items)
                 ELSE jsonb_build_object('paragraphId', b.id, 'content', b.body, 'position', b.position,
                                         'heading', b.heading, 'translations', t.items) END ORDER BY b.position
        ), '[]'::jsonb) AS paragraphs
        FROM content_reading.paragraphs b
        CROSS JOIN LATERAL (
            SELECT coalesce(jsonb_agg(jsonb_build_object('language', x.language, 'text', x.text) ORDER BY x.language),
                            '[]'::jsonb) AS items
            FROM content_reading.translations x WHERE x.paragraph_id = b.id
        ) t
        WHERE b.passage_version_id = v.id
      ) fields
      WHERE p.id = passage AND v.id = edition AND (NOT draft_only OR v.state = 'DRAFT');
    IF result IS NULL THEN
        RAISE EXCEPTION 'Reading edition unavailable' USING ERRCODE = 'RD003';
    END IF;
    IF octet_length(result::text) > 786432 THEN
        RAISE EXCEPTION 'Reading graph exceeds response limit' USING ERRCODE = 'RD001';
    END IF;
    RETURN result;
END;
$$;

-- Extend the typed shared audit contract without changing the applied Catalog migration.
ALTER TYPE content_catalog.catalog_entity ADD VALUE 'PASSAGE';
ALTER TYPE content_catalog.catalog_entity ADD VALUE 'PLACEMENT';
ALTER TYPE content_catalog.catalog_action ADD VALUE 'CLONE';
ALTER TYPE content_catalog.catalog_action ADD VALUE 'SET_PLACEMENT';

ALTER TABLE content_catalog.content_audit_events
    ADD COLUMN passage_id uuid REFERENCES content_reading.passages(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD COLUMN passage_version_id uuid,
    ADD COLUMN placement_passage_id uuid REFERENCES content_reading.placement(passage_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT reading_audit_edition_fk FOREIGN KEY (passage_id, passage_version_id)
        REFERENCES content_reading.passage_versions(passage_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    DROP CONSTRAINT content_audit_events_check,
    ADD CONSTRAINT content_audit_target_ck CHECK (
       (passage_id IS NULL AND passage_version_id IS NULL AND placement_passage_id IS NULL
        AND operation::text IN ('CREATE','EDIT','CHANGE_STATUS')
        AND ((entity_kind::text = 'COURSE' AND course_id IS NOT NULL AND level_id IS NULL AND lesson_id IS NULL)
          OR (entity_kind::text = 'LEVEL' AND course_id IS NULL AND level_id IS NOT NULL AND lesson_id IS NULL)
          OR (entity_kind::text = 'LESSON' AND course_id IS NULL AND level_id IS NULL AND lesson_id IS NOT NULL)))
       OR
       (course_id IS NULL AND level_id IS NULL AND lesson_id IS NULL AND passage_id IS NOT NULL
        AND ((entity_kind::text = 'PASSAGE' AND operation::text IN ('CREATE','CLONE','EDIT')
              AND passage_version_id IS NOT NULL AND placement_passage_id IS NULL)
          OR (entity_kind::text = 'PLACEMENT' AND operation::text = 'SET_PLACEMENT'
              AND passage_version_id IS NULL AND placement_passage_id IS NOT NULL
              AND placement_passage_id = passage_id)))
    );
CREATE INDEX reading_audit_passage_idx ON content_catalog.content_audit_events
    (passage_id, occurred_at, event_id) WHERE passage_id IS NOT NULL;
CREATE INDEX reading_audit_edition_idx ON content_catalog.content_audit_events
    (passage_id, passage_version_id) WHERE passage_version_id IS NOT NULL;
CREATE INDEX reading_audit_placement_idx ON content_catalog.content_audit_events
    (placement_passage_id) WHERE placement_passage_id IS NOT NULL;

CREATE FUNCTION reading_staff.require_revision(actual bigint, expected bigint) RETURNS void
LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF expected IS NULL THEN
        RAISE EXCEPTION 'Reading precondition required' USING ERRCODE = 'RD005';
    ELSIF expected < 0 THEN
        RAISE EXCEPTION 'Reading precondition invalid' USING ERRCODE = 'RD007';
    ELSIF actual <> expected THEN
        RAISE EXCEPTION 'Reading revision changed' USING ERRCODE = 'RD006';
    END IF;
END;
$$;

CREATE FUNCTION reading_staff.valid_text(input jsonb, maximum integer, required boolean, multiline boolean)
RETURNS boolean LANGUAGE sql IMMUTABLE SET search_path = pg_catalog, pg_temp AS $$
    SELECT CASE
        WHEN input IS NULL OR input = 'null'::jsonb THEN NOT required
        WHEN jsonb_typeof(input) <> 'string' THEN false
        ELSE char_length(input #>> '{}') BETWEEN 1 AND maximum
             AND (input #>> '{}') ~ '[^[:space:]]'
             AND (CASE WHEN multiline THEN translate(input #>> '{}', E'\n\t', '')
                       ELSE input #>> '{}' END) !~ '[[:cntrl:]]'
        END
$$;

-- The controller validates and normalizes the same authoring contract before invoking this command.
CREATE FUNCTION reading_staff.valid_paragraphs(paragraphs jsonb) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE SET search_path = pg_catalog, pg_temp AS $$
DECLARE item jsonb; translation jsonb; language text; translations jsonb;
BEGIN
    IF jsonb_typeof(paragraphs) IS DISTINCT FROM 'array' THEN RETURN false; END IF;
    FOR item IN SELECT value FROM jsonb_array_elements(paragraphs) LOOP
        IF jsonb_typeof(item) IS DISTINCT FROM 'object' THEN RETURN false; END IF;
        IF item - ARRAY['paragraphId','heading','content','translations'] <> '{}'::jsonb
           OR NOT reading_staff.valid_text(item->'heading', 160, false, false)
           OR NOT reading_staff.valid_text(item->'content', 8000, true, true)
           OR (item ? 'paragraphId' AND (jsonb_typeof(item->'paragraphId') IS DISTINCT FROM 'string'
               OR item->>'paragraphId' !~ '^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$'))
           OR (item ? 'translations' AND jsonb_typeof(item->'translations') IS DISTINCT FROM 'array') THEN
            RETURN false;
        END IF;
        translations := coalesce(item->'translations', '[]'::jsonb);
        FOR translation IN SELECT value FROM jsonb_array_elements(translations) LOOP
            IF jsonb_typeof(translation) IS DISTINCT FROM 'object' THEN RETURN false; END IF;
            language := translation->>'language';
            IF translation - ARRAY['language','text'] <> '{}'::jsonb
               OR NOT reading_staff.valid_text(translation->'language', 255, true, false)
               OR language !~ '^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$'
               OR NOT reading_staff.valid_text(translation->'text', 8000, true, true) THEN
                RETURN false;
            END IF;
        END LOOP;
        IF EXISTS (SELECT 1 FROM jsonb_array_elements(translations) x
                   GROUP BY lower(x.value->>'language') HAVING count(*) > 1) THEN RETURN false; END IF;
    END LOOP;
    RETURN true;
END;
$$;

-- Accept only the complete validated list, with server UUIDs already assigned for new rows.
CREATE FUNCTION reading_staff.replace_paragraphs(edition uuid, paragraphs jsonb) RETURNS void
LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE affected bigint;
BEGIN
    SET CONSTRAINTS content_reading.passage_paragraph_position_uk DEFERRED;
    WITH retained AS MATERIALIZED (
        SELECT (b.value->>'paragraphId')::uuid AS id, x.value->>'language' AS language
        FROM jsonb_array_elements(paragraphs) b
        CROSS JOIN LATERAL jsonb_array_elements(coalesce(b.value->'translations', '[]'::jsonb)) x
    )
    DELETE FROM content_reading.translations t USING content_reading.paragraphs p
        WHERE p.passage_version_id = edition AND t.paragraph_id = p.id
          AND NOT EXISTS (SELECT 1 FROM retained r WHERE r.id = p.id AND r.language = t.language);
    DELETE FROM content_reading.paragraphs p WHERE p.passage_version_id = edition AND NOT EXISTS (
        SELECT 1 FROM jsonb_array_elements(paragraphs) b WHERE (b.value->>'paragraphId')::uuid = p.id
    );
    INSERT INTO content_reading.paragraphs AS stored (id, passage_version_id, position, heading, body)
        SELECT (value->>'paragraphId')::uuid, edition, ordinality::integer, value->>'heading', value->>'content'
        FROM jsonb_array_elements(paragraphs) WITH ORDINALITY
        ON CONFLICT (id) DO UPDATE SET position = excluded.position, heading = excluded.heading, body = excluded.body
        WHERE stored.passage_version_id = edition;
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> jsonb_array_length(paragraphs) THEN
        RAISE EXCEPTION 'Passage paragraph unavailable' USING ERRCODE = 'RD003';
    END IF;
    INSERT INTO content_reading.translations AS stored (paragraph_id, language, text)
        SELECT (b.value->>'paragraphId')::uuid, x.value->>'language', x.value->>'text'
        FROM jsonb_array_elements(paragraphs) b
        CROSS JOIN LATERAL jsonb_array_elements(coalesce(b.value->'translations', '[]'::jsonb)) x
        ON CONFLICT (paragraph_id, language) DO UPDATE SET text = excluded.text
        WHERE stored.text IS DISTINCT FROM excluded.text;
    SET CONSTRAINTS content_reading.passage_paragraph_position_uk IMMEDIATE;
END;
$$;

CREATE FUNCTION reading_staff.mutate_passage(action text, actor uuid, expected bigint, passage uuid, edition uuid,
                                              payload jsonb, expected_revision bigint, request_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    source_id uuid;
    result_passage uuid := passage;
    result_edition uuid := edition;
    current_revision bigint;
    current_state text;
    is_archived boolean;
    topic uuid;
    paragraphs jsonb;
    projection jsonb;
BEGIN
    PERFORM reading_staff.require_access(actor, expected, true);
    IF action IS NULL OR action NOT IN ('CREATE','CLONE','EDIT') OR request_id IS NULL
       OR jsonb_typeof(payload) IS DISTINCT FROM 'object' OR octet_length(payload::text) > 524288 THEN
        RAISE EXCEPTION 'Invalid Reading command' USING ERRCODE = 'RD001';
    END IF;
    IF action = 'CREATE' THEN
        IF payload - ARRAY['slug','title'] <> '{}'::jsonb
           OR NOT reading_staff.valid_text(payload->'slug', 80, true, false)
           OR payload->>'slug' !~ '^[a-z0-9]+(-[a-z0-9]+)*$'
           OR NOT reading_staff.valid_text(payload->'title', 160, true, false) THEN
            RAISE EXCEPTION 'Invalid Passage creation' USING ERRCODE = 'RD001';
        END IF;
        INSERT INTO content_reading.passages(slug, author_id) VALUES (payload->>'slug', actor)
            ON CONFLICT (slug) DO NOTHING RETURNING id INTO result_passage;
        IF result_passage IS NULL THEN
            RAISE EXCEPTION 'Passage slug unavailable' USING ERRCODE = 'RD009';
        END IF;
        INSERT INTO content_reading.passage_versions(passage_id, version_no, title, visibility)
            VALUES (result_passage, 1, payload->>'title', 'PRIVATE') RETURNING id, revision INTO result_edition, current_revision;
    ELSE
        SELECT p.archived_at IS NOT NULL INTO is_archived
            FROM content_reading.passages p WHERE p.id = passage FOR NO KEY UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Passage unavailable' USING ERRCODE = 'RD003'; END IF;
        IF action = 'CLONE' THEN
            IF payload - 'baseVersionId' <> '{}'::jsonb OR jsonb_typeof(payload->'baseVersionId') IS DISTINCT FROM 'string'
               OR payload->>'baseVersionId' !~ '^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$' THEN
                RAISE EXCEPTION 'Invalid Passage base' USING ERRCODE = 'RD001';
            END IF;
            source_id := (payload->>'baseVersionId')::uuid;
            SELECT state::text INTO current_state FROM content_reading.passage_versions
                WHERE id = source_id AND passage_id = passage;
            IF NOT FOUND THEN RAISE EXCEPTION 'Passage edition unavailable' USING ERRCODE = 'RD003'; END IF;
            IF is_archived OR current_state <> 'FROZEN' OR EXISTS (
                SELECT 1 FROM content_reading.passage_versions WHERE passage_id = passage AND state = 'DRAFT'
            ) THEN
                RAISE EXCEPTION 'Cannot clone Passage edition' USING ERRCODE = 'RD004';
            END IF;
            INSERT INTO content_reading.passage_versions
                (passage_id, version_no, title, summary, instructions, topic_id, visibility, base_version_id)
                SELECT passage, (SELECT max(prior.version_no) + 1 FROM content_reading.passage_versions prior
                                 WHERE prior.passage_id = passage), v.title, v.summary, v.instructions,
                       v.topic_id, v.visibility, v.id
                FROM content_reading.passage_versions v WHERE v.id = source_id
                RETURNING id, revision INTO result_edition, current_revision;
            SELECT coalesce(jsonb_agg(jsonb_build_object('paragraphId', gen_random_uuid(), 'content', p.body,
                    'heading', p.heading, 'translations', translations.items) ORDER BY p.position), '[]'::jsonb)
                INTO paragraphs FROM content_reading.paragraphs p
                CROSS JOIN LATERAL (
                    SELECT coalesce(jsonb_agg(jsonb_build_object('language', t.language, 'text', t.text)
                                    ORDER BY t.language), '[]'::jsonb) AS items
                    FROM content_reading.translations t WHERE t.paragraph_id = p.id
                ) translations WHERE p.passage_version_id = source_id;
            PERFORM reading_staff.replace_paragraphs(result_edition, paragraphs);
        ELSE
            IF payload - ARRAY['title','summary','instructions','topicId','visibility','paragraphs'] <> '{}'::jsonb
               OR NOT reading_staff.valid_text(payload->'title', 160, true, false)
               OR NOT reading_staff.valid_text(payload->'summary', 1000, false, true)
               OR NOT reading_staff.valid_text(payload->'instructions', 2000, false, true)
               OR coalesce(payload->>'visibility', '') NOT IN ('PUBLIC','PRIVATE')
               OR (payload ? 'topicId' AND payload->'topicId' <> 'null'::jsonb AND
                   (jsonb_typeof(payload->'topicId') IS DISTINCT FROM 'string'
                    OR payload->>'topicId' !~ '^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$'))
               OR NOT reading_staff.valid_paragraphs(payload->'paragraphs') THEN
                RAISE EXCEPTION 'Invalid Passage edit' USING ERRCODE = 'RD001';
            END IF;
            SELECT revision, state::text INTO current_revision, current_state FROM content_reading.passage_versions
                WHERE id = edition AND passage_id = passage FOR UPDATE;
            IF NOT FOUND THEN RAISE EXCEPTION 'Passage edition unavailable' USING ERRCODE = 'RD003'; END IF;
            topic := (payload->>'topicId')::uuid;
            IF topic IS NOT NULL AND NOT EXISTS (SELECT 1 FROM content_reading.topics t WHERE t.id = topic) THEN
                RAISE EXCEPTION 'Passage topic unavailable' USING ERRCODE = 'RD003';
            END IF;
            paragraphs := payload->'paragraphs';
            IF EXISTS (SELECT 1 FROM jsonb_array_elements(paragraphs) b WHERE b.value ? 'paragraphId'
                       GROUP BY (b.value->>'paragraphId')::uuid HAVING count(*) > 1) THEN
                RAISE EXCEPTION 'Repeated Passage paragraph' USING ERRCODE = 'RD001';
            END IF;
            IF EXISTS (SELECT 1 FROM jsonb_array_elements(paragraphs) b WHERE b.value ? 'paragraphId' AND NOT EXISTS (
                SELECT 1 FROM content_reading.paragraphs p
                WHERE p.passage_version_id = edition AND p.id = (b.value->>'paragraphId')::uuid
            )) THEN
                RAISE EXCEPTION 'Passage paragraph unavailable' USING ERRCODE = 'RD003';
            END IF;
            IF current_state <> 'DRAFT' THEN RAISE EXCEPTION 'Passage edition immutable' USING ERRCODE = 'RD004'; END IF;
            PERFORM reading_staff.require_revision(current_revision, expected_revision);
            UPDATE content_reading.passage_versions SET title = payload->>'title', summary = payload->>'summary',
                instructions = payload->>'instructions', topic_id = topic,
                visibility = (payload->>'visibility')::content_reading.visibility, revision = revision + 1
                WHERE id = edition AND passage_id = passage AND state = 'DRAFT' AND revision = expected_revision
                RETURNING revision INTO current_revision;
            IF NOT FOUND THEN RAISE EXCEPTION 'Reading revision changed' USING ERRCODE = 'RD006'; END IF;
            SELECT coalesce(jsonb_agg(value || jsonb_build_object('paragraphId',
                    coalesce((value->>'paragraphId')::uuid, gen_random_uuid())) ORDER BY ordinality), '[]'::jsonb)
                INTO paragraphs FROM jsonb_array_elements(paragraphs) WITH ORDINALITY;
            PERFORM reading_staff.replace_paragraphs(edition, paragraphs);
        END IF;
    END IF;
    projection := reading_staff.materialize_edition(result_passage, result_edition, true, false);
    INSERT INTO content_catalog.content_audit_events(actor_id, operation, entity_kind, passage_id,
        passage_version_id, revision, request_id)
    VALUES (actor, action::content_catalog.catalog_action, 'PASSAGE', result_passage, result_edition,
            current_revision, request_id);
    RETURN projection;
END;
$$;

CREATE FUNCTION reading_staff.set_placement(actor uuid, expected bigint, passage uuid, lesson uuid,
                                             new_position integer, expected_revision bigint, request_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE current_revision bigint; previous_revision bigint; previous_lesson uuid; locked_lesson uuid; targets uuid[];
BEGIN
    PERFORM reading_staff.require_access(actor, expected, true);
    IF (lesson IS NULL) <> (new_position IS NULL) OR new_position <= 0 OR request_id IS NULL THEN
        RAISE EXCEPTION 'Invalid Reading placement' USING ERRCODE = 'RD001';
    END IF;
    PERFORM 1 FROM content_reading.passages p WHERE p.id = passage;
    IF NOT FOUND OR (lesson IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM content_catalog.reading_lesson l WHERE l.id = lesson)) THEN
        RAISE EXCEPTION 'Reading placement target unavailable' USING ERRCODE = 'RD003';
    END IF;
    SELECT p.lesson_id, p.revision INTO previous_lesson, previous_revision
        FROM content_reading.placement p WHERE p.passage_id = passage;
    targets := ARRAY[previous_lesson, lesson];
    -- Lock both navigation chains, if present, in Catalog hierarchy and UUID order.
    PERFORM c.id FROM content_catalog.course c WHERE EXISTS (
        SELECT 1 FROM content_catalog.course_level v JOIN content_catalog.reading_lesson l ON l.level_id = v.id
        WHERE v.course_id = c.id AND l.id = ANY(targets)
    ) ORDER BY c.id FOR SHARE OF c;
    PERFORM v.id FROM content_catalog.course_level v WHERE EXISTS (
        SELECT 1 FROM content_catalog.reading_lesson l WHERE l.level_id = v.id AND l.id = ANY(targets)
    ) ORDER BY v.id FOR SHARE OF v;
    PERFORM l.id FROM content_catalog.reading_lesson l WHERE l.id = ANY(targets) ORDER BY l.id FOR SHARE OF l;
    SELECT p.revision, p.lesson_id INTO current_revision, locked_lesson FROM content_reading.placement p
        WHERE p.passage_id = passage FOR UPDATE;
    PERFORM reading_staff.require_revision(coalesce(current_revision, 0), expected_revision);
    IF locked_lesson IS DISTINCT FROM previous_lesson OR current_revision IS DISTINCT FROM previous_revision THEN
        RAISE EXCEPTION 'Reading placement changed while locking navigation' USING ERRCODE = 'RD006';
    END IF;
    IF current_revision IS NULL THEN
        INSERT INTO content_reading.placement(passage_id, lesson_id, position)
            VALUES (passage, lesson, new_position) ON CONFLICT (passage_id) DO NOTHING
            RETURNING revision INTO current_revision;
    ELSE
        UPDATE content_reading.placement SET lesson_id = lesson, position = new_position, revision = revision + 1
            WHERE passage_id = passage AND revision = expected_revision RETURNING revision INTO current_revision;
    END IF;
    IF current_revision IS NULL THEN RAISE EXCEPTION 'Reading revision changed' USING ERRCODE = 'RD006'; END IF;
    INSERT INTO content_catalog.content_audit_events(actor_id, operation, entity_kind, passage_id,
        placement_passage_id, revision, request_id)
    VALUES (actor, 'SET_PLACEMENT', 'PLACEMENT', passage, passage, current_revision, request_id);
    RETURN jsonb_build_object('lessonId', lesson, 'position', new_position, 'revision', current_revision);
END;
$$;

CREATE FUNCTION reading_staff.get_edition(actor uuid, expected bigint, passage uuid, edition uuid, draft_only boolean)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    PERFORM reading_staff.require_access(actor, expected, false);
    RETURN reading_staff.materialize_edition(passage, edition, coalesce(draft_only, true), false);
END;
$$;

CREATE FUNCTION reading_staff.get_preview(actor uuid, expected bigint, passage uuid, edition uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    PERFORM reading_staff.require_access(actor, expected, false);
    RETURN reading_staff.materialize_edition(passage, edition, true, true);
END;
$$;

CREATE FUNCTION reading_staff.list_passages(actor uuid, expected bigint, page integer, size integer,
                                            search text, topic uuid, status_filter text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE result jsonb;
BEGIN
    PERFORM reading_staff.require_access(actor, expected, false);
    IF page IS NULL OR page NOT BETWEEN 0 AND 99 OR size IS NULL OR size NOT BETWEEN 1 AND 50 OR
       (search IS NOT NULL AND (char_length(search) NOT BETWEEN 1 AND 100 OR search ~ '[[:cntrl:]]')) OR
       (status_filter IS NOT NULL AND status_filter NOT IN ('DRAFT', 'FROZEN')) THEN
        RAISE EXCEPTION 'Invalid Reading search' USING ERRCODE = 'RD001';
    END IF;
    WITH filtered AS MATERIALIZED (
        SELECT p.id, p.slug, p.current_version_id, v.id AS edition_id, v.title, v.state, v.revision, v.topic_id
          FROM content_reading.passages p
          JOIN LATERAL (
            SELECT d.id, d.title, d.state, d.revision, d.topic_id
              FROM content_reading.passage_versions d
              WHERE d.passage_id = p.id AND (status_filter IS NULL OR d.state::text = status_filter)
              ORDER BY d.version_no DESC LIMIT 1
          ) v ON true
          WHERE p.archived_at IS NULL AND (topic IS NULL OR v.topic_id = topic)
            AND (search IS NULL OR strpos(lower(v.title), lower(search)) > 0
                                OR strpos(lower(p.slug), lower(search)) > 0)
    ), items AS (
        SELECT * FROM filtered ORDER BY slug, id LIMIT size OFFSET page * size
    )
    SELECT jsonb_build_object('data', coalesce((SELECT jsonb_agg(jsonb_build_object(
              'stableId', id, 'editionId', edition_id, 'slug', slug, 'title', title, 'state', state,
              'revision', revision, 'currentVersionId', current_version_id, 'topicId', topic_id)
              ORDER BY slug, id) FROM items), '[]'::jsonb),
              'page', jsonb_build_object('number', page, 'size', size, 'totalElements', total,
                                         'totalPages', (total + size - 1) / size))
      INTO result FROM (SELECT count(*) AS total FROM filtered) counted;
    RETURN result;
END;
$$;

REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA reading_staff FROM PUBLIC;
