package com.baseielts.platform.identity;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Reuses identity's AUTH binding and account guard for a dedicated content transaction. */
@Service
@ConditionalOnProperty(name = "base.identity.enabled", havingValue = "true")
public final class StaffAuthentication {
    private final AuthSessions sessions;
    private final AuthenticationService authentication;

    StaffAuthentication(AuthSessions sessions, AuthenticationService authentication) {
        this.sessions = sessions;
        this.authentication = authentication;
    }

    public <T> T execute(HttpServletRequest request, Database database, Operation<T> operation) {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            throw denied(Rejection.AUTH_REQUIRED);
        }
        AuthSessions.Binding binding = sessions.lookup(CookieSupport.value(request, AuthSessions.COOKIE));
        if (binding == null) {
            throw denied(Rejection.AUTH_REQUIRED);
        }
        boolean read = "GET".equals(request.getMethod());
        if (read != (database.access() == Access.READ)) {
            throw new IllegalArgumentException("Staff database does not match request access");
        }
        if (!read && !OpaqueTokens.matches(request.getHeader(PreauthStore.HEADER), binding.csrf)) {
            throw denied(Rejection.CSRF_INVALID);
        }
        return authentication.authorizedStaff(binding, database, operation);
    }

    static Denied denied(Rejection error) {
        return new Denied(error);
    }

    public enum Rejection { AUTH_REQUIRED, CSRF_INVALID }

    public static final class Denied extends RuntimeException {
        private final Rejection error;

        private Denied(Rejection error) {
            super(error.name());
            this.error = error;
        }

        public Rejection error() {
            return error;
        }
    }

    public enum Access {
        READ("content_staff_reader"), WRITE("content_author_writer");

        private final String principal;

        Access(String principal) {
            this.principal = principal;
        }

        String principal() {
            return principal;
        }
    }

    public static final class Database {
        private final Access access;
        private final JdbcTemplate jdbc;
        private final TransactionTemplate transaction;

        public Database(Access access, DataSource source) {
            this.access = Objects.requireNonNull(access);
            jdbc = new JdbcTemplate(source);
            transaction = new TransactionTemplate(new JdbcTransactionManager(source));
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        }

        Access access() {
            return access;
        }

        JdbcTemplate jdbc() {
            return jdbc;
        }

        TransactionTemplate transaction() {
            return transaction;
        }
    }

    public record Actor(UUID id, long securityRevision) {}

    @FunctionalInterface
    public interface Operation<T> {
        /** Invoke only scoped routines that authorize capability and targets. Return a fully materialized value. */
        T materialize(JdbcTemplate connection, Actor actor);
    }
}
