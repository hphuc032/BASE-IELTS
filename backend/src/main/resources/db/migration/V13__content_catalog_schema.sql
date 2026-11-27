-- The migration owner owns the catalog. Runtime access waits for the fenced M2.1 routines.
CREATE SCHEMA content_catalog;
REVOKE ALL ON SCHEMA content_catalog FROM PUBLIC;

CREATE TYPE content_catalog.catalog_status AS ENUM ('ACTIVE', 'INACTIVE', 'ARCHIVED');
REVOKE ALL ON TYPE content_catalog.catalog_status FROM PUBLIC;

CREATE TABLE content_catalog.course (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug text NOT NULL UNIQUE CHECK (char_length(slug) BETWEEN 1 AND 80 AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    title text NOT NULL CHECK (char_length(title) BETWEEN 1 AND 160 AND btrim(title) <> ''),
    description text CHECK (char_length(description) BETWEEN 1 AND 2000 AND btrim(description) <> ''),
    display_order integer NOT NULL CHECK (display_order > 0),
    status content_catalog.catalog_status NOT NULL DEFAULT 'ACTIVE',
    revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE content_catalog.course_level (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES content_catalog.course(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    code text NOT NULL CHECK (char_length(code) BETWEEN 1 AND 32 AND code ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name text NOT NULL CHECK (char_length(name) BETWEEN 1 AND 160 AND btrim(name) <> ''),
    description text CHECK (char_length(description) BETWEEN 1 AND 2000 AND btrim(description) <> ''),
    display_order integer NOT NULL CHECK (display_order > 0),
    status content_catalog.catalog_status NOT NULL DEFAULT 'ACTIVE',
    revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (course_id, code)
);

CREATE TABLE content_catalog.reading_lesson (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    level_id uuid NOT NULL REFERENCES content_catalog.course_level(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    slug text NOT NULL CHECK (char_length(slug) BETWEEN 1 AND 80 AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    title text NOT NULL CHECK (char_length(title) BETWEEN 1 AND 160 AND btrim(title) <> ''),
    description text CHECK (char_length(description) BETWEEN 1 AND 2000 AND btrim(description) <> ''),
    display_order integer NOT NULL CHECK (display_order > 0),
    status content_catalog.catalog_status NOT NULL DEFAULT 'ACTIVE',
    revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (level_id, slug)
);

CREATE INDEX course_order_idx ON content_catalog.course (display_order, id);
CREATE INDEX course_level_status_order_idx ON content_catalog.course_level (course_id, status, display_order, id);
CREATE INDEX reading_lesson_status_order_idx ON content_catalog.reading_lesson (level_id, status, display_order, id);

REVOKE ALL ON ALL TABLES IN SCHEMA content_catalog FROM PUBLIC;
