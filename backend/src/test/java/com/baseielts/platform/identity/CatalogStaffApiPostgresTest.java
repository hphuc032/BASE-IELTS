package com.baseielts.platform.identity;

import com.baseielts.platform.content.CatalogStaffApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "BASE_TEST_CATALOG_STAFF_API", matches = "true")
class CatalogStaffApiPostgresTest {
    private static final String ROOT = "/api/v1/teacher/";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AtomicLong sessionTime = new AtomicLong();
    private final AuthSessions sessions = new AuthSessions(sessionTime::get);
    private DriverManagerDataSource ownerSource;
    private JdbcTemplate owner;
    private UUID grantor;
    private HikariDataSource readerPool;
    private HikariDataSource writerPool;
    private MeasuredPool reader;
    private MeasuredPool writer;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        String url = System.getenv("BASE_TEST_STAFF_DB");
        assertTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/staff_(fresh|upgrade)_[0-9a-f]{32}"));
        ownerSource = new DriverManagerDataSource(url, "postgres", System.getenv("BASE_TEST_STAFF_OWNER_PASSWORD"));
        owner = new JdbcTemplate(ownerSource);
        readerPool = pool(url, "content_staff_reader", "BASE_TEST_STAFF_READER_PASSWORD");
        writerPool = pool(url, "content_author_writer", "BASE_TEST_STAFF_WRITER_PASSWORD");
        reader = new MeasuredPool(readerPool);
        writer = new MeasuredPool(writerPool);
        var authentication = new StaffAuthentication(sessions, new AuthenticationService(null, null, null, sessions));
        var databases = new CatalogStaffApi.Databases(reader, writer);
        mvc = MockMvcBuilders.standaloneSetup(new CatalogStaffApi(authentication, databases)).build();
        grantor = account(true, null).account();
    }

    @AfterEach
    void closePools() {
        if (readerPool != null) {
            readerPool.close();
        }
        if (writerPool != null) {
            writerPool.close();
        }
    }

    @Test
    void catalogCrudUsesScopedRolesExactPagingAndRevisionPrecedence() throws Exception {
        var editor = account(true, "CONTENT_EDIT");
        var publisher = account(true, "CONTENT_PUBLISH");
        var learner = account(false, "CONTENT_EDIT");
        String courseBody = """
            {"slug":"foundation","title":"Practice %_ Set","displayOrder":1}
            """;
        mvc.perform(post(ROOT + "courses").contentType("text/plain").content("{}")).andExpect(status().is(415));
        mvc.perform(post(ROOT + "courses").contentType("application/json").content(new byte[32769]))
            .andExpect(status().is(413));
        mvc.perform(post(ROOT + "courses").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(cookie(post(ROOT + "courses"), editor).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(body(post(ROOT + "courses"), publisher, "{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(cookie(get(ROOT + "courses/invalid"), learner))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(body(post(ROOT + "courses"), editor, "{}")).andExpect(status().isBadRequest());

        MvcResult course = mvc.perform(body(post(ROOT + "courses"), editor, courseBody))
            .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"1\""))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.slug").value("foundation")).andReturn();
        String courseId = id(course);
        assertEquals(ROOT + "courses/" + courseId, course.getResponse().getHeader("Location"));
        mvc.perform(body(post(ROOT + "courses"), editor, courseBody)).andExpect(status().isConflict());
        mvc.perform(cookie(get(ROOT + "courses/" + courseId), publisher))
            .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.parentId").doesNotExist());

        String levelBody = """
            {"code":"a1","name":"Intro Level","displayOrder":2}
            """;
        mvc.perform(body(post(ROOT + "courses/" + UUID.randomUUID() + "/levels"), editor, levelBody))
            .andExpect(status().isNotFound());
        MvcResult level = mvc.perform(body(post(ROOT + "courses/" + courseId + "/levels"), editor, levelBody))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.parentId").value(courseId))
            .andExpect(jsonPath("$.data.code").value("a1")).andExpect(jsonPath("$.data.name").value("Intro Level"))
            .andReturn();
        String levelId = id(level);
        MvcResult lesson = mvc.perform(body(post(ROOT + "levels/" + levelId + "/lessons"), editor, """
            {"slug":"first","title":"First lesson","displayOrder":1}
            """)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.parentId").value(levelId)).andReturn();
        mvc.perform(cookie(get(ROOT + "levels/" + levelId), publisher))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.parentId").value(courseId));
        mvc.perform(cookie(get(ROOT + "lessons/" + id(lesson)), publisher))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.parentId").value(levelId));

        mvc.perform(cookie(get(ROOT + "courses").param("q", "%_").param("size", "1"), publisher))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(courseId))
            .andExpect(jsonPath("$.page.totalElements").value(1)).andExpect(jsonPath("$.page.totalPages").value(1));
        mvc.perform(cookie(get(ROOT + "courses").param("q", "%_").param("size", "1").param("page", "1"), publisher))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(cookie(get(ROOT + "levels/" + levelId + "/lessons"), publisher))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id(lesson)));
        mvc.perform(cookie(get(ROOT + "courses").param("q", "%' OR true --"), publisher))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(cookie(get(ROOT + "courses").param("page", "100"), publisher)).andExpect(status().isBadRequest());

        mvc.perform(body(patch(ROOT + "levels/" + levelId), editor, levelBody)).andExpect(status().is(428));
        mvc.perform(body(patch(ROOT + "levels/" + UUID.randomUUID()).header("If-Match", "*"), editor, levelBody))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(body(patch(ROOT + "levels/" + levelId).header("If-Match", "*"), editor, levelBody))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_PRECONDITION"));
        mvc.perform(body(patch(ROOT + "levels/" + levelId).header("If-Match", "\"1\""), editor, """
            {"code":"a2","name":"Updated","displayOrder":3}
            """)).andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\""))
            .andExpect(jsonPath("$.data.parentId").value(courseId));
        mvc.perform(body(patch(ROOT + "levels/" + levelId).header("If-Match", "\"1\""), editor, levelBody))
            .andExpect(status().is(412));
        mvc.perform(body(post(ROOT + "lessons/" + id(lesson) + "/status").header("If-Match", "\"1\""), editor,
            "{\"status\":\"ARCHIVED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mvc.perform(body(post(ROOT + "lessons/" + id(lesson) + "/status").header("If-Match", "\"1\""), editor,
            "{\"status\":\"ACTIVE\"}")).andExpect(status().isConflict());
        assertEquals(5L, owner.queryForObject("select count(*) from content_catalog.content_audit_events where actor_id = ?",
            Long.class, editor.account()));
        assertEquals(0L, owner.queryForObject("select count(*) from content_catalog.content_audit_events where actor_id = ?",
            Long.class, publisher.account()));
    }

    @Test
    void fixturePrivilegesAndRevocationFailClosed() throws Exception {
        var administrator = account(true, "CONTENT_EDIT");
        assertFalse(owner.queryForObject("""
            select has_table_privilege('content_staff_reader','content_catalog.course','select')
                or has_table_privilege('content_author_writer','content_catalog.course','update')
                or has_table_privilege('content_author_writer','content_catalog.content_audit_events','insert')
                or has_function_privilege('content_staff_reader',
                    'account_fence_routines."lockForLogin"(uuid)', 'execute')
                or has_function_privilege('content_author_writer',
                    'catalog_staff.get_item(text,uuid,bigint,uuid)', 'execute')
                or has_function_privilege('content_staff_reader',
                    'catalog_staff.mutate_item(text,text,uuid,bigint,uuid,text,text,text,integer,text,bigint,uuid)','execute')
                or has_function_privilege('content_staff_reader',
                    'catalog_staff.require_access(uuid,bigint,boolean)','execute')
            """, Boolean.class));
        assertTrue(owner.queryForObject("""
            select has_function_privilege('content_staff_reader','catalog_staff.get_item(text,uuid,bigint,uuid)','execute')
               and has_function_privilege('content_author_writer',
                    'catalog_staff.mutate_item(text,text,uuid,bigint,uuid,text,text,text,integer,text,bigint,uuid)','execute')
            """, Boolean.class));
        mvc.perform(cookie(get(ROOT + "courses"), administrator)).andExpect(status().isOk());
        try (Connection revoke = ownerSource.getConnection()) {
            revoke.setAutoCommit(false);
            try (PreparedStatement revision = revoke.prepareStatement("""
                update identity.users set security_revision = security_revision + 1 where id = ?
                """); PreparedStatement capability = revoke.prepareStatement("""
                update content_catalog.content_capability_grants
                set revoked_at = greatest(clock_timestamp(), granted_at), revoked_by = grantor_id where grantee_id = ?
                """)) {
                revision.setObject(1, administrator.account());
                capability.setObject(1, administrator.account());
                assertEquals(1, revision.executeUpdate());
                assertEquals(1, capability.executeUpdate());
                revoke.commit();
            }
        }
        mvc.perform(cookie(get(ROOT + "courses"), administrator)).andExpect(status().isUnauthorized());
        var fresh = publish(administrator.account(), 1);
        mvc.perform(cookie(get(ROOT + "courses"), fresh)).andExpect(status().isForbidden());
        assertEquals(0L, owner.queryForObject("select count(*) from content_catalog.content_audit_events where actor_id = ?",
            Long.class, administrator.account()));
    }

    @Test
    void expiredAndDisabledEditorsCannotReadOrWriteCatalog() throws Exception {
        var expired = account(true, "CONTENT_EDIT");
        sessionTime.set(Duration.ofMinutes(60).toNanos());
        var disabled = account(true, "CONTENT_EDIT");
        assertEquals(1, owner.update("""
            update identity.users set status = 'DISABLED', security_revision = security_revision + 1 where id = ?
            """, disabled.account()));

        String input = """
            {"slug":"blocked-editor","title":"Blocked editor fixture","displayOrder":1}
            """;
        for (var actor : new ActorSession[] {expired, disabled}) {
            mvc.perform(cookie(get(ROOT + "courses"), actor)).andExpect(status().isUnauthorized());
            mvc.perform(body(post(ROOT + "courses"), actor, input)).andExpect(status().isUnauthorized());
        }
        assertEquals(0L, owner.queryForObject("select count(*) from content_catalog.course where slug = 'blocked-editor'",
            Long.class));
        assertEquals(0L, owner.queryForObject("select count(*) from content_catalog.content_audit_events where actor_id in (?, ?)",
            Long.class, expired.account(), disabled.account()));
    }

    @Test
    void concurrentRevisionHasOneWinnerAndAuditFailureRollsBack() throws Exception {
        var first = account(true, "CONTENT_EDIT");
        var second = account(true, "CONTENT_EDIT");
        String courseId = id(mvc.perform(body(post(ROOT + "courses"), first, """
            {"slug":"race-fixture","title":"Before","displayOrder":10}
            """)).andExpect(status().isCreated()).andReturn());
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> updateCourse(start, first, courseId, "First edit"));
            var b = workers.submit(() -> updateCourse(start, second, courseId, "Second edit"));
            start.countDown();
            int[] results = {a.get(8, TimeUnit.SECONDS), b.get(8, TimeUnit.SECONDS)};
            Arrays.sort(results);
            assertArrayEquals(new int[] {200, 412}, results);
        }
        assertEquals(2L, owner.queryForObject("select count(*) from content_catalog.content_audit_events where course_id = ?",
            Long.class, UUID.fromString(courseId)));

        owner.execute("""
            create function catalog_staff.reject_test_audit() returns trigger
            language plpgsql set search_path = pg_catalog, pg_temp as $$
            begin
                if exists (select 1 from content_catalog.course where id = new.course_id and slug = 'audit-failure-fixture') then
                    raise exception 'Synthetic audit write failure' using errcode = 'P0001';
                end if;
                return new;
            end $$;
            """);
        owner.execute("""
            create trigger reject_test_audit before insert on content_catalog.content_audit_events
            for each row when (new.course_id is not null) execute function catalog_staff.reject_test_audit()
            """);
        mvc.perform(body(post(ROOT + "courses"), first, """
            {"slug":"audit-failure-fixture","title":"Will roll back","displayOrder":10}
            """)).andExpect(status().isServiceUnavailable());
        assertEquals(0L, owner.queryForObject("""
            select count(*) from content_catalog.course where slug = 'audit-failure-fixture'
            """, Long.class));

        try (Connection lockHolder = ownerSource.getConnection()) {
            lockHolder.setAutoCommit(false);
            try (PreparedStatement hold = lockHolder.prepareStatement("""
                select id from content_catalog.course where id = ? for update
                """)) {
                hold.setObject(1, UUID.fromString(courseId));
                assertTrue(hold.executeQuery().next());
                long started = System.nanoTime();
                mvc.perform(body(patch(ROOT + "courses/" + courseId).header("If-Match", "\"2\""), first, """
                    {"slug":"race-fixture","title":"While locked","displayOrder":10}
                    """)).andExpect(status().isServiceUnavailable());
                System.out.printf(Locale.ROOT, "Catalog G03 held row request: %.3f ms (250 ms configured lock timeout)%n",
                    milliseconds(System.nanoTime() - started));
            } finally {
                lockHolder.rollback();
            }
        }
        assertEquals(2L, owner.queryForObject("select revision from content_catalog.course where id = ?",
            Long.class, UUID.fromString(courseId)));
    }

    @Test
    void measuresConcurrentAuthenticatedCatalogPagesOnFourConnectionPool() throws Exception {
        owner.update("""
            insert into content_catalog.course (slug, title, display_order)
            select 'g03-fixture-' || n, 'Runtime catalog probe ' || n, n / 5 + 1 from generate_series(1, 10000) n
            """);
        var actors = new ArrayList<ActorSession>();
        for (int i = 0; i < 4; i++) {
            actors.add(account(true, "CONTENT_PUBLISH"));
        }
        for (ActorSession actor : actors) {
            performSample(actor);
        }
        reader.reset();
        var requestNanos = new ConcurrentLinkedQueue<Long>();
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(4)) {
            var futures = actors.stream().map(actor -> workers.submit(() -> {
                assertTrue(start.await(2, TimeUnit.SECONDS));
                for (int sample = 0; sample < 30; sample++) {
                    long started = System.nanoTime();
                    performSample(actor);
                    requestNanos.add(System.nanoTime() - started);
                }
                return null;
            })).toList();
            start.countDown();
            for (var future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }
        assertEquals(120, requestNanos.size());
        assertEquals(120, reader.transactionNanos.size());
        assertEquals(720, reader.statements.get(), "Three SET LOCAL, principal, fence, one page/count routine per request");
        printMeasurements("MockMvc AUTH + fence + page/count", requestNanos);
        printMeasurements("JDBC transaction through COMMIT", reader.transactionNanos);
        System.out.printf(Locale.ROOT, "Catalog G03 pool peak %d/4, JDBC statements %d for %d requests%n",
            reader.peakActive.get(), reader.statements.get(), requestNanos.size());
    }

    private void performSample(ActorSession actor) throws Exception {
        mvc.perform(cookie(get(ROOT + "courses").param("q", "Runtime catalog probe").param("page", "99")
            .param("size", "50"), actor)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(50)).andExpect(jsonPath("$.page.totalElements").value(10_000));
    }

    @Test
    void measuresStaffPointAndParentRoutesDuringCatalogWrites() throws Exception {
        UUID course = owner.queryForObject("""
            insert into content_catalog.course (slug, title, display_order)
            values ('g03-parent', 'Parent fixture', 1) returning id
            """, UUID.class);
        owner.update("""
            insert into content_catalog.course (slug, title, display_order)
            select 'g03-mixed-' || n, 'Mixed fixture ' || n, n from generate_series(1, 10000) n
            """);
        owner.update("""
            insert into content_catalog.course_level (course_id, code, name, display_order)
            select id, 'mixed-level', 'Mixed level outside the parent', 1
            from content_catalog.course where slug like 'g03-mixed-%'
            """);
        owner.update("""
            insert into content_catalog.course_level (course_id, code, name, display_order)
            select ?, 'mixed-level-' || n, 'Mixed level ' || n, n from generate_series(1, 200) n
            """, course);
        owner.update("""
            insert into content_catalog.course_level (course_id, code, name, display_order, status)
            values (?, 'mixed-inactive', 'Mixed level inactive', 1, 'INACTIVE')
            """, course);
        owner.execute("analyze content_catalog.course");
        owner.execute("analyze content_catalog.course_level");
        printScopedLevelPlan(course);

        var readers = new ArrayList<ActorSession>();
        for (int i = 0; i < 4; i++) {
            readers.add(account(true, "CONTENT_PUBLISH"));
        }
        var editor = account(true, "CONTENT_EDIT");
        var pointNanos = new ConcurrentLinkedQueue<Long>();
        var parentNanos = new ConcurrentLinkedQueue<Long>();
        var writeNanos = new ConcurrentLinkedQueue<Long>();
        reader.reset();
        writer.reset();
        var ready = new CountDownLatch(5);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(5)) {
            var writes = workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                for (int revision = 1; revision <= 120; revision++) {
                    long started = System.nanoTime();
                    mvc.perform(body(patch(ROOT + "courses/" + course).header("If-Match", "\"" + revision + "\""),
                        editor, """
                        {"slug":"g03-parent","title":"Parent fixture edited","displayOrder":1}
                        """)).andExpect(status().isOk()).andExpect(header().string("ETag", "\"" + (revision + 1) + "\""));
                    writeNanos.add(System.nanoTime() - started);
                }
                return null;
            });
            var reads = readers.stream().map(actor -> workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                for (int sample = 0; sample < 30; sample++) {
                    long started = System.nanoTime();
                    mvc.perform(cookie(get(ROOT + "courses/" + course), actor)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.id").value(course.toString()));
                    pointNanos.add(System.nanoTime() - started);
                    started = System.nanoTime();
                    mvc.perform(cookie(get(ROOT + "courses/" + course + "/levels").param("q", "mixed level")
                        .param("status", "ACTIVE").param("page", "3").param("size", "50"), actor))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(50))
                        .andExpect(jsonPath("$.data[0].displayOrder").value(151))
                        .andExpect(jsonPath("$.page.totalElements").value(200));
                    parentNanos.add(System.nanoTime() - started);
                }
                return null;
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (var read : reads) {
                read.get(60, TimeUnit.SECONDS);
            }
            writes.get(60, TimeUnit.SECONDS);
        }
        assertEquals(120, pointNanos.size());
        assertEquals(120, parentNanos.size());
        assertEquals(120, writeNanos.size());
        assertEquals(120L, owner.queryForObject("select count(*) from content_catalog.content_audit_events where actor_id = ?",
            Long.class, editor.account()));
        printMeasurements("MockMvc mixed load course detail", pointNanos);
        printMeasurements("MockMvc mixed load scoped level page/count", parentNanos);
        printMeasurements("MockMvc mixed load course edit", writeNanos);
        reader.report("reader", 240);
        writer.report("writer", 120);
    }

    private void printScopedLevelPlan(UUID course) {
        var before = owner.queryForList("""
            EXPLAIN (ANALYZE, BUFFERS) WITH filtered AS MATERIALIZED (
                SELECT * FROM catalog_staff.items('LEVEL') i WHERE i.parent_id IS NOT DISTINCT FROM ?
                    AND i.status = 'ACTIVE'
                    AND (strpos(lower(i.label), 'mixed level') > 0 OR strpos(lower(i.item_key), 'mixed level') > 0)
            ), rows AS (
                SELECT * FROM filtered f ORDER BY f.display_order, f.id OFFSET 150 LIMIT 50
            )
            SELECT r.*, t.total FROM (SELECT count(*) AS total FROM filtered) t
            LEFT JOIN rows r ON true ORDER BY r.display_order, r.id
            """, String.class, course);
        var after = owner.queryForList("""
            EXPLAIN (ANALYZE, BUFFERS) WITH filtered AS MATERIALIZED (
                SELECT l.id, l.course_id AS parent_id, l.code AS item_key, l.name AS label, l.description,
                       l.display_order, l.status::text AS status, l.revision, l.created_at, l.updated_at
                FROM content_catalog.course_level l
                WHERE l.course_id = ? AND l.status = 'ACTIVE'
                    AND (strpos(lower(l.name), 'mixed level') > 0 OR strpos(lower(l.code), 'mixed level') > 0)
            ), rows AS (
                SELECT * FROM filtered f ORDER BY f.display_order, f.id OFFSET 150 LIMIT 50
            )
            SELECT r.*, t.total FROM (SELECT count(*) AS total FROM filtered) t
            LEFT JOIN rows r ON true ORDER BY r.display_order, r.id
            """, String.class, course);
        System.out.println("Catalog G03 scoped level inner queries bind parent=" + course
            + " status=ACTIVE q=mixed level offset=150 limit=50");
        System.out.println("Catalog G03 V16 scoped level plan");
        before.forEach(System.out::println);
        System.out.println("Catalog G03 V17.1 scoped level branch plan");
        after.forEach(System.out::println);
    }

    private int updateCourse(CountDownLatch start, ActorSession actor, String courseId, String label) throws Exception {
        assertTrue(start.await(2, TimeUnit.SECONDS));
        var response = mvc.perform(body(patch(ROOT + "courses/" + courseId).header("If-Match", "\"1\""), actor,
            "{\"slug\":\"race-fixture\",\"title\":\"" + label + "\",\"displayOrder\":10}")).andReturn();
        return response.getResponse().getStatus();
    }

    private ActorSession account(boolean admin, String capability) {
        UUID account = owner.queryForObject("""
            insert into identity.users (canonical_email, display_name, status, password_hash, verified_at, verification_method)
            values (?, 'Catalog Auth Fixture', 'ACTIVE', 'fixture', clock_timestamp(), 'MAILBOX_TOKEN') returning id
            """, UUID.class, UUID.randomUUID() + "@catalog-fixture.test");
        if (admin) {
            assertEquals(1, owner.update("""
                insert into identity.user_roles (user_id, role_id) select ?, id from identity.roles where code = 'ADMIN'
                """, account));
        }
        if (capability != null) {
            assertEquals(1, owner.update("""
                insert into content_catalog.content_capability_grants (grantee_id, grantor_id, capability)
                values (?, ?, cast(? as content_catalog.capability))
                """, account, grantor, capability));
        }
        return publish(account, 0);
    }

    private ActorSession publish(UUID account, long revision) {
        AuthSessions.Prepared prepared = sessions.prepare(account, revision);
        sessions.publish(prepared);
        return new ActorSession(account, prepared);
    }

    private static HikariDataSource pool(String url, String login, String passwordVariable) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(login);
        config.setPassword(System.getenv(passwordVariable));
        config.setMaximumPoolSize(4);
        config.setConnectionTimeout(1_000);
        return new HikariDataSource(config);
    }

    private static MockHttpServletRequestBuilder cookie(MockHttpServletRequestBuilder request, ActorSession actor) {
        return request.cookie(new Cookie(AuthSessions.COOKIE, actor.session().token()));
    }

    private static MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, ActorSession actor, String json) {
        return cookie(request, actor).header(PreauthStore.HEADER, actor.session().binding().csrf)
            .contentType("application/json").content(json);
    }

    private static String id(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsByteArray()).path("data").path("id").asText();
    }

    private static void printMeasurements(String label, ConcurrentLinkedQueue<Long> samples) {
        long[] sorted = samples.stream().mapToLong(Long::longValue).sorted().toArray();
        System.out.printf(Locale.ROOT, "Catalog G03 %s n=%d p50=%.3f p95=%.3f p99=%.3f max=%.3f ms%n",
            label, sorted.length, percentile(sorted, 0.50), percentile(sorted, 0.95),
            percentile(sorted, 0.99), milliseconds(sorted[sorted.length - 1]));
    }

    private static double percentile(long[] sorted, double percentile) {
        return milliseconds(sorted[(int) Math.ceil(sorted.length * percentile) - 1]);
    }

    private static double milliseconds(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record ActorSession(UUID account, AuthSessions.Prepared session) {}

    private static final class MeasuredPool extends HikariDataSource {
        private final HikariDataSource pool;
        private final AtomicInteger statements = new AtomicInteger();
        private final AtomicInteger peakActive = new AtomicInteger();
        private final AtomicInteger acquisitionFailures = new AtomicInteger();
        private final ConcurrentLinkedQueue<Long> acquisitionNanos = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Long> transactionNanos = new ConcurrentLinkedQueue<>();

        MeasuredPool(HikariDataSource pool) {

            this.pool = pool;
        }

        @Override
        public int getMaximumPoolSize() {
            return pool.getMaximumPoolSize();
        }

        void reset() {
            statements.set(0);
            peakActive.set(0);
            acquisitionFailures.set(0);
            acquisitionNanos.clear();
            transactionNanos.clear();
        }

        void report(String role, int requests) {
            assertEquals(0, acquisitionFailures.get());
            assertEquals(requests, acquisitionNanos.size());
            assertEquals(requests, transactionNanos.size());
            assertEquals(6 * requests, statements.get());
            printMeasurements(role + " JDBC checkout", acquisitionNanos);
            printMeasurements(role + " JDBC transaction through COMMIT", transactionNanos);
            System.out.printf(Locale.ROOT, "Catalog G03 %s pool peak %d/4, JDBC statements %d for %d requests%n",
                role, peakActive.get(), statements.get(), requests);
        }

        @Override
        public Connection getConnection() throws SQLException {
            long started = System.nanoTime();
            Connection connection;
            try {
                connection = pool.getConnection();
            } catch (SQLException failure) {
                acquisitionFailures.incrementAndGet();
                throw failure;
            }
            acquisitionNanos.add(System.nanoTime() - started);
            peakActive.accumulateAndGet(pool.getHikariPoolMXBean().getActiveConnections(), Math::max);
            long[] transactionStart = {0L};
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    Object result;
                    try {
                        result = method.invoke(connection, arguments);
                    } catch (InvocationTargetException wrapped) {
                        throw wrapped.getCause();
                    }
                    if (method.getName().equals("setAutoCommit") && Boolean.FALSE.equals(arguments[0])) {
                        transactionStart[0] = System.nanoTime();
                    } else if (method.getName().equals("commit") && transactionStart[0] != 0) {
                        transactionNanos.add(System.nanoTime() - transactionStart[0]);
                    } else if (result instanceof Statement statement) {
                        Class<?> type = result instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {type},
                            (statementProxy, sqlMethod, sqlArguments) -> {
                                if (sqlMethod.getName().startsWith("execute")) {
                                    statements.incrementAndGet();
                                }
                                try {
                                    return sqlMethod.invoke(statement, sqlArguments);
                                } catch (InvocationTargetException wrapped) {
                                    throw wrapped.getCause();
                                }
                            });
                    }
                    return result;
                });
        }
    }
}
