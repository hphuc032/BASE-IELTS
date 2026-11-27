package com.baseielts.platform.identity;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.function.LongSupplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Fixed memory token buckets. Distinct inputs that share a bucket also share its limit. */
final class BoundedRateLimit {
    private final Mac hash;
    private final double[] tokens;
    private final long[] refreshed;
    private final double perNano;
    private final int capacity;
    private final LongSupplier nanoTime;

    BoundedRateLimit(int buckets, double perSecond, int capacity) {
        this(buckets, perSecond, capacity, System::nanoTime);
    }

    BoundedRateLimit(int buckets, double perSecond, int capacity, LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        this.perNano = perSecond / 1_000_000_000;
        this.capacity = capacity;
        this.tokens = new double[buckets];
        this.refreshed = new long[buckets];
        java.util.Arrays.fill(tokens, capacity);
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        try {
            this.hash = Mac.getInstance("HmacSHA256");
            this.hash.init(new SecretKeySpec(key, "HmacSHA256"));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("Rate bucket hash unavailable", error);
        }
    }

    synchronized boolean allow(String source) {
        byte[] digest = hash.doFinal(source.getBytes(StandardCharsets.UTF_8));
        int key = (digest[0] & 255) << 8 | (digest[1] & 255);
        int index = key % tokens.length;
        long now = nanoTime.getAsLong();
        long elapsed = refreshed[index] == 0 ? 0 : Math.max(0, now - refreshed[index]);
        refreshed[index] = now;
        tokens[index] = Math.min(capacity, tokens[index] + elapsed * perNano);
        if (tokens[index] < 1) {
            return false;
        }
        tokens[index]--;
        return true;
    }
}
