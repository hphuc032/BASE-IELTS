package com.baseielts.platform.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

final class OpaqueTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private OpaqueTokens() {}

    static String create(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    static String key(String token) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(token));
    }

    static boolean valid(String token, int characters) {
        return token != null && token.length() == characters && token.matches("[A-Za-z0-9_-]+");
    }

    static boolean matches(String supplied, String expected) {
        return supplied != null && MessageDigest.isEqual(
            supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
