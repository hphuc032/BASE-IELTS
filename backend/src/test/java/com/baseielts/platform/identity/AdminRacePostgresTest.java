package com.baseielts.platform.identity;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "BASE_TEST_GOVERNANCE", matches = "true")
class AdminRacePostgresTest {
    private final UUID target = UUID.fromString(System.getenv("BASE_TEST_GOVERNANCE_TARGET"));
    private final long revision = Long.parseLong(System.getenv("BASE_TEST_GOVERNANCE_REVISION"));
    private final String fixtureHash = new IdentityPasswords().encode("synthetic isolated admin race fixture");

    @Test
    void cancellationAndRecoveryEachWinOnceUnderSeparateAuthenticatedConnections() throws Exception {
        try (Connection approver = connect("operator_approver", "BASE_TEST_APPROVER_PASSWORD");
             Connection otherApprover = connect("operator_approver_secondary", "BASE_TEST_SECONDARY_APPROVER_PASSWORD");
             Connection operator = connect("deployment_operator", "BASE_TEST_OPERATOR_PASSWORD");
             Connection lifecycle = connect("identity_lifecycle_operator", "BASE_TEST_LIFECYCLE_PASSWORD");
             var executor = Executors.newSingleThreadExecutor()) {
            UUID cancelled = approve(approver);
            assertEquals("42501", denial(() -> cancel(otherApprover, cancelled)));
            approver.setAutoCommit(false);
            assertEquals("CANCELLED", cancel(approver, cancelled));
            var losingRecovery = executor.submit(() -> denial(() -> recover(operator, cancelled)));
            try {
                assertThrows(TimeoutException.class, () -> losingRecovery.get(200, TimeUnit.MILLISECONDS));
            } finally {
                approver.commit();
                approver.setAutoCommit(true);
            }
            assertEquals("42501", losingRecovery.get(4, TimeUnit.SECONDS));

            UUID consumed = approve(approver);
            assertLifecycleDeniedBeforeCommit(lifecycle, "SET_CREDENTIAL", revision);
            assertLifecycleDeniedBeforeCommit(lifecycle, "DISABLE", revision);
            // Close the physical operator connection with a staged recovery.
            // The same one-use approval must still be usable after PostgreSQL rolls it back.
            try (Connection disconnected = connect("deployment_operator", "BASE_TEST_OPERATOR_PASSWORD")) {
                disconnected.setAutoCommit(false);
                assertEquals(target, recover(disconnected, consumed));
            }
            interruptRecovery(consumed, false);
            interruptRecovery(consumed, true);
            try (Connection actor = connect(System.getenv("BASE_DB_RUNTIME_USER"), "BASE_DB_RUNTIME_PASSWORD")) {
                actor.setAutoCommit(false);
                assertTrue(actorFence(actor, revision));
                operator.setAutoCommit(false);
                try (var settings = operator.createStatement()) {
                    settings.execute("set local lock_timeout = '250ms'");
                }
                assertEquals("55P03", denial(() -> recover(operator, consumed)));
                operator.rollback();
                actor.rollback();
            }
            operator.setAutoCommit(false);
            assertEquals(target, recover(operator, consumed));
            lifecycle.setAutoCommit(false);
            assertEquals("55P03", denial(() -> mutate(lifecycle, "SET_CREDENTIAL", revision)));
            lifecycle.rollback();
            assertEquals("55P03", denial(() -> mutate(lifecycle, "DISABLE", revision)));
            lifecycle.rollback();
            lifecycle.setAutoCommit(true);
            try (Connection actor = connect(System.getenv("BASE_DB_RUNTIME_USER"), "BASE_DB_RUNTIME_PASSWORD")) {
                actor.setAutoCommit(false);
                try (var settings = actor.createStatement()) {
                    settings.execute("set local lock_timeout = '250ms'");
                }
                assertEquals("55P03", denial(() -> actorFence(actor, revision)));
                actor.rollback();
            }
            var losingCancellation = executor.submit(() -> denial(() -> cancel(approver, consumed)));
            try {
                assertThrows(TimeoutException.class, () -> losingCancellation.get(200, TimeUnit.MILLISECONDS));
            } finally {
                operator.commit();
                operator.setAutoCommit(true);
            }
            assertEquals("23514", losingCancellation.get(4, TimeUnit.SECONDS));
            assertEquals("42501", denial(() -> recover(operator, consumed)));
            assertFalse(mutate(lifecycle, "SET_CREDENTIAL", revision));
            assertFalse(mutate(lifecycle, "DISABLE", revision));
            assertLifecycleDeniedBeforeCommit(lifecycle, "SET_CREDENTIAL", revision + 1);
            assertLifecycleDeniedBeforeCommit(lifecycle, "DISABLE", revision + 1);
            try (Connection actor = connect(System.getenv("BASE_DB_RUNTIME_USER"), "BASE_DB_RUNTIME_PASSWORD")) {
                actor.setAutoCommit(false);
                assertFalse(actorFence(actor, revision));
                assertTrue(actorFence(actor, revision + 1));
                actor.rollback();
            }
            recoveryWithLostCommitReply(approver, operator);
        }
    }

    private void recoveryWithLostCommitReply(Connection approver, Connection operator) throws SQLException {
        long expectedRevision = revision + 1;
        UUID approval = approve(approver, expectedRevision);
        assertRecoveryOutcome(operator, approval, "UNKNOWN", null);
        assertEquals("42501", denial(() -> assertRecoveryOutcome(approver, approval, "UNKNOWN", null)));
        var fault = LostCommitReplySocketFactory.fixture();
        try (Connection lost = fault.connect(System.getenv("BASE_DB_JDBC_URL"),
                "deployment_operator", System.getenv("BASE_TEST_OPERATOR_PASSWORD"))) {
            lost.setAutoCommit(false);
            assertEquals(target, recover(lost, approval, expectedRevision));
            fault.arm();
            var outcome = assertThrows(SQLException.class, lost::commit);
            assertEquals("08006", outcome.getSQLState());
            assertTrue(fault.replyDropped());
        }
        try (Connection reconciliation = connect("deployment_operator", "BASE_TEST_OPERATOR_PASSWORD")) {
            reconciliation.setReadOnly(true);
            reconciliation.setAutoCommit(false);
            assertEquals("COMMITTED revision=" + (expectedRevision + 1),
                DevelopmentAdminCommand.reconciliationOutput(reconciliation, true, approval));
            assertEquals("UNKNOWN", DevelopmentAdminCommand.reconciliationOutput(reconciliation, true, UUID.randomUUID()));
            reconciliation.rollback();
        }
        try (Connection actor = connect(System.getenv("BASE_DB_RUNTIME_USER"), "BASE_DB_RUNTIME_PASSWORD")) {
            actor.setAutoCommit(false);
            assertFalse(actorFence(actor, expectedRevision));
            assertTrue(actorFence(actor, expectedRevision + 1));
            actor.rollback();
        }
        assertEquals("42501", denial(() -> recover(operator, approval, expectedRevision)));
    }

    private void assertRecoveryOutcome(Connection connection, UUID approval, String outcome, Long revision)
            throws SQLException {
        try (var statement = connection.prepareStatement("select * from deployment_operator.recovery_outcome(?)")) {
            statement.setObject(1, approval);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(outcome, result.getString("outcome"));
                assertEquals(revision, result.getObject("revision", Long.class));
                assertFalse(result.next());
            }
        }
    }

    private void interruptRecovery(UUID approval, boolean duringCommit) throws Exception {
        try (Connection interrupted = connect("deployment_operator", "BASE_TEST_OPERATOR_PASSWORD");
             Connection observer = connect("deployment_operator", "BASE_TEST_OPERATOR_PASSWORD");
             var worker = Executors.newSingleThreadExecutor()) {
            int pid = interrupted.unwrap(org.postgresql.PGConnection.class).getBackendPID();
            int namespace = 106187;
            try (var lock = observer.prepareStatement("select pg_advisory_lock(?, ?)")) {
                lock.setInt(1, namespace);
                lock.setInt(2, pid);
                lock.execute();
            }
            interrupted.setAutoCommit(false);
            assertEquals(target, recover(interrupted, approval));
            if (duringCommit) {
                // Transaction-local fixture only. The deferred trigger executes
                // inside the real server-side COMMIT before durability.
                try (var ddl = interrupted.createStatement()) {
                    ddl.execute("create temp table commit_barrier (id int)");
                    ddl.execute("""
                        create function pg_temp.await_commit_barrier() returns trigger language plpgsql as $$
                        begin
                            perform pg_advisory_xact_lock(106187, pg_backend_pid());
                            return new;
                        end
                        $$
                        """);
                    ddl.execute("""
                        create constraint trigger hold_commit after insert on commit_barrier
                        deferrable initially deferred for each row execute function pg_temp.await_commit_barrier()
                        """);
                    ddl.execute("insert into commit_barrier values (1)");
                }
            }
            var pending = worker.submit(() -> {
                try {
                    if (duringCommit) interrupted.commit();
                    else try (var statement = interrupted.prepareStatement("select pg_advisory_xact_lock(?, ?)")) {
                        statement.setInt(1, namespace);
                        statement.setInt(2, pid);
                        statement.execute();
                    }
                    return null;
                } catch (SQLException disconnected) {
                    return disconnected;
                }
            });
            try {
                awaitServerBarrier(observer, pid, duringCommit);
                try (var terminate = observer.prepareStatement("select pg_terminate_backend(?, 3000)")) {
                    terminate.setInt(1, pid);
                    try (var reply = terminate.executeQuery()) {
                        assertTrue(reply.next() && reply.getBoolean(1));
                    }
                }
                assertNotNull(pending.get(5, TimeUnit.SECONDS), "Interrupted client reported success");
                try (Connection actor = connect(System.getenv("BASE_DB_RUNTIME_USER"), "BASE_DB_RUNTIME_PASSWORD")) {
                    actor.setAutoCommit(false);
                    try (var settings = actor.createStatement()) {
                        settings.execute("set local lock_timeout = '500ms'");
                    }
                    assertTrue(actorFence(actor, revision), "Interrupted recovery changed the committed revision");
                    actor.rollback();
                }
            } finally {
                try (var release = observer.prepareStatement("select pg_advisory_unlock(?, ?)")) {
                    release.setInt(1, namespace);
                    release.setInt(2, pid);
                    release.execute();
                }
            }
        }
    }

    private static void awaitServerBarrier(Connection observer, int pid, boolean duringCommit)
            throws SQLException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try (var state = observer.prepareStatement("""
            select exists(select 1 from pg_stat_activity where pid=? and wait_event='advisory'
                and (upper(trim(query))='COMMIT')=?)
            """)) {
            state.setInt(1, pid);
            state.setBoolean(2, duringCommit);
            while (System.nanoTime() < deadline) {
                try (var result = state.executeQuery()) {
                    if (result.next() && result.getBoolean(1)) return;
                }
                Thread.sleep(20);
            }
        }
        fail("PostgreSQL did not reach the expected interruption barrier");
    }

    private void assertLifecycleDeniedBeforeCommit(Connection lifecycle, String action, long expectedRevision)
            throws SQLException {
        String expected = action.equals("SET_CREDENTIAL") ? "42501" : "23514";
        assertEquals(expected, denial(() -> mutate(lifecycle, action, expectedRevision)));
    }

    private boolean mutate(Connection lifecycle, String action, long expectedRevision) throws SQLException {
        if (action.equals("SET_CREDENTIAL")) {
            try (var statement = lifecycle.prepareStatement("select identity_lifecycle.reset_credential(?,?,?,?)")) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, target);
                statement.setLong(3, expectedRevision);
                statement.setString(4, fixtureHash);
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    return result.getBoolean(1);
                }
            }
        }
        try (var statement = lifecycle.prepareStatement(
                "select identity_lifecycle.mutate(?, ?, ?::identity_lifecycle.action, ?, null)")) {
            statement.setObject(1, target);
            statement.setLong(2, expectedRevision);
            statement.setString(3, action);
            statement.setString(4, action.equals("SET_CREDENTIAL") ? fixtureHash : null);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private boolean actorFence(Connection actor, long expectedRevision) throws SQLException {
        try (var statement = actor.prepareStatement(
                "select account_fence_routines.\"lockAndCheckActor\"(?,?)")) {
            statement.setObject(1, target);
            statement.setLong(2, expectedRevision);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private static Connection connect(String user, String passwordVariable) throws SQLException {
        return DriverManager.getConnection(System.getenv("BASE_DB_JDBC_URL"), user, System.getenv(passwordVariable));
    }

    private UUID approve(Connection approver) throws SQLException {
        return approve(approver, revision);
    }

    private UUID approve(Connection approver, long expectedRevision) throws SQLException {
        try (var statement = approver.prepareStatement("""
            select operator_approvals.approve(?,?,'RESET_DEDICATED',null,'deployment_operator',
                'synthetic-fixture-race',clock_timestamp()+interval '1 hour')
            """)) {
            statement.setObject(1, target);
            statement.setLong(2, expectedRevision);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getObject(1, UUID.class);
            }
        }
    }

    private String cancel(Connection approver, UUID approval) throws SQLException {
        try (var statement = approver.prepareStatement("""
            select operator_approvals."cancelApproval"(?, 'ISSUER_WITHDRAWAL')
            """)) {
            statement.setObject(1, approval);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private UUID recover(Connection operator, UUID approval) throws SQLException {
        return recover(operator, approval, revision);
    }

    private UUID recover(Connection operator, UUID approval, long expectedRevision) throws SQLException {
        try (var statement = operator.prepareStatement("""
            select deployment_operator.recover(?,?,?,'RESET_DEDICATED',null,?)
            """)) {
            statement.setObject(1, approval);
            statement.setObject(2, target);
            statement.setLong(3, expectedRevision);
            statement.setString(4, fixtureHash);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static String denial(SqlOperation attempt) {
        try {
            attempt.run();
            fail("A losing operation unexpectedly succeeded");
            return null;
        } catch (SQLException denied) {
            return denied.getSQLState();
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }
}
