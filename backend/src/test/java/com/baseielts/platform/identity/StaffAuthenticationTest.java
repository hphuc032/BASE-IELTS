package com.baseielts.platform.identity;

import jakarta.servlet.http.Cookie;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

class StaffAuthenticationTest {
    @Test
    void anonymousExpiredAndMissingScopedCsrfFailBeforeDatabase() {
        AtomicLong now = new AtomicLong();
        var sessions = new AuthSessions(now::get);
        var authentication = staff(sessions);
        var closedSource = new DriverManagerDataSource("jdbc:unsupported:test");
        var reader = new StaffAuthentication.Database(StaffAuthentication.Access.READ, closedSource);
        var author = new StaffAuthentication.Database(StaffAuthentication.Access.WRITE, closedSource);

        denied(StaffAuthentication.Rejection.AUTH_REQUIRED,
            () -> authentication.execute(new MockHttpServletRequest("GET", "/api/v1/teacher/courses"),
                reader, (jdbc, actor) -> fail("Anonymous request reached the database")));
        AuthSessions.Prepared prepared = sessions.prepare(UUID.randomUUID(), 0);
        sessions.publish(prepared);
        denied(StaffAuthentication.Rejection.CSRF_INVALID,
            () -> authentication.execute(request("PATCH", prepared, null), author,
                (jdbc, actor) -> fail("Request without AUTH CSRF reached the database")));
        denied(StaffAuthentication.Rejection.CSRF_INVALID,
            () -> authentication.execute(request("PATCH", prepared, OpaqueTokens.create(32)), author,
                (jdbc, actor) -> fail("Wrong scoped CSRF reached the database")));
        now.set(Duration.ofMinutes(60).toNanos());
        denied(StaffAuthentication.Rejection.AUTH_REQUIRED,
            () -> authentication.execute(request("GET", prepared, null), reader,
                (jdbc, actor) -> fail("Expired request reached the database")));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BASE_TEST_STAFF_AUTH", matches = "true")
    void usesSameFenceForMaterializationAndRejectsStaleRevisionAndWrongPool() {
        String url = System.getenv("BASE_TEST_STAFF_DB");
        var ownerSource = new DriverManagerDataSource(url, "postgres", System.getenv("BASE_TEST_STAFF_OWNER_PASSWORD"));
        var owner = new JdbcTemplate(ownerSource);
        var ownerTx = new TransactionTemplate(new JdbcTransactionManager(ownerSource));
        UUID account = owner.queryForObject("""
            insert into identity.users(canonical_email,display_name,status,password_hash,verified_at,verification_method)
            values ('staff-bridge@example.test','Staff bridge fixture','ACTIVE','fixture',now(),'MAILBOX_TOKEN')
            returning id
            """, UUID.class);
        var sessions = new AuthSessions();
        var authentication = staff(sessions);
        AuthSessions.Prepared first = sessions.prepare(account, 0);
        sessions.publish(first);
        var reader = database(StaffAuthentication.Access.READ, url, "BASE_TEST_STAFF_READER_PASSWORD");
        var author = database(StaffAuthentication.Access.WRITE, url, "BASE_TEST_STAFF_WRITER_PASSWORD");
        var wrongPool = new StaffAuthentication.Database(StaffAuthentication.Access.READ, ownerSource);
        assertThrows(PermissionDeniedDataAccessException.class,
            () -> authentication.execute(request("GET", first, null), wrongPool,
                (jdbc, actor) -> fail("Wrong principal reached operation")));

        Long revision = authentication.execute(request("GET", first, null), reader, (jdbc, actor) -> {
            assertEquals(account, actor.id());
            assertEquals("content_staff_reader", jdbc.queryForObject("select session_user::text", String.class));
            String transaction = transactionIdentity(jdbc);
            // A separate database connection must be unable to update the row while the DTO is built.
            assertFenced(ownerTx, owner, account);
            assertEquals(transaction, transactionIdentity(jdbc));
            return actor.securityRevision();
        });
        assertEquals(0L, revision);
        assertEquals(1, owner.update("update identity.users set security_revision = 1 where id = ?", account));
        denied(StaffAuthentication.Rejection.AUTH_REQUIRED,
            () -> authentication.execute(request("GET", first, null), reader,
                (jdbc, actor) -> fail("Stale revision reached operation")));
        assertNull(sessions.lookup(first.token()));

        AuthSessions.Prepared current = sessions.prepare(account, 1);
        sessions.publish(current);
        assertEquals("content_author_writer", authentication.execute(request("PATCH", current, current.binding().csrf),
            author, (jdbc, actor) -> {
                String transaction = transactionIdentity(jdbc);
                assertEquals(1, actor.securityRevision());
                assertFenced(ownerTx, owner, account);
                assertEquals(transaction, transactionIdentity(jdbc));
                return jdbc.queryForObject("select session_user::text", String.class);
            }));
        assertEquals(1, owner.update("update identity.users set status = 'DISABLED' where id = ?", account));
        denied(StaffAuthentication.Rejection.AUTH_REQUIRED,
            () -> authentication.execute(request("GET", current, null), reader,
                (jdbc, actor) -> fail("Inactive account reached operation")));
    }

    private static void assertFenced(TransactionTemplate ownerTx, JdbcTemplate owner, UUID account) {
        DataAccessException blocked = assertThrows(DataAccessException.class, () -> ownerTx.executeWithoutResult(status -> {
            owner.execute("set local lock_timeout = '80ms'");
            owner.update("update identity.users set security_revision = security_revision + 1 where id = ?", account);
        }));
        SQLException sql = assertInstanceOf(SQLException.class, blocked.getMostSpecificCause());
        assertEquals("55P03", sql.getSQLState());
    }

    private static String transactionIdentity(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select pg_backend_pid()::text || ':' || pg_current_xact_id()::text", String.class);
    }

    private static StaffAuthentication.Database database(StaffAuthentication.Access access, String url, String password) {
        return new StaffAuthentication.Database(access,
            new DriverManagerDataSource(url, access.principal(), System.getenv(password)));
    }

    private static StaffAuthentication staff(AuthSessions sessions) {
        return new StaffAuthentication(sessions, new AuthenticationService(null, null, null, sessions));
    }

    private static MockHttpServletRequest request(String method, AuthSessions.Prepared prepared, String csrf) {
        var request = new MockHttpServletRequest(method, "/api/v1/teacher/courses");
        request.setCookies(new Cookie(AuthSessions.COOKIE, prepared.token()));
        if (csrf != null) {
            request.addHeader(PreauthStore.HEADER, csrf);
        }
        return request;
    }

    private static void denied(StaffAuthentication.Rejection expected, Runnable request) {
        assertEquals(expected, assertThrows(StaffAuthentication.Denied.class, request::run).error());
    }
}
