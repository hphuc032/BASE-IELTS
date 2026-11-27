package com.baseielts.platform.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

final class CookieSupport {
    private CookieSupport() {}

    static String value(HttpServletRequest request, String name) {
        String value = null;
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) {
                    if (value != null) return null;
                    value = cookie.getValue();
                }
            }
        }
        return value;
    }

    static ResponseCookie secure(String name, String token, Duration maxAge) {
        return ResponseCookie.from(name, token).secure(true).httpOnly(true)
            .sameSite("Lax").path("/api/v1").maxAge(maxAge).build();
    }
}
