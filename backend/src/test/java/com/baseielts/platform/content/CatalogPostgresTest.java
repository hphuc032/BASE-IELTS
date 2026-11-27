package com.baseielts.platform.content;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "BASE_TEST_CATALOG", matches = "true")
class CatalogPostgresTest {
    private static final String MIGRATION_ROLE = "SET ROLE identity_migration_owner";
    private static final String MIGRATION_USER = "base_ielts_migration";

    @Test
    void freshDatabaseMigratesThroughV13AndEnforcesCatalogBoundary() throws Exception {
        String url = fixtureUrl("BASE_TEST_CATALOG_FRESH_DB");
        Flyway flyway = flyway(url, "13");
        assertEquals(13, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
        verifyCatalog(url);
    }

    @Test
    void upgradeFromV12PreservesIdentityAndAddsCatalog() throws Exception {
        String url = fixtureUrl("BASE_TEST_CATALOG_UPGRADE_DB");
        assertEquals(12, flyway(url, "12").migrate().migrationsExecuted);
        try (Connection connection = migrateConnection(url); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO identity.users (canonical_email, display_name)
                VALUES ('m2-v12-fixture@example.test', 'V12 catalog fixture')
                """);
            assertEquals("true", scalar(statement, "SELECT (to_regnamespace('content_catalog') IS NULL)::text"));
        }
        Flyway upgraded = flyway(url, "13");
        assertEquals(1, upgraded.migrate().migrationsExecuted);
        upgraded.validate();
        try (Connection connection = migrateConnection(url); Statement statement = connection.createStatement()) {
            assertEquals("PENDING:0", scalar(statement, """
                SELECT status::text || ':' || security_revision
                FROM identity.users WHERE canonical_email = 'm2-v12-fixture@example.test'
                """));
        }
        verifyCatalog(url);
    }

    private static void verifyCatalog(String url) throws SQLException {
        try (Connection connection = migrateConnection(url); Statement statement = connection.createStatement()) {
            assertEquals("false", scalar(statement,
                "SELECT has_schema_privilege('base_ielts_runtime', 'content_catalog', 'USAGE')::text"));
            assertEquals("false", scalar(statement, """
                SELECT has_table_privilege('base_ielts_runtime', 'content_catalog.course', 'SELECT,INSERT,UPDATE,DELETE')::text
                """));
            assertEquals("identity_migration_owner", scalar(statement, """
                SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = 'content_catalog'
                """));
            assertEquals("3", scalar(statement, """
                SELECT count(*) FROM pg_indexes WHERE schemaname = 'content_catalog'
                AND indexname IN ('course_order_idx', 'course_level_status_order_idx', 'reading_lesson_status_order_idx')
                """));
            connection.setAutoCommit(false);
            try {
                statement.executeUpdate("""
                    INSERT INTO content_catalog.course (slug, title, display_order)
                    VALUES ('reading', 'Reading', 1), ('practice', 'Practice', 1)
                    """);
                statement.executeUpdate("""
                    INSERT INTO content_catalog.course_level (course_id, code, name, display_order)
                    SELECT id, 'a1', 'A1', 1 FROM content_catalog.course
                    """);
                statement.executeUpdate("""
                    INSERT INTO content_catalog.reading_lesson (level_id, slug, title, display_order)
                    SELECT id, 'intro', 'Introduction', 1 FROM content_catalog.course_level
                    """);
                assertEquals("2", scalar(statement, "SELECT count(*) FROM content_catalog.reading_lesson"));
                assertEquals("ACTIVE:1", scalar(statement, """
                    SELECT status::text || ':' || revision FROM content_catalog.course WHERE slug = 'reading'
                    """));
                reject(connection, statement, "23505",
                    "INSERT INTO content_catalog.course (slug, title, display_order) VALUES ('reading', 'Other', 1)");
                reject(connection, statement, "23505", """
                    INSERT INTO content_catalog.course_level (course_id, code, name, display_order)
                    SELECT id, 'a1', 'Other', 2 FROM content_catalog.course WHERE slug = 'reading'
                    """);
                reject(connection, statement, "23505", """
                    INSERT INTO content_catalog.reading_lesson (level_id, slug, title, display_order)
                    SELECT id, 'intro', 'Other', 2 FROM content_catalog.course_level
                    """);
                reject(connection, statement, "23503", """
                    INSERT INTO content_catalog.course_level (course_id, code, name, display_order)
                    VALUES (gen_random_uuid(), 'a1', 'Orphan', 1)
                    """);
                reject(connection, statement, "23503", "DELETE FROM content_catalog.course WHERE slug = 'reading'");
                reject(connection, statement, "23514",
                    "INSERT INTO content_catalog.course (slug, title, display_order) VALUES ('invalid', 'Title', 0)");
                reject(connection, statement, "23514", "UPDATE content_catalog.course SET revision = 0");
                reject(connection, statement, "23514",
                    "INSERT INTO content_catalog.course (slug, title, display_order) VALUES ('Uppercase', 'Title', 1)");
            } finally {
                connection.rollback();
            }
        }
    }

    private static void reject(Connection connection, Statement statement, String state, String sql) throws SQLException {
        var savepoint = connection.setSavepoint();
        try {
            assertEquals(state, assertThrows(SQLException.class, () -> statement.executeUpdate(sql)).getSQLState());
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }

    private static String scalar(Statement statement, String sql) throws SQLException {
        try (var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static String fixtureUrl(String variable) {
        String name = System.getenv(variable);
        assertTrue(name != null && name.matches("base_ielts_m2test_[a-z0-9_]+"),
            "Use a fresh isolated local M2 fixture database");
        return "jdbc:postgresql://127.0.0.1:5432/" + name;
    }

    private static Flyway flyway(String url, String target) {
        return Flyway.configure()
            .dataSource(url, MIGRATION_USER, System.getenv("BASE_DB_MIGRATION_PASSWORD"))
            .defaultSchema("identity")
            .initSql(MIGRATION_ROLE)
            .target(target)
            .load();
    }

    private static Connection migrateConnection(String url) throws SQLException {
        Connection connection = DriverManager.getConnection(url, MIGRATION_USER, System.getenv("BASE_DB_MIGRATION_PASSWORD"));
        try (Statement statement = connection.createStatement()) {
            statement.execute(MIGRATION_ROLE);
            return connection;
        } catch (SQLException failure) {
            connection.close();
            throw failure;
        }
    }
}
