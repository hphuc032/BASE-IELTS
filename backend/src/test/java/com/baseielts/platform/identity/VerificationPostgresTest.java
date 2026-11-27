package com.baseielts.platform.identity;

import jakarta.servlet.http.Cookie;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "base.identity.enabled=true", "base.identity.verification.capture=true",
    "spring.flyway.enabled=true", "spring.flyway.default-schema=identity",
    "spring.flyway.url=${BASE_DB_JDBC_URL}",
    "spring.flyway.user=${BASE_DB_MIGRATION_USER}",
    "spring.flyway.password=${BASE_DB_MIGRATION_PASSWORD}",
    "spring.flyway.init-sqls[0]=SET ROLE identity_migration_owner"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(VerificationPostgresTest.TransactionFaults.class)
@EnabledIfEnvironmentVariable(named = "BASE_TEST_POSTGRES", matches = "true")
class VerificationPostgresTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DevelopmentVerificationMailbox mailbox;
    @Autowired FaultingTransactionManager transactionManager;
    @Autowired VerificationService verificationService;
    @Autowired io.micrometer.core.instrument.MeterRegistry metrics;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    @EnabledIfEnvironmentVariable(named = "BASE_TEST_VERIFICATION_LOAD", matches = "true")
    void boundedConcurrentIssuanceCoexistsWithAuthenticatedTraffic() throws Exception {
        metrics.config().meterFilter(new io.micrometer.core.instrument.config.MeterFilter() {
            @Override public io.micrometer.core.instrument.distribution.DistributionStatisticConfig configure(
                    io.micrometer.core.instrument.Meter.Id id,
                    io.micrometer.core.instrument.distribution.DistributionStatisticConfig original) {
                if (!id.getName().startsWith("identity.verification.database.")) return original;
                return io.micrometer.core.instrument.distribution.DistributionStatisticConfig.builder()
                    .percentiles(.50, .95, .99).build().merge(original);
            }
        });
        String active = "load-active-" + UUID.randomUUID() + "@identity-test.invalid";
        String password = "Isolated verification workload active credential";
        var digest = OpaqueTokens.digest(OpaqueTokens.create(32));
        org.junit.jupiter.api.Assertions.assertNotNull(jdbc.queryForObject(
            "select identity_api.register(?, 'Active workload fixture')", UUID.class, active));
        org.junit.jupiter.api.Assertions.assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
            "select identity_api.issue(?, ?, 0, null)", Boolean.class, active, digest)));
        org.junit.jupiter.api.Assertions.assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
            "select identity_api.consume(?, ?)", Boolean.class, digest, new IdentityPasswords().encode(password))));
        var initial = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH"))
            .andExpect(status().isOk()).andReturn();
        Cookie auth = login(initial.getResponse().getCookie(PreauthStore.COOKIE), csrfToken(initial),
            json.writeValueAsString(new VerificationController.Login(active, password)));
        try (var workers = Executors.newFixedThreadPool(13)) {
            int band = 0;
            for (int concurrency : new int[]{1, 4, 8, 12, 8}) {
                boolean contend = ++band == 5;
                var registrations = new java.util.ArrayList<java.util.concurrent.Callable<Long>>();
                var accounts = new java.util.HashSet<String>();
                var ready = new java.util.concurrent.CountDownLatch(concurrency + 1);
                var start = new java.util.concurrent.CountDownLatch(1);
                String shared = "load-shared-" + UUID.randomUUID() + "@identity-test.invalid";
                for (int i = 0; i < concurrency; i++) {
                    String source = "192.0.2." + (20 + concurrency + i);
                    String email = i < 2 ? shared : "load-distinct-" + UUID.randomUUID() + "@identity-test.invalid";
                    accounts.add(email);
                    var bootstrap = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH")
                        .with(request -> { request.setRemoteAddr(source); return request; }))
                        .andExpect(status().isOk()).andReturn();
                    var cookie = bootstrap.getResponse().getCookie(PreauthStore.COOKIE);
                    var csrf = csrfToken(bootstrap);
                    String body = json.writeValueAsString(new VerificationController.Registration(email, "Load fixture"));
                    registrations.add(() -> {
                        ready.countDown();
                        org.junit.jupiter.api.Assertions.assertTrue(start.await(3, java.util.concurrent.TimeUnit.SECONDS));
                        long before = System.nanoTime();
                        mvc.perform(post("/api/v1/auth/register").cookie(cookie).header(PreauthStore.HEADER, csrf)
                            .contentType("application/json").content(body)
                            .with(request -> { request.setRemoteAddr(source); return request; }))
                            .andExpect(status().isAccepted()).andExpect(header().doesNotExist("Set-Cookie"));
                        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);
                    });
                }
                var authenticated = workers.submit(() -> {
                    ready.countDown();
                    org.junit.jupiter.api.Assertions.assertTrue(start.await(3, java.util.concurrent.TimeUnit.SECONDS));
                    long before = System.nanoTime();
                    mvc.perform(get("/api/v1/users/me").cookie(auth)).andExpect(status().isOk());
                    return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);
                });
                var pending = registrations.stream().map(workers::submit).toList();
                var reservations = new java.util.ArrayList<java.sql.Connection>();
                try {
                    if (contend) {
                        var pool = java.util.Objects.requireNonNull(jdbc.getDataSource())
                            .unwrap(com.zaxxer.hikari.HikariDataSource.class);
                        for (int i = 0; i < pool.getMaximumPoolSize() - 2; i++) {
                            reservations.add(pool.getConnection());
                        }
                    }
                    org.junit.jupiter.api.Assertions.assertTrue(ready.await(3, java.util.concurrent.TimeUnit.SECONDS));
                    start.countDown();
                    var elapsed = new java.util.ArrayList<Long>();
                    for (var request : pending) elapsed.add(request.get(5, java.util.concurrent.TimeUnit.SECONDS));
                    java.util.Collections.sort(elapsed);
                    int delivered = 0;
                    for (String account : accounts) {
                        if (mailbox.takeForTest(account).isPresent()) delivered++;
                        org.junit.jupiter.api.Assertions.assertTrue(mailbox.takeForTest(account).isEmpty());
                    }
                    System.out.printf("verification requests concurrency=%d pool_reserved=%d unique=%d captured=%d p50=%d p95=%d p99=%d max=%d auth_ms=%d%n",
                        concurrency, reservations.size(), accounts.size(), delivered, percentile(elapsed, .50),
                        percentile(elapsed, .95), percentile(elapsed, .99), elapsed.getLast(),
                        authenticated.get(5, java.util.concurrent.TimeUnit.SECONDS));
                } finally {
                    start.countDown();
                    for (var reserved : reservations) reserved.close();
                }
            }
        }
        metrics.getMeters().stream().filter(meter -> meter.getId().getName().startsWith("identity.verification.database."))
            .forEach(meter -> {
                if (meter instanceof io.micrometer.core.instrument.Timer timer) {
                    var unit = java.util.concurrent.TimeUnit.MILLISECONDS;
                    var snapshot = timer.takeSnapshot();
                    System.out.printf("verification phase %s %s count=%d percentiles_ms=%s max_ms=%.3f%n",
                        meter.getId().getName(), meter.getId().getTags(), timer.count(),
                        java.util.Arrays.toString(java.util.Arrays.stream(snapshot.percentileValues())
                            .mapToDouble(value -> value.value(unit)).toArray()), timer.max(unit));
                } else if (meter instanceof io.micrometer.core.instrument.Counter counter) {
                    System.out.printf("verification outcome %s count=%.0f%n", meter.getId().getTags(), counter.count());
                }
            });
    }

    private static ProcessBuilder fixturePsql(String database) {
        String root = System.getenv("BASE_TEST_WSL_FIXTURE_ROOT");
        if (root == null || root.isEmpty()) {
            return new ProcessBuilder("docker.exe", "compose", "exec", "-T", "postgres", "psql",
                "-XAtq", "-v", "ON_ERROR_STOP=1", "-U", "base_ielts_bootstrap", "-d", database)
                .directory(new java.io.File(".."));
        }
        String port = System.getenv("BASE_TEST_WSL_FIXTURE_PORT");
        if (!root.matches("/tmp/base-ielts-pg1712-[A-Za-z0-9]{8}") || port == null || !port.matches("[1-9][0-9]{3,4}")) {
            throw new IllegalArgumentException("Expected a dedicated WSL PostgreSQL fixture");
        }
        return new ProcessBuilder("wsl.exe", "-d", "Ubuntu", "--", root + "/runtime/bin/psql",
            "-h", root + "/socket", "-p", port, "-XAtq", "-v", "ON_ERROR_STOP=1",
            "-U", "base_ielts_bootstrap", "-d", database);
    }

    private static long percentile(java.util.List<Long> ordered, double percentile) {
        return ordered.get((int) Math.ceil(percentile * ordered.size()) - 1);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BASE_TEST_VERIFICATION_LOAD", matches = "true")
    void retentionAndIssuanceShareTheAccountLockWithoutDeletingLiveTokens() throws Exception {
        String url = System.getenv("BASE_DB_JDBC_URL");
        String database = java.net.URI.create(url.substring("jdbc:".length())).getPath().substring(1);
        org.junit.jupiter.api.Assertions.assertTrue(database.matches("base_ielts_m1test_[0-9]+"));
        String user = System.getenv("BASE_DB_RUNTIME_USER");
        String password = System.getenv("BASE_DB_RUNTIME_PASSWORD");
        String email = "load-retention-" + UUID.randomUUID() + "@identity-test.invalid";
        String liveEmail = "load-retention-live-" + UUID.randomUUID() + "@identity-test.invalid";
        var cleanup = fixturePsql(database).start();
        try (var commands = cleanup.outputWriter();
             var output = cleanup.inputReader();
             var observer = DriverManager.getConnection(url, user, password);
             var workers = Executors.newFixedThreadPool(5)) {
            commands.write("""
                WITH fixtures AS (
                    INSERT INTO identity.users(canonical_email,display_name)
                    VALUES ('%s','Retention workload'),('%s','Live token workload')
                    RETURNING id,canonical_email
                )
                INSERT INTO identity.verification_tokens(user_id,email_revision,token_digest,issued_at,expires_at)
                SELECT id,0,sha256(uuid_send(id)),
                    CASE WHEN canonical_email='%s' THEN clock_timestamp()-interval '12 days' ELSE clock_timestamp() END,
                    CASE WHEN canonical_email='%s' THEN clock_timestamp()-interval '11 days' ELSE clock_timestamp()+interval '10 minutes' END
                FROM fixtures;
                SET SESSION AUTHORIZATION maintenance_cleanup;
                BEGIN;
                SELECT pg_backend_pid() || '|' || identity_maintenance."cleanupExpiredTokens"(
                    clock_timestamp()-interval '10 days',500);
                """.formatted(email, liveEmail, email, email));
            commands.flush();
            String receipt = workers.submit(output::readLine).get(8, java.util.concurrent.TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertNotNull(receipt);
            org.junit.jupiter.api.Assertions.assertTrue(receipt.matches("[0-9]+\\|[1-9][0-9]*"));
            int cleanupPid = Integer.parseInt(receipt.split("\\|")[0]);
            var beginning = new java.util.concurrent.CountDownLatch(1);
            var pending = java.util.stream.IntStream.range(0, 4).mapToObj(index -> workers.submit(() -> {
                beginning.await();
                verificationService.request(email, null, false);
                return null;
            })).toList();
            var waits = new java.util.ArrayList<Double>();
            try (var sample = observer.prepareStatement("""
                SELECT extract(epoch from clock_timestamp()-waitstart)*1000
                FROM pg_locks WHERE NOT granted AND waitstart IS NOT NULL
                  AND ? = ANY(pg_blocking_pids(pid))
                """)) {
                sample.setInt(1, cleanupPid);
                beginning.countDown();
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
                while (pending.stream().anyMatch(task -> !task.isDone()) && System.nanoTime() < deadline) {
                    try (var rows = sample.executeQuery()) {
                        while (rows.next()) waits.add(rows.getDouble(1));
                    }
                }
            }
            for (var task : pending) task.get(3, java.util.concurrent.TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertFalse(waits.isEmpty(), "No PostgreSQL lock wait was observed");
            org.junit.jupiter.api.Assertions.assertTrue(mailbox.takeForTest(email).isEmpty());
            java.util.Collections.sort(waits);
            System.out.printf("verification retention lock samples=%d p50=%.3f p95=%.3f p99=%.3f max=%.3f ms%n",
                waits.size(), waits.get((int) Math.ceil(waits.size() * .50) - 1),
                waits.get((int) Math.ceil(waits.size() * .95) - 1),
                waits.get((int) Math.ceil(waits.size() * .99) - 1), waits.getLast());
            commands.write("""
                COMMIT;
                RESET SESSION AUTHORIZATION;
                SELECT count(*) FROM identity.verification_tokens t JOIN identity.users u ON u.id=t.user_id
                WHERE u.canonical_email='%s' AND t.consumed_at IS NULL AND t.invalidated_at IS NULL;
                """.formatted(liveEmail));
            commands.flush();
            org.junit.jupiter.api.Assertions.assertEquals("1",
                workers.submit(output::readLine).get(8, java.util.concurrent.TimeUnit.SECONDS));
            verificationService.request(email, null, false);
            org.junit.jupiter.api.Assertions.assertTrue(mailbox.takeForTest(email).isPresent());
        } finally {
            if (cleanup.isAlive()) cleanup.destroyForcibly();
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BASE_TEST_VERIFICATION_COMMIT_FAULT", matches = "true")
    void verificationSuppressesMailWhenDurableCommitReplyIsDelayedAndLost() throws Exception {
        String url = System.getenv("BASE_DB_JDBC_URL");
        java.net.URI location = java.net.URI.create(url.substring("jdbc:".length()));
        String database = location.getPath().substring(1);
        org.junit.jupiter.api.Assertions.assertTrue(database.matches("base_ielts_m1test_[0-9]+"));
        String email = "lost-issuance-" + UUID.randomUUID() + "@identity-test.invalid";
        var fault = LostCommitReplySocketFactory.fixture();
        try (var physical = fault.connect(url, System.getenv("BASE_DB_RUNTIME_USER"),
                System.getenv("BASE_DB_RUNTIME_PASSWORD"))) {
            var intercepted = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{java.sql.Connection.class}, (instance, method, args) -> {
                    if ("commit".equals(method.getName())) fault.arm(165);
                    try {
                        return method.invoke(physical, args);
                    } catch (java.lang.reflect.InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
            var source = new org.springframework.jdbc.datasource.AbstractDataSource() {
                @Override public java.sql.Connection getConnection() { return intercepted; }
                @Override public java.sql.Connection getConnection(String user, String password) { return intercepted; }
            };
            var admission = new VerificationConnectionAdmission(source);
            try {
                var service = new VerificationService(jdbc,
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager),
                    context.getBeanProvider(DevelopmentVerificationMailbox.class), new IdentityPasswords(),
                    admission, metrics);
                var failures = metrics.counter("identity.verification.database.failure", "phase", "commit", "kind", "connection");
                double before = failures.count();
                service.request(email, "Commit reply fault fixture", true);
                org.junit.jupiter.api.Assertions.assertTrue(fault.replyDropped());
                org.junit.jupiter.api.Assertions.assertEquals(before + 1, failures.count());
                org.junit.jupiter.api.Assertions.assertTrue(mailbox.takeForTest(email).isEmpty());
                org.junit.jupiter.api.Assertions.assertTrue(physical.isClosed());
            } finally {
                admission.shutdown();
            }
        }
        var observer = fixturePsql(database).start();
        try (var input = observer.getOutputStream()) {
            input.write(("""
                SELECT count(*) FROM identity.users u JOIN identity.verification_tokens t ON t.user_id=u.id
                WHERE u.canonical_email='%s' AND u.status='PENDING' AND t.invalidated_at IS NULL
                  AND t.consumed_at IS NULL AND t.expires_at > clock_timestamp();
                """.formatted(email)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        try {
            org.junit.jupiter.api.Assertions.assertTrue(observer.waitFor(8, java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertEquals(0, observer.exitValue());
            org.junit.jupiter.api.Assertions.assertEquals("1",
                new String(observer.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim());
        } finally {
            if (observer.isAlive()) observer.destroyForcibly();
        }
    }

    @Test
    void concurrentIssuanceWithLockedPendingAccountDoesNotDispatchUncommittedTokens() throws Exception {
        String email = "locked-issuance-" + UUID.randomUUID() + "@identity-test.invalid";
        try (var writer = DriverManager.getConnection(System.getenv("BASE_DB_JDBC_URL"),
                 System.getenv("BASE_DB_RUNTIME_USER"), System.getenv("BASE_DB_RUNTIME_PASSWORD"));
             var register = writer.prepareStatement("select identity_api.register(?, 'Locked issuance fixture')");
             var lock = writer.prepareStatement("select identity_api.issue(?, ?, 0, null)");
             var callers = Executors.newFixedThreadPool(3)) {
            register.setString(1, email);
            try (var result = register.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next() && result.getObject(1) != null);
            }
            writer.setAutoCommit(false);
            lock.setString(1, email);
            lock.setBytes(2, OpaqueTokens.digest(OpaqueTokens.create(32)));
            try (var result = lock.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next() && result.getBoolean(1));
            }
            var lockFailures = metrics.counter("identity.verification.database.failure", "phase", "issue", "kind", "lock");
            double before = lockFailures.count();
            for (int round = 0; round < 3; round++) {
                var results = callers.<Void>invokeAll(java.util.List.of(
                    () -> { verificationService.request(email, null, false); return null; },
                    () -> { verificationService.request(email, null, false); return null; },
                    () -> { verificationService.request(email, null, false); return null; }),
                    3, java.util.concurrent.TimeUnit.SECONDS);
                for (var result : results) result.get();
                org.junit.jupiter.api.Assertions.assertTrue(mailbox.takeForTest(email).isEmpty());
            }
            org.junit.jupiter.api.Assertions.assertTrue(lockFailures.count() > before);
            writer.rollback();
            verificationService.request(email, null, false);
            var message = mailbox.takeForTest(email).orElseThrow();
            org.junit.jupiter.api.Assertions.assertTrue(
                verificationService.verify(message.token(), "isolated lock recovery fixture"));
        }
    }

    @Test
    void scopedPostCreatesPendingAccountAndConsumesOnlyItsToken() throws Exception {
        mvc.perform(patch("/api/v1/users/me").contentType("text/plain").content("not JSON"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA"));
        mvc.perform(patch("/api/v1/users/me").contentType("application/json").content(" ".repeat(8193)))
            .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.error.code").value("BODY_TOO_LARGE"));
        mvc.perform(patch("/api/v1/users/me").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
        String email = "verification-" + UUID.randomUUID() + "@identity-test.invalid";
        String registration = json.writeValueAsString(new VerificationController.Registration(email, "Verification fixture"));
        MvcResult bootstrap = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH"))
            .andExpect(status().isOk()).andReturn();
        Cookie cookie = bootstrap.getResponse().getCookie(PreauthStore.COOKIE);
        assertNotNull(cookie);
        String csrf = json.readTree(bootstrap.getResponse().getContentAsString()).path("data").path("token").asText();

        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(registration))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/v1/auth/register").cookie(cookie).header(PreauthStore.HEADER, csrf)
                .contentType("application/json").content(registration))
            .andExpect(status().isAccepted()).andExpect(header().doesNotExist("Set-Cookie"));
        var message = mailbox.takeForTest(email).orElseThrow();
        String form = json.writeValueAsString(new VerificationController.Verification(
            message.token(), "test-only long nonsecret fixture"));
        mvc.perform(post("/api/v1/auth/verify-email").cookie(cookie).header(PreauthStore.HEADER, csrf)
                .contentType("application/json").content(form))
            .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(post("/api/v1/auth/verify-email").cookie(cookie).header(PreauthStore.HEADER, csrf)
                .contentType("application/json").content(form))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_VERIFICATION"));

        String loginForm = json.writeValueAsString(
            new VerificationController.Login(email, "test-only long nonsecret fixture"));
        Cookie first = login(cookie, csrf, loginForm);
        mvc.perform(get("/api/v1/users/me").cookie(first))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(email));
        MvcResult replacement = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH").cookie(cookie))
            .andExpect(status().isOk()).andReturn();
        Cookie second = login(replacement.getResponse().getCookie(PreauthStore.COOKIE),
            csrfToken(replacement), loginForm);
        mvc.perform(get("/api/v1/users/me").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").cookie(second).header(PreauthStore.HEADER, csrf))
            .andExpect(status().isForbidden());
        MvcResult auth = mvc.perform(get("/api/v1/auth/csrf").param("scope", "AUTH").cookie(second))
            .andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/auth/logout").cookie(second).header(PreauthStore.HEADER, csrfToken(auth)))
            .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/api/v1/users/me").cookie(second)).andExpect(status().isUnauthorized());

        var left = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH"))
            .andExpect(status().isOk()).andReturn();
        var right = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH"))
            .andExpect(status().isOk()).andReturn();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var results = workers.<Cookie>invokeAll(java.util.List.of(
                () -> login(left.getResponse().getCookie(PreauthStore.COOKIE), csrfToken(left), loginForm),
                () -> login(right.getResponse().getCookie(PreauthStore.COOKIE), csrfToken(right), loginForm)));
            Cookie leftAuth = results.get(0).get();
            Cookie rightAuth = results.get(1).get();
            boolean leftActive = mvc.perform(get("/api/v1/users/me").cookie(leftAuth))
                .andReturn().getResponse().getStatus() == 200;
            Cookie winner = leftActive ? leftAuth : rightAuth;
            Cookie stale = leftActive ? rightAuth : leftAuth;
            mvc.perform(get("/api/v1/users/me").cookie(stale)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/logout").cookie(stale).header(PreauthStore.HEADER, csrfToken(auth)))
                .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/users/me").cookie(winner)).andExpect(status().isOk());
        }
    }

    @Test
    void definitiveRollbackPreservesBindingAndUncertainCommitRequiresReauthentication() throws Exception {
        String email = "commit-" + UUID.randomUUID() + "@identity-test.invalid";
        String password = "dedicated commit outcome fixture";
        MvcResult initial = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH").with(request -> {
                // This fixture models a distinct client of the real per-source admission bucket.
                request.setRemoteAddr("192.0.2.12");
                return request;
            }))
            .andExpect(status().isOk()).andReturn();
        Cookie preauth = initial.getResponse().getCookie(PreauthStore.COOKIE);
        String csrf = csrfToken(initial);
        mvc.perform(post("/api/v1/auth/register").cookie(preauth).header(PreauthStore.HEADER, csrf)
            .contentType("application/json").content(json.writeValueAsString(
                new VerificationController.Registration(email, "Commit fixture"))))
            .andExpect(status().isAccepted());
        var message = mailbox.takeForTest(email).orElseThrow();
        mvc.perform(post("/api/v1/auth/verify-email").cookie(preauth).header(PreauthStore.HEADER, csrf)
            .contentType("application/json").content(json.writeValueAsString(
                new VerificationController.Verification(message.token(), password))))
            .andExpect(status().isNoContent());
        String form = json.writeValueAsString(new VerificationController.Login(email, password));
        Cookie active = login(preauth, csrf, form);
        MvcResult retry = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH").with(request -> {
                // This fixture models a distinct client of the real per-source admission bucket.
                request.setRemoteAddr("192.0.2.12");
                return request;
            }))
            .andExpect(status().isOk()).andReturn();
        Cookie retryCookie = retry.getResponse().getCookie(PreauthStore.COOKIE);
        String retryCsrf = csrfToken(retry);
        transactionManager.next.set(CommitFault.ROLLBACK);
        mvc.perform(post("/api/v1/auth/login").cookie(retryCookie).header(PreauthStore.HEADER, retryCsrf)
            .contentType("application/json").content(form))
            .andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/api/v1/users/me").cookie(active)).andExpect(status().isOk());
        transactionManager.next.set(CommitFault.UNKNOWN_AFTER_COMMIT);
        mvc.perform(post("/api/v1/auth/login").cookie(retryCookie).header(PreauthStore.HEADER, retryCsrf)
            .contentType("application/json").content(form))
            .andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/api/v1/users/me").cookie(active)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").cookie(retryCookie).header(PreauthStore.HEADER, retryCsrf)
            .contentType("application/json").content(form)).andExpect(status().isForbidden());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BASE_TEST_LIFECYCLE", matches = "true")
    void committedDisablementFencesExistingAndRacingLogins() throws Exception {
        String email = "revocation-" + UUID.randomUUID() + "@identity-test.invalid";
        String password = "separate long revocation fixture";
        var bootstrap = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH"))
            .andExpect(status().isOk()).andReturn();
        Cookie preauth = bootstrap.getResponse().getCookie(PreauthStore.COOKIE);
        String csrf = csrfToken(bootstrap);
        mvc.perform(post("/api/v1/auth/register").cookie(preauth).header(PreauthStore.HEADER, csrf)
            .contentType("application/json").content(json.writeValueAsString(
                new VerificationController.Registration(email, "Revocation fixture"))))
            .andExpect(status().isAccepted());
        var message = mailbox.takeForTest(email).orElseThrow();
        mvc.perform(post("/api/v1/auth/verify-email").cookie(preauth).header(PreauthStore.HEADER, csrf)
            .contentType("application/json").content(json.writeValueAsString(
                new VerificationController.Verification(message.token(), password))))
            .andExpect(status().isNoContent());
        String form = json.writeValueAsString(new VerificationController.Login(email, password));
        Cookie current = login(preauth, csrf, form);
        var profile = mvc.perform(get("/api/v1/users/me").cookie(current)).andExpect(status().isOk()).andReturn();
        String id = json.readTree(profile.getResponse().getContentAsString()).path("data").path("id").asText();
        var newBootstrap = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH"))
            .andExpect(status().isOk()).andReturn();
        Cookie newCookie = newBootstrap.getResponse().getCookie(PreauthStore.COOKIE);
        try (var tasks = Executors.newFixedThreadPool(2);
             var lifecycle = DriverManager.getConnection(System.getenv("BASE_DB_JDBC_URL"),
                 "identity_lifecycle_operator", System.getenv("BASE_TEST_LIFECYCLE_PASSWORD"))) {
            var login = tasks.submit(() -> mvc.perform(post("/api/v1/auth/login")
                .cookie(newCookie).header(PreauthStore.HEADER, csrfToken(newBootstrap))
                .contentType("application/json").content(form)).andReturn());
            try (var statement = lifecycle.prepareStatement(
                "select identity_lifecycle.mutate(?,1,'DISABLE',null,null)")) {
                statement.setObject(1, UUID.fromString(id));
                try (var result = statement.executeQuery()) {
                    org.junit.jupiter.api.Assertions.assertTrue(result.next() && result.getBoolean(1));
                }
            }
            var outcome = login.get().getResponse();
            org.junit.jupiter.api.Assertions.assertTrue(outcome.getStatus() == 200 || outcome.getStatus() == 401);
            mvc.perform(get("/api/v1/users/me").cookie(current)).andExpect(status().isUnauthorized());
            Cookie racing = outcome.getCookie(AuthSessions.COOKIE);
            if (racing != null) {
                mvc.perform(get("/api/v1/users/me").cookie(racing)).andExpect(status().isUnauthorized());
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BASE_TEST_LIFECYCLE", matches = "true")
    void resetRollbackAndCommitFenceRealLoginAndProfile() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@identity-test.invalid";
        String previous = "Previous passphrase for isolated fixture";
        String replacement = "Replacement passphrase for isolated fixture";
        var bootstrap = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH")).andExpect(status().isOk()).andReturn();
        Cookie preauth = bootstrap.getResponse().getCookie(PreauthStore.COOKIE);
        String csrf = csrfToken(bootstrap);
        mvc.perform(post("/api/v1/auth/register").cookie(preauth).header(PreauthStore.HEADER, csrf)
            .contentType("application/json").content(json.writeValueAsString(
                new VerificationController.Registration(email, "Password reset fixture")))).andExpect(status().isAccepted());
        var verification = mailbox.takeForTest(email).orElseThrow();
        mvc.perform(post("/api/v1/auth/verify-email").cookie(preauth).header(PreauthStore.HEADER, csrf)
            .contentType("application/json").content(json.writeValueAsString(
                new VerificationController.Verification(verification.token(), previous)))).andExpect(status().isNoContent());
        String oldForm = json.writeValueAsString(new VerificationController.Login(email, previous));
        Cookie active = login(preauth, csrf, oldForm);
        var profile = mvc.perform(get("/api/v1/users/me").cookie(active)).andExpect(status().isOk()).andReturn();
        UUID id = UUID.fromString(json.readTree(profile.getResponse().getContentAsString()).path("data").path("id").asText());
        var authCsrf = mvc.perform(get("/api/v1/auth/csrf").cookie(active).param("scope", "AUTH"))
            .andExpect(status().isOk()).andReturn();
        UUID operation = UUID.randomUUID();
        String resetHash = new IdentityPasswords().encode(replacement);
        try (var lifecycle = DriverManager.getConnection(System.getenv("BASE_DB_JDBC_URL"),
                 "identity_lifecycle_operator", System.getenv("BASE_TEST_LIFECYCLE_PASSWORD"));
             var change = lifecycle.prepareStatement("select identity_lifecycle.reset_credential(?,?,?,?)");
             var threads = Executors.newSingleThreadExecutor()) {
            lifecycle.setAutoCommit(false);
            change.setObject(1, operation);
            change.setObject(2, id);
            change.setLong(3, 1);
            change.setString(4, resetHash);
            try (var result = change.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next() && result.getBoolean(1));
            }
            lifecycle.rollback();
            try (var read = lifecycle.prepareStatement("select * from identity_lifecycle.reset_outcome(?)")) {
                read.setObject(1, operation);
                try (var result = read.executeQuery()) {
                    org.junit.jupiter.api.Assertions.assertFalse(result.next());
                }
            }
            lifecycle.rollback();
            mvc.perform(get("/api/v1/users/me").cookie(active)).andExpect(status().isOk());
            var racingPreauth = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH"))
                .andExpect(status().isOk()).andReturn();
            var gate = new CommitBarrier();
            transactionManager.barrier.set(gate);
            var loginFirst = threads.submit(() -> login(racingPreauth.getResponse().getCookie(PreauthStore.COOKIE),
                csrfToken(racingPreauth), oldForm));
            try {
                org.junit.jupiter.api.Assertions.assertTrue(gate.arrived.await(5, java.util.concurrent.TimeUnit.SECONDS));
                var timeout = org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, change::executeQuery);
                org.junit.jupiter.api.Assertions.assertEquals("55P03", timeout.getSQLState());
                lifecycle.rollback();
            } finally {
                gate.release.countDown();
            }
            active = loginFirst.get(5, java.util.concurrent.TimeUnit.SECONDS);
            try (var result = change.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next() && result.getBoolean(1));
            }
            lifecycle.rollback();
            var fault = LostCommitReplySocketFactory.fixture();
            try (var lost = fault.connect(System.getenv("BASE_DB_JDBC_URL"), "identity_lifecycle_operator",
                     System.getenv("BASE_TEST_LIFECYCLE_PASSWORD"));
                 var reset = lost.prepareStatement("select identity_lifecycle.reset_credential(?,?,1,?)")) {
                lost.setAutoCommit(false);
                reset.setObject(1, operation);
                reset.setObject(2, id);
                reset.setString(3, resetHash);
                try (var result = reset.executeQuery()) {
                    org.junit.jupiter.api.Assertions.assertTrue(result.next() && result.getBoolean(1));
                }
                fault.arm();
                var error = org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, lost::commit);
                org.junit.jupiter.api.Assertions.assertEquals("08006", error.getSQLState());
                org.junit.jupiter.api.Assertions.assertTrue(fault.replyDropped());
            }
            try (var reconcile = DriverManager.getConnection(System.getenv("BASE_DB_JDBC_URL"),
                    "identity_lifecycle_operator", System.getenv("BASE_TEST_LIFECYCLE_PASSWORD"))) {
                reconcile.setReadOnly(true);
                reconcile.setAutoCommit(false);
                org.junit.jupiter.api.Assertions.assertEquals("COMMITTED revision=2",
                    DevelopmentAdminCommand.reconciliationOutput(reconcile, false, operation));
                reconcile.rollback();
            }
            change.setLong(3, 2);
            try (var result = change.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next() && !result.getBoolean(1));
            }
            lifecycle.rollback();
        }
        mvc.perform(patch("/api/v1/users/me").cookie(active).header(PreauthStore.HEADER, csrfToken(authCsrf))
            .contentType("application/json").content("{\"displayName\":\"Rejected stale edit\"}"))
            .andExpect(status().isUnauthorized());
        var next = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH")).andExpect(status().isOk()).andReturn();
        Cookie nextCookie = next.getResponse().getCookie(PreauthStore.COOKIE);
        mvc.perform(post("/api/v1/auth/login").cookie(nextCookie).header(PreauthStore.HEADER, csrfToken(next))
            .contentType("application/json").content(oldForm)).andExpect(status().isUnauthorized());
        Cookie updated = login(nextCookie, csrfToken(next),
            json.writeValueAsString(new VerificationController.Login(email, replacement)));
        mvc.perform(get("/api/v1/users/me").cookie(updated)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.displayName").value("Password reset fixture"));
        try (var runtime = DriverManager.getConnection(System.getenv("BASE_DB_JDBC_URL"),
                 System.getenv("BASE_DB_RUNTIME_USER"), System.getenv("BASE_DB_RUNTIME_PASSWORD"))) {
            org.junit.jupiter.api.Assertions.assertEquals(2, snapshotRevision(runtime, email));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BASE_TEST_ADMIN_HTTP", matches = "true")
    void dedicatedRecoveryInvalidatesActualLoginAndProfilePaths() throws Exception {
        String email = System.getenv("BASE_TEST_GOVERNANCE_EMAIL");
        UUID account = UUID.fromString(System.getenv("BASE_TEST_GOVERNANCE_TARGET"));
        String original = "Initial dedicated recovery HTTP fixture";
        String replacement = "Replacement dedicated recovery HTTP fixture";
        String jdbcUrl = System.getenv("BASE_DB_JDBC_URL");
        try (var runtime = DriverManager.getConnection(jdbcUrl, System.getenv("BASE_DB_RUNTIME_USER"),
                 System.getenv("BASE_DB_RUNTIME_PASSWORD"));
             var approver = DriverManager.getConnection(jdbcUrl, "operator_approver",
                 System.getenv("BASE_TEST_APPROVER_PASSWORD"));
             var operator = DriverManager.getConnection(jdbcUrl, "deployment_operator",
                 System.getenv("BASE_TEST_OPERATOR_PASSWORD"));
             var executor = Executors.newSingleThreadExecutor()) {
            long initial = snapshotRevision(runtime, email);
            recoverDedicated(operator, approveDedicated(approver, account, initial), account, initial, original);
            long previous = initial + 1;
            UUID approval = approveDedicated(approver, account, previous);
            String oldForm = json.writeValueAsString(new VerificationController.Login(email, original));
            var first = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH"))
                .andExpect(status().isOk()).andReturn();
            var gate = new CommitBarrier();
            transactionManager.barrier.set(gate);
            var authenticating = executor.submit(() -> login(
                first.getResponse().getCookie(PreauthStore.COOKIE), csrfToken(first), oldForm));
            operator.setAutoCommit(false);
            try {
                org.junit.jupiter.api.Assertions.assertTrue(gate.arrived.await(5, java.util.concurrent.TimeUnit.SECONDS));
                try (var settings = operator.createStatement()) {
                    settings.execute("set local lock_timeout = '250ms'");
                }
                var locked = org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                    () -> recoverDedicated(operator, approval, account, previous, replacement));
                org.junit.jupiter.api.Assertions.assertEquals("55P03", locked.getSQLState());
                operator.rollback();
            } finally {
                gate.release.countDown();
            }
            Cookie oldCookie = authenticating.get(5, java.util.concurrent.TimeUnit.SECONDS);
            var csrf = mvc.perform(get("/api/v1/auth/csrf").cookie(oldCookie).param("scope", "AUTH"))
                .andExpect(status().isOk()).andReturn();
            var profileGate = new CommitBarrier(1);
            transactionManager.barrier.set(profileGate);
            var updating = executor.submit(() -> mvc.perform(patch("/api/v1/users/me")
                .cookie(oldCookie).header(PreauthStore.HEADER, csrfToken(csrf))
                .contentType("application/json").content("{\"displayName\":\"Committed before recovery\"}"))
                .andExpect(status().isOk()).andReturn());
            try {
                org.junit.jupiter.api.Assertions.assertTrue(profileGate.arrived.await(5, java.util.concurrent.TimeUnit.SECONDS));
                try (var settings = operator.createStatement()) {
                    settings.execute("set local lock_timeout = '250ms'");
                }
                var locked = org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                    () -> recoverDedicated(operator, approval, account, previous, replacement));
                org.junit.jupiter.api.Assertions.assertEquals("55P03", locked.getSQLState());
                operator.rollback();
            } finally {
                profileGate.release.countDown();
            }
            updating.get(5, java.util.concurrent.TimeUnit.SECONDS);
            recoverDedicated(operator, approval, account, previous, replacement);
            operator.commit();
            mvc.perform(patch("/api/v1/users/me").cookie(oldCookie).header(PreauthStore.HEADER, csrfToken(csrf))
                .contentType("application/json").content("{\"displayName\":\"Stale write\"}"))
                .andExpect(status().isUnauthorized());
            var fresh = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH"))
                .andExpect(status().isOk()).andReturn();
            Cookie preauth = fresh.getResponse().getCookie(PreauthStore.COOKIE);
            mvc.perform(post("/api/v1/auth/login").cookie(preauth).header(PreauthStore.HEADER, csrfToken(fresh))
                .contentType("application/json").content(oldForm))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Set-Cookie"));
            Cookie valid = login(preauth, csrfToken(fresh),
                json.writeValueAsString(new VerificationController.Login(email, replacement)));
            mvc.perform(get("/api/v1/users/me").cookie(valid)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Committed before recovery"));
            org.junit.jupiter.api.Assertions.assertEquals(previous + 1, snapshotRevision(runtime, email));
        }
    }

    private static long snapshotRevision(java.sql.Connection runtime, String email) throws java.sql.SQLException {
        try (var statement = runtime.prepareStatement("select security_revision from identity_api.login_snapshot(?)")) {
            statement.setString(1, email);
            try (var result = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static UUID approveDedicated(java.sql.Connection approver, UUID account, long revision)
            throws java.sql.SQLException {
        try (var statement = approver.prepareStatement("""
            select operator_approvals.approve(?,?,'RESET_DEDICATED',null,'deployment_operator',
                'synthetic-fixture-http',clock_timestamp()+interval '1 hour')
            """)) {
            statement.setObject(1, account);
            statement.setLong(2, revision);
            try (var result = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static void recoverDedicated(java.sql.Connection operator, UUID approval, UUID account,
                                         long revision, String password) throws java.sql.SQLException {
        try (var statement = operator.prepareStatement("""
            select deployment_operator.recover(?,?,?,'RESET_DEDICATED',null,?)
            """)) {
            statement.setObject(1, approval);
            statement.setObject(2, account);
            statement.setLong(3, revision);
            statement.setString(4, new IdentityPasswords().encode(password));
            try (var result = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                org.junit.jupiter.api.Assertions.assertEquals(account, result.getObject(1, UUID.class));
            }
        }
    }

    private Cookie login(Cookie cookie, String csrf, String body) throws Exception {
        assertNotNull(cookie);
        var result = mvc.perform(post("/api/v1/auth/login").cookie(cookie).header(PreauthStore.HEADER, csrf)
                .contentType("application/json").content(body))
            .andExpect(status().isOk()).andReturn();
        Cookie auth = result.getResponse().getCookie(AuthSessions.COOKIE);
        assertNotNull(auth);
        return auth;
    }

    private String csrfToken(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TransactionFaults {
        @Bean FaultingTransactionManager transactionManager(DataSource dataSource) {
            return new FaultingTransactionManager(dataSource);
        }
    }

    enum CommitFault { ROLLBACK, UNKNOWN_AFTER_COMMIT }

    static final class CommitBarrier {
        final java.util.concurrent.CountDownLatch arrived = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger precedingCommits;

        CommitBarrier() {
            this(0);
        }

        CommitBarrier(int precedingCommits) {
            this.precedingCommits = new java.util.concurrent.atomic.AtomicInteger(precedingCommits);
        }
    }

    static final class FaultingTransactionManager extends JdbcTransactionManager {
        private final AtomicReference<CommitFault> next = new AtomicReference<>();
        private final AtomicReference<CommitBarrier> barrier = new AtomicReference<>();

        FaultingTransactionManager(DataSource dataSource) {
            super(dataSource);
        }

        @Override protected void doCommit(DefaultTransactionStatus status) {
            CommitBarrier gate = barrier.get();
            if (gate != null && gate.precedingCommits.getAndDecrement() == 0 && barrier.compareAndSet(gate, null)) {
                gate.arrived.countDown();
                try {
                    if (!gate.release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new TransactionSystemException("Fixture did not release commit barrier");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new TransactionSystemException("Fixture interrupted", interrupted);
                }
            }
            CommitFault fault = next.getAndSet(null);
            if (fault == CommitFault.ROLLBACK) {
                doRollback(status);
                throw new UnexpectedRollbackException("Injected confirmed rollback");
            }
            super.doCommit(status);
            if (fault == CommitFault.UNKNOWN_AFTER_COMMIT) {
                throw new TransactionSystemException("Injected lost commit reply");
            }
        }
    }
}
