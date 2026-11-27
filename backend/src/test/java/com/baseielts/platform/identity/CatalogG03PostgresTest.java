package com.baseielts.platform.identity;

import com.baseielts.platform.BaseIeltsApplication;
import com.baseielts.platform.content.CatalogStaffApi;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Synthetic measurements. The isolated staff fixture must already have completed Flyway and applied test grants. */
@EnabledIfEnvironmentVariable(named = "BASE_TEST_CATALOG_G03", matches = "true")
class CatalogG03PostgresTest {
    private static final String ROOT = "/api/v1/teacher/";
    private static final String SEARCH = "G03%_hit";
    private final AuthSessions sessions = new AuthSessions();
    private final ThreadLocal<Sample> current = new ThreadLocal<>();
    private final Map<String, Sample> wireSamples = new ConcurrentHashMap<>();

    @Test
    @DisabledIfEnvironmentVariable(named = "BASE_TEST_CATALOG_G03_WIRE", matches = "true")
    void meetsPo09ForSearchParentsAndMixedWrites() throws Exception {
        var owner = fixtureOwner();
        String url = System.getenv("BASE_TEST_STAFF_DB");
        try (var reader = pool(url, "content_staff_reader", "BASE_TEST_STAFF_READER_PASSWORD", 4);
             var writer = pool(url, "content_author_writer", "BASE_TEST_STAFF_WRITER_PASSWORD", 2)) {
            var mvc = staffMvc(reader, writer);
            UUID grantor = insertAccount(owner);
            var readers = new ArrayList<AuthSessions.Prepared>();
            for (int i = 0; i < 4; i++) {
                readers.add(staff(owner, grantor, "CONTENT_PUBLISH"));
            }
            var editor = staff(owner, grantor, "CONTENT_EDIT");
            owner.update("""
                insert into content_catalog.course(slug,title,display_order) values ('g03-search-decoy','g03XYhit decoy',1)
                """);

            for (int total : new int[] {1_000, 10_000}) {
                seed(owner, total == 1_000 ? 1 : 1_001, total);
                System.out.printf("Catalog G03 fixture cohort=%d actual=%s%n", total, owner.queryForMap("""
                    select (select count(*) from content_catalog.course) as courses,
                           (select count(*) from content_catalog.course_level) as levels,
                           (select count(*) from content_catalog.reading_lesson) as lessons
                    """));
                int activeMatches = total / 2;
                var result = new ConcurrentLinkedQueue<Sample>();
                UUID course = id(owner, "select id from content_catalog.course where slug = ?", "g03-load-1");
                UUID level = id(owner, "select id from content_catalog.course_level where course_id = ? and code='l1'", course);
                UUID lesson = id(owner, "select id from content_catalog.reading_lesson where level_id = ? and slug='s1'", level);
                UUID nextLesson = id(owner, "select id from content_catalog.reading_lesson where level_id = ? and slug='s2'", level);
                verifyPageAndPlan(owner, mvc, readers.getFirst(), course, level, lesson, activeMatches);
                var jobs = new ArrayList<Callable<Void>>();
                for (var viewer : readers.subList(0, 4)) {
                    jobs.add(() -> {
                        for (int i = 0; i < 25; i++) {
                            run(mvc, get(ROOT + "courses/" + course), viewer, "course-point", 200, result);
                            run(mvc, get(ROOT + "levels/" + level), viewer, "level-point", 200, result);
                            run(mvc, get(ROOT + "lessons/" + lesson), viewer, "lesson-point", 200, result);
                            run(mvc, get(ROOT + "courses/" + course + "/levels"), viewer, "level-list", 200, result);
                            run(mvc, get(ROOT + "levels/" + level + "/lessons"), viewer, "lesson-list", 200, result);
                            run(mvc, get(ROOT + "courses").queryParam("page", "99").queryParam("size", "50"),
                                viewer, "course-unfiltered-list", 200, result);
                            run(mvc, courseSearch(activeMatches / 50 - 1), viewer, "course-exact-search", 200, result);
                        }
                        return null;
                    });
                }
                if (total == 10_000) {
                    jobs.add(() -> {
                        for (int i = 0; i < 25; i++) {
                            var request = patch(ROOT + "lessons/" + nextLesson).contentType("application/json")
                                .header("If-Match", "\"" + (i + 1) + "\"")
                                .header(PreauthStore.HEADER, editor.binding().csrf)
                                .content("{\"slug\":\"s2\",\"title\":\"Reorder\",\"displayOrder\":" + (i + 9) + "}");
                            run(mvc, request, editor, "lesson-reorder", 200, result);
                        }
                        return null;
                    });
                }
                var pressure = runConcurrently(jobs, owner, reader, writer);
                report(total, result, pressure);
                assertPo09(result);
            }
            assertEquals(25L, owner.queryForObject("select count(*) from content_catalog.content_audit_events where actor_id=?",
                Long.class, editor.binding().account));

            try (var holder = owner.getDataSource().getConnection()) {
                holder.setAutoCommit(false);
                try (var lock = holder.prepareStatement("select id from identity.users where id=? for no key update")) {
                    var viewer = readers.getFirst();
                    lock.setObject(1, viewer.binding().account);
                    try (var rows = lock.executeQuery()) {
                        assertTrue(rows.next());
                    }
                    var blocked = new ConcurrentLinkedQueue<Sample>();
                    var pressure = runConcurrently(List.of(() -> {
                        run(mvc, courseSearch(0), viewer, "account-fence-timeout", 503, blocked);
                        return null;
                    }), owner, reader, writer);
                    assertTrue(pressure.lockWaitMillis() > 0, "PostgreSQL must observe the blocked account fence");
                    assertEquals(0, blocked.element().catalogSql, "No projection is allowed after a timed-out fence");
                    report(10_000, blocked, pressure);
                } finally {
                    holder.rollback();
                }
            }
        }
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "BASE_TEST_CATALOG_G03_WIRE", matches = "true")
    void meetsPo10ForAuthenticatedPoolExhaustionAndRecovers() throws Exception {
        var owner = fixtureOwner();
        String url = System.getenv("BASE_TEST_STAFF_DB");
        try (var reader = pool(url, "content_staff_reader", "BASE_TEST_STAFF_READER_PASSWORD", 4);
             var writer = pool(url, "content_author_writer", "BASE_TEST_STAFF_WRITER_PASSWORD", 2)) {
            var mvc = staffMvc(reader, writer);
            UUID grantor = insertAccount(owner);
            var viewers = new ArrayList<AuthSessions.Prepared>();
            for (int i = 0; i < 12; i++) {
                viewers.add(staff(owner, grantor, "CONTENT_PUBLISH"));
            }
            var editor = staff(owner, grantor, "CONTENT_EDIT");
            var hold = new CheckoutHold(new CountDownLatch(4), new CountDownLatch(1));
            reader.checkoutHold = hold;
            try (var holders = Executors.newFixedThreadPool(4)) {
                var admitted = new ConcurrentLinkedQueue<Sample>();
                var requests = viewers.subList(0, 4).stream().map(viewer -> holders.submit(() -> {
                    run(mvc, courseSearch(0), viewer, "admitted-reader", 200, admitted);
                    return null;
                })).toList();
                try {
                    assertTrue(hold.ready().await(5, TimeUnit.SECONDS));
                    assertEquals(4, reader.getHikariPoolMXBean().getActiveConnections());
                    mvc.perform(courseSearch(0)).andExpect(status().isUnauthorized());

                    var rejected = new ConcurrentLinkedQueue<Sample>();
                    List<Callable<Void>> jobs = viewers.subList(4, 12).stream().<Callable<Void>>map(viewer -> () -> {
                        run(mvc, courseSearch(0), viewer, "pre-checkout-overload", 503, rejected);
                        return null;
                    }).toList();
                    var pressure = runConcurrently(jobs, owner, reader, writer);
                    report(0, rejected, pressure);
                    assertEquals(8, rejected.size());
                    assertEquals(0, pressure.readerPending());
                    assertEquals(0, reader.getHikariPoolMXBean().getThreadsAwaitingConnection());
                    assertTrue(rejected.stream().allMatch(sample -> !sample.checkoutAttempted && sample.queries == 0));
                    assertTrue(rejected.stream().allMatch(sample -> sample.http < TimeUnit.MILLISECONDS.toNanos(500)),
                        "Catalog should reject before the 1,000 ms Hikari checkout timeout");

                    String body = "{\"slug\":\"po10-admitted-writer\",\"title\":\"Writer fixture\",\"displayOrder\":1}";
                    mvc.perform(withCookie(post(ROOT + "courses").contentType("application/json").content(body), editor))
                        .andExpect(status().isForbidden());
                    run(mvc, post(ROOT + "courses").header(PreauthStore.HEADER, editor.binding().csrf)
                        .contentType("application/json").content(body), editor, "writer-during-reader-overload", 201,
                        new ConcurrentLinkedQueue<>());
                    assertEquals(1L, owner.queryForObject("select count(*) from content_catalog.content_audit_events where actor_id=?",
                        Long.class, editor.binding().account));
                } finally {
                    reader.checkoutHold = null;
                    hold.release().countDown();
                }
                for (var request : requests) {
                    request.get(10, TimeUnit.SECONDS);
                }
                assertEquals(4, admitted.size());
            }
            var recovered = new ConcurrentLinkedQueue<Sample>();
            run(mvc, courseSearch(0), viewers.getFirst(), "after-overload", 200, recovered);
            assertTrue(recovered.element().checkoutAttempted);
            report(0, recovered, new Pressure(0, 0, 0, 0, 0));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BASE_TEST_CATALOG_G03_WIRE", matches = "true")
    void verifiesPo09AndPo10OverAnEmbeddedHttpServer() throws Exception {
        var owner = fixtureOwner();
        String url = System.getenv("BASE_TEST_STAFF_DB");
        boolean envelope = "true".equals(System.getenv("BASE_TEST_CATALOG_ENVELOPE"));
        int readers = "3".equals(System.getenv("BASE_TEST_CATALOG_ENVELOPE_READERS")) ? 3 : 4;
        try (var reader = pool(url, "content_staff_reader", "BASE_TEST_STAFF_READER_PASSWORD", readers);
             var writer = pool(url, "content_author_writer", "BASE_TEST_STAFF_WRITER_PASSWORD", 2);
             var app = new SpringApplicationBuilder(BaseIeltsApplication.class)
                 .initializers(context -> {
                     var beans = context.getBeanFactory();
                     beans.registerSingleton("catalogStaffDatabases", new CatalogStaffApi.Databases(reader, writer));
                     beans.registerSingleton("catalogFixtureAuthentication",
                         new StaffAuthentication(sessions, new AuthenticationService(null, null, null, sessions)));
                     if (envelope) {
                         beans.registerSingleton("catalogFixturePhaseProbe", wireProbe());
                     }
                 })
                 .run("--server.address=127.0.0.1", "--server.port=0", "--base.catalog.enabled=true", "--base.identity.enabled=false",
                     "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                     "--spring.datasource.url=" + url, "--spring.datasource.username=fixture", "--spring.datasource.password=fixture")) {
            int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
            String base = "http://127.0.0.1:" + port + ROOT;
            UUID grantor = insertAccount(owner);
            var viewers = new ArrayList<AuthSessions.Prepared>();
            for (int i = 0; i < 12; i++) {
                viewers.add(staff(owner, grantor, "CONTENT_PUBLISH"));
            }
            if (envelope) {
                verifyEnvelope(owner, base, viewers, grantor, reader, writer);
                return;
            }
            owner.update("""
                with courses as (
                    insert into content_catalog.course(slug,title,display_order)
                    select 'wire-g03-'||n, 'wire-g03 exact '||n, n from generate_series(1,10000) n returning id,slug
                ), level as (
                    insert into content_catalog.course_level(course_id,code,name,display_order)
                    select id,'wire','Wire level',1 from courses where slug='wire-g03-1' returning id
                )
                insert into content_catalog.reading_lesson(level_id,slug,title,display_order)
                select level.id,'wire-'||n,'Wire lesson '||n,n from level cross join generate_series(1,32) n
                """);
            owner.execute("analyze content_catalog.course, content_catalog.course_level, content_catalog.reading_lesson");
            System.out.printf("Catalog TCP fixture courses=%d%n", owner.queryForObject("select count(*) from content_catalog.course", Long.class));
            UUID level = id(owner, "select l.id from content_catalog.course_level l join content_catalog.course c on c.id=l.course_id where c.slug=?",
                "wire-g03-1");
            UUID lesson = id(owner, "select id from content_catalog.reading_lesson where level_id=? and slug='wire-1'", level);
            try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build()) {
                var routes = java.util.Map.of("lesson-point", "lessons/" + lesson, "lesson-list", "levels/" + level + "/lessons",
                    "course-unfiltered-list", "courses?size=50&page=99", "course-exact-search", "courses?q=wire-g03&size=50&page=99");
                int samplesPerClient = (100 + readers - 1) / readers;
                for (var viewer : viewers.subList(0, readers)) {
                    for (var route : routes.entrySet()) {
                        var response = client.send(httpGet(base + route.getValue(), viewer), HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        if (route.getKey().equals("course-exact-search")) {
                            assertEquals(10_000, com.jayway.jsonpath.JsonPath.<Integer>read(response.body(), "$.page.totalElements"));
                        }
                    }
                }

                var normal = new ConcurrentLinkedQueue<Sample>();
                List<Callable<Void>> load = viewers.subList(0, readers).stream().<Callable<Void>>map(viewer -> () -> {
                    for (int i = 0; i < samplesPerClient; i++) {
                        for (var route : routes.entrySet()) {
                            send(client, httpGet(base + route.getValue(), viewer), route.getKey(), 200, normal);
                        }
                    }
                    return null;
                }).toList();
                runConcurrently(load, owner, reader, writer);
                assertEquals(routes.size() * readers * samplesPerClient, normal.size());
                reportHttpTransport(normal);
                assertPo09(normal);

                var hold = new CheckoutHold(new CountDownLatch(readers), new CountDownLatch(1));
                reader.checkoutHold = hold;
                try (var holders = Executors.newFixedThreadPool(readers)) {
                    var admitted = new ConcurrentLinkedQueue<Sample>();
                    var requests = viewers.subList(0, readers).stream().map(viewer -> holders.submit(() -> {
                        send(client, httpGet(base + "lessons/" + lesson, viewer), "admitted-http", 200, admitted);
                        return null;
                    })).toList();
                    try {
                        assertTrue(hold.ready().await(5, TimeUnit.SECONDS));
                        int before = reader.checkoutAttempts.get();
                        var rejected = new ConcurrentLinkedQueue<Sample>();
                        List<Callable<Void>> overload = viewers.subList(readers, readers + 8).stream().<Callable<Void>>map(viewer -> () -> {
                            send(client, httpGet(base + "lessons/" + lesson, viewer), "fail-fast-http", 503, rejected);
                            return null;
                        }).toList();
                        var pressure = runConcurrently(overload, owner, reader, writer);
                        assertEquals(8, rejected.size());
                        assertEquals(before, reader.checkoutAttempts.get());
                        assertEquals(0, pressure.readerPending());
                        assertEquals(0, pressure.active());
                        assertTrue(rejected.stream().allMatch(sample -> sample.http < TimeUnit.MILLISECONDS.toNanos(500)));
                        reportHttpTransport(rejected);
                        System.out.printf("Catalog PO10 TCP n=8 checkoutDelta=0 SQL=0 poolWaiters=%d activeSql=%d%n",
                            pressure.readerPending(), pressure.active());
                    } finally {
                        reader.checkoutHold = null;
                        hold.release().countDown();
                    }
                    for (var request : requests) {
                        request.get(10, TimeUnit.SECONDS);
                    }
                    assertEquals(readers, admitted.size());
                }
                var recovered = new ConcurrentLinkedQueue<Sample>();
                send(client, httpGet(base + "lessons/" + lesson, viewers.getFirst()), "recovered-http", 200, recovered);
                reportHttpTransport(recovered);
                verifyWireWriterOverload(owner, base, grantor, viewers.getFirst(), client, writer);
            }
        }
    }

    private FilterRegistrationBean<Filter> wireProbe() {
        Filter filter = (request, response, chain) -> {
            String key = ((HttpServletRequest) request).getHeader("X-Catalog-Fixture-Sample");
            Sample sample = key == null ? null : wireSamples.get(key);
            if (sample != null) {
                current.set(sample);
            }
            try {
                chain.doFilter(request, response);
            } finally {
                if (sample != null) {
                    current.remove();
                    sample.observed.countDown();
                }
            }
        };
        var registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns(ROOT + "*");
        return registration;
    }

    private void verifyEnvelope(JdbcTemplate owner, String base, List<AuthSessions.Prepared> viewers, UUID grantor,
                                HikariDataSource reader, HikariDataSource writer) throws Exception {
        assertEquals(0L, owner.queryForObject("select count(*) from content_catalog.course", Long.class),
            "Use a clean separate performance fixture");
        var editors = List.of(staff(owner, grantor, "CONTENT_EDIT"), staff(owner, grantor, "CONTENT_EDIT"));
        int seeded = 0;
        int hotLevels = 1;
        int hotLessons = 0;
        int round = 0;
        int readClients = reader.getMaximumPoolSize();
        int samplesPerClient = (100 + readClients - 1) / readClients;
        var normalReads = new ConcurrentLinkedQueue<Sample>();
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build()) {
            for (int[] shape : new int[][] {{20_000, 24, 256}}) {
                int courses = shape[0];
                seedEnvelope(owner, seeded + 1, courses);
                seeded = courses;
                UUID course = id(owner, "select id from content_catalog.course where slug=?", "envelope-course-1");
                UUID level = id(owner, "select id from content_catalog.course_level where course_id=? and code='l1'", course);
                extendHotParents(owner, course, level, hotLevels, shape[1], hotLessons, shape[2]);
                hotLevels = shape[1];
                hotLessons = shape[2];
                UUID lesson = id(owner, "select id from content_catalog.reading_lesson where level_id=? and slug='hot-1'", level);
                UUID secondLesson = id(owner, "select id from content_catalog.reading_lesson where level_id=? and slug='hot-2'", level);
                owner.execute("analyze content_catalog.course, content_catalog.course_level, content_catalog.reading_lesson");
                long expectedFiltered = courses - courses / 4 - (courses + 7) / 8 - (courses + 5) / 8;
                String hotLevelList = "courses/" + course + "/levels?size=50";
                String hotLessonList = "levels/" + level + "/lessons?page=" + ((hotLessons - 1) / 50) + "&size=50";
                String top = "courses?page=99&size=50";
                String filtered = top + "&status=ACTIVE&q=sTaFf-CaTaLoG";
                assertWirePage(client, base + top, viewers.getFirst(), courses, 50);
                assertWirePage(client, base + filtered, viewers.getFirst(), expectedFiltered, 50);
                assertWirePage(client, base + hotLevelList, viewers.getFirst(), hotLevels, hotLevels);
                assertWirePage(client, base + hotLessonList, viewers.getFirst(), hotLessons, (hotLessons - 1) % 50 + 1);
                if (round == 0) {
                    verifyWireInputLimits(client, base, viewers.getFirst(), courses);
                    editEnvelopeLesson(client, base, editors.get(0), lesson, 1, 0, new ConcurrentLinkedQueue<>());
                    editEnvelopeLesson(client, base, editors.get(1), secondLesson, 2, 0, new ConcurrentLinkedQueue<>());
                }
                System.out.printf("Catalog ENVELOPE readers=%d courses=%d hotLevels=%d hotLessons=%d actual=%s%n", readClients, courses, hotLevels, hotLessons,
                    owner.queryForMap("""
                        select (select count(*) from content_catalog.course_level) as levels,
                               (select count(*) from content_catalog.reading_lesson) as lessons,
                               (select max(char_length(description)) from content_catalog.reading_lesson) as description_max
                        """));
                if (courses == 20_000 || courses == 40_005) {
                    explainEnvelopeCount(owner, courses);
                }
                var routes = Map.of("course-unfiltered", top, "course-exact", filtered,
                    "course-point", "courses/" + course, "hot-level-list", hotLevelList, "hot-lesson-list", hotLessonList, "lesson-point", "lessons/" + lesson);
                var samples = new ConcurrentLinkedQueue<Sample>();
                var jobs = new ArrayList<Callable<Void>>();
                for (var viewer : viewers.subList(0, readClients)) {
                    for (int warmup = 0; warmup < 3; warmup++) {
                        for (var route : routes.values()) {
                            assertEquals(200, client.send(httpGet(base + route, viewer), HttpResponse.BodyHandlers.discarding()).statusCode());
                        }
                    }
                    jobs.add(() -> {
                        for (int i = 0; i < samplesPerClient; i++) {
                            for (var route : routes.entrySet()) {
                                sendWithWireProbe(client, httpGet(base + route.getValue(), viewer), route.getKey(), samples);
                            }
                        }
                        return null;
                    });
                }
                int earlierEdits = round + 1;
                jobs.add(() -> editEnvelopeLesson(client, base, editors.get(0), lesson, 1, earlierEdits, samples));
                jobs.add(() -> editEnvelopeLesson(client, base, editors.get(1), secondLesson, 2, earlierEdits, samples));
                var pressure = runConcurrently(jobs, owner, reader, writer);
                assertEquals(routes.size() * samplesPerClient * readClients + editors.size(), samples.size());
                assertTrue(samples.stream().allMatch(s -> s.checkoutAttempted && s.queries == 6));
                report(courses, samples, pressure);
                if (courses == 20_000) {
                    samples.stream().filter(s -> !s.operation.equals("concurrent-lesson-edit")).forEach(normalReads::add);
                }
                round++;
            }
            for (var editor : editors) {
                assertEquals((long) round + 1, owner.queryForObject("select count(*) from content_catalog.content_audit_events where actor_id=?",
                    Long.class, editor.binding().account));
            }
        }
        assertTrue(wireSamples.isEmpty());
        assertPo09(normalReads);
    }

    private void verifyWireWriterOverload(JdbcTemplate owner, String base, UUID grantor, AuthSessions.Prepared viewer,
                                          HttpClient client, ProbeDataSource writer) throws Exception {
        var editors = List.of(staff(owner, grantor, "CONTENT_EDIT"), staff(owner, grantor, "CONTENT_EDIT"),
            staff(owner, grantor, "CONTENT_EDIT"));
        var hold = new CheckoutHold(new CountDownLatch(2), new CountDownLatch(1));
        writer.checkoutHold = hold;
        var results = new ConcurrentLinkedQueue<Sample>();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { send(client, courseCreate(base, editors.get(0), 1), "writer-admitted", 201, results); return null; });
            var second = workers.submit(() -> { send(client, courseCreate(base, editors.get(1), 2), "writer-admitted", 201, results); return null; });
            try {
                assertTrue(hold.ready().await(5, TimeUnit.SECONDS));
                assertEquals(2, writer.getHikariPoolMXBean().getActiveConnections());
                int before = writer.checkoutAttempts.get();
                send(client, courseCreate(base, editors.get(2), 3), "writer-fail-fast-http", 503, results);
                assertEquals(before, writer.checkoutAttempts.get());
                assertEquals(0, writer.getHikariPoolMXBean().getThreadsAwaitingConnection());
                assertEquals(0L, owner.queryForObject("select count(*) from content_catalog.content_audit_events where actor_id=?",
                    Long.class, editors.get(2).binding().account));
                send(client, httpGet(base + "courses?size=1", viewer), "reader-during-writer-overload", 200, results);
            } finally {
                writer.checkoutHold = null;
                hold.release().countDown();
            }
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        send(client, courseCreate(base, editors.get(2), 3), "writer-recovered-http", 201, results);
        for (var actor : editors) {
            assertEquals(1L, owner.queryForObject("select count(*) from content_catalog.content_audit_events where actor_id=?",
                Long.class, actor.binding().account));
        }
        assertTrue(results.stream().filter(s -> s.status == 503).allMatch(s -> s.http < TimeUnit.MILLISECONDS.toNanos(500)));
        reportHttpTransport(results);
    }

    private static HttpRequest courseCreate(String base, AuthSessions.Prepared editor, int index) {
        return HttpRequest.newBuilder(URI.create(base + "courses")).timeout(Duration.ofSeconds(5))
            .header("Cookie", AuthSessions.COOKIE + "=" + editor.token()).header(PreauthStore.HEADER, editor.binding().csrf)
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                "{\"slug\":\"wire-writer-" + index + "\",\"title\":\"Writer fixture\",\"displayOrder\":1}")).build();
    }

    private static void explainEnvelopeCount(JdbcTemplate owner, int courses) {
        String plan = """
            with filtered as materialized (select %s from content_catalog.course),
            page as (select * from filtered order by display_order,id offset 4950 limit 50)
            select %s, totals.exact_count from (select count(*) exact_count from filtered) totals
            left join page on true %s order by page.display_order,page.id
            """;
        String wide = plan.formatted("*", "page.*", "");
        String narrow = plan.formatted("id,display_order", "c.*", "left join content_catalog.course c on c.id=page.id");
        assertEquals(owner.queryForList(wide), owner.queryForList(narrow));
        explain(owner, courses, "envelope unfiltered wide", wide);
        explain(owner, courses, "envelope unfiltered narrow experiment", narrow);
    }

    private Void editEnvelopeLesson(HttpClient client, String base, AuthSessions.Prepared editor, UUID lesson,
                                    int index, int earlierEdits, ConcurrentLinkedQueue<Sample> samples) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + "lessons/" + lesson)).timeout(Duration.ofSeconds(5))
            .header("Cookie", AuthSessions.COOKIE + "=" + editor.token()).header(PreauthStore.HEADER, editor.binding().csrf)
            .header("Content-Type", "application/json").header("If-Match", "\"" + (earlierEdits + 1) + "\"")
            .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"slug\":\"hot-" + index
                + "\",\"title\":\"Boundary lesson\",\"displayOrder\":" + index + "}"))
            .build();
        sendWithWireProbe(client, request, "concurrent-lesson-edit", samples);
        return null;
    }

    private static void seedEnvelope(JdbcTemplate owner, int first, int last) {
        owner.update("""
            with seed as (select n from generate_series(?, ?) n), courses as (
                insert into content_catalog.course(slug,title,display_order,description,status)
                select 'envelope-course-'||n, case when n%4=0 then 'Other '||n else 'Staff-Catalog '||n end,
                    n, case when n%100=0 then repeat('界', 2000) when n%5=0 then repeat('detail ',30) else null end,
                    (case when n%8=3 or n%8=1 then 'INACTIVE' else 'ACTIVE' end)::content_catalog.catalog_status
                from seed returning id, display_order
            ), levels as (
                insert into content_catalog.course_level(course_id,code,name,display_order)
                select c.id,'l'||k,'Level '||k,k from courses c cross join lateral generate_series(1,
                    case when c.display_order=1 then 1 when c.display_order%20=0 then 6 when c.display_order%5=0 then 3
                         when c.display_order%3=0 then 1 else 0 end) k
                returning id,course_id,display_order
            )
            insert into content_catalog.reading_lesson(level_id,slug,title,display_order)
            select l.id,'lesson-'||k,'Lesson '||k,k from levels l join courses c on c.id=l.course_id
            cross join lateral generate_series(1,(c.display_order+l.display_order)%12+1) k where c.display_order<>1
            """, first, last);
    }

    private static void extendHotParents(JdbcTemplate owner, UUID course, UUID level, int previousLevels,
                                         int levels, int previousLessons, int lessons) {
        owner.update("""
            insert into content_catalog.course_level(course_id,code,name,display_order)
            select ?, 'l'||n, 'Level '||n,n from generate_series(?,?) n
            """, course, previousLevels + 1, levels);
        owner.update("""
            insert into content_catalog.reading_lesson(level_id,slug,title,display_order,description)
            select ?, 'hot-'||n, 'Boundary lesson '||n,n, case when n%4=0 then repeat('界',2000) end
            from generate_series(?,?) n
            """, level, previousLessons + 1, lessons);
    }

    private static void verifyWireInputLimits(HttpClient client, String base, AuthSessions.Prepared viewer, int courses) throws Exception {
        for (String query : List.of("?size=51", "?page=100", "?size=0", "?q=", "?q=%C2%A0", "?sort=title",
                "?q=" + URLEncoder.encode("𐐀".repeat(101), StandardCharsets.UTF_8))) {
            assertEquals(400, client.send(httpGet(base + "courses" + query, viewer), HttpResponse.BodyHandlers.discarding()).statusCode(), query);
        }
        for (int size : new int[] {49, 50}) {
            assertWirePage(client, base + "courses?size=" + size, viewer, courses, size);
        }
        assertWirePage(client, base + "courses?page=98&size=50", viewer, courses, 50);
        for (int length : new int[] {99, 100}) {
            assertWirePage(client, base + "courses?q=" + URLEncoder.encode("𐐀".repeat(length), StandardCharsets.UTF_8), viewer, 0, 0);
        }
    }

    private static void assertWirePage(HttpClient client, String url, AuthSessions.Prepared viewer, long exact, int rows) throws Exception {
        var response = client.send(httpGet(url, viewer), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        var json = com.jayway.jsonpath.JsonPath.parse(response.body());
        assertEquals(exact, json.<Number>read("$.page.totalElements").longValue());
        assertEquals(rows, json.<List<?>>read("$.data").size());
    }

    private void sendWithWireProbe(HttpClient client, HttpRequest original, String operation,
                                   ConcurrentLinkedQueue<Sample> samples) throws Exception {
        var sample = new Sample(operation);
        String key = UUID.randomUUID().toString();
        wireSamples.put(key, sample);
        try {
            var request = HttpRequest.newBuilder(original, (name, value) -> true).header("X-Catalog-Fixture-Sample", key).build();
            long started = System.nanoTime();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            sample.http = System.nanoTime() - started;
            sample.status = response.statusCode();
            sample.responseBytes = response.body().length;
            assertEquals(200, sample.status, operation);
            assertTrue(sample.observed.await(5, TimeUnit.SECONDS), "A real servlet must record JDBC phases");
            samples.add(sample);
        } finally {
            wireSamples.remove(key);
        }
    }

    private static HttpRequest httpGet(String uri, AuthSessions.Prepared actor) {
        return HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(5))
            .header("Cookie", AuthSessions.COOKIE + "=" + actor.token()).GET().build();
    }

    private static void send(HttpClient client, HttpRequest request, String operation, int expected,
                             ConcurrentLinkedQueue<Sample> results) throws Exception {
        long started = System.nanoTime();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        var sample = new Sample(operation);
        sample.http = System.nanoTime() - started;
        sample.status = response.statusCode();
        assertEquals(expected, sample.status, operation);
        if (expected == 503) {
            assertEquals("SERVICE_UNAVAILABLE", com.jayway.jsonpath.JsonPath.read(response.body(), "$.error.code"));
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        }
        results.add(sample);
    }

    private static void reportHttpTransport(ConcurrentLinkedQueue<Sample> results) {
        results.stream().collect(java.util.stream.Collectors.groupingBy(sample -> sample.operation)).forEach((operation, samples) -> {
            System.out.printf("Catalog TCP %s n=%d status=%d%n", operation, samples.size(), samples.getFirst().status);
            printStats("HTTP/1.1 loopback through response body", samples, sample -> sample.http);
        });
    }

    private static JdbcTemplate fixtureOwner() {
        String url = System.getenv("BASE_TEST_STAFF_DB");
        assertTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/staff_(fresh|upgrade)_[0-9a-f]{32}"));
        var owner = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", System.getenv("BASE_TEST_STAFF_OWNER_PASSWORD")));
        assertEquals(170012, owner.queryForObject("select current_setting('server_version_num')::integer", Integer.class));
        return owner;
    }

    private MockMvc staffMvc(HikariDataSource reader, HikariDataSource writer) {
        var authentication = new StaffAuthentication(sessions, new AuthenticationService(null, null, null, sessions));
        return MockMvcBuilders.standaloneSetup(new CatalogStaffApi(authentication, new CatalogStaffApi.Databases(reader, writer)))
            .setMessageConverters(new TimedJsonConverter()).build();
    }

    private static void assertPo09(ConcurrentLinkedQueue<Sample> results) {
        var groups = results.stream().collect(java.util.stream.Collectors.groupingBy(sample -> sample.operation));
        groups.forEach((operation, samples) -> {
            long[] sorted = samples.stream().mapToLong(sample -> sample.http).sorted().toArray();
            assertTrue(at(sorted, 0.95) <= 100, operation + " HTTP p95 exceeded 100 ms: " + at(sorted, 0.95));
            if (sorted.length >= 100) {
                assertTrue(at(sorted, 0.99) <= 200, operation + " HTTP p99 exceeded 200 ms: " + at(sorted, 0.99));
            }
        });
    }

    private static void seed(JdbcTemplate owner, int first, int last) {
        owner.update("""
            with courses as (
                insert into content_catalog.course(slug,title,status,display_order,description)
                select 'g03-load-'||n, 'g03%_hit '||n,
                    (case when n%2=0 then 'ACTIVE' else 'INACTIVE' end)::content_catalog.catalog_status,
                    n, repeat('text ', 60) from generate_series(?,?) n returning id
            ), levels as (
                insert into content_catalog.course_level(course_id,code,name,display_order)
                select c.id, 'l'||n, 'Level '||n, n from courses c cross join generate_series(1,4) n returning id
            )
            insert into content_catalog.reading_lesson(level_id,slug,title,display_order)
            select l.id,'s'||n,'Lesson '||n,n from levels l cross join generate_series(1,8) n
            """, first, last);
        owner.execute("analyze content_catalog.course, content_catalog.course_level, content_catalog.reading_lesson");
    }

    private void verifyPageAndPlan(JdbcTemplate owner, MockMvc mvc, AuthSessions.Prepared reader,
                                   UUID course, UUID level, UUID lesson, int total) throws Exception {
        int lastPage = total / 50 - 1;
        var page = mvc.perform(withCookie(courseSearch(lastPage), reader))
            .andExpect(jsonPath("$.page.totalElements").value(total)).andExpect(jsonPath("$.data.length()").value(50));
        page.andExpect(jsonPath("$.data[0].displayOrder").value((total - 49) * 2));
        if (lastPage < 99) {
            mvc.perform(withCookie(courseSearch(lastPage + 1), reader))
                .andExpect(jsonPath("$.page.totalElements").value(total)).andExpect(jsonPath("$.data.length()").value(0));
        }
        mvc.perform(withCookie(get(ROOT + "levels/" + level + "/lessons"), reader))
            .andExpect(jsonPath("$.page.totalElements").value(8)).andExpect(jsonPath("$.data[0].id").value(lesson.toString()));
        int allCourses = owner.queryForObject("select count(*) from content_catalog.course", Integer.class);
        mvc.perform(withCookie(get(ROOT + "courses").queryParam("page", "99").queryParam("size", "50"), reader))
            .andExpect(jsonPath("$.page.totalElements").value(allCourses))
            .andExpect(jsonPath("$.data.length()").value(Math.min(50, Math.max(0, allCourses - 4_950))));

        for (var point : List.of(new Object[] {"course", course}, new Object[] {"course_level", level},
                new Object[] {"reading_lesson", lesson})) {
            String table = (String) point[0];
            explain(owner, total, table + " point", "select t.* from content_catalog." + table + " t where id=?", point[1]);
        }
        explain(owner, total, "V16 lesson outer lookup baseline", """
            select * from catalog_staff.items('LESSON') where id=?
            """, lesson);
        explain(owner, total, "V16 lesson outer parent baseline", """
            select * from catalog_staff.items('LESSON') where parent_id=? order by display_order,id
            """, level);
        explain(owner, total, "course search count", """
            select count(*) from content_catalog.course where status='ACTIVE'
                and (strpos(lower(title),lower(?))>0 or strpos(lower(slug),lower(?))>0)
            """, SEARCH, SEARCH);
        explain(owner, total, "course search page", """
            select c.* from content_catalog.course c where status='ACTIVE'
                and (strpos(lower(title),lower(?))>0 or strpos(lower(slug),lower(?))>0)
            order by display_order,id offset ? limit 50
            """, SEARCH, SEARCH, lastPage * 50);
        String filters = "status='ACTIVE' and (strpos(lower(title),lower(?))>0 or strpos(lower(slug),lower(?))>0)";
        String pageAndCount = """
            with filtered as materialized (select %s from content_catalog.course where %s),
            page as (select * from filtered order by display_order,id offset ? limit 50)
            select %s, totals.exact_count from (select count(*) exact_count from filtered) totals
            left join page on true %s order by page.display_order,page.id
            """;
        String wide = pageAndCount.formatted("*", filters, "page.id,page.display_order,page.description", "");
        String narrow = pageAndCount.formatted("id,display_order", filters, "page.id,page.display_order,c.description",
            "left join content_catalog.course c on c.id=page.id");
        Object[] parameters = {SEARCH, SEARCH, lastPage * 50};
        assertEquals(owner.queryForList(wide, parameters), owner.queryForList(narrow, parameters));
        explain(owner, total, "course combined wide", wide, parameters);
        explain(owner, total, "course combined narrow experiment", narrow, parameters);
        explain(owner, total, "level parent", """
            select l.* from content_catalog.course_level l where course_id=? order by display_order,id
            """, course);
        explain(owner, total, "lesson parent with status", """
            select l.* from content_catalog.reading_lesson l where level_id=? and status='ACTIVE' order by display_order,id
            """, level);
    }

    private static void explain(JdbcTemplate owner, int total, String label, String sql, Object... arguments) {
        for (int sample = 1; sample <= 3; sample++) {
            var plan = owner.queryForList("explain (analyze, buffers, verbose) " + sql, String.class, arguments);
            System.out.printf("Catalog G03 SQL matches=%d %s sample=%d%n%s%n", total, label, sample, String.join("\n", plan));
        }
    }

    private static MockHttpServletRequestBuilder courseSearch(int page) {
        return get(ROOT + "courses").queryParam("q", SEARCH).queryParam("status", "ACTIVE")
            .queryParam("size", "50").queryParam("page", Integer.toString(page));
    }

    private void run(MockMvc mvc, MockHttpServletRequestBuilder request, AuthSessions.Prepared actor, String operation,
                     int expected, ConcurrentLinkedQueue<Sample> result) throws Exception {
        var sample = new Sample(operation);
        current.set(sample);
        long started = System.nanoTime();
        try {
            var response = mvc.perform(withCookie(request, actor)).andReturn().getResponse();
            sample.http = System.nanoTime() - started;
            sample.status = response.getStatus();
            assertEquals(expected, sample.status, operation);
            if (expected == 503) {
                assertEquals("SERVICE_UNAVAILABLE", com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.error.code"));
                assertEquals("no-store", response.getHeader("Cache-Control"));
            }
            if (expected == 200 || expected == 201) {
                assertEquals(6, sample.queries, operation);
            }
            result.add(sample);
        } finally {
            current.remove();
        }
    }

    private record Pressure(int active, int locking, double lockWaitMillis, int readerPending, int writerPending) {}

    private static Pressure runConcurrently(List<Callable<Void>> jobs, JdbcTemplate owner, HikariDataSource reader,
                                             HikariDataSource writer) throws Exception {
        var ready = new CountDownLatch(jobs.size());
        var start = new CountDownLatch(1);
        int active = 0;
        int locking = 0;
        int readerPending = 0;
        int writerPending = 0;
        double lockWait = 0;
        try (var workers = Executors.newFixedThreadPool(jobs.size()); var monitor = owner.getDataSource().getConnection();
             var activity = monitor.prepareStatement("""
                 select count(distinct a.pid) filter(where a.state='active'),
                        count(distinct a.pid) filter(where l.waitstart is not null),
                        coalesce(max(extract(epoch from (clock_timestamp()-l.waitstart))) * 1000,0)
                 from pg_stat_activity a left join pg_locks l on l.pid=a.pid and not l.granted
                 where a.datname=current_database() and a.usename in ('content_staff_reader','content_author_writer')
                 """)) {
            var futures = jobs.stream().map(job -> workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return job.call();
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
            while (!futures.stream().allMatch(future -> future.isDone())) {
                assertTrue(System.nanoTime() < deadline, "Catalog fixture workload exceeded 45 seconds");
                try (var observations = activity.executeQuery()) {
                    assertTrue(observations.next());
                    active = Math.max(active, observations.getInt(1));
                    locking = Math.max(locking, observations.getInt(2));
                    lockWait = Math.max(lockWait, observations.getDouble(3));
                }
                readerPending = Math.max(readerPending, reader.getHikariPoolMXBean().getThreadsAwaitingConnection());
                writerPending = Math.max(writerPending, writer.getHikariPoolMXBean().getThreadsAwaitingConnection());
                Thread.sleep(20);
            }
            for (var future : futures) {
                future.get();
            }
        }
        return new Pressure(active, locking, lockWait, readerPending, writerPending);
    }

    private static void report(int courses, ConcurrentLinkedQueue<Sample> result, Pressure pressure) {
        var grouped = result.stream().collect(java.util.stream.Collectors.groupingBy(sample -> sample.operation));
        grouped.forEach((operation, samples) -> {
            System.out.printf(Locale.ROOT, "Catalog G03 HTTP courses=%d %s n=%d errors=%d checkoutTimeouts=%d peakActive=%d readerPending=%d writerPending=%d DBactive=%d DBlocks=%d DBlockMax=%.3fms%n",
                courses, operation, samples.size(), samples.stream().filter(s -> s.status >= 400).count(),
                samples.stream().filter(s -> s.checkoutFailed).count(), samples.stream().mapToInt(s -> s.active).max().orElse(0),
                pressure.readerPending(), pressure.writerPending(), pressure.active(), pressure.locking(), pressure.lockWaitMillis());
            printStats("end-to-end", samples, s -> s.http);
            printStats("checkout", samples, s -> s.checkout);
            printStats("AUTH/fence JDBC", samples, s -> s.authSql);
            printStats("Catalog SQL", samples, s -> s.catalogSql);
            printStats("transaction", samples, s -> s.transaction);
            if (samples.stream().anyMatch(s -> s.serialization > 0)) {
                printStats("serialization", samples, s -> s.serialization);
            }
            if (samples.stream().anyMatch(s -> s.responseBytes > 0)) {
                System.out.printf("  largest response=%d bytes%n", samples.stream().mapToInt(s -> s.responseBytes).max().orElseThrow());
            }
        });
    }

    private static void printStats(String metric, List<Sample> samples, java.util.function.ToLongFunction<Sample> value) {
        var sorted = samples.stream().mapToLong(value).sorted().toArray();
        System.out.printf(Locale.ROOT, "  %s p50=%.3f p95=%.3f p99=%s max=%.3f ms%n", metric, at(sorted, 0.50),
            at(sorted, 0.95), sorted.length < 100 ? "n/a" : String.format(Locale.ROOT, "%.3f", at(sorted, 0.99)), at(sorted, 1));
    }

    private static double at(long[] sorted, double percentile) {
        return sorted[(int) Math.ceil(sorted.length * percentile) - 1] / 1_000_000.0;
    }

    private static UUID id(JdbcTemplate owner, String sql, Object value) {
        return owner.queryForObject(sql, UUID.class, value);
    }

    private static UUID insertAccount(JdbcTemplate owner) {
        return owner.queryForObject("""
            insert into identity.users(canonical_email,display_name,status,password_hash,verified_at,verification_method)
            values (?, 'G03 synthetic account','ACTIVE','fixture',clock_timestamp(),'MAILBOX_TOKEN') returning id
            """, UUID.class, UUID.randomUUID() + "@g03-fixture.test");
    }

    private AuthSessions.Prepared staff(JdbcTemplate owner, UUID grantor, String capability) {
        UUID account = insertAccount(owner);
        owner.update("insert into identity.user_roles(user_id,role_id) select ?,id from identity.roles where code='ADMIN'", account);
        owner.update("""
            insert into content_catalog.content_capability_grants(grantee_id,grantor_id,capability)
            values (?, ?, ?::content_catalog.capability)
            """, account, grantor, capability);
        var prepared = sessions.prepare(account, 0);
        sessions.publish(prepared);
        return prepared;
    }

    private static MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, AuthSessions.Prepared actor) {
        return request.cookie(new Cookie(AuthSessions.COOKIE, actor.token()));
    }

    private ProbeDataSource pool(String url, String login, String variable, int maximum) {
        var config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(login);
        config.setPassword(System.getenv(variable));
        config.setMaximumPoolSize(maximum);
        config.setConnectionTimeout(1_000);
        return new ProbeDataSource(config);
    }

    private static final class Sample {
        private final String operation;
        private final CountDownLatch observed = new CountDownLatch(1);
        private long http, checkout, authSql, catalogSql, transaction, serialization;
        private int queries, status, active, responseBytes;
        private boolean checkoutAttempted, checkoutFailed;

        private Sample(String operation) {
            this.operation = operation;
        }
    }

    private final class TimedJsonConverter extends MappingJackson2HttpMessageConverter {
        private TimedJsonConverter() {
            super(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        }

        @Override
        protected void writeInternal(Object body, Type type, HttpOutputMessage message) throws java.io.IOException {
            long started = System.nanoTime();
            super.writeInternal(body, type, message);
            if (current.get() != null) {
                current.get().serialization += System.nanoTime() - started;
            }
        }
    }

    private record CheckoutHold(CountDownLatch ready, CountDownLatch release) {}

    private final class ProbeDataSource extends HikariDataSource {
        private volatile CheckoutHold checkoutHold;
        private final AtomicInteger checkoutAttempts = new AtomicInteger();

        private ProbeDataSource(HikariConfig config) {
            super(config);
        }

        @Override
        public Connection getConnection() throws SQLException {
            var sample = current.get();
            checkoutAttempts.incrementAndGet();
            if (sample != null) {
                sample.checkoutAttempted = true;
            }
            long started = System.nanoTime();
            Connection connection;
            try {
                connection = super.getConnection();
            } catch (SQLException failure) {
                if (sample != null) {
                    sample.checkoutFailed = true;
                }
                throw failure;
            } finally {
                if (sample != null) {
                    sample.checkout = System.nanoTime() - started;
                    sample.active = getHikariPoolMXBean().getActiveConnections();
                }
            }
            CheckoutHold hold = checkoutHold;
            if (hold != null) {
                hold.ready().countDown();
                try {
                    if (!hold.release().await(10, TimeUnit.SECONDS)) {
                        connection.close();
                        throw new SQLException("Fixture checkout was not released");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    connection.close();
                    throw new SQLException("Fixture checkout interrupted", interrupted);
                }
            }
            if (sample == null) {
                return connection;
            }
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                new java.lang.reflect.InvocationHandler() {
                    private long transactionStart;

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
                        Object value = call(connection, method, arguments);
                        if ("setAutoCommit".equals(method.getName()) && Boolean.FALSE.equals(arguments[0])) {
                            transactionStart = System.nanoTime();
                        } else if ("commit".equals(method.getName()) || "rollback".equals(method.getName())) {
                            sample.transaction = System.nanoTime() - transactionStart;
                        } else if (value instanceof Statement statement) {
                            String sql = arguments != null && arguments.length > 0 && arguments[0] instanceof String text ? text : "";
                            Class<?> api = statement instanceof java.sql.PreparedStatement ? java.sql.PreparedStatement.class : Statement.class;
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {api}, (p, statementMethod, args) -> {
                                if (!statementMethod.getName().startsWith("execute")) {
                                    return call(statement, statementMethod, args);
                                }
                                long queryStarted = System.nanoTime();
                                sample.queries++;
                                try {
                                    return call(statement, statementMethod, args);
                                } finally {
                                    if (sql.contains("catalog_staff.")) {
                                        sample.catalogSql += System.nanoTime() - queryStarted;
                                    } else {
                                        sample.authSql += System.nanoTime() - queryStarted;
                                    }
                                }
                            });
                        }
                        return value;
                    }
                });
        }
    }

    private static Object call(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
