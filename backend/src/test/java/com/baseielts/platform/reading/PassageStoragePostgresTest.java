package com.baseielts.platform.reading;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "BASE_TEST_READING_STORAGE", matches = "true")
class PassageStoragePostgresTest {
    private static final String MIGRATION_ROLE = "SET ROLE identity_migration_owner";

    @BeforeAll
    static void migrate() {
        String fresh = url("BASE_TEST_READING_FRESH");
        assertEquals(15, flyway(fresh, "15").migrate().migrationsExecuted);
        assertEquals(0, flyway(fresh, "15").migrate().migrationsExecuted);
        flyway(fresh, "15").validate();

        String upgrade = url("BASE_TEST_READING_UPGRADE");
        assertEquals(14, flyway(upgrade, "14").migrate().migrationsExecuted);
        assertEquals(1, flyway(upgrade, "15").migrate().migrationsExecuted);
        flyway(upgrade, "15").validate();
    }

    @Test
    void editionMembershipAndOrderedParagraphsPreserveIdentity() throws SQLException {
        try (var db = owner("BASE_TEST_READING_FRESH"); var sql = db.createStatement()) {
            db.setAutoCommit(false);
            try {
                var passage = seed(sql, "draft-fixture");
                var other = seed(sql, "other-fixture");
                String paragraph = scalar(sql, """
                    INSERT INTO content_reading.paragraphs (passage_version_id, position, body)
                    VALUES ('%s', 1, 'Original text') RETURNING id
                    """.formatted(passage.edition));
                String second = scalar(sql, """
                    INSERT INTO content_reading.paragraphs (passage_version_id, position, body)
                    VALUES ('%s', 2, 'Second text') RETURNING id
                    """.formatted(passage.edition));
                sql.executeUpdate("UPDATE content_reading.paragraphs SET position = 3 - position WHERE passage_version_id = '%s'"
                    .formatted(passage.edition));
                sql.executeUpdate("UPDATE content_reading.paragraphs SET body = 'Edited' WHERE id = '%s'".formatted(paragraph));
                assertEquals(paragraph, scalar(sql, """
                    SELECT id FROM content_reading.paragraphs
                    WHERE passage_version_id = '%s' AND position = 2 AND body = 'Edited'
                    """.formatted(passage.edition)));
                assertEquals(second, scalar(sql, """
                    SELECT id FROM content_reading.paragraphs WHERE passage_version_id = '%s' AND position = 1
                    """.formatted(passage.edition)));
                reject(db, sql, "23505", """
                    INSERT INTO content_reading.paragraphs (passage_version_id, position, body)
                    VALUES ('%s', 1, 'Duplicate position')
                    """.formatted(passage.edition));
                reject(db, sql, "23503", """
                    INSERT INTO content_reading.translations (paragraph_id, language, text)
                    VALUES (gen_random_uuid(), 'vi', 'Missing paragraph')
                    """);
                reject(db, sql, "23505", """
                    INSERT INTO content_reading.passage_versions (passage_id, version_no, title, visibility)
                    VALUES ('%s', 2, 'Duplicate open draft', 'PRIVATE')
                    """.formatted(passage.root));
                reject(db, sql, "23514", "UPDATE content_reading.paragraphs SET passage_version_id = '%s' WHERE id = '%s'"
                    .formatted(other.edition, paragraph));
                reject(db, sql, "23514", "UPDATE content_reading.passages SET current_version_id = '%s' WHERE id = '%s'"
                    .formatted(passage.edition, passage.root));
                freeze(sql, passage.edition);
                reject(db, sql, "23514", "UPDATE content_reading.passages SET current_version_id = '%s' WHERE id = '%s'"
                    .formatted(passage.edition, other.root));
                assertEquals(1, sql.executeUpdate("UPDATE content_reading.passages SET current_version_id = '%s' WHERE id = '%s'"
                    .formatted(passage.edition, passage.root)));
                reject(db, sql, "23514", """
                    INSERT INTO content_reading.passage_versions (passage_id, version_no, base_version_id, title, visibility)
                    VALUES ('%s', 2, '%s', 'Incorrect lineage', 'PRIVATE')
                    """.formatted(other.root, passage.edition));
                assertNotNull(scalar(sql, """
                    INSERT INTO content_reading.passage_versions (passage_id, version_no, base_version_id, title, visibility)
                    VALUES ('%s', 2, '%s', 'Matching lineage', 'PRIVATE') RETURNING id
                    """.formatted(passage.root, passage.edition)));
            } finally {
                db.rollback();
            }
        }
    }

    @Test
    void frozenEditionBlocksChildWritesAndRuntimeHasNoDirectAccess() throws SQLException {
        try (var db = owner("BASE_TEST_READING_UPGRADE"); var sql = db.createStatement()) {
            db.setAutoCommit(false);
            try {
                var passage = seed(sql, "frozen-fixture");
                String paragraph = scalar(sql, """
                    INSERT INTO content_reading.paragraphs (passage_version_id, position, body)
                    VALUES ('%s', 1, 'Original text') RETURNING id
                    """.formatted(passage.edition));
                String translation = scalar(sql, """
                    INSERT INTO content_reading.translations (paragraph_id, language, text)
                    VALUES ('%s', 'vi', 'Translated') RETURNING id
                    """.formatted(paragraph));
                freeze(sql, passage.edition);
                reject(db, sql, "23514", "UPDATE content_reading.passage_versions SET title = 'Changed' WHERE id = '%s'"
                    .formatted(passage.edition));
                reject(db, sql, "23514", "DELETE FROM content_reading.passage_versions WHERE id = '%s'"
                    .formatted(passage.edition));
                reject(db, sql, "23514", "DELETE FROM content_reading.paragraphs WHERE id = '%s'".formatted(paragraph));
                reject(db, sql, "23514", "UPDATE content_reading.translations SET text = 'Changed' WHERE id = '%s'"
                    .formatted(translation));
                assertEquals("false", scalar(sql, """
                    SELECT has_schema_privilege('base_ielts_runtime', 'content_reading', 'USAGE')::text
                    """));
                assertEquals("false", scalar(sql, """
                    SELECT has_table_privilege('base_ielts_runtime', 'content_reading.passage_versions',
                      'SELECT,INSERT,UPDATE,DELETE')::text
                    """));
            } finally {
                db.rollback();
            }
        }
    }

    @Test
    void paragraphInsertWaitsForFreezeAndRechecksState() throws SQLException {
        try (var freezer = owner("BASE_TEST_READING_FRESH"); var editor = owner("BASE_TEST_READING_FRESH");
             var freezeSql = freezer.createStatement(); var editSql = editor.createStatement()) {
            var passage = seed(freezeSql, "freeze-race-fixture");
            assertEquals(1, freezeSql.executeUpdate("""
                INSERT INTO content_reading.paragraphs (passage_version_id, position, body)
                VALUES ('%s', 1, 'Existing paragraph')
                """.formatted(passage.edition)));
            freezer.setAutoCommit(false);
            editor.setAutoCommit(false);
            String insert = """
                INSERT INTO content_reading.paragraphs (passage_version_id, position, body)
                VALUES ('%s', 2, 'Possible addition')
                """.formatted(passage.edition);
            try {
                freeze(freezeSql, passage.edition);
                editSql.execute("SET LOCAL lock_timeout = '250ms'");
                reject(editor, editSql, "55P03", insert);
                freezer.commit();
                reject(editor, editSql, "23514", insert);
            } finally {
                editor.rollback();
                freezer.rollback();
            }
        }
    }

    private record Fixture(String root, String edition) {}

    private static Fixture seed(Statement sql, String slug) throws SQLException {
        String actor = scalar(sql, """
            INSERT INTO identity.users (canonical_email, display_name)
            VALUES ('%s@example.test', 'Passage fixture') RETURNING id
            """.formatted(slug));
        String root = scalar(sql, """
            INSERT INTO content_reading.passages (slug, author_id) VALUES ('%s', '%s') RETURNING id
            """.formatted(slug, actor));
        String edition = scalar(sql, """
            INSERT INTO content_reading.passage_versions (passage_id, version_no, title, visibility)
            VALUES ('%s', 1, 'Passage fixture', 'PRIVATE') RETURNING id
            """.formatted(root));
        return new Fixture(root, edition);
    }

    private static void freeze(Statement sql, String edition) throws SQLException {
        assertEquals(1, sql.executeUpdate("""
            UPDATE content_reading.passage_versions SET state = 'FROZEN', frozen_at = clock_timestamp()
            WHERE id = '%s'
            """.formatted(edition)));
    }

    private static void reject(Connection db, Statement sql, String sqlState, String command) throws SQLException {
        var savepoint = db.setSavepoint();
        try {
            assertEquals(sqlState, assertThrows(SQLException.class, () -> sql.executeUpdate(command)).getSQLState());
        } finally {
            db.rollback(savepoint);
            db.releaseSavepoint(savepoint);
        }
    }

    private static String scalar(Statement sql, String command) throws SQLException {
        try (var result = sql.executeQuery(command)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static Connection owner(String variable) throws SQLException {
        Connection db = DriverManager.getConnection(url(variable), "base_ielts_migration", password());
        try (var sql = db.createStatement()) {
            sql.execute(MIGRATION_ROLE);
            return db;
        } catch (SQLException failure) {
            db.close();
            throw failure;
        }
    }

    private static Flyway flyway(String url, String target) {
        return Flyway.configure().dataSource(url, "base_ielts_migration", password())
            .defaultSchema("identity").initSql(MIGRATION_ROLE).target(target).load();
    }

    private static String password() {
        return System.getenv("BASE_DB_MIGRATION_PASSWORD");
    }

    private static String url(String variable) {
        String name = System.getenv(variable);
        assertTrue(name != null && name.matches("base_ielts_m2test_reading_[a-z0-9_]+"),
            "Use isolated Reading fixture databases");
        return "jdbc:postgresql://127.0.0.1:5432/" + name;
    }
}
