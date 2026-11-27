package com.baseielts.platform.identity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreauthStoreTest {
    @Test
    void distributedConcurrentAdmissionsSaturateExactCapacityAndReclaimOnlyExactBindings() throws Exception {
        AtomicLong nanos = new AtomicLong(1);
        var store = new PreauthStore(nanos::get);
        var auth = new AuthSessions();
        String authToken = activate(auth, UUID.randomUUID(), 0);
        var existing = store.bootstrap(null, "existing");
        int accepted = 1;
        try (var workers = Executors.newFixedThreadPool(16)) {
            for (int second = 0; second < 60; second++) {
                nanos.addAndGet(Duration.ofSeconds(1).toNanos());
                var round = new ArrayList<Callable<Boolean>>();
                for (int request = 0; request < 48; request++) {
                    String source = "fixture-" + second + "-" + request;
                    round.add(() -> {
                        try {
                            return store.bootstrap(null, source).newSessionToken() != null;
                        } catch (IdentityCapacityException expected) {
                            return false;
                        }
                    });
                }
                for (var result : workers.invokeAll(round)) {
                    if (result.get()) accepted++;
                }
            }
        }
        assertEquals(2000, accepted);
        assertNotNull(auth.lookup(authToken));
        assertThrows(IdentityCapacityException.class, () -> store.bootstrap(null, "overflow"));
        assertNull(store.bootstrap(existing.newSessionToken(), "existing").newSessionToken());
        var claim = store.claim(existing.newSessionToken(), existing.csrf());
        assertNotNull(claim);
        assertNull(store.claim(existing.newSessionToken(), existing.csrf()));
        store.finish(claim, true);
        store.finish(claim, true);
        var replacement = store.bootstrap(null, "replacement");
        store.abandonUndelivered(replacement);
        store.abandonUndelivered(replacement);
        assertNotNull(store.bootstrap(null, "replacement").newSessionToken());
        assertThrows(IdentityCapacityException.class, () -> store.bootstrap(null, "overflow"));
        nanos.addAndGet(Duration.ofMinutes(16).toNanos());
        store.cleanup();
        assertNotNull(store.bootstrap(null, "after-expiry").newSessionToken());
        assertNotNull(auth.lookup(authToken));
    }

    private static String activate(AuthSessions store, UUID user, long revision) {
        var prepared = store.prepare(user, revision);
        store.publish(prepared);
        return prepared.token();
    }
}
