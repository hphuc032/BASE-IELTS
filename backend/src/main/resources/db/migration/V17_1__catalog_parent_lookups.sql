-- Scope nested lists before projecting rows. Keep the page and exact count on one snapshot.
CREATE OR REPLACE FUNCTION catalog_staff.list_items(kind text, actor uuid, security_revision bigint, parent uuid,
                                                   page integer, size integer, search text, status_filter text)
RETURNS SETOF catalog_staff.page_item
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE requested_status content_catalog.catalog_status;
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
    requested_status := status_filter::content_catalog.catalog_status;
    RETURN QUERY WITH filtered AS MATERIALIZED (
        SELECT c.id, NULL::uuid AS parent_id, c.slug AS item_key, c.title AS label, c.description,
               c.display_order, c.status::text AS status, c.revision, c.created_at, c.updated_at
        FROM content_catalog.course c
        WHERE kind = 'COURSE'
          AND (requested_status IS NULL OR c.status = requested_status)
          AND (search IS NULL OR strpos(lower(c.title), lower(search)) > 0 OR strpos(lower(c.slug), lower(search)) > 0)
        UNION ALL
        SELECT l.id, l.course_id, l.code, l.name, l.description, l.display_order,
               l.status::text, l.revision, l.created_at, l.updated_at
        FROM content_catalog.course_level l
        WHERE kind = 'LEVEL' AND l.course_id = parent
          AND (requested_status IS NULL OR l.status = requested_status)
          AND (search IS NULL OR strpos(lower(l.name), lower(search)) > 0 OR strpos(lower(l.code), lower(search)) > 0)
        UNION ALL
        SELECT l.id, l.level_id, l.slug, l.title, l.description, l.display_order,
               l.status::text, l.revision, l.created_at, l.updated_at
        FROM content_catalog.reading_lesson l
        WHERE kind = 'LESSON' AND l.level_id = parent
          AND (requested_status IS NULL OR l.status = requested_status)
          AND (search IS NULL OR strpos(lower(l.title), lower(search)) > 0 OR strpos(lower(l.slug), lower(search)) > 0)
    ), rows AS (
        SELECT * FROM filtered f ORDER BY f.display_order, f.id OFFSET page * size LIMIT size
    )
    SELECT r.*, t.total FROM (SELECT count(*) AS total FROM filtered) t
    LEFT JOIN rows r ON true ORDER BY r.display_order, r.id;
END;
$$;
