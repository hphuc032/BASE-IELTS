package com.baseielts.platform.content;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
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
    void freshDatabaseMigratesThroughV14AndEnforcesCatalogBoundary() throws Exception {
        String url = fixtureUrl("BASE_TEST_CATALOG_FRESH_DB");
        Flyway flyway = flyway(url, "14");
        assertEquals(14, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
        verifyCatalog(url);
        measureCatalogPageCandidate(url);
    }

    @Test
    void upgradeFromV13PreservesIdentityAndAddsCatalogAuditAndCapabilityStorage() throws Exception {
        String url = fixtureUrl("BASE_TEST_CATALOG_UPGRADE_DB");
        assertEquals(13, flyway(url, "13").migrate().migrationsExecuted);
        try (Connection connection = migrateConnection(url); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO identity.users (canonical_email, display_name)
                VALUES ('m2-v13-fixture@example.test', 'V13 catalog fixture')
                """);
            assertEquals("true", scalar(statement, """
                SELECT (to_regclass('content_catalog.content_audit_events') IS NULL)::text
                """));
        }
        Flyway upgraded = flyway(url, "14");
        assertEquals(1, upgraded.migrate().migrationsExecuted);
        upgraded.validate();
        try (Connection connection = migrateConnection(url); Statement statement = connection.createStatement()) {
            assertEquals("PENDING:0", scalar(statement, """
                SELECT status::text || ':' || security_revision
                FROM identity.users WHERE canonical_email = 'm2-v13-fixture@example.test'
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
                verifyAuthorityAndAuditStorage(connection, statement);
            } finally {
                connection.rollback();
            }
        }
    }

    private static void verifyAuthorityAndAuditStorage(Connection connection, Statement statement) throws SQLException {
        assertEquals("false", scalar(statement, """
            SELECT (has_table_privilege('base_ielts_runtime', 'content_catalog.content_capability_grants',
                    'SELECT,INSERT,UPDATE,DELETE') OR
                    has_table_privilege('base_ielts_runtime', 'content_catalog.content_audit_events',
                    'SELECT,INSERT,UPDATE,DELETE'))::text
            """));
        statement.executeUpdate("""
            INSERT INTO identity.users (canonical_email, display_name)
            VALUES ('catalog-grantor@example.test', 'Catalog grantor fixture'),
                   ('catalog-grantee@example.test', 'Catalog grantee fixture')
            """);
        // A migration-owner storage fixture does not exercise runtime authorization or provisioning.
        String grant = """
            INSERT INTO content_catalog.content_capability_grants (grantee_id, grantor_id, capability)
            SELECT grantee.id, grantor.id, '%s'::content_catalog.capability
            FROM identity.users grantee CROSS JOIN identity.users grantor
            WHERE grantee.canonical_email = 'catalog-grantee@example.test'
              AND grantor.canonical_email = 'catalog-grantor@example.test'
            """;
        statement.executeUpdate(grant.formatted("CONTENT_EDIT"));
        statement.executeUpdate(grant.formatted("CONTENT_PUBLISH"));
        reject(connection, statement, "23505", grant.formatted("CONTENT_EDIT"));
        reject(connection, statement, "23514", """
            UPDATE content_catalog.content_capability_grants SET revoked_at = clock_timestamp()
            WHERE capability = 'CONTENT_EDIT'
            """);
        statement.executeUpdate("""
            UPDATE content_catalog.content_capability_grants
            SET revoked_at = clock_timestamp(), revoked_by = grantor_id WHERE capability = 'CONTENT_EDIT'
            """);
        assertEquals("CONTENT_PUBLISH", scalar(statement, """
            SELECT capability::text FROM content_catalog.content_capability_grants WHERE revoked_at IS NULL
            """));
        statement.executeUpdate(grant.formatted("CONTENT_EDIT"));

        String audit = """
            INSERT INTO content_catalog.content_audit_events
                (actor_id, operation, entity_kind, course_id, revision, request_id)
            SELECT actor.id, 'CREATE', '%s'::content_catalog.catalog_entity, target.id, 1, gen_random_uuid()
            FROM identity.users actor CROSS JOIN content_catalog.course target
            WHERE actor.canonical_email = 'catalog-grantor@example.test' AND target.slug = 'reading'
            """;
        assertEquals(1, statement.executeUpdate(audit.formatted("COURSE")));
        reject(connection, statement, "23514", audit.formatted("LESSON"));
        reject(connection, statement, "23503", """
            UPDATE content_catalog.content_audit_events SET course_id = gen_random_uuid()
            """);
    }

    private static void measureCatalogPageCandidate(String url) throws SQLException {
        try (Connection connection = migrateConnection(url); Statement setup = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                setup.executeUpdate("""
                    INSERT INTO content_catalog.course (slug, title, display_order)
                    SELECT 'sample-' || n, 'Reading fixture ' || n, n / 5 + 1
                    FROM generate_series(1, 10000) n
                    """);
                setup.execute("SET LOCAL statement_timeout = '1000ms'");
                // Test a literal search, exact count and a late page from a single snapshot.
                try (var page = connection.prepareStatement("""
                    WITH filtered AS MATERIALIZED (
                        SELECT id, display_order FROM content_catalog.course
                        WHERE strpos(lower(title), lower(?)) > 0
                    )
                    SELECT (SELECT count(*) FROM filtered),
                           (SELECT count(*) FROM (SELECT id FROM filtered ORDER BY display_order, id
                                                   OFFSET ? LIMIT ?) page)
                    """)) {
                    page.setString(1, "Reading fixture");
                    page.setInt(2, 4_950);
                    page.setInt(3, 50);
                    long start = System.nanoTime();
                    try (var result = page.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals(10_000, result.getLong(1));
                        assertEquals(50, result.getLong(2));
                    }
                    double millis = (System.nanoTime() - start) / 1_000_000.0;
                    System.out.printf(Locale.ROOT, "Catalog G03 probe: 10000 rows, one page/count statement, %.3f ms%n", millis);
                    page.setString(1, "%_");
                    try (var result = page.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals(0, result.getLong(1));
                        assertEquals(0, result.getLong(2));
                    }
                }
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
