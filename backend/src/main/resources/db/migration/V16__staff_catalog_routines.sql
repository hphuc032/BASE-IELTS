-- No runtime grants or role creation. Install scoped ownership/EXECUTE during approved deployment.
CREATE SCHEMA catalog_staff;
REVOKE ALL ON SCHEMA catalog_staff FROM PUBLIC;

CREATE TYPE catalog_staff.item AS (
    id uuid, parent_id uuid, item_key text, label text, description text,
    display_order integer, status text, revision bigint, created_at timestamptz, updated_at timestamptz
);
CREATE TYPE catalog_staff.page_item AS (
    id uuid, parent_id uuid, item_key text, label text, description text,
    display_order integer, status text, revision bigint, created_at timestamptz, updated_at timestamptz,
    total_count bigint
);

CREATE FUNCTION catalog_staff.require_access(actor uuid, expected bigint, editing boolean) RETURNS void
LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF (editing AND (session_user <> 'content_author_writer' OR current_user <> 'content_catalog_writer_owner'))
       OR (NOT editing AND (session_user <> 'content_staff_reader' OR current_user <> 'content_catalog_reader_owner')) THEN
        RAISE EXCEPTION 'Catalog role denied' USING ERRCODE = 'CA002';
    END IF;
    IF NOT account_fence_routines."lockAndCheckActor"(actor, expected) THEN
        RAISE EXCEPTION 'Catalog actor no longer authenticated' USING ERRCODE = 'CA008';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM identity.user_roles ur JOIN identity.roles r ON r.id = ur.role_id
                   WHERE ur.user_id = actor AND r.code = 'ADMIN')
       OR NOT EXISTS (SELECT 1 FROM content_catalog.content_capability_grants g
                      WHERE g.grantee_id = actor AND g.scope = 'GLOBAL' AND g.revoked_at IS NULL
                        AND (g.capability = 'CONTENT_EDIT' OR (NOT editing AND g.capability = 'CONTENT_PUBLISH'))) THEN
        RAISE EXCEPTION 'Catalog capability denied' USING ERRCODE = 'CA002';
    END IF;
END;
$$;

-- Invoker helper: the effective routine owner supplies only the table/column grants it needs.
CREATE FUNCTION catalog_staff.items(kind text) RETURNS SETOF catalog_staff.item
LANGUAGE sql STABLE SET search_path = pg_catalog, pg_temp AS $$
    SELECT c.id, NULL::uuid, c.slug, c.title, c.description, c.display_order, c.status::text,
           c.revision, c.created_at, c.updated_at
      FROM content_catalog.course c WHERE kind = 'COURSE'
    UNION ALL
    SELECT l.id, l.course_id, l.code, l.name, l.description, l.display_order, l.status::text,
           l.revision, l.created_at, l.updated_at
      FROM content_catalog.course_level l WHERE kind = 'LEVEL'
    UNION ALL
    SELECT l.id, l.level_id, l.slug, l.title, l.description, l.display_order, l.status::text,
           l.revision, l.created_at, l.updated_at
      FROM content_catalog.reading_lesson l WHERE kind = 'LESSON'
$$;

CREATE FUNCTION catalog_staff.list_items(kind text, actor uuid, security_revision bigint, parent uuid,
                                        page integer, size integer, search text, status_filter text)
RETURNS SETOF catalog_staff.page_item
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    PERFORM catalog_staff.require_access(actor, security_revision, false);
    IF kind IS NULL OR kind NOT IN ('COURSE','LEVEL','LESSON')
       OR ((kind = 'COURSE') <> (parent IS NULL))
       OR page IS NULL OR page NOT BETWEEN 0 AND 99 OR size IS NULL OR size NOT BETWEEN 1 AND 50
       OR (search IS NOT NULL AND (char_length(search) NOT BETWEEN 1 AND 100 OR search ~ '[[:cntrl:]]'))
       OR (status_filter IS NOT NULL AND status_filter NOT IN ('ACTIVE','INACTIVE','ARCHIVED')) THEN
        RAISE EXCEPTION 'Invalid catalog query' USING ERRCODE = 'CA001';
    END IF;
    IF (kind = 'LEVEL' AND NOT EXISTS (SELECT 1 FROM content_catalog.course c WHERE c.id = parent))
       OR (kind = 'LESSON' AND NOT EXISTS (SELECT 1 FROM content_catalog.course_level l WHERE l.id = parent)) THEN
        RAISE EXCEPTION 'Catalog parent not found' USING ERRCODE = 'CA003';
    END IF;
    RETURN QUERY WITH filtered AS MATERIALIZED (
        SELECT * FROM catalog_staff.items(kind) i WHERE i.parent_id IS NOT DISTINCT FROM parent
          AND (status_filter IS NULL OR i.status = status_filter)
          AND (search IS NULL OR strpos(lower(i.label),lower(search)) > 0 OR strpos(lower(i.item_key),lower(search)) > 0)
    ), rows AS (
        SELECT * FROM filtered f ORDER BY f.display_order, f.id OFFSET page * size LIMIT size
    )
    SELECT r.*, t.total FROM (SELECT count(*) AS total FROM filtered) t
    LEFT JOIN rows r ON true ORDER BY r.display_order, r.id;
END;
$$;

CREATE FUNCTION catalog_staff.get_item(kind text, actor uuid, security_revision bigint, target uuid)
RETURNS catalog_staff.item
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE result catalog_staff.item;
BEGIN
    PERFORM catalog_staff.require_access(actor, security_revision, false);
    IF kind IS NULL OR kind NOT IN ('COURSE','LEVEL','LESSON') OR target IS NULL THEN
        RAISE EXCEPTION 'Invalid catalog target' USING ERRCODE = 'CA001';
    END IF;
    SELECT * INTO result FROM catalog_staff.items(kind) i WHERE i.id = target;
    IF NOT FOUND THEN RAISE EXCEPTION 'Catalog target not found' USING ERRCODE = 'CA003'; END IF;
    RETURN result;
END;
$$;

CREATE FUNCTION catalog_staff.mutate_item(kind text, action text, actor uuid, security_revision bigint, target uuid,
    key text, label text, description text, ordering integer, new_status text, expected bigint, request uuid)
RETURNS catalog_staff.item
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    existing catalog_staff.item;
    parent uuid;
    root uuid;
    changed uuid;
BEGIN
    PERFORM catalog_staff.require_access(actor, security_revision, true);
    IF kind IS NULL OR kind NOT IN ('COURSE','LEVEL','LESSON')
       OR action IS NULL OR action NOT IN ('CREATE','EDIT','CHANGE_STATUS') OR request IS NULL
       OR (action = 'CREATE' AND ((kind = 'COURSE') <> (target IS NULL)))
       OR (action <> 'CREATE' AND target IS NULL)
       OR (action IN ('CREATE','EDIT') AND (
            key IS NULL OR key !~ '^[a-z0-9]+(-[a-z0-9]+)*$'
            OR char_length(key) > CASE WHEN kind = 'LEVEL' THEN 32 ELSE 80 END
            OR label IS NULL OR char_length(label) NOT BETWEEN 1 AND 160 OR btrim(label) = ''
            OR label ~ '[[:cntrl:]]' OR ordering IS NULL OR ordering < 1
            OR (description IS NOT NULL AND (char_length(description) NOT BETWEEN 1 AND 2000 OR btrim(description) = ''))))
       OR (action = 'CHANGE_STATUS' AND (new_status IS NULL OR new_status NOT IN ('ACTIVE','INACTIVE','ARCHIVED'))) THEN
        RAISE EXCEPTION 'Invalid catalog input' USING ERRCODE = 'CA001';
    END IF;
    IF action = 'CREATE' THEN
        parent := target;
    ELSE
        SELECT * INTO existing FROM catalog_staff.items(kind) i WHERE i.id = target;
        IF NOT FOUND THEN RAISE EXCEPTION 'Catalog target not found' USING ERRCODE = 'CA003'; END IF;
        parent := existing.parent_id;
    END IF;
    IF kind = 'COURSE' THEN
        root := target;
    ELSIF kind = 'LEVEL' THEN
        root := parent;
    ELSE
        SELECT l.course_id INTO root FROM content_catalog.course_level l WHERE l.id = parent;
        IF NOT FOUND THEN RAISE EXCEPTION 'Catalog parent not found' USING ERRCODE = 'CA003'; END IF;
    END IF;
    IF root IS NOT NULL THEN
        PERFORM 1 FROM content_catalog.course c WHERE c.id = root FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Catalog parent not found' USING ERRCODE = 'CA003'; END IF;
    END IF;
    IF kind = 'LESSON' OR (kind = 'LEVEL' AND action <> 'CREATE') THEN
        PERFORM 1 FROM content_catalog.course_level l
         WHERE l.id = CASE WHEN kind = 'LESSON' THEN parent ELSE target END FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Catalog target not found' USING ERRCODE = 'CA003'; END IF;
    END IF;
    IF kind = 'LESSON' AND action <> 'CREATE' THEN
        PERFORM 1 FROM content_catalog.reading_lesson l WHERE l.id = target FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Catalog target not found' USING ERRCODE = 'CA003'; END IF;
    END IF;

    IF action = 'CREATE' THEN
        IF kind = 'COURSE' THEN
            INSERT INTO content_catalog.course AS c (slug,title,description,display_order)
            VALUES (key,label,description,ordering) RETURNING c.id INTO changed;
        ELSIF kind = 'LEVEL' THEN
            INSERT INTO content_catalog.course_level AS l (course_id,code,name,description,display_order)
            VALUES (parent,key,label,description,ordering) RETURNING l.id INTO changed;
        ELSE
            INSERT INTO content_catalog.reading_lesson AS l (level_id,slug,title,description,display_order)
            VALUES (parent,key,label,description,ordering) RETURNING l.id INTO changed;
        END IF;
    ELSE
        SELECT * INTO existing FROM catalog_staff.items(kind) i WHERE i.id = target;
        IF NOT FOUND THEN RAISE EXCEPTION 'Catalog target not found' USING ERRCODE = 'CA003'; END IF;
        IF existing.status = 'ARCHIVED' OR (action = 'CHANGE_STATUS' AND existing.status = new_status) THEN
            RAISE EXCEPTION 'Catalog state conflict' USING ERRCODE = 'CA004';
        END IF;
        IF expected IS NULL THEN RAISE EXCEPTION 'Catalog precondition required' USING ERRCODE = 'CA005'; END IF;
        IF expected < 1 THEN RAISE EXCEPTION 'Invalid catalog precondition' USING ERRCODE = 'CA007'; END IF;
        IF existing.revision <> expected THEN RAISE EXCEPTION 'Catalog revision mismatch' USING ERRCODE = 'CA006'; END IF;
        IF kind = 'COURSE' THEN
            UPDATE content_catalog.course c SET
                slug = CASE WHEN action = 'EDIT' THEN key ELSE c.slug END,
                title = CASE WHEN action = 'EDIT' THEN label ELSE c.title END,
                description = CASE WHEN action = 'EDIT' THEN mutate_item.description ELSE c.description END,
                display_order = CASE WHEN action = 'EDIT' THEN ordering ELSE c.display_order END,
                status = CASE WHEN action = 'CHANGE_STATUS' THEN new_status::content_catalog.catalog_status ELSE c.status END,
                revision = c.revision + 1, updated_at = clock_timestamp()
            WHERE c.id = target AND c.revision = expected RETURNING c.id INTO changed;
        ELSIF kind = 'LEVEL' THEN
            UPDATE content_catalog.course_level l SET
                code = CASE WHEN action = 'EDIT' THEN key ELSE l.code END,
                name = CASE WHEN action = 'EDIT' THEN label ELSE l.name END,
                description = CASE WHEN action = 'EDIT' THEN mutate_item.description ELSE l.description END,
                display_order = CASE WHEN action = 'EDIT' THEN ordering ELSE l.display_order END,
                status = CASE WHEN action = 'CHANGE_STATUS' THEN new_status::content_catalog.catalog_status ELSE l.status END,
                revision = l.revision + 1, updated_at = clock_timestamp()
            WHERE l.id = target AND l.revision = expected RETURNING l.id INTO changed;
        ELSE
            UPDATE content_catalog.reading_lesson l SET
                slug = CASE WHEN action = 'EDIT' THEN key ELSE l.slug END,
                title = CASE WHEN action = 'EDIT' THEN label ELSE l.title END,
                description = CASE WHEN action = 'EDIT' THEN mutate_item.description ELSE l.description END,
                display_order = CASE WHEN action = 'EDIT' THEN ordering ELSE l.display_order END,
                status = CASE WHEN action = 'CHANGE_STATUS' THEN new_status::content_catalog.catalog_status ELSE l.status END,
                revision = l.revision + 1, updated_at = clock_timestamp()
            WHERE l.id = target AND l.revision = expected RETURNING l.id INTO changed;
        END IF;
        IF NOT FOUND THEN RAISE EXCEPTION 'Catalog revision mismatch' USING ERRCODE = 'CA006'; END IF;
    END IF;
    SELECT * INTO STRICT existing FROM catalog_staff.items(kind) i WHERE i.id = changed;
    INSERT INTO content_catalog.content_audit_events
        (actor_id,operation,entity_kind,course_id,level_id,lesson_id,revision,request_id)
    VALUES (actor,action::content_catalog.catalog_action,kind::content_catalog.catalog_entity,
        CASE WHEN kind = 'COURSE' THEN changed END, CASE WHEN kind = 'LEVEL' THEN changed END,
        CASE WHEN kind = 'LESSON' THEN changed END, existing.revision, request);
    RETURN existing;
END;
$$;

REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA catalog_staff FROM PUBLIC;
REVOKE ALL ON TYPE catalog_staff.item, catalog_staff.page_item FROM PUBLIC;
