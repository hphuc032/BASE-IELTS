package com.baseielts.platform.identity;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "base.identity.enabled", havingValue = "true")
final class VerificationService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DevelopmentVerificationMailbox mailbox;
    private final IdentityPasswords passwords;
    private final VerificationConnectionAdmission admission;
    private final MeterRegistry metrics;

    VerificationService(JdbcTemplate jdbc, TransactionTemplate transaction,
                        ObjectProvider<DevelopmentVerificationMailbox> mailbox, IdentityPasswords passwords,
                        VerificationConnectionAdmission admission, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.mailbox = mailbox.getIfAvailable();
        this.passwords = passwords;
        this.admission = admission;
        this.metrics = metrics;
    }

    boolean deliveryAvailable() {
        return mailbox != null;
    }

    /** The HTTP layer applies the shared admission limits and the generic acknowledgement window. */
    void request(String email, String displayName, boolean registration) {
        if (!deliveryAvailable()) {
            throw new IllegalStateException("Development verification capture is disabled");
        }
        String canonicalEmail = email.strip().toLowerCase(Locale.ROOT);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(150);
        try (var slot = mailbox.tryReserve()) {
            if (slot == null) {
                count("queue_full");
                return;
            }
            String rawToken = OpaqueTokens.create(32);
            byte[] digest = OpaqueTokens.digest(rawToken);
            if (issueWithinDeadline(deadline, canonicalEmail, displayName, digest, registration)) {
                slot.capture(canonicalEmail, rawToken);
                count("captured");
            }
        }
    }

    private boolean issueWithinDeadline(long deadline, String email, String displayName, byte[] digest,
                                        boolean registration) {
        long started = System.nanoTime();
        String phase = "lease";
        try (Connection connection = admission.acquire(deadline)) {
            metrics.timer("identity.verification.database.lease_acquisition")
                .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            phase = "setup";
            int originalNetworkTimeout = connection.getNetworkTimeout();
            try {
                long setupStarted = System.nanoTime();
                int remaining = remainingMillis(deadline);
                connection.setNetworkTimeout(Runnable::run, remaining);
                connection.setAutoCommit(false);
                try (var statement = connection.prepareStatement("""
                    select set_config('lock_timeout','20ms',true),
                           set_config('statement_timeout','50ms',true),
                           set_config('transaction_timeout',?,true)
                    """)) {
                    statement.setString(1, remaining + "ms");
                    statement.execute();
                }
                recordDuration("setup", setupStarted);
                phase = "register";
                long registering = System.nanoTime();
                boolean eligible = !registration || insertPending(connection, deadline, email, displayName);
                if (registration) recordDuration("register", registering);
                phase = "issue";
                long issuing = System.nanoTime();
                boolean issued = eligible && issueToken(connection, deadline, email, digest, registration);
                if (eligible) recordDuration("issue", issuing);
                phase = "commit";
                connection.setNetworkTimeout(Runnable::run, remainingMillis(deadline));
                long commitStarted = System.nanoTime();
                try {
                    connection.commit();
                } finally {
                    metrics.timer("identity.verification.database.commit")
                        .record(System.nanoTime() - commitStarted, TimeUnit.NANOSECONDS);
                }
                return issued;
            } catch (SQLException failure) {
                count("transaction_unavailable");
                recordFailure(phase, failure);
                // No delivery on rollback or an indeterminate commit. Discard an uncertain lease.
                try {
                    connection.abort(Runnable::run);
                } catch (SQLException ignored) {
                    // PostgreSQL also enforces transaction_timeout on the server.
                }
                return false;
            } finally {
                phase = "release";
                long releaseStarted = System.nanoTime();
                try {
                    if (!connection.isClosed()) connection.setNetworkTimeout(Runnable::run, originalNetworkTimeout);
                } finally {
                    recordDuration("reset", releaseStarted);
                }
            }
        } catch (SQLException failure) {
            count("connection_unavailable");
            recordFailure(phase, failure);
            return false;
        } finally {
            metrics.timer("identity.verification.database.duration").record(
                System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }

    private void recordDuration(String phase, long started) {
        metrics.timer("identity.verification.database.phase", "phase", phase)
            .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    }

    private void recordFailure(String phase, SQLException failure) {
        String state = failure.getSQLState();
        String kind = switch (state == null ? "" : state) {
            case "55P03" -> "lock";
            case "57014" -> "cancelled";
            case "08006", "08003", "08001" -> "connection";
            default -> "other";
        };
        metrics.counter("identity.verification.database.failure", "phase", phase, "kind", kind).increment();
    }

    private void count(String outcome) {
        metrics.counter("identity.verification.issuance", "outcome", outcome).increment();
    }

    private boolean insertPending(Connection connection, long deadline, String email, String displayName)
            throws SQLException {
        connection.setNetworkTimeout(Runnable::run, remainingMillis(deadline));
        try (var statement = connection.prepareStatement("select identity_api.register(?, ?)")) {
            statement.setString(1, email);
            statement.setString(2, displayName);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getObject(1, UUID.class) != null;
            }
        }
    }

    private boolean issueToken(Connection connection, long deadline, String email, byte[] digest, boolean registration)
            throws SQLException {
        connection.setNetworkTimeout(Runnable::run, remainingMillis(deadline));
        try (var statement = connection.prepareStatement("select identity_api.issue(?, ?, ?, null)")) {
            statement.setString(1, email);
            statement.setBytes(2, digest);
            if (registration) statement.setLong(3, 0);
            else statement.setNull(3, Types.BIGINT);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private static int remainingMillis(long deadline) throws SQLException {
        long milliseconds = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (milliseconds < 1) throw new SQLException("Verification transaction deadline exceeded");
        return (int) milliseconds;
    }

    boolean verify(String token, String password) {
        if (!OpaqueTokens.valid(token, 43)) {
            return false;
        }
        String hash = passwords.encode(password);
        return Boolean.TRUE.equals(transaction.execute(current -> {
            configureDatabaseLimits();
            return jdbc.queryForObject("select identity_api.consume(?, ?)", Boolean.class, OpaqueTokens.digest(token), hash);
        }));
    }

    private void configureDatabaseLimits() {
        jdbc.execute("set local lock_timeout = '20ms'");
        jdbc.execute("set local statement_timeout = '50ms'");
        jdbc.execute("set local transaction_timeout = '150ms'");
    }
}
