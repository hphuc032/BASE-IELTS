-- P01 private placement storage. Access remains behind separately gated staff routines.
CREATE TABLE content_reading.placement (
    passage_id uuid PRIMARY KEY REFERENCES content_reading.passages(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    lesson_id uuid REFERENCES content_catalog.reading_lesson(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    position integer CHECK (position > 0),
    revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
    CONSTRAINT placement_assignment_pair_ck CHECK ((lesson_id IS NULL) = (position IS NULL))
);

CREATE INDEX placement_lesson_order_idx ON content_reading.placement (lesson_id, position, passage_id)
    WHERE lesson_id IS NOT NULL;

REVOKE ALL ON TABLE content_reading.placement FROM PUBLIC;
