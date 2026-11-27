-- Private M2.2 storage. Authenticated authoring, placement and publication use later boundaries.
CREATE SCHEMA content_reading;
REVOKE ALL ON SCHEMA content_reading FROM PUBLIC;

CREATE TYPE content_reading.version_state AS ENUM ('DRAFT', 'FROZEN');
CREATE TYPE content_reading.visibility AS ENUM ('PUBLIC', 'PRIVATE');
REVOKE ALL ON TYPE content_reading.version_state, content_reading.visibility FROM PUBLIC;

CREATE TABLE content_reading.passages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug text NOT NULL UNIQUE CHECK (char_length(slug) BETWEEN 1 AND 80 AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    author_id uuid NOT NULL REFERENCES identity.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    current_version_id uuid,
    archived_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX passages_author_idx ON content_reading.passages (author_id);
CREATE INDEX passages_current_version_idx ON content_reading.passages (current_version_id) WHERE current_version_id IS NOT NULL;

CREATE TABLE content_reading.topics (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug text NOT NULL UNIQUE CHECK (char_length(slug) BETWEEN 1 AND 80 AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    label text NOT NULL CHECK (char_length(label) BETWEEN 1 AND 160 AND btrim(label) <> '')
);

CREATE TABLE content_reading.passage_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    passage_id uuid NOT NULL REFERENCES content_reading.passages(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    version_no integer NOT NULL CHECK (version_no > 0),
    revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
    state content_reading.version_state NOT NULL DEFAULT 'DRAFT',
    title text NOT NULL CHECK (char_length(title) BETWEEN 1 AND 160 AND btrim(title) <> ''),
    summary text CHECK (char_length(summary) BETWEEN 1 AND 1000 AND btrim(summary) <> ''),
    instructions text CHECK (char_length(instructions) BETWEEN 1 AND 2000 AND btrim(instructions) <> ''),
    topic_id uuid REFERENCES content_reading.topics(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    visibility content_reading.visibility NOT NULL,
    frozen_at timestamptz,
    base_version_id uuid,
    UNIQUE (passage_id, id),
    UNIQUE (passage_id, version_no),
    CHECK ((state = 'DRAFT') = (frozen_at IS NULL)),
    CHECK (base_version_id IS DISTINCT FROM id),
    CONSTRAINT passage_version_base_fk FOREIGN KEY (passage_id, base_version_id)
        REFERENCES content_reading.passage_versions(passage_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT
);
CREATE UNIQUE INDEX passage_one_draft_idx ON content_reading.passage_versions (passage_id) WHERE state = 'DRAFT';
CREATE INDEX passage_version_base_idx ON content_reading.passage_versions (passage_id, base_version_id)
    WHERE base_version_id IS NOT NULL;
CREATE INDEX passage_version_topic_idx ON content_reading.passage_versions (topic_id) WHERE topic_id IS NOT NULL;
ALTER TABLE content_reading.passages ADD CONSTRAINT passage_current_version_fk
    FOREIGN KEY (id, current_version_id) REFERENCES content_reading.passage_versions(passage_id, id)
    ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE TABLE content_reading.paragraphs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    passage_version_id uuid NOT NULL
        REFERENCES content_reading.passage_versions(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    position integer NOT NULL CHECK (position > 0),
    heading text CHECK (char_length(heading) BETWEEN 1 AND 160 AND btrim(heading) <> ''),
    body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 8000 AND btrim(body) <> ''),
    UNIQUE (passage_version_id, id),
    CONSTRAINT passage_paragraph_position_uk UNIQUE (passage_version_id, position) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE content_reading.translations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paragraph_id uuid NOT NULL
        REFERENCES content_reading.paragraphs(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    language text NOT NULL CHECK (language <> '' AND language = btrim(language)),
    text text NOT NULL CHECK (char_length(text) BETWEEN 1 AND 8000 AND btrim(text) <> ''),
    UNIQUE (paragraph_id, language)
);

CREATE FUNCTION content_reading.guard_passage_identity() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND (NEW.id <> OLD.id OR NEW.author_id <> OLD.author_id) THEN
        RAISE EXCEPTION 'Passage identity is immutable' USING ERRCODE = '23514';
    END IF;
    IF NEW.current_version_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM content_reading.passage_versions v
        WHERE v.id = NEW.current_version_id AND v.passage_id = NEW.id AND v.state = 'FROZEN'
    ) THEN
        RAISE EXCEPTION 'Current passage version must belong to the passage and be frozen' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION content_reading.guard_passage_version() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF TG_OP <> 'INSERT' AND OLD.state = 'FROZEN' THEN
        RAISE EXCEPTION 'Frozen passage version is immutable' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE' AND (NEW.id <> OLD.id OR NEW.passage_id <> OLD.passage_id
        OR NEW.version_no <> OLD.version_no OR NEW.base_version_id IS DISTINCT FROM OLD.base_version_id) THEN
        RAISE EXCEPTION 'Passage version identity and lineage are immutable' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'INSERT' AND (NEW.state <> 'DRAFT' OR (NEW.base_version_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM content_reading.passage_versions v
        WHERE v.id = NEW.base_version_id AND v.passage_id = NEW.passage_id AND v.state = 'FROZEN'
    ))) THEN
        RAISE EXCEPTION 'New passage version must be a draft with valid lineage' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION content_reading.guard_passage_child() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    edition_id uuid;
    parent_paragraph uuid;
    edition_state content_reading.version_state;
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.id <> OLD.id THEN
        RAISE EXCEPTION 'Passage child identity is immutable' USING ERRCODE = '23514';
    END IF;

    IF TG_TABLE_NAME = 'paragraphs' THEN
        IF TG_OP = 'UPDATE' AND NEW.passage_version_id <> OLD.passage_version_id THEN
            RAISE EXCEPTION 'Paragraph cannot move between passage versions' USING ERRCODE = '23514';
        END IF;
        edition_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.passage_version_id ELSE NEW.passage_version_id END;
    ELSE
        IF TG_OP = 'UPDATE' AND NEW.paragraph_id <> OLD.paragraph_id THEN
            RAISE EXCEPTION 'Translation cannot move between paragraphs' USING ERRCODE = '23514';
        END IF;
        parent_paragraph := CASE WHEN TG_OP = 'DELETE' THEN OLD.paragraph_id ELSE NEW.paragraph_id END;
        SELECT p.passage_version_id INTO edition_id
        FROM content_reading.paragraphs p WHERE p.id = parent_paragraph;
    END IF;

    -- Wait for a concurrent freeze before checking state. Authoring routines also lock the stable passage first.
    SELECT v.state INTO edition_state FROM content_reading.passage_versions v WHERE v.id = edition_id FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Passage child has no visible edition' USING ERRCODE = '23503';
    END IF;
    IF edition_state = 'FROZEN' THEN
        RAISE EXCEPTION 'Frozen passage content is immutable' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION content_reading.guard_passage_identity(), content_reading.guard_passage_version(),
    content_reading.guard_passage_child() FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA content_reading FROM PUBLIC;

CREATE TRIGGER passage_identity_guard BEFORE INSERT OR UPDATE ON content_reading.passages
FOR EACH ROW EXECUTE FUNCTION content_reading.guard_passage_identity();
CREATE TRIGGER passage_version_guard BEFORE INSERT OR UPDATE OR DELETE ON content_reading.passage_versions
FOR EACH ROW EXECUTE FUNCTION content_reading.guard_passage_version();
CREATE TRIGGER passage_paragraph_guard BEFORE INSERT OR UPDATE OR DELETE ON content_reading.paragraphs
FOR EACH ROW EXECUTE FUNCTION content_reading.guard_passage_child();
CREATE TRIGGER passage_translation_guard BEFORE INSERT OR UPDATE OR DELETE ON content_reading.translations
FOR EACH ROW EXECUTE FUNCTION content_reading.guard_passage_child();
