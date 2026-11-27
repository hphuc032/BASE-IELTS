package com.baseielts.platform.identity;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "base.identity.enabled", havingValue = "true")
final class AuthSessions {
    static final String COOKIE = "__Secure-BASEIELTS-AUTH";
    static final Duration ABSOLUTE = Duration.ofDays(7);
    private static final long IDLE = Duration.ofMinutes(60).toNanos();
    private final Map<UUID, Binding> accounts = new HashMap<>();
    private final Map<String, Binding> keys = new HashMap<>();
    private final LongSupplier nanoTime;

    AuthSessions() {
        this(System::nanoTime);
    }

    AuthSessions(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    synchronized Binding lookup(String raw) {
        if (!OpaqueTokens.valid(raw, 43)) {
            return null;
        }
        Binding binding = keys.get(OpaqueTokens.key(raw));
        return current(binding) ? binding : null;
    }

    synchronized boolean current(Binding binding) {
        if (binding == null || accounts.get(binding.account) != binding) {
            return false;
        }
        if (binding.expired(nanoTime.getAsLong())) {
            revoke(binding);
            return false;
        }
        return true;
    }

    Prepared prepare(UUID account, long revision) {
        String token = OpaqueTokens.create(32);
        Binding binding = new Binding(account, revision, OpaqueTokens.key(token), OpaqueTokens.create(32), nanoTime.getAsLong());
        return new Prepared(token, binding);
    }

    synchronized void publish(Prepared prepared) {
        Binding binding = prepared.binding();
        revoke(accounts.get(binding.account));
        accounts.put(binding.account, binding);
        keys.put(binding.key, binding);
    }

    record Prepared(String token, Binding binding) {
        @Override public String toString() { return "PreparedAuth[redacted]"; }
    }

    synchronized void touch(Binding binding) {
        if (current(binding)) {
            binding.lastActivity = nanoTime.getAsLong();
        }
    }

    synchronized void revokeAccount(UUID account) {
        revoke(accounts.get(account));
    }

    synchronized void revoke(Binding binding) {
        if (binding != null && accounts.remove(binding.account, binding)) {
            keys.remove(binding.key, binding);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    synchronized void cleanup() {
        long now = nanoTime.getAsLong();
        accounts.values().removeIf(binding -> {
            if (!binding.expired(now)) {
                return false;
            }
            keys.remove(binding.key, binding);
            return true;
        });
    }

    static final class Binding {
        final UUID account;
        final long revision;
        final String key;
        final String csrf;
        private final long created;
        private long lastActivity;

        Binding(UUID account, long revision, String key, String csrf, long created) {
            this.account = account;
            this.revision = revision;
            this.key = key;
            this.csrf = csrf;
            this.created = created;
            this.lastActivity = created;
        }

        boolean expired(long now) {
            return now - created >= ABSOLUTE.toNanos() || now - lastActivity >= IDLE;
        }
    }
}
