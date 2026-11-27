package com.baseielts.platform.content;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "BASE_TEST_STAFF_MIGRATION", matches = "fresh|upgrade|placement|identity")
class StaffFixtureMigrationTest {
    @Test
    void provisionsAndValidatesTheRealFlywaySequence() throws SQLException {
        String mode = System.getenv("BASE_TEST_STAFF_MIGRATION");
        if ("identity".equals(mode)) {
            Flyway identity = flyway("12");
            assertEquals(12, identity.migrate().migrationsExecuted);
            identity.validate();
            assertEquals(0, identity.migrate().migrationsExecuted);
            return;
        }
        Flyway latest = flyway("17.3");
        if ("upgrade".equals(mode)) {
            assertEquals(15, flyway("15").migrate().migrationsExecuted);
        }
        if ("placement".equals(mode)) {
            assertEquals(19, flyway("17.2").migrate().migrationsExecuted);
        }
        assertEquals(switch (mode) {
            case "upgrade" -> 5;
            case "placement" -> 1;
            default -> 20;
        }, latest.migrate().migrationsExecuted);
        latest.validate();
        assertEquals(0, latest.migrate().migrationsExecuted);
        assertEquals(MigrationVersion.fromVersion("17.3"), latest.info().current().getVersion());
        assertTrue(latest.info().current().getVersion().compareTo(MigrationVersion.fromVersion("18")) < 0);
        var v16 = Arrays.stream(latest.info().applied())
            .filter(migration -> MigrationVersion.fromVersion("16").equals(migration.getVersion()))
            .findFirst().orElseThrow();
        assertEquals(957144442, v16.getChecksum());
        var v173 = Arrays.stream(latest.info().applied())
            .filter(migration -> MigrationVersion.fromVersion("17.3").equals(migration.getVersion()))
            .findFirst().orElseThrow();
        assertEquals(-574889560, v173.getChecksum());
        verifyPlacement();
    }

    private static void verifyPlacement() throws SQLException {
        try (var connection = DriverManager.getConnection(System.getenv("BASE_TEST_STAFF_DB"),
                "base_ielts_migration", System.getenv("BASE_TEST_STAFF_MIGRATION_PASSWORD"));
                var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute("SET LOCAL ROLE identity_migration_owner");
                statement.executeUpdate("""
                    WITH actor AS (
                        INSERT INTO identity.users (canonical_email, display_name)
                            VALUES ('placement-fixture@example.test', 'Placement fixture') RETURNING id
                    ), course AS (
                        INSERT INTO content_catalog.course (slug,title,display_order)
                            VALUES ('placement','Placement',1) RETURNING id
                    ), level AS (
                        INSERT INTO content_catalog.course_level (course_id,code,name,display_order)
                            SELECT id, 'a1','A1',1 FROM course RETURNING id
                    ), lesson AS (
                        INSERT INTO content_catalog.reading_lesson (level_id,slug,title,display_order)
                            SELECT id, 'placement','Placement',1 FROM level RETURNING id
                    ), passage AS (
                        INSERT INTO content_reading.passages (slug,author_id)
                            SELECT 'placement', id FROM actor RETURNING id
                    )
                    INSERT INTO content_reading.placement (passage_id,lesson_id,position)
                    SELECT passage.id, lesson.id, 1 FROM passage CROSS JOIN lesson
                    """);
                var savepoint = connection.setSavepoint();
                assertEquals("23503", assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    UPDATE content_reading.placement SET lesson_id = gen_random_uuid()
                    """)).getSQLState());
                connection.rollback(savepoint);
                assertEquals("23514", assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    UPDATE content_reading.placement SET position = NULL
                    """)).getSQLState());
                connection.rollback(savepoint);
                assertEquals(1, statement.executeUpdate("""
                    UPDATE content_reading.placement SET lesson_id = NULL, position = NULL, revision = 2
                    WHERE revision = 1
                    """));
                try (var row = statement.executeQuery("""
                    SELECT revision, lesson_id IS NULL AND position IS NULL,
                        has_table_privilege('base_ielts_runtime', 'content_reading.placement', 'SELECT')
                    FROM content_reading.placement
                    """)) {
                    assertTrue(row.next());
                    assertEquals(2, row.getLong(1));
                    assertTrue(row.getBoolean(2));
                    assertFalse(row.getBoolean(3));
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
            .dataSource(System.getenv("BASE_TEST_STAFF_DB"), "base_ielts_migration",
                System.getenv("BASE_TEST_STAFF_MIGRATION_PASSWORD"))
            .defaultSchema("identity")
            .initSql("SET ROLE identity_migration_owner")
            .target(target)
            .load();
    }
}
