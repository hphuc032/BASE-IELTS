package com.baseielts.platform.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baseielts.platform.content.reading.ReadingStaffApi;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.IMetricsTracker;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "BASE_TEST_READING_COMBINED", matches = "true")
class ReadingStaffPersistencePostgresTest {
    private static final String FRESH = "BASE_TEST_READING_V15_FRESH_URL";
    private static final String UPGRADE = "BASE_TEST_READING_V15_UPGRADE_URL";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String AUDIT_INSERT = """
        INSERT (actor_id, operation, entity_kind, passage_id, passage_version_id, placement_passage_id, revision, request_id)
        ON content_catalog.content_audit_events
        """;

    @BeforeAll
    static void migrate() throws Exception {
        assertNotEquals(url(FRESH), url(UPGRADE));
        for (String fixture : List.of(FRESH, UPGRADE)) {
            var migrated = flyway(fixture, "17.3");
            migrated.validate();
            assertEquals(0, migrated.migrate().migrationsExecuted);
            var admin = owner(fixture);
            assertEquals(957144442, admin.queryForObject("""
                select checksum from identity.flyway_schema_history where version = '16' and success
                """, Integer.class));
            assertEquals(-574889560, admin.queryForObject("""
                select checksum from identity.flyway_schema_history where version = '17.3' and success
                """, Integer.class));
        }
    }

    @Test
    void fencedPassageAuthoringKeepsParagraphIdsAndProjectsAnAllowlistedPreview() throws Exception {
        var h = new Harness(FRESH);
        var editor = h.actor("CONTENT_EDIT");
        var draft = create(h, editor);
        UUID passage = uuid(draft, "stableId");
        UUID edition = uuid(draft, "editionId");
        assertEquals(1, draft.path("revision").asInt());
        UUID topic = h.owner.queryForObject("""
            insert into content_reading.topics(slug,label) values (?, 'Estuaries') returning id
            """, UUID.class, "topic-" + UUID.randomUUID());
        var initial = edit(h, editor, passage, edition, 1L, """
            {"title":"Updated","topicId":"%s","visibility":"PRIVATE","paragraphs":[
              {"content":"First","translations":[{"language":"vi-VN","text":"Một"}]},{"content":"Second"}]}
            """.formatted(topic));
        UUID first = uuid(initial.path("paragraphs").get(0), "paragraphId");
        UUID second = uuid(initial.path("paragraphs").get(1), "paragraphId");
        var reordered = edit(h, editor, passage, edition, 2L, """
            {"title":"Reordered","topicId":"%s","visibility":"PRIVATE","paragraphs":[
              {"paragraphId":"%s","content":"Second edited"},
              {"paragraphId":"%s","content":"First","translations":[{"language":"vi-VN","text":"Một"}]}]}
            """.formatted(topic, second, first));
        assertEquals(3, reordered.path("revision").asInt());
        assertEquals(second, uuid(reordered.path("paragraphs").get(0), "paragraphId"));
        assertEquals(first, uuid(reordered.path("paragraphs").get(1), "paragraphId"));
        assertEquals(topic, uuid(reordered, "topicId"));
        assertEquals("Một", reordered.at("/paragraphs/1/translations/0/text").asText());
        assertEquals(3, h.auditCount(passage));

        var preview = h.auth.execute(request("GET", editor, true), h.reader, (jdbc, actor) -> {
            String identity = transactionIdentity(jdbc);
            var response = jdbc.queryForObject("select reading_staff.get_preview(?,?,?,?)::text", String.class,
                    actor.id(), actor.securityRevision(), passage, edition);
            assertEquals(identity, transactionIdentity(jdbc));
            h.assertAccountLocked(actor.id());
            return parse(response);
        });
        assertTrue(preview.at("/preview/draft").asBoolean());
        assertEquals("Second edited", preview.at("/preview/paragraphs/0/body").asText());
        assertFalse(preview.toString().matches("(?is).*(paragraphId|marking|explanation|evidence|audit|instructions).*"));
        assertEquals(3, h.auditCount(passage));
        var page = read(h, editor, "select reading_staff.list_passages(?,?,?,?,?,?,?)::text", 0, 10,
                "Reordered", topic, "DRAFT");
        assertEquals(1, page.at("/page/totalElements").asInt());
        assertEquals(passage, uuid(page.at("/data/0"), "stableId"));
        state("RD003", () -> read(h, editor, "select reading_staff.get_edition(?,?,?,?,?)::text",
                UUID.randomUUID(), edition, true));
        state("RD006", () -> edit(h, editor, passage, edition, 2L, basic()));
        state("RD003", () -> edit(h, editor, passage, edition, 3L, """
            {"title":"Wrong","visibility":"PRIVATE","paragraphs":[{"paragraphId":"%s","content":"Other"}]}
            """.formatted(UUID.randomUUID())));
        assertEquals(3, h.auditCount(passage));
    }

    @Test
    void clonesFrozenGraphWithNewParagraphIdsAndCannotEditItsSource() {
        var h = new Harness(UPGRADE);
        var editor = h.actor("CONTENT_EDIT");
        var draft = create(h, editor);
        UUID passage = uuid(draft, "stableId");
        UUID edition = uuid(draft, "editionId");
        var saved = edit(h, editor, passage, edition, 1L, basic());
        UUID paragraph = uuid(saved.at("/paragraphs/0"), "paragraphId");
        state("RD004", () -> clone(h, editor, passage, edition));
        // Seed a frozen edition as fixture owner. No publication API is involved.
        h.owner.update("update content_reading.passage_versions set state='FROZEN',frozen_at=clock_timestamp() where id=?", edition);
        state("RD004", () -> edit(h, editor, passage, edition, 2L, basic()));
        var cloned = clone(h, editor, passage, edition);
        assertEquals(2, cloned.path("versionNumber").asInt());
        assertEquals(1, cloned.path("revision").asInt());
        assertNotEquals(paragraph, uuid(cloned.at("/paragraphs/0"), "paragraphId"));
        assertEquals(paragraph, uuid(read(h, editor, "select reading_staff.get_edition(?,?,?,?,?)::text",
                passage, edition, false).at("/paragraphs/0"), "paragraphId"));
        assertEquals(3, h.auditCount(passage));
        h.owner.update("update content_reading.passages set archived_at=clock_timestamp() where id=?", passage);
        var page = read(h, editor, "select reading_staff.list_passages(?,?,?,?,?,?,?)::text", 0, 10,
                draft.path("slug").asText(), null, null);
        assertEquals(0, page.at("/page/totalElements").asInt());
        assertEquals("FROZEN", read(h, editor, "select reading_staff.get_edition(?,?,?,?,?)::text",
                passage, edition, false).path("state").asText());
        h.owner.update("update content_reading.passage_versions set state='FROZEN',frozen_at=clock_timestamp() where id=?",
                uuid(cloned, "editionId"));
        state("RD004", () -> clone(h, editor, passage, edition));
    }

    @Test
    void requiresAuthCsrfActiveAdminAndCapabilityAndKeepsRuntimeAwayFromTables() {
        var h = new Harness(FRESH);
        var editor = h.actor("CONTENT_EDIT");
        var publisher = h.actor("CONTENT_PUBLISH");
        var noGrant = h.actor(null);
        var draft = create(h, editor);
        UUID passage = uuid(draft, "stableId");
        UUID edition = uuid(draft, "editionId");
        assertEquals("DRAFT", read(h, publisher, "select reading_staff.get_edition(?,?,?,?,?)::text",
                passage, edition, true).path("state").asText());
        state("RD002", () -> edit(h, publisher, passage, edition, 1L, basic()));
        state("RD002", () -> read(h, noGrant, "select reading_staff.get_edition(?,?,?,?,?)::text", passage, edition, true));
        assertEquals(StaffAuthentication.Rejection.CSRF_INVALID,
            assertThrows(StaffAuthentication.Denied.class, () -> h.auth.execute(request("PUT", editor, false),
                h.writer, (jdbc, actor) -> fail("Missing CSRF reached the business callback"))).error());
        assertEquals(StaffAuthentication.Rejection.AUTH_REQUIRED,
            assertThrows(StaffAuthentication.Denied.class, () -> h.auth.execute(new MockHttpServletRequest("GET",
                "/api/v1/teacher/reading"), h.reader, (jdbc, actor) -> fail("Anonymous request reached the business callback"))).error());
        h.owner.update("update identity.users set status='DISABLED', security_revision=security_revision+1 where id=?",
                editor.binding().account);
        assertThrows(StaffAuthentication.Denied.class, () -> read(h, editor,
                "select reading_staff.get_edition(?,?,?,?,?)::text", passage, edition, true));
        h.now.set(Duration.ofMinutes(60).toNanos());
        assertThrows(StaffAuthentication.Denied.class, () -> read(h, publisher,
                "select reading_staff.get_edition(?,?,?,?,?)::text", passage, edition, true));

        for (var access : StaffAuthentication.Access.values()) {
            var login = new JdbcTemplate(source(FRESH, access.principal(), staffPassword(access)));
            state("42501", () -> login.queryForObject("select count(*) from content_reading.paragraphs", Long.class));
            state("42501", () -> login.update("delete from content_catalog.content_audit_events where false"));
            state("42501", () -> login.queryForObject("select reading_staff.materialize_edition(?,?,?,?)::text",
                    String.class, passage, edition, true, false));
        }
        assertEquals(1, h.auditCount(passage));
    }

    @Test
    void auditFailureRollsBackPassageAndPlacementAndCasRetainsTheTombstone() throws SQLException {
        var h = new Harness(UPGRADE);
        var editor = h.actor("CONTENT_EDIT");
        var draft = create(h, editor);
        UUID passage = uuid(draft, "stableId");
        UUID edition = uuid(draft, "editionId");
        UUID lesson = h.lesson();
        assertEquals(1, place(h, editor, passage, lesson, 1, 0L).path("revision").asInt());
        h.owner.execute("REVOKE " + AUDIT_INSERT + " FROM content_reading_writer_owner");
        try {
            state("42501", () -> edit(h, editor, passage, edition, 1L, basic()));
            state("42501", () -> place(h, editor, passage, null, null, 1L));
        } finally {
            h.owner.execute("GRANT " + AUDIT_INSERT + " TO content_reading_writer_owner");
        }
        var unchanged = read(h, editor, "select reading_staff.get_edition(?,?,?,?,?)::text", passage, edition, true);
        assertEquals(1, unchanged.path("revision").asInt());
        assertEquals(0, unchanged.path("paragraphs").size());
        assertEquals(lesson, h.owner.queryForObject("select lesson_id from content_reading.placement where passage_id=?",
                UUID.class, passage));
        assertEquals(2, h.auditCount(passage));
        var tombstone = place(h, editor, passage, null, null, 1L);
        assertTrue(tombstone.path("lessonId").isNull());
        assertEquals(2, tombstone.path("revision").asInt());
        state("RD006", () -> place(h, editor, passage, lesson, 1, 0L));
        assertEquals(3, place(h, editor, passage, lesson, 2, 2L).path("revision").asInt());
        assertEquals(4, h.auditCount(passage));
        assertEquals(4, h.owner.queryForObject("""
            select count(*) from content_catalog.content_audit_events where passage_id=? and actor_id=?
            """, Integer.class, passage, editor.binding().account));
        // Navigation changes do not acquire the Passage authoring row lock.
        try (var observer = h.owner.getDataSource().getConnection();
             var lock = observer.prepareStatement("select id from content_reading.passages where id=? for no key update")) {
            observer.setAutoCommit(false);
            lock.setObject(1, passage);
            try (var row = lock.executeQuery()) { assertTrue(row.next()); }
            assertEquals(4, place(h, editor, passage, null, null, 3L).path("revision").asInt());
            observer.rollback();
        }
    }

    @Test
    void interveningPlacementMoveInvalidatesNavigationLocksEvenIfExpectedRevisionMatches() throws Exception {
        var h = new Harness(FRESH);
        var admin = h.actor("CONTENT_EDIT");
        UUID passage = uuid(create(h, admin), "stableId");
        UUID initial = h.lesson();
        UUID requested = h.lesson();
        UUID intervening = h.lesson();
        place(h, admin, passage, initial, 1, 0L);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor();
             var change = h.owner.getDataSource().getConnection()) {
            change.setAutoCommit(false);
            int holder;
            try (var query = change.createStatement(); var row = query.executeQuery("select pg_backend_pid()")) {
                assertTrue(row.next());
                holder = row.getInt(1);
            }
            try (var lock = change.prepareStatement("""
                select c.id from content_catalog.course c
                join content_catalog.course_level v on v.course_id=c.id
                join content_catalog.reading_lesson l on l.level_id=v.id
                where l.id=? for update of c
                """)) {
                lock.setObject(1, initial);
                try (var row = lock.executeQuery()) { assertTrue(row.next()); }
            }
            var ready = new CompletableFuture<Integer>();
            var request = workers.submit(() -> h.auth.execute(request("PUT", admin, true), h.writer, (jdbc, actor) -> {
                ready.complete(pid(jdbc));
                return placementCommand(jdbc, actor, passage, requested, 2, 2);
            }));
            try {
                assertBlocked(h.owner, ready.get(2, TimeUnit.SECONDS), holder);
                // An isolated fixture transaction supplies the intervening row state.
                try (var update = change.prepareStatement("""
                    update content_reading.placement set lesson_id=?, revision=2 where passage_id=? and revision=1
                    """)) {
                    update.setObject(1, intervening);
                    update.setObject(2, passage);
                    assertEquals(1, update.executeUpdate());
                }
                change.commit();
                assertEquals("RD006", sqlState(assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> request.get(4, TimeUnit.SECONDS))));
            } finally {
                change.rollback();
            }
        }
        assertEquals(intervening, h.owner.queryForObject("select lesson_id from content_reading.placement where passage_id=?",
                UUID.class, passage));
        assertEquals(2, h.auditCount(passage));
    }

    @Test
    void loopbackStaffHttpUsesRealRoutinesAndMeasuresConcurrentDraftReads() throws Exception {
        var h = new Harness(FRESH);
        var editors = List.of(h.actor("CONTENT_EDIT"), h.actor("CONTENT_EDIT"), h.actor("CONTENT_EDIT"),
                h.actor("CONTENT_EDIT"));
        var readerMetrics = new PoolEvidence();
        var writerMetrics = new PoolEvidence();
        try (var readerPool = pool(FRESH, StaffAuthentication.Access.READ, readerMetrics);
             var writerPool = pool(FRESH, StaffAuthentication.Access.WRITE, writerMetrics);
             var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                     .connectTimeout(Duration.ofSeconds(2)).build()) {
            var databases = new ReadingStaffApi.Databases(
                    new StaffAuthentication.Database(StaffAuthentication.Access.READ, new ObservedDatabase(readerPool, readerMetrics)),
                    new StaffAuthentication.Database(StaffAuthentication.Access.WRITE, new ObservedDatabase(writerPool, writerMetrics)));
            var configuration = Class.forName("com.baseielts.platform.content.reading.ReadingStaffSecurity");
            var fallback = Class.forName("com.baseielts.platform.config.SecurityConfiguration");
            try (var app = new SpringApplicationBuilder(HttpFixture.class, ReadingStaffApi.class, configuration, fallback)
                    .initializers(ctx -> {
                        var beans = ((org.springframework.context.support.GenericApplicationContext) ctx).getBeanFactory();
                        beans.registerSingleton("staffAuthentication", h.auth);
                        beans.registerSingleton("readingDatabases", databases);
                    })
                    .run("--base.reading.enabled=true", "--server.port=0", "--server.address=127.0.0.1",
                        "--spring.main.banner-mode=off")) {
                String base = "http://127.0.0.1:" + ((ServletWebServerApplicationContext) app).getWebServer().getPort();
                String collection = "/api/v1/teacher/reading";
                assertEquals(401, http(client, base, collection, "GET", null, null, null, false).statusCode());
                assertEquals(401, http(client, base, "/api/v1/reading", "GET", null, null, null, false).statusCode());
                assertEquals(403, http(client, base, collection, "POST", editors.getFirst(),
                    createPayload(), null, false).statusCode());
                var limitEditor = editors.getFirst();
                var limitDraft = http(client, base, collection, "POST", limitEditor, createPayload(), null, true);
                assertEquals(201, limitDraft.statusCode(), limitDraft.body());
                UUID limitRoot = uuid(parse(limitDraft.body()).path("data"), "stableId");
                String limitTarget = limitDraft.headers().firstValue("Location").orElseThrow();
                String paragraph = """
                    {"content":"Boundary paragraph","translations":[{"language":"vi","text":"Vietnamese"},{"language":"fr","text":"French"}]}
                    """;
                String graph = "{\"title\":\"Cardinality boundary\",\"visibility\":\"PRIVATE\",\"paragraphs\":[";
                String atCap = graph + String.join(",", java.util.Collections.nCopies(32, paragraph)) + "]}";
                var savedLimit = http(client, base, limitTarget, "PUT", limitEditor, atCap, "1", true);
                assertEquals(200, savedLimit.statusCode(), savedLimit.body());
                assertEquals(32, parse(savedLimit.body()).at("/data/paragraphs").size());
                assertEquals(2, parse(savedLimit.body()).at("/data/paragraphs/0/translations").size());
                int auditAtCap = h.auditCount(limitRoot);
                String extraParagraph = graph + String.join(",", java.util.Collections.nCopies(33, paragraph)) + "]}";
                String extraTranslation = graph + paragraph.replace("]}", ",{\"language\":\"de\",\"text\":\"German\"}]}") + "]}";
                for (String overLimit : List.of(extraParagraph, extraTranslation)) {
                    var declined = http(client, base, limitTarget, "PUT", limitEditor, overLimit, "2", true);
                    assertEquals(400, declined.statusCode(), declined.body());
                }
                var afterLimits = http(client, base, limitTarget, "GET", limitEditor, null, null, false);
                assertEquals(200, afterLimits.statusCode(), afterLimits.body());
                assertEquals(parse(savedLimit.body()).path("data"), parse(afterLimits.body()).path("data"));
                assertEquals(auditAtCap, h.auditCount(limitRoot));
                List<String> targets = new ArrayList<>();
                List<String> placements = new ArrayList<>();
                List<String> forward = new ArrayList<>();
                List<String> reverse = new ArrayList<>();
                for (var editor : editors) {
                    var created = http(client, base, collection, "POST", editor, createPayload(), null, true);
                    assertEquals(201, created.statusCode(), created.body());
                    String target = created.headers().firstValue("Location").orElseThrow();
                    String initial = draftPayload();
                    var saved = http(client, base, target, "PUT", editor, initial, "1", true);
                    assertEquals(200, saved.statusCode(), saved.body());
                    targets.add(target);
                    var body = parse(saved.body()).path("data");
                    placements.add(collection + "/" + uuid(body, "stableId") + "/placement");
                    forward.add(editPayload(body, false));
                    reverse.add(editPayload(body, true));
                }
                var editor = editors.getFirst();
                String target = targets.getFirst();
                var preview = http(client, base, target + "/preview", "GET", editor, null, null, false);
                assertEquals(200, preview.statusCode(), preview.body());
                assertEquals("no-store", preview.headers().firstValue("Cache-Control").orElseThrow());
                assertTrue(parse(preview.body()).at("/data/draft").asBoolean());
                assertFalse(preview.body().matches("(?is).*(paragraphId|marking|explanation|evidence|audit|instructions).*"));
                assertEquals(412, http(client, base, target, "PUT", editor, basic(), "1", true).statusCode());
                assertEquals(200, http(client, base, collection + "?page=0&size=20", "GET", editor, null, null, false)
                        .statusCode());

                measureHttp("draft GET (32 paragraphs and translations)", readerMetrics, (slot, i) -> {
                    var response = http(client, base, targets.get(slot), "GET", editors.get(slot), null, null, false);
                    assertEquals(32, parse(response.body()).at("/data/paragraphs").size(), response.body());
                    return response;
                });
                measureHttp("draft PUT (reorder and retain 32 UUIDs)", writerMetrics, (slot, i) ->
                    http(client, base, targets.get(slot), "PUT", editors.get(slot),
                        (i % 2 == 0 ? reverse : forward).get(slot), Integer.toString(i + 2), true));
                UUID lesson = h.lesson();
                String place = "{\"lessonId\":\"" + lesson + "\",\"position\":1}";
                measureHttp("placement PUT (assign and unassign)", writerMetrics, (slot, i) ->
                    http(client, base, placements.get(slot), "PUT", editors.get(slot),
                        i % 2 == 0 ? place : "{\"lessonId\":null}", Integer.toString(i), true));

                h.owner.update("""
                    with roots as (
                        insert into content_reading.passages(slug,author_id)
                        select 'corpus-' || gen_random_uuid(), ?
                        from generate_series(1,10000) returning id
                    )
                    insert into content_reading.passage_versions(passage_id,version_no,title,visibility)
                    select id,1,'Curriculum Reading fixture','PRIVATE' from roots
                    """, editor.binding().account);
                h.owner.execute("analyze content_reading.passages; analyze content_reading.passage_versions");
                measureHttp("list GET (10,000 matching roots, exact count)", readerMetrics, (slot, i) -> {
                    var response = http(client, base, collection + "?size=20&page=0&q=Curriculum",
                            "GET", editors.get(slot), null, null, false);
                    assertEquals(10000, parse(response.body()).at("/page/totalElements").asInt(), response.body());
                    return response;
                });
                h.auth.execute(request("GET", editor, false), databases.reader(), (jdbc, actor) -> {
                    var plan = jdbc.queryForList("""
                        explain (analyze, buffers) select reading_staff.list_passages(?,?,0,20,'Curriculum',null,null)
                        """, String.class, actor.id(), actor.securityRevision());
                    System.out.println("Reading exact-count routine EXPLAIN:\n" + String.join("\n", plan));
                    return null;
                });
            }
        }
    }

    private static void measureHttp(String name, PoolEvidence metrics, HttpSample sample) throws Exception {
        metrics.clear();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<List<Long>>> samples = new ArrayList<>();
            for (int slot = 0; slot < 4; slot++) {
                int worker = slot;
                samples.add(workers.submit(() -> {
                    List<Long> times = new ArrayList<>();
                    for (int i = 0; i < 35; i++) {
                        long started = System.nanoTime();
                        var response = sample.request(worker, i);
                        long elapsed = System.nanoTime() - started;
                        assertEquals(200, response.statusCode(), response.body());
                        if (i >= 5) times.add(elapsed);
                    }
                    return times;
                }));
            }
            List<Long> times = new ArrayList<>();
            for (var future : samples) times.addAll(future.get(35, TimeUnit.SECONDS));
            var sorted = times.stream().sorted().toList();
            System.out.printf(Locale.ROOT,
                "Reading loopback %s n=%d concurrency=4 p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms errors=0%n",
                name, times.size(), percentile(sorted, 0.50), percentile(sorted, 0.95), percentile(sorted, 0.99),
                percentile(sorted, 1));
            metrics.report();
            assertTrue(percentile(sorted, 0.95) <= 100 && percentile(sorted, 0.99) <= 200,
                    "PO-09 latency threshold exceeded: " + name);
        }
    }

    private static String editPayload(JsonNode saved, boolean reversed) {
        var draft = JSON.createObjectNode().put("title", "Passage").put("visibility", "PRIVATE");
        var rows = draft.putArray("paragraphs");
        var source = saved.path("paragraphs");
        for (int i = 0; i < source.size(); i++) {
            var paragraph = source.get(reversed ? source.size() - i - 1 : i);
            rows.addObject().put("paragraphId", paragraph.path("paragraphId").asText())
                .put("content", paragraph.path("content").asText()).set("translations", paragraph.path("translations"));
        }
        return draft.toString();
    }

    private static HttpResponse<String> http(HttpClient client, String base, String target, String method,
                                             AuthSessions.Prepared binding, String body, String revision, boolean csrf)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + target)).timeout(Duration.ofSeconds(3));
        if (binding != null) {
            request.header("Cookie", AuthSessions.COOKIE + "=" + binding.token());
            if (csrf) request.header(PreauthStore.HEADER, binding.binding().csrf);
        }
        if (body != null) request.header("Content-Type", "application/json");
        if (revision != null) request.header("If-Match", "\"" + revision + "\"");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String createPayload() {
        return "{\"slug\":\"draft-" + UUID.randomUUID() + "\",\"title\":\"Passage\"}";
    }

    private static String draftPayload() {
        var draft = JSON.createObjectNode().put("title", "Passage").put("visibility", "PRIVATE");
        var rows = draft.putArray("paragraphs");
        for (int i = 1; i <= 32; i++) {
            rows.addObject().put("content", "Paragraph " + i).putArray("translations")
                    .addObject().put("language", "vi-VN").put("text", "Bản dịch " + i);
        }
        return draft.toString();
    }

    private static double percentile(List<Long> sorted, double quantile) {
        return sorted.get((int) Math.ceil(sorted.size() * quantile) - 1) / 1_000_000.0;
    }

    private static HikariDataSource pool(String fixture, StaffAuthentication.Access access, PoolEvidence metrics) {
        var settings = new HikariConfig();
        settings.setDataSource(source(fixture, access.principal(), staffPassword(access)));
        settings.setMaximumPoolSize(4);
        settings.setMinimumIdle(4);
        settings.setMetricsTrackerFactory((name, stats) -> metrics.tracker(stats));
        return new HikariDataSource(settings);
    }

    @FunctionalInterface
    private interface HttpSample {
        HttpResponse<String> request(int slot, int iteration) throws Exception;
    }

    private static final class PoolEvidence {
        private final ConcurrentLinkedQueue<Long> acquired = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Long> used = new ConcurrentLinkedQueue<>();
        private final AtomicLong timeouts = new AtomicLong();
        private final AtomicLong peak = new AtomicLong();
        private final ConcurrentLinkedQueue<Long> transaction = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Long> sql = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Long> fence = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Integer> counts = new ConcurrentLinkedQueue<>();

        IMetricsTracker tracker(com.zaxxer.hikari.metrics.PoolStats stats) {
            return new IMetricsTracker() {
                @Override public void recordConnectionAcquiredNanos(long nanos) {
                    acquired.add(nanos);
                    peak.accumulateAndGet(stats.getActiveConnections(), Math::max);
                }
                @Override public void recordConnectionUsageMillis(long millis) { used.add(millis * 1_000_000); }
                @Override public void recordConnectionTimeout() { timeouts.incrementAndGet(); }
            };
        }

        void clear() {
            acquired.clear();
            used.clear();
            peak.set(0);
            timeouts.set(0);
            transaction.clear();
            sql.clear();
            fence.clear();
            counts.clear();
        }

        void report() {
            System.out.printf(Locale.ROOT, "Reading pool n=%d acquisition-p95=%.3fms checkout-to-return-p95=%.3fms "
                    + "peak-sampled=%d/4 timeouts=%d%n", acquired.size(), percentile(acquired.stream().sorted().toList(), 0.95),
                    percentile(used.stream().sorted().toList(), 0.95), peak.get(), timeouts.get());
            System.out.printf(Locale.ROOT,
                "Reading JDBC n=%d transaction-through-commit-p95=%.3fms SQL-round-trips-p95=%.3fms "
                    + "outer-fence-p95=%.3fms statement-count-min/max=%d/%d%n",
                transaction.size(), percentile(transaction.stream().sorted().toList(), 0.95),
                percentile(sql.stream().sorted().toList(), 0.95), percentile(fence.stream().sorted().toList(), 0.95),
                counts.stream().mapToInt(Integer::intValue).min().orElseThrow(),
                counts.stream().mapToInt(Integer::intValue).max().orElseThrow());
        }
    }

    private static final class ObservedDatabase extends DelegatingDataSource {
        private final PoolEvidence evidence;

        ObservedDatabase(HikariDataSource pool, PoolEvidence evidence) {
            super(pool);
            this.evidence = evidence;
        }

        @Override public Connection getConnection() throws SQLException {
            Connection actual = super.getConnection();
            var observation = new TransactionObservation(actual, evidence);
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    observation::invoke);
        }
    }

    private static final class TransactionObservation {
        private final Connection actual;
        private final PoolEvidence evidence;
        private long started;
        private long sqlNanos;
        private long fenceNanos;
        private int count;

        TransactionObservation(Connection actual, PoolEvidence evidence) {
            this.actual = actual;
            this.evidence = evidence;
        }

        Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("setAutoCommit") && Boolean.FALSE.equals(args[0])) {
                started = System.nanoTime();
            }
            Object result = invokeOn(actual, method, args);
            if (method.getName().equals("commit") && started != 0) {
                evidence.transaction.add(System.nanoTime() - started);
                evidence.sql.add(sqlNanos);
                evidence.fence.add(fenceNanos);
                evidence.counts.add(count);
                started = 0;
            }
            if (result instanceof Statement statement && started != 0) {
                String query = args != null && args.length > 0 && args[0] instanceof String text ? text : "";
                Class<?> type = statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
                    (ignored, statementMethod, statementArgs) -> observe(statement, statementMethod, statementArgs, query));
            }
            return result;
        }

        private Object observe(Statement statement, Method method, Object[] args, String query) throws Throwable {
            if (!method.getName().startsWith("execute")) return invokeOn(statement, method, args);
            long before = System.nanoTime();
            try {
                return invokeOn(statement, method, args);
            } finally {
                long duration = System.nanoTime() - before;
                sqlNanos += duration;
                count++;
                if (query.contains("lockAndCheckActor")) fenceNanos += duration;
            }
        }

        private static Object invokeOn(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException failed) {
                throw failed.getCause();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
    static class HttpFixture {}

    @Test
    void competingFencedWritersAndRevocationSerializeOnRealPostgresRows() throws Exception {
        var h = new Harness(FRESH);
        var first = h.actor("CONTENT_EDIT");
        // Isolate the PostgreSQL barrier from collisions in the existing 64-stripe JVM account guard.
        UUID secondId = UUID.randomUUID();
        while (Math.floorMod(secondId.hashCode(), 64) == Math.floorMod(first.binding().account.hashCode(), 64)) {
            secondId = UUID.randomUUID();
        }
        var second = h.actor("CONTENT_EDIT", secondId);
        var draft = create(h, first);
        UUID root = uuid(draft, "stableId");
        UUID edition = uuid(draft, "editionId");
        UUID lesson = h.lesson();
        assertEquals(1, compete(h, first, second,
                (jdbc, actor) -> placementCommand(jdbc, actor, root, lesson, 1, 0),
                (jdbc, actor) -> placementCommand(jdbc, actor, root, lesson, 2, 0)).path("revision").asInt());
        assertEquals(2, compete(h, first, second,
                (jdbc, actor) -> placementCommand(jdbc, actor, root, null, null, 1),
                (jdbc, actor) -> placementCommand(jdbc, actor, root, lesson, 2, 1)).path("revision").asInt());
        assertEquals(2, compete(h, first, second,
                (jdbc, actor) -> editCommand(jdbc, actor, root, edition, 1),
                (jdbc, actor) -> editCommand(jdbc, actor, root, edition, 1)).path("revision").asInt());
        assertEquals(4, h.auditCount(root));

        h.auth.execute(request("GET", first, true), h.reader, (jdbc, actor) -> {
            String identity = transactionIdentity(jdbc);
            assertNotNull(command(jdbc, actor, "select reading_staff.get_edition(?,?,?,?,?)::text", root, edition, true));
            h.assertAccountLocked(actor.id());
            assertEquals(identity, transactionIdentity(jdbc));
            return null;
        });
        h.owner.update("update identity.users set security_revision=security_revision+1 where id=?", first.binding().account);
        assertThrows(StaffAuthentication.Denied.class, () -> read(h, first,
                "select reading_staff.get_edition(?,?,?,?,?)::text", root, edition, true));
    }

    @Test
    void catalogRollbackReleasesNavigationLocksAndKeepsReadingPlacementAtomic() throws Exception {
        var h = new Harness(UPGRADE);
        var catalog = h.actor("CONTENT_EDIT", new UUID(0, 1001));
        var reader = h.actor("CONTENT_EDIT", new UUID(0, 1002));
        UUID passage = uuid(create(h, reader), "stableId");
        UUID lesson = h.lesson();
        UUID catalogRequest = UUID.randomUUID();
        int before = h.auditCount(passage);
        var catalogLocked = new CompletableFuture<Integer>();
        var placementStarted = new CompletableFuture<Integer>();
        var rollback = new CompletableFuture<Void>();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var mutation = workers.submit(() -> assertThrows(IllegalStateException.class, () ->
                h.auth.execute(request("POST", catalog, true), h.writer, (jdbc, actor) -> {
                    jdbc.queryForMap("select * from catalog_staff.mutate_item(?,?,?,?,?,?,?,?,?,?,?,?)",
                        "LESSON", "CHANGE_STATUS", actor.id(), actor.securityRevision(), lesson,
                        null, null, null, null, "INACTIVE", 1L, catalogRequest);
                    catalogLocked.complete(pid(jdbc));
                    rollback.orTimeout(2, TimeUnit.SECONDS).join();
                    throw new IllegalStateException("Intentional fixture rollback");
                })));
            try {
                int holder = catalogLocked.get(4, TimeUnit.SECONDS);
                var placement = workers.submit(() -> h.auth.execute(request("PUT", reader, true), h.writer, (jdbc, actor) -> {
                    placementStarted.complete(pid(jdbc));
                    return placementCommand(jdbc, actor, passage, lesson, 1, 0);
                }));
                assertBlocked(h.owner, placementStarted.get(2, TimeUnit.SECONDS), holder);
                rollback.complete(null);
                mutation.get(4, TimeUnit.SECONDS);
                assertEquals(1, placement.get(4, TimeUnit.SECONDS).path("revision").asInt());
            } finally {
                rollback.complete(null);
            }
        }
        assertEquals("ACTIVE", h.owner.queryForObject(
            "select status::text from content_catalog.reading_lesson where id=?", String.class, lesson));
        assertEquals(0, h.owner.queryForObject(
            "select count(*) from content_catalog.content_audit_events where request_id=?", Integer.class, catalogRequest));
        assertEquals(before + 1, h.auditCount(passage));
        assertEquals(lesson, h.owner.queryForObject(
            "select lesson_id from content_reading.placement where passage_id=?", UUID.class, passage));
    }

    @Test
    void overBudgetDraftCommandLeavesRevisionGraphAndAuditUnchanged() {
        var h = new Harness(FRESH);
        var editor = h.actor("CONTENT_EDIT");
        var draft = create(h, editor);
        UUID root = uuid(draft, "stableId");
        UUID edition = uuid(draft, "editionId");
        int auditBefore = h.auditCount(root);
        String paragraph = "{\"content\":\"" + "x".repeat(8000) + "\"}";
        String payload = "{\"title\":\"Near boundary\",\"visibility\":\"PRIVATE\",\"paragraphs\":["
            + String.join(",", java.util.Collections.nCopies(66, paragraph)) + "]}";
        assertTrue(payload.length() > 524288);
        state("RD001", () -> edit(h, editor, root, edition, 1L, payload));
        var unchanged = read(h, editor, "select reading_staff.get_edition(?,?,?,?,?)::text", root, edition, true);
        assertEquals(1, unchanged.path("revision").asInt());
        assertTrue(unchanged.path("paragraphs").isEmpty());
        assertEquals(auditBefore, h.auditCount(root));
    }

    private static JsonNode compete(Harness h, AuthSessions.Prepared first, AuthSessions.Prepared second,
                                    BiFunction<JdbcTemplate, StaffAuthentication.Actor, JsonNode> win,
                                    BiFunction<JdbcTemplate, StaffAuthentication.Actor, JsonNode> lose) throws Exception {
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstReady = new CompletableFuture<Integer>();
            var secondReady = new CompletableFuture<Integer>();
            var allowCommit = new CompletableFuture<Void>();
            var firstWriter = workers.submit(() -> h.auth.execute(request("PUT", first, true), h.writer, (jdbc, actor) -> {
                String identity = transactionIdentity(jdbc);
                JsonNode result = win.apply(jdbc, actor);
                assertEquals(identity, transactionIdentity(jdbc));
                firstReady.complete(pid(jdbc));
                allowCommit.orTimeout(2, TimeUnit.SECONDS).join();
                return result;
            }));
            try {
                int holding = firstReady.get(4, TimeUnit.SECONDS);
                var losing = workers.submit(() -> {
                    try {
                        return h.auth.execute(request("PUT", second, true), h.writer, (jdbc, actor) -> {
                            secondReady.complete(pid(jdbc));
                            return lose.apply(jdbc, actor);
                        });
                    } catch (RuntimeException | Error failure) {
                        secondReady.completeExceptionally(failure);
                        throw failure;
                    }
                });
                assertBlocked(h.owner, secondReady.get(2, TimeUnit.SECONDS), holding);
                allowCommit.complete(null);
                var result = firstWriter.get(4, TimeUnit.SECONDS);
                var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> losing.get(4, TimeUnit.SECONDS));
                assertEquals("RD006", sqlState(failure));
                return result;
            } finally {
                allowCommit.complete(null);
            }
        }
    }

    private static JsonNode placementCommand(JdbcTemplate jdbc, StaffAuthentication.Actor actor,
                                             UUID root, UUID lesson, Integer position, long revision) {
        return command(jdbc, actor, "select reading_staff.set_placement(?,?,?,?,?,?,?)::text",
                root, lesson, position, revision, UUID.randomUUID());
    }

    private static JsonNode editCommand(JdbcTemplate jdbc, StaffAuthentication.Actor actor,
                                       UUID root, UUID edition, long revision) {
        return parse(jdbc.queryForObject("select reading_staff.mutate_passage(?,?,?,?,?,?::jsonb,?,?)::text", String.class,
                "EDIT", actor.id(), actor.securityRevision(), root, edition, basic(), revision, UUID.randomUUID()));
    }

    private static final class Harness {
        final JdbcTemplate owner;
        final AtomicLong now = new AtomicLong();
        final AuthSessions sessions = new AuthSessions(now::get);
        final StaffAuthentication auth = new StaffAuthentication(sessions, new AuthenticationService(null, null, null, sessions));
        final StaffAuthentication.Database reader;
        final StaffAuthentication.Database writer;

        Harness(String fixture) {
            owner = ReadingStaffPersistencePostgresTest.owner(fixture);
            reader = database(fixture, StaffAuthentication.Access.READ);
            writer = database(fixture, StaffAuthentication.Access.WRITE);
        }

        AuthSessions.Prepared actor(String capability) {
            return actor(capability, UUID.randomUUID());
        }

        AuthSessions.Prepared actor(String capability, UUID id) {
            UUID admin = account(owner, id);
            owner.update("insert into identity.user_roles(user_id,role_id) select ?,id from identity.roles where code='ADMIN'", admin);
            if (capability != null) {
                owner.update("""
                    insert into content_catalog.content_capability_grants(grantee_id,grantor_id,capability)
                    values (?,?,?::content_catalog.capability)
                    """, admin, account(owner), capability);
            }
            var binding = sessions.prepare(admin, 0);
            sessions.publish(binding);
            return binding;
        }

        int auditCount(UUID passage) {
            return owner.queryForObject("select count(*) from content_catalog.content_audit_events where passage_id=?",
                    Integer.class, passage);
        }

        void assertAccountLocked(UUID account) {
            var transaction = new TransactionTemplate(new JdbcTransactionManager(owner.getDataSource()));
            state("55P03", () -> transaction.executeWithoutResult(status -> {
                owner.execute("set local lock_timeout='60ms'");
                owner.update("update identity.users set security_revision=security_revision+1 where id=?", account);
            }));
        }

        UUID lesson() {
            return owner.queryForObject("""
                with c as (insert into content_catalog.course(slug,title,display_order) values (?,'Course',1) returning id),
                l as (insert into content_catalog.course_level(course_id,code,name,display_order)
                      select id,'a1','Level',1 from c returning id)
                insert into content_catalog.reading_lesson(level_id,slug,title,display_order)
                select id,'reading','Reading',1 from l returning id
                """, UUID.class, "course-" + UUID.randomUUID());
        }
    }

    private static JsonNode create(Harness h, AuthSessions.Prepared actor) {
        return mutate(h, actor, "CREATE", null, null, """
            {"slug":"draft-%s","title":"Passage"}
            """.formatted(UUID.randomUUID()), null);
    }

    private static JsonNode edit(Harness h, AuthSessions.Prepared actor, UUID root, UUID edition, Long revision, String payload) {
        return mutate(h, actor, "EDIT", root, edition, payload, revision);
    }

    private static JsonNode clone(Harness h, AuthSessions.Prepared actor, UUID root, UUID edition) {
        return mutate(h, actor, "CLONE", root, null, "{\"baseVersionId\":\"" + edition + "\"}", null);
    }

    private static JsonNode mutate(Harness h, AuthSessions.Prepared actor, String action, UUID root, UUID edition,
                                   String payload, Long revision) {
        return h.auth.execute(request("PUT", actor, true), h.writer, (jdbc, trusted) ->
            parse(jdbc.queryForObject("select reading_staff.mutate_passage(?,?,?,?,?,?::jsonb,?,?)::text", String.class,
                action, trusted.id(), trusted.securityRevision(), root, edition, payload, revision, UUID.randomUUID())));
    }

    private static JsonNode place(Harness h, AuthSessions.Prepared actor, UUID root, UUID lesson, Integer position, Long revision) {
        return h.auth.execute(request("PUT", actor, true), h.writer, (jdbc, trusted) ->
            command(jdbc, trusted, "select reading_staff.set_placement(?,?,?,?,?,?,?)::text",
                    root, lesson, position, revision, UUID.randomUUID()));
    }

    private static JsonNode read(Harness h, AuthSessions.Prepared actor, String sql, Object... values) {
        return h.auth.execute(request("GET", actor, true), h.reader, (jdbc, trusted) -> command(jdbc, trusted, sql, values));
    }

    private static JsonNode command(JdbcTemplate jdbc, StaffAuthentication.Actor actor, String sql, Object... values) {
        Object[] parameters = new Object[values.length + 2];
        parameters[0] = actor.id();
        parameters[1] = actor.securityRevision();
        System.arraycopy(values, 0, parameters, 2, values.length);
        return parse(jdbc.queryForObject(sql, String.class, parameters));
    }

    private static void assertBlocked(JdbcTemplate observer, int blocked, int holder) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofMillis(150).toNanos();
        while (true) {
            Double wait = observer.queryForObject("""
                select max(extract(epoch from clock_timestamp() - waitstart) * 1000)::double precision
                from pg_locks where pid=? and not granted and ? = any(pg_blocking_pids(pid))
                """, Double.class, blocked, holder);
            if (wait != null) {
                System.out.printf(Locale.ROOT, "Observed PostgreSQL wait before barrier release: %.3fms%n", wait);
                return;
            }
            assertTrue(System.nanoTime() < deadline, "PostgreSQL did not report the placement lock");
            Thread.sleep(2);
        }
    }

    private static String transactionIdentity(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select pg_backend_pid()::text || ':' || pg_current_xact_id()::text", String.class);
    }

    private static int pid(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select pg_backend_pid()", Integer.class);
    }

    private static void state(String expected, Runnable action) {
        assertEquals(expected, sqlState(assertThrows(DataAccessException.class, action::run)));
    }

    private static String sqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) return sql.getSQLState();
        }
        throw new AssertionError("No SQLSTATE", failure);
    }

    private static MockHttpServletRequest request(String method, AuthSessions.Prepared binding, boolean csrf) {
        var request = new MockHttpServletRequest(method, "/api/v1/teacher/reading");
        request.setCookies(new Cookie(AuthSessions.COOKIE, binding.token()));
        if (csrf) request.addHeader(PreauthStore.HEADER, binding.binding().csrf);
        return request;
    }

    private static String basic() {
        return """
            {"title":"Draft edit","visibility":"PRIVATE","paragraphs":[{"content":"One paragraph"}]}
            """;
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.path(field).asText());
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception malformed) {
            throw new AssertionError("Unexpected Reading DTO", malformed);
        }
    }

    private static UUID account(JdbcTemplate db) {
        return account(db, UUID.randomUUID());
    }

    private static UUID account(JdbcTemplate db, UUID id) {
        return db.queryForObject("""
            insert into identity.users(id,canonical_email,display_name,status,password_hash,verified_at,verification_method)
            values (?, ?, 'Reading fixture','ACTIVE','fixture',clock_timestamp(),'MAILBOX_TOKEN') returning id
            """, UUID.class, id, "reading-" + id + "@test.invalid");
    }

    private static JdbcTemplate owner(String fixture) {
        return new JdbcTemplate(source(fixture, System.getenv("BASE_DB_BOOTSTRAP_USER"), System.getenv("BASE_DB_BOOTSTRAP_PASSWORD")));
    }

    private static StaffAuthentication.Database database(String fixture, StaffAuthentication.Access access) {
        return new StaffAuthentication.Database(access, source(fixture, access.principal(), staffPassword(access)));
    }

    private static String staffPassword(StaffAuthentication.Access access) {
        return System.getenv(access == StaffAuthentication.Access.READ
                ? "BASE_TEST_READING_STAFF_READER_PASSWORD" : "BASE_TEST_READING_STAFF_WRITER_PASSWORD");
    }

    private static DriverManagerDataSource source(String fixture, String name, String password) {
        return new DriverManagerDataSource(url(fixture), name, password);
    }

    private static Flyway flyway(String fixture, String target) {
        return Flyway.configure().dataSource(url(fixture), "base_ielts_migration", System.getenv("BASE_DB_MIGRATION_PASSWORD"))
                .defaultSchema("identity").initSql("SET ROLE identity_migration_owner").target(target).load();
    }

    private static String url(String fixture) {
        String url = System.getenv(fixture);
        assertTrue(url != null && url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[1-9][0-9]{0,4}/staff_(fresh|placement)_[a-f0-9]{32}"));
        return url;
    }
}
