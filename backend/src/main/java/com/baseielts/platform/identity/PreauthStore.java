package com.baseielts.platform.identity;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "base.identity.enabled", havingValue = "true")
final class PreauthStore {
    static final String COOKIE = "__Secure-BASEIELTS-PREAUTH";
    static final String HEADER = "X-CSRF-TOKEN";
    private static final int LIMIT = 2000;
    private static final long LIFE = Duration.ofMinutes(15).toNanos();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final BoundedRateLimit global;
    private final BoundedRateLimit source;
    private final LongSupplier nanoTime;

    PreauthStore() {
        this(System::nanoTime);
    }

    PreauthStore(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        global = new BoundedRateLimit(1, 40, 40, nanoTime);
        source = new BoundedRateLimit(4096, .5, 10, nanoTime);
    }

    Bootstrap bootstrap(String presented, String trustedSource) {
        String knownKey = OpaqueTokens.valid(presented, 43) ? OpaqueTokens.key(presented) : null;
        synchronized (this) {
            Bootstrap existing = reuse(knownKey, nanoTime.getAsLong());
            if (existing != null) return existing;
        }
        String token = OpaqueTokens.create(32);
        String key = OpaqueTokens.key(token);
        String csrf = OpaqueTokens.create(32);
        return insert(knownKey, token, key, csrf, trustedSource);
    }

    private synchronized Bootstrap insert(String knownKey, String token, String key, String csrf, String sourceKey) {
        long now = nanoTime.getAsLong();
        Bootstrap existing = reuse(knownKey, now);
        if (existing != null) return existing;
        if (entries.size() >= LIMIT || entries.containsKey(key) || !global.allow("") || !source.allow(sourceKey)) {
            throw new IdentityCapacityException();
        }
        entries.put(key, new Entry(csrf, now, null));
        return new Bootstrap(token, csrf);
    }

    private Bootstrap reuse(String knownKey, long now) {
        Entry known = knownKey == null ? null : entries.get(knownKey);
        if (known != null && now - known.created() < LIFE && known.claim() != null) {
            throw new IdentityCapacityException();
        }
        if (known != null && now - known.created() < LIFE) {
            return new Bootstrap(null, known.csrf());
        }
        if (known != null) {
            entries.remove(knownKey);
        }
        return null;
    }

    void abandonUndelivered(Bootstrap bootstrap) {
        if (bootstrap.newSessionToken() == null) return;
        String key = OpaqueTokens.key(bootstrap.newSessionToken());
        synchronized (this) {
            Entry entry = entries.get(key);
            if (entry != null && entry.claim() == null && entry.csrf().equals(bootstrap.csrf())) {
                entries.remove(key);
            }
        }
    }

    synchronized boolean validates(String token, String csrf) {
        if (!OpaqueTokens.valid(token, 43) || !OpaqueTokens.valid(csrf, 43)) {
            return false;
        }
        Entry entry = entries.get(OpaqueTokens.key(token));
        return entry != null && entry.claim() == null && nanoTime.getAsLong() - entry.created() < LIFE
            && OpaqueTokens.matches(csrf, entry.csrf());
    }

    // Entry insertion time is monotonic, and reuse does not extend lifetime.
    // Each cleanup examines only the oldest prefix, with an explicit work bound.
    private void discardExpired(long now, int max) {
        Iterator<Entry> iterator = entries.values().iterator();
        for (int i = 0; i < max && iterator.hasNext(); i++) {
            if (now - iterator.next().created() < LIFE) {
                return;
            }
            iterator.remove();
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 1000)
    synchronized void cleanup() {
        discardExpired(nanoTime.getAsLong(), 128);
    }

    record Bootstrap(String newSessionToken, String csrf) {
        @Override public String toString() { return "PreauthBootstrap[redacted]"; }
    }

    synchronized Claim claim(String raw, String csrf) {
        if (!validates(raw, csrf)) {
            return null;
        }
        String key = OpaqueTokens.key(raw);
        Entry entry = entries.get(key);
        Claim claim = new Claim(key, java.util.UUID.randomUUID());
        entries.put(key, new Entry(entry.csrf(), entry.created(), claim.id()));
        return claim;
    }

    synchronized void finish(Claim claim, boolean discard) {
        Entry entry = entries.get(claim.key());
        if (entry == null || !claim.id().equals(entry.claim())) {
            return;
        }
        if (discard || nanoTime.getAsLong() - entry.created() >= LIFE) {
            entries.remove(claim.key());
        } else {
            entries.put(claim.key(), new Entry(entry.csrf(), entry.created(), null));
        }
    }

    record Claim(String key, java.util.UUID id) {
        @Override public String toString() { return "PreauthClaim[redacted]"; }
    }

    private record Entry(String csrf, long created, java.util.UUID claim) {}
}
