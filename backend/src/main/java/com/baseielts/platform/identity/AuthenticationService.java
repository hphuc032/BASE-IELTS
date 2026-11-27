package com.baseielts.platform.identity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "base.identity.enabled", havingValue = "true")
final class AuthenticationService {
    private static final RowMapper<Snapshot> SNAPSHOT = (row, ignored) -> new Snapshot(
        row.getObject("user_id", UUID.class), row.getString("password_hash"),
        row.getString("status"), row.getLong("security_revision"));
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final IdentityPasswords passwords;
    private final AuthSessions sessions;
    private final ReentrantLock[] stripes = new ReentrantLock[64];

    AuthenticationService(JdbcTemplate jdbc, TransactionTemplate transaction,
                          IdentityPasswords passwords, AuthSessions sessions) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.passwords = passwords;
        this.sessions = sessions;
        Arrays.setAll(stripes, ignored -> new ReentrantLock());
    }

    LoginResult login(String email, String password) {
        Snapshot original = one(jdbc.query("select * from identity_api.login_snapshot(?)", SNAPSHOT,
            email.strip().toLowerCase(Locale.ROOT)));
        String eligibleHash = original != null && "ACTIVE".equals(original.status()) ? original.hash() : null;
        if (!passwords.matches(password, eligibleHash)) {
            return null;
        }
        AccountGuard guard = new AccountGuard(original.account());
        try {
            LoginResult staged = transaction.execute(current -> {
                limits();
                Snapshot locked = one(jdbc.query("select * from account_fence_routines.\"lockForLogin\"(?)",
                    SNAPSHOT, original.account()));
                guard.lock();
                if (!original.equals(locked)) {
                    return null;
                }
                Profile profile = profile(original.account(), original.revision(), null);
                return profile == null ? null : new LoginResult(sessions.prepare(original.account(), original.revision()), profile);
            });
            if (staged == null) {
                return null;
            }
            sessions.publish(staged.prepared());
            return staged;
        } catch (org.springframework.transaction.TransactionSystemException failure) {
            if (guard.held) {
                sessions.revokeAccount(original.account());
            }
            throw failure;
        } finally {
            guard.close();
        }
    }

    <T> T authorized(AuthSessions.Binding binding, Function<UUID,T> operation,
                     boolean touch, boolean logout) {
        AccountGuard guard = new AccountGuard(binding.account);
        try {
            T result = transaction.execute(current -> {
                limits();
                Boolean active = jdbc.queryForObject(
                    "select account_fence_routines.\"lockAndCheckActor\"(?, ?)", Boolean.class,
                    binding.account, binding.revision);
                guard.lock();
                if (!Boolean.TRUE.equals(active)) {
                    sessions.revoke(binding);
                    return null;
                }
                if (!sessions.current(binding)) {
                    return null;
                }
                return operation.apply(binding.account);
            });
            if (result != null) {
                if (logout) {
                    sessions.revoke(binding);
                } else if (touch) {
                    sessions.touch(binding);
                }
            }
            return result;
        } finally {
            guard.close();
        }
    }

    Profile profile(UUID account, long revision, String displayName) {
        return one(jdbc.query("select * from identity_api.profile(?, ?, ?)", (row, ignored) -> new Profile(
            row.getObject("user_id", UUID.class), row.getString("email"), row.getString("display_name"),
            List.of((String[]) row.getArray("roles").getArray()), "ACTIVE"), account, revision, displayName));
    }

    private void limits() {
        jdbc.execute("set local lock_timeout = '250ms'");
        jdbc.execute("set local statement_timeout = '1000ms'");
        jdbc.execute("set local transaction_timeout = '2000ms'");
    }

    private static <T> T one(List<T> rows) {
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private final class AccountGuard implements AutoCloseable {
        private final ReentrantLock stripe;
        private boolean held;

        AccountGuard(UUID account) {
            stripe = stripes[Math.floorMod(account.hashCode(), stripes.length)];
        }

        void lock() {
            try {
                held = stripe.tryLock(250, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            if (!held) {
                throw new org.springframework.dao.TransientDataAccessResourceException("Account gate unavailable");
            }
        }

        @Override
        public void close() {
            if (held) {
                stripe.unlock();
            }
        }
    }

    private record Snapshot(UUID account, String hash, String status, long revision) {
        @Override public String toString() { return "CredentialSnapshot[redacted]"; }
    }

    record Profile(UUID id, String email, String displayName, List<String> roles, String status) {}
    record LoginResult(AuthSessions.Prepared prepared, Profile profile) {
        @Override public String toString() { return "LoginResult[redacted]"; }
    }
}
