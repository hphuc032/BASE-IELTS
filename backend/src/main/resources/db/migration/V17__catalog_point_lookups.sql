-- Push the UUID lookup into each Catalog relation so existing primary keys can serve it.
CREATE OR REPLACE FUNCTION catalog_staff.get_item(kind text, actor uuid, security_revision bigint, target uuid)
RETURNS catalog_staff.item
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE result catalog_staff.item;
BEGIN
    PERFORM catalog_staff.require_access(actor, security_revision, false);
    IF kind IS NULL OR kind NOT IN ('COURSE','LEVEL','LESSON') OR target IS NULL THEN
        RAISE EXCEPTION 'Invalid catalog target' USING ERRCODE = 'CA001';
    END IF;
    SELECT * INTO result FROM (
        SELECT c.id, NULL::uuid, c.slug, c.title, c.description, c.display_order, c.status::text,
           c.revision, c.created_at, c.updated_at
      FROM content_catalog.course c WHERE kind = 'COURSE' AND c.id = target
    UNION ALL
    SELECT l.id, l.course_id, l.code, l.name, l.description, l.display_order, l.status::text,
           l.revision, l.created_at, l.updated_at
      FROM content_catalog.course_level l WHERE kind = 'LEVEL' AND l.id = target
    UNION ALL
    SELECT l.id, l.level_id, l.slug, l.title, l.description, l.display_order, l.status::text,
           l.revision, l.created_at, l.updated_at
      FROM content_catalog.reading_lesson l WHERE kind = 'LESSON' AND l.id = target
    ) i;
    IF NOT FOUND THEN RAISE EXCEPTION 'Catalog target not found' USING ERRCODE = 'CA003'; END IF;
    RETURN result;
END;
$$;

CREATE OR REPLACE FUNCTION catalog_staff.mutate_item(kind text, action text, actor uuid, security_revision bigint, target uuid,
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
        SELECT * INTO existing FROM (
        SELECT c.id, NULL::uuid, c.slug, c.title, c.description, c.display_order, c.status::text,
           c.revision, c.created_at, c.updated_at
      FROM content_catalog.course c WHERE kind = 'COURSE' AND c.id = target
    UNION ALL
    SELECT l.id, l.course_id, l.code, l.name, l.description, l.display_order, l.status::text,
           l.revision, l.created_at, l.updated_at
      FROM content_catalog.course_level l WHERE kind = 'LEVEL' AND l.id = target
    UNION ALL
    SELECT l.id, l.level_id, l.slug, l.title, l.description, l.display_order, l.status::text,
           l.revision, l.created_at, l.updated_at
      FROM content_catalog.reading_lesson l WHERE kind = 'LESSON' AND l.id = target
    ) i;
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
        SELECT * INTO existing FROM (
        SELECT c.id, NULL::uuid, c.slug, c.title, c.description, c.display_order, c.status::text,
           c.revision, c.created_at, c.updated_at
      FROM content_catalog.course c WHERE kind = 'COURSE' AND c.id = target
    UNION ALL
    SELECT l.id, l.course_id, l.code, l.name, l.description, l.display_order, l.status::text,
           l.revision, l.created_at, l.updated_at
      FROM content_catalog.course_level l WHERE kind = 'LEVEL' AND l.id = target
    UNION ALL
    SELECT l.id, l.level_id, l.slug, l.title, l.description, l.display_order, l.status::text,
           l.revision, l.created_at, l.updated_at
      FROM content_catalog.reading_lesson l WHERE kind = 'LESSON' AND l.id = target
    ) i;
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
    SELECT * INTO STRICT existing FROM (
        SELECT c.id, NULL::uuid, c.slug, c.title, c.description, c.display_order, c.status::text,
           c.revision, c.created_at, c.updated_at
      FROM content_catalog.course c WHERE kind = 'COURSE' AND c.id = changed
    UNION ALL
    SELECT l.id, l.course_id, l.code, l.name, l.description, l.display_order, l.status::text,
           l.revision, l.created_at, l.updated_at
      FROM content_catalog.course_level l WHERE kind = 'LEVEL' AND l.id = changed
    UNION ALL
    SELECT l.id, l.level_id, l.slug, l.title, l.description, l.display_order, l.status::text,
           l.revision, l.created_at, l.updated_at
      FROM content_catalog.reading_lesson l WHERE kind = 'LESSON' AND l.id = changed
    ) i;
    INSERT INTO content_catalog.content_audit_events
        (actor_id,operation,entity_kind,course_id,level_id,lesson_id,revision,request_id)
    VALUES (actor,action::content_catalog.catalog_action,kind::content_catalog.catalog_entity,
        CASE WHEN kind = 'COURSE' THEN changed END, CASE WHEN kind = 'LEVEL' THEN changed END,
        CASE WHEN kind = 'LESSON' THEN changed END, existing.revision, request);
    RETURN existing;
END;
$$;
