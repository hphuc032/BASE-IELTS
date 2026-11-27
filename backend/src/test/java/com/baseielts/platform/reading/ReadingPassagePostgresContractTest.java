package com.baseielts.platform.reading;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "BASE_TEST_READING_V15", matches = "true")
class ReadingPassagePostgresContractTest {
    private static final String FRESH = "BASE_TEST_READING_V15_FRESH_URL";
    private static final String UPGRADE = "BASE_TEST_READING_V15_UPGRADE_URL";
    private static final String OWNER = "SET ROLE identity_migration_owner";

    @BeforeAll
    static void migrateIsolatedFixtures() throws SQLException {
        assertFalse(url(FRESH).equals(url(UPGRADE)), "Fresh and upgrade fixtures must differ");
        assertEquals(15, flyway(FRESH, "15").migrate().migrationsExecuted);
        assertEquals(0, flyway(FRESH, "15").migrate().migrationsExecuted);
        flyway(FRESH, "15").validate();

        assertEquals(14, flyway(UPGRADE, "14").migrate().migrationsExecuted);
        UUID identity;
        try (var db = owner(UPGRADE)) {
            identity = uuid(db, """
                INSERT INTO identity.users (canonical_email, display_name)
                VALUES ('pending-upgrade@test.invalid', 'Pending upgrade fixture') RETURNING id
                """);
        }
        assertEquals(1, flyway(UPGRADE, "15").migrate().migrationsExecuted);
        flyway(UPGRADE, "15").validate();
        try (var db = owner(UPGRADE)) {
            assertEquals("PENDING:0", cell(db, """
                SELECT status::text || ':' || security_revision FROM identity.users WHERE id = ?
                """, identity));
            assertEquals(Boolean.FALSE, cell(db, """
                SELECT has_schema_privilege('base_ielts_runtime', 'content_reading', 'USAGE')
                    OR has_table_privilege('base_ielts_runtime', 'content_reading.paragraphs', 'SELECT,INSERT,UPDATE,DELETE')
                """));
        }
    }

    @Test
    void concurrentDraftEditsSerializeOnThePassageAndOnlyOneRevisionCommits() throws Exception {
        try (var winner = owner(FRESH); var loser = owner(FRESH); var observer = owner(FRESH);
             var worker = Executors.newSingleThreadExecutor()) {
            var initial = seed(winner);
            int winnerPid = (Integer) cell(winner, "SELECT pg_backend_pid()");
            int loserPid = (Integer) cell(loser, "SELECT pg_backend_pid()");
            winner.setAutoCommit(false);
            loser.setAutoCommit(false);
            change(winner, "SET LOCAL statement_timeout = '8s'");
            change(loser, "SET LOCAL statement_timeout = '8s'");
            try {
                lockPassage(winner, initial.root());
                var losingEdit = worker.submit(() -> {
                    try {
                        lockPassage(loser, initial.root());
                        int changed = bumpRevision(loser, initial, 1);
                        if (changed == 1) {
                            change(loser, "UPDATE content_reading.paragraphs SET body = 'Losing edit' WHERE id = ?",
                                    initial.second());
                        }
                        loser.commit();
                        return changed;
                    } finally {
                        loser.rollback();
                    }
                });
                awaitBlockedBy(observer, loserPid, winnerPid);
                assertEquals(1, bumpRevision(winner, initial, 1));
                change(winner, "SET CONSTRAINTS content_reading.passage_paragraph_position_uk DEFERRED");
                change(winner, "UPDATE content_reading.paragraphs SET position = 2, body = 'Winner edit' WHERE id = ?",
                        initial.first());
                change(winner, "UPDATE content_reading.paragraphs SET position = 1 WHERE id = ?", initial.second());
                winner.commit();
                assertEquals(0, losingEdit.get(5, TimeUnit.SECONDS));
                assertEquals(2L, cell(observer, "SELECT revision FROM content_reading.passage_versions WHERE id = ?",
                        initial.edition()));
                assertEquals(List.of(new Paragraph(initial.second(), 1, "Second"),
                        new Paragraph(initial.first(), 2, "Winner edit")), paragraphs(observer, initial.edition()));
            } finally {
                winner.rollback();
            }
        }
    }

    @Test
    void deferredOrderingFailureRollsBackRevisionAndGraphAndFreezePreservesTheExactEdition() throws SQLException {
        try (var writer = owner(UPGRADE); var observer = owner(UPGRADE)) {
            var initial = seed(writer);
            writer.setAutoCommit(false);
            try {
                lockPassage(writer, initial.root());
                assertEquals(1, bumpRevision(writer, initial, 1));
                change(writer, "SET CONSTRAINTS content_reading.passage_paragraph_position_uk DEFERRED");
                change(writer, "UPDATE content_reading.paragraphs SET position = 2, body = 'Uncommitted' WHERE id = ?",
                        initial.first());
                assertEquals("23505", assertThrows(SQLException.class, writer::commit).getSQLState());
                writer.rollback();
                assertEquals(1L, cell(observer, "SELECT revision FROM content_reading.passage_versions WHERE id = ?",
                        initial.edition()));
                var original = List.of(new Paragraph(initial.first(), 1, "First"),
                        new Paragraph(initial.second(), 2, "Second"));
                assertEquals(original, paragraphs(observer, initial.edition()));

                // Seed a frozen historical edition directly as fixture owner. There is no publication operation.
                lockPassage(writer, initial.root());
                change(writer, """
                    UPDATE content_reading.passage_versions SET state = 'FROZEN', frozen_at = clock_timestamp()
                    WHERE id = ?
                    """, initial.edition());
                UUID successor = uuid(writer, """
                    INSERT INTO content_reading.passage_versions (passage_id, version_no, base_version_id, title, visibility)
                    VALUES (?, 2, ?, 'Successor', 'PRIVATE') RETURNING id
                    """, initial.root(), initial.edition());
                UUID newParagraph = uuid(writer, """
                    INSERT INTO content_reading.paragraphs (passage_version_id, position, body)
                    VALUES (?, 1, 'Successor paragraph') RETURNING id
                    """, successor);
                writer.commit();
                assertFalse(initial.first().equals(newParagraph));
                assertEquals(original, paragraphs(observer, initial.edition()));
                assertEquals(List.of(new Paragraph(newParagraph, 1, "Successor paragraph")), paragraphs(observer, successor));
                assertEquals("23514", assertThrows(SQLException.class, () -> change(observer, """
                    UPDATE content_reading.paragraphs SET passage_version_id = ? WHERE id = ?
                    """, successor, initial.first())).getSQLState());
                assertEquals("23514", assertThrows(SQLException.class, () -> change(observer, """
                    UPDATE content_reading.paragraphs SET body = 'Invalid edit' WHERE id = ?
                    """, initial.first())).getSQLState());
                assertEquals(original, paragraphs(observer, initial.edition()));
            } finally {
                writer.rollback();
            }
        }
    }

    @Test
    void evidenceContractCanReferenceV15AndRejectsCrossEditionTuples() throws SQLException {
        try (var db = owner(FRESH)) {
            db.setAutoCommit(false);
            try {
                var initial = seed(db);
                var other = seed(db);
                UUID exerciseVersion = UUID.randomUUID();
                // Only the two FK edges under test. The fixture tables roll back and are never runtime storage.
                change(db, """
                    CREATE TABLE content_reading.test_exercise_pin (
                        id uuid PRIMARY KEY,
                        passage_id uuid NOT NULL,
                        passage_version_id uuid NOT NULL,
                        UNIQUE (id, passage_version_id),
                        FOREIGN KEY (passage_id, passage_version_id)
                            REFERENCES content_reading.passage_versions(passage_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT
                    )
                    """);
                change(db, """
                    CREATE TABLE content_reading.test_evidence (
                        exercise_version_id uuid NOT NULL,
                        passage_version_id uuid NOT NULL,
                        paragraph_id uuid NOT NULL,
                        FOREIGN KEY (exercise_version_id, passage_version_id)
                            REFERENCES content_reading.test_exercise_pin(id, passage_version_id)
                            ON UPDATE RESTRICT ON DELETE RESTRICT,
                        FOREIGN KEY (passage_version_id, paragraph_id)
                            REFERENCES content_reading.paragraphs(passage_version_id, id)
                            ON UPDATE RESTRICT ON DELETE RESTRICT
                    )
                    """);
                String insertPin = "INSERT INTO content_reading.test_exercise_pin VALUES (?, ?, ?)";
                rejectForeignKey(db, insertPin, exerciseVersion, initial.root(), other.edition());
                assertEquals(1, change(db, insertPin, exerciseVersion, initial.root(), initial.edition()));
                String insertEvidence = "INSERT INTO content_reading.test_evidence VALUES (?, ?, ?)";
                rejectForeignKey(db, insertEvidence, exerciseVersion, initial.edition(), other.first());
                rejectForeignKey(db, insertEvidence, exerciseVersion, other.edition(), other.first());
                assertEquals(1, change(db, insertEvidence, exerciseVersion, initial.edition(), initial.first()));
                rejectForeignKey(db, "DELETE FROM content_reading.paragraphs WHERE id = ?", initial.first());
                assertEquals(1, change(db, "UPDATE content_reading.paragraphs SET body = 'Edited' WHERE id = ?",
                        initial.first()));
                assertEquals(initial.first(), cell(db, "SELECT paragraph_id FROM content_reading.test_evidence"));
            } finally {
                db.rollback();
            }
        }
    }

    @Test
    void measuresCandidateV15DraftReadAndEditWithTopicAndTranslations() throws SQLException {
        final int samples = 30;
        final int paragraphCount = 32;
        Fixture fixture;
        UUID topic;
        try (var db = owner(FRESH)) {
            fixture = seed(db);
            topic = uuid(db, """
                INSERT INTO content_reading.topics (slug, label) VALUES (?, 'Coastal geography') RETURNING id
                """, "topic-" + UUID.randomUUID());
            change(db, "UPDATE content_reading.passage_versions SET topic_id = ? WHERE id = ?", topic, fixture.edition());
            rejectForeignKeyInTransaction(db, fixture);
            for (int i = 3; i <= paragraphCount; i++) {
                insertParagraph(db, fixture.edition(), i, "Coastal survey paragraph " + i);
            }
            assertEquals(paragraphCount, change(db, """
                INSERT INTO content_reading.translations (paragraph_id, language, text)
                SELECT id, 'vi-VN', 'Bản dịch thử nghiệm' FROM content_reading.paragraphs WHERE passage_version_id = ?
                """, fixture.edition()));
        }
        List<Long> acquisition = new ArrayList<>();
        List<Long> read = new ArrayList<>();
        List<Long> edit = new ArrayList<>();
        List<Long> rootLock = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            long openedAt = System.nanoTime();
            try (var db = owner(FRESH)) {
                acquisition.add(System.nanoTime() - openedAt);
                db.setAutoCommit(false);
                long readStarted = System.nanoTime();
                try (var sql = db.prepareStatement("""
                    SELECT v.topic_id, p.id, p.position, t.language, t.text
                    FROM content_reading.passage_versions v
                    JOIN content_reading.paragraphs p ON p.passage_version_id = v.id
                    JOIN content_reading.translations t ON t.paragraph_id = p.id
                    WHERE v.passage_id = ? AND v.id = ? ORDER BY p.position, t.language
                    """)) {
                    sql.setObject(1, fixture.root());
                    sql.setObject(2, fixture.edition());
                    try (var rows = sql.executeQuery()) {
                        int seen = 0;
                        while (rows.next()) {
                            assertEquals(topic, rows.getObject(1, UUID.class));
                            assertEquals(++seen, rows.getInt(3));
                            assertEquals("vi-VN", rows.getString(4));
                            assertEquals("Bản dịch thử nghiệm", rows.getString(5));
                        }
                        assertEquals(paragraphCount, seen);
                    }
                }
                db.commit();
                read.add(System.nanoTime() - readStarted);

                long writeStarted = System.nanoTime();
                lockPassage(db, fixture.root());
                rootLock.add(System.nanoTime() - writeStarted);
                assertEquals(1, bumpRevision(db, fixture, i + 1L));
                assertEquals(1, change(db, "UPDATE content_reading.paragraphs SET body = ? WHERE id = ?",
                        "Edited paragraph " + i, fixture.first()));
                db.commit();
                edit.add(System.nanoTime() - writeStarted);
            }
        }
        printSample("connection-and-role-setup", acquisition);
        printSample("draft-read-one-statement-through-commit", read);
        printSample("paragraph-edit-three-statements-through-commit", edit);
        printSample("uncontended-root-lock-statement", rootLock);
    }

    private static void rejectForeignKeyInTransaction(Connection db, Fixture fixture) throws SQLException {
        db.setAutoCommit(false);
        try {
            rejectForeignKey(db, "UPDATE content_reading.passage_versions SET topic_id = ? WHERE id = ?",
                    UUID.randomUUID(), fixture.edition());
        } finally {
            db.rollback();
            db.setAutoCommit(true);
        }
    }

    private static void printSample(String name, List<Long> elapsed) {
        var sorted = elapsed.stream().sorted().toList();
        System.out.printf(java.util.Locale.ROOT,
                "V15 candidate %s n=%d p50=%.3fms p95=%.3fms max=%.3fms%n", name, sorted.size(),
                sorted.get((int) Math.ceil(sorted.size() * 0.50) - 1) / 1_000_000.0,
                sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1) / 1_000_000.0,
                sorted.getLast() / 1_000_000.0);
    }

    private static void rejectForeignKey(Connection db, String command, Object... values) throws SQLException {
        var checkpoint = db.setSavepoint();
        try {
            assertEquals("23503", assertThrows(SQLException.class, () -> change(db, command, values)).getSQLState());
        } finally {
            db.rollback(checkpoint);
            db.releaseSavepoint(checkpoint);
        }
    }

    private static void awaitBlockedBy(Connection observer, int blocked, int blocker) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        do {
            if (Boolean.TRUE.equals(cell(observer, "SELECT ? = ANY (pg_blocking_pids(?))", blocker, blocked))) {
                return;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("PostgreSQL never observed the losing connection waiting on the stable Passage");
    }

    private static Fixture seed(Connection db) throws SQLException {
        String slug = "reading-v15-" + UUID.randomUUID();
        UUID actor = uuid(db, "INSERT INTO identity.users (canonical_email, display_name) VALUES (?, ?) RETURNING id",
                slug + "@test.invalid", "Reading fixture");
        UUID root = uuid(db, "INSERT INTO content_reading.passages (slug, author_id) VALUES (?, ?) RETURNING id",
                slug, actor);
        UUID edition = uuid(db, """
            INSERT INTO content_reading.passage_versions (passage_id, version_no, title, visibility)
            VALUES (?, 1, 'Fixture', 'PRIVATE') RETURNING id
            """, root);
        return new Fixture(root, edition, insertParagraph(db, edition, 1, "First"),
                insertParagraph(db, edition, 2, "Second"));
    }

    private static void lockPassage(Connection db, UUID root) throws SQLException {
        assertEquals(root, uuid(db, "SELECT id FROM content_reading.passages WHERE id = ? FOR UPDATE", root));
    }

    private static int bumpRevision(Connection db, Fixture fixture, long revision) throws SQLException {
        return change(db, """
            UPDATE content_reading.passage_versions SET revision = revision + 1
            WHERE passage_id = ? AND id = ? AND revision = ? AND state = 'DRAFT'
            """, fixture.root(), fixture.edition(), revision);
    }

    private static UUID insertParagraph(Connection db, UUID edition, int position, String body) throws SQLException {
        return uuid(db, """
            INSERT INTO content_reading.paragraphs (passage_version_id, position, body) VALUES (?, ?, ?) RETURNING id
            """, edition, position, body);
    }

    private static List<Paragraph> paragraphs(Connection db, UUID edition) throws SQLException {
        try (var sql = db.prepareStatement("""
            SELECT id, position, body FROM content_reading.paragraphs WHERE passage_version_id = ? ORDER BY position
            """)) {
            sql.setObject(1, edition);
            try (var rows = sql.executeQuery()) {
                List<Paragraph> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(new Paragraph(rows.getObject(1, UUID.class), rows.getInt(2), rows.getString(3)));
                }
                return result;
            }
        }
    }

    private static UUID uuid(Connection db, String query, Object... values) throws SQLException {
        return (UUID) cell(db, query, values);
    }

    private static Object cell(Connection db, String query, Object... values) throws SQLException {
        try (var sql = db.prepareStatement(query)) {
            for (int i = 0; i < values.length; i++) {
                sql.setObject(i + 1, values[i]);
            }
            try (ResultSet row = sql.executeQuery()) {
                assertTrue(row.next());
                return row.getObject(1);
            }
        }
    }

    private static int change(Connection db, String query, Object... values) throws SQLException {
        try (var sql = db.prepareStatement(query)) {
            for (int i = 0; i < values.length; i++) {
                sql.setObject(i + 1, values[i]);
            }
            return sql.executeUpdate();
        }
    }

    private static Connection owner(String fixture) throws SQLException {
        Connection db = DriverManager.getConnection(url(fixture), "base_ielts_migration", password());
        try {
            change(db, OWNER);
            return db;
        } catch (SQLException failure) {
            db.close();
            throw failure;
        }
    }

    private static Flyway flyway(String fixture, String target) {
        return Flyway.configure().dataSource(url(fixture), "base_ielts_migration", password())
            .defaultSchema("identity").initSql(OWNER).target(target).load();
    }

    private static String password() {
        return System.getenv("BASE_DB_MIGRATION_PASSWORD");
    }

    private static String url(String fixture) {
        String url = System.getenv(fixture);
        assertTrue(url != null && url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[1-9][0-9]{0,4}/base_ielts_m2test_reading_v15_[a-z0-9_]+"),
                "Use an isolated loopback Reading fixture database");
        return url;
    }

    private record Fixture(UUID root, UUID edition, UUID first, UUID second) {}
    private record Paragraph(UUID id, int position, String body) {}
}
