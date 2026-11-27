package com.baseielts.platform.identity;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthSessionsTest {
    @Test
    void exactIdleAndAbsoluteExpiryAndRestart() {
        var time = new AtomicLong(1);
        var store = new AuthSessions(time::get);
        UUID user = UUID.randomUUID();
        String idleToken = activate(store, user, 0);
        time.addAndGet(Duration.ofMinutes(60).toNanos() - 1);
        assertNotNull(store.lookup(idleToken));
        time.incrementAndGet();
        assertNull(store.lookup(idleToken));

        String absoluteToken = activate(store, user, 1);
        for (int hours = 0; hours < 168; hours++) {
            for (int half = 0; half < 2; half++) {
                time.addAndGet(Duration.ofMinutes(30).toNanos());
                if (hours == 167 && half == 1) break;
                var binding = store.lookup(absoluteToken);
                assertNotNull(binding);
                store.touch(binding);
            }
        }
        assertNull(store.lookup(absoluteToken));

        String activeToken = activate(store, user, 2);
        assertNotNull(store.lookup(activeToken));
        assertNull(new AuthSessions(time::get).lookup(activeToken));
    }

    @Test
    void staleGenerationCannotRevokeReplacement() {
        var store = new AuthSessions();
        UUID user = UUID.randomUUID();
        var old = store.lookup(activate(store, user, 0));
        String replacement = activate(store, user, 0);
        store.revoke(old);
        assertNotNull(store.lookup(replacement));
    }

    private static String activate(AuthSessions store, UUID user, long revision) {
        var prepared = store.prepare(user, revision);
        store.publish(prepared);
        return prepared.token();
    }
}
