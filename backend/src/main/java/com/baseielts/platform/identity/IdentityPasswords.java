package com.baseielts.platform.identity;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;

@Component
final class IdentityPasswords {
    private static final Set<String> COMMON = Set.of(
        "passwordpassword", "passwordpassword1", "123456789012345", "qwertyuiopasdfgh",
        "qwerty1234567890", "letmeinletmein1", "iloveyouiloveyou");
    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    private final Semaphore permits = new Semaphore(4);
    private final String dummyHash = encoder.encode(OpaqueTokens.create(32));

    boolean isAccepted(String password) {
        if (password == null || password.length() > 256) {
            return false;
        }
        int length = password.codePointCount(0, password.length());
        return length >= 15 && length <= 128
            && !COMMON.contains(password.toLowerCase(Locale.ROOT))
            && password.codePoints().noneMatch(c -> c == 0 || c >= 0xD800 && c <= 0xDFFF);
    }

    boolean matches(String password, String hash) {
        if (!permits.tryAcquire()) {
            throw new IdentityCapacityException();
        }
        try {
            if (password == null || password.length() > 256) {
                return false;
            }
            return encoder.matches(password, hash == null ? dummyHash : hash) && hash != null;
        } finally {
            permits.release();
        }
    }

    String encode(String password) {
        if (!isAccepted(password)) {
            throw new IllegalArgumentException("Password does not satisfy the approved policy");
        }
        if (!permits.tryAcquire()) {
            throw new IdentityCapacityException();
        }
        try {
            return encoder.encode(password);
        } finally {
            permits.release();
        }
    }
}
