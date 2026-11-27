package com.baseielts.platform.identity;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baseielts.platform.config.IdentityRequestBoundary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "base.identity.enabled", havingValue = "true")
@RequestMapping("/api/v1")
final class VerificationController {
    private static final int MAX_BODY = 8192;
    private static final Map<String, Object> ACK =
        Map.of("data", Map.of("message", "If verification is available, instructions will be sent."));
    private final VerificationService verification;
    private final PreauthStore preauth;
    private final AuthSessions sessions;
    private final AuthenticationService authentication;
    private final BoundedRateLimit loginSource = new BoundedRateLimit(4096, 30.0/3600, 30);
    private final Semaphore loginSlots = new Semaphore(4);
    private final IdentityPasswords passwords;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final Semaphore bootstrapSlots = new Semaphore(16);
    private final Semaphore issuanceSlots = new Semaphore(16);
    private final BoundedRateLimit bootstrapRequests = new BoundedRateLimit(1, 80, 80);
    private final BoundedRateLimit issuance = new BoundedRateLimit(1, 20, 20);
    private final BoundedRateLimit issuanceSource = new BoundedRateLimit(4096, 20.0/3600, 20);
    private final BoundedRateLimit issuanceAddress = new BoundedRateLimit(4096, 5.0/3600, 5);
    private final BoundedRateLimit verificationSource = new BoundedRateLimit(4096, 20.0/3600, 20);

    VerificationController(VerificationService verification, PreauthStore preauth,
                           IdentityPasswords passwords, ObjectMapper mapper, Validator validator,
                           AuthSessions sessions, AuthenticationService authentication) {
        this.verification = verification;
        this.preauth = preauth;
        this.passwords = passwords;
        this.mapper = mapper;
        this.validator = validator;
        this.sessions = sessions;
        this.authentication = authentication;
    }

    @GetMapping("/auth/csrf")
    ResponseEntity<?> csrf(@RequestParam("scope") String scope, HttpServletRequest request) {
        if ("AUTH".equals(scope)) {
            AuthSessions.Binding binding = sessions.lookup(CookieSupport.value(request, AuthSessions.COOKIE));
            if (binding == null) return unauthorized();
            var token = authentication.authorized(binding, ignored -> binding.csrf, false, false);
            return token == null ? unauthorized() : ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("data", Map.of("token", token, "headerName", PreauthStore.HEADER)));
        }
        if (!"PREAUTH".equals(scope)) {
            return error(400, "INVALID_REQUEST", "Choose a supported token scope.");
        }
        if (!bootstrapRequests.allow("") || !bootstrapSlots.tryAcquire()) {
            return throttled();
        }
        try {
            PreauthStore.Bootstrap session = preauth.bootstrap(CookieSupport.value(request, PreauthStore.COOKIE), request.getRemoteAddr());
            try {
                IdentityRequestBoundary.reclaimIfUndelivered(request, () -> preauth.abandonUndelivered(session));
                var response = ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store");
                if (session.newSessionToken() != null) {
                    ResponseCookie cookie = ResponseCookie.from(PreauthStore.COOKIE, session.newSessionToken())
                        .secure(true).httpOnly(true).sameSite("Lax").path("/api/v1")
                        .maxAge(Duration.ofMinutes(15)).build();
                    response.header(HttpHeaders.SET_COOKIE, cookie.toString());
                }
                return response.body(Map.of("data", Map.of("token", session.csrf(), "headerName", PreauthStore.HEADER)));
            } catch (RuntimeException prepFailure) {
                preauth.abandonUndelivered(session);
                throw prepFailure;
            }
        } catch (IdentityCapacityException error) {
            return throttled();
        } finally {
            bootstrapSlots.release();
        }
    }

    @PostMapping("/auth/register")
    ResponseEntity<?> register(HttpServletRequest request) {
        try {
            Registration body = read(request, Registration.class);
            return issue(request, body.email(), body.displayName(), true);
        } catch (InvalidRequest failure) {
            return failure.response();
        }
    }

    @PostMapping("/auth/resend-verification")
    ResponseEntity<?> resend(HttpServletRequest request) {
        try {
            Resend body = read(request, Resend.class);
            return issue(request, body.email(), null, false);
        } catch (InvalidRequest failure) {
            return failure.response();
        }
    }

    @PostMapping("/auth/verify-email")
    ResponseEntity<?> verify(HttpServletRequest request) {
        try {
            Verification body = read(request, Verification.class);
            if (!verificationSource.allow(request.getRemoteAddr())) {
                return throttled();
            }
            if (!verification.deliveryAvailable()) {
                return error(503, "SERVICE_UNAVAILABLE", "Verification is unavailable.");
            }
            if (!passwords.isAccepted(body.newPassword())) {
                return error(400, "INVALID_REQUEST", "Use a password that meets the length and strength requirements.");
            }
            if (!verification.verify(body.token(), body.newPassword())) {
                return error(400, "INVALID_VERIFICATION", "The verification link is not valid.");
            }
            return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        } catch (InvalidRequest failure) {
            return failure.response();
        } catch (IdentityCapacityException failure) {
            return throttled();
        } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
            return error(503, "SERVICE_UNAVAILABLE", "Verification is unavailable.");
        }
    }

    @PostMapping("/auth/login")
    ResponseEntity<?> login(HttpServletRequest request) {
        try {
            Login body = read(request, Login.class);
            if (!loginSource.allow(request.getRemoteAddr()) || !loginSlots.tryAcquire()) return throttled();
            try {
                var claim = preauth.claim(CookieSupport.value(request, PreauthStore.COOKIE),
                    request.getHeader(PreauthStore.HEADER));
                if (claim == null) return error(403, "CSRF_INVALID", "Refresh the verification token.");
                boolean discard = true;
                try {
                    var login = authentication.login(body.email(), body.password());
                    if (login == null) {
                        discard = false;
                        return error(401, "LOGIN_FAILED", "Email or password is incorrect.");
                    }
                    try {
                        IdentityRequestBoundary.reclaimIfUndelivered(request, () -> sessions.revoke(login.prepared().binding()));
                        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                            .header(HttpHeaders.SET_COOKIE, CookieSupport.secure(AuthSessions.COOKIE,
                                login.prepared().token(), AuthSessions.ABSOLUTE).toString())
                            .body(Map.of("data", login.profile()));
                    } catch (RuntimeException responsePreparationFailure) {
                        sessions.revoke(login.prepared().binding());
                        throw responsePreparationFailure;
                    }
                } catch (org.springframework.transaction.UnexpectedRollbackException
                         | org.springframework.dao.DataAccessException | IdentityCapacityException knownFailure) {
                    discard = false;
                    throw knownFailure;
                } finally {
                    preauth.finish(claim, discard);
                }
            } finally {
                loginSlots.release();
            }
        } catch (InvalidRequest failure) {
            return failure.response();
        } catch (IdentityCapacityException failure) {
            return throttled();
        } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
            return error(503, "SERVICE_UNAVAILABLE", "Please try again.");
        }
    }

    @GetMapping("/users/me")
    ResponseEntity<?> profile(HttpServletRequest request) {
        return profile(request, null);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/users/me")
    ResponseEntity<?> updateProfile(HttpServletRequest request) {
        try {
            AuthSessions.Binding binding = sessions.lookup(CookieSupport.value(request, AuthSessions.COOKIE));
            if (binding == null) return unauthorized();
            ProfilePatch patch = read(request, ProfilePatch.class, binding.csrf);
            return profile(request, patch.displayName());
        } catch (InvalidRequest failure) {
            return failure.response();
        }
    }

    @PostMapping("/auth/logout")
    ResponseEntity<?> logout(HttpServletRequest request) {
        AuthSessions.Binding binding = sessions.lookup(CookieSupport.value(request, AuthSessions.COOKIE));
        if (binding == null) return unauthorized();
        if (!OpaqueTokens.matches(request.getHeader(PreauthStore.HEADER), binding.csrf)) {
            return error(403, "CSRF_INVALID", "Refresh the verification token.");
        }
        try {
            if (authentication.authorized(binding, ignored -> true, false, true) == null) return unauthorized();
            return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
            return error(503, "SERVICE_UNAVAILABLE", "Please try again.");
        }
    }

    private ResponseEntity<?> profile(HttpServletRequest request, String displayName) {
        AuthSessions.Binding binding = sessions.lookup(CookieSupport.value(request, AuthSessions.COOKIE));
        if (binding == null) return unauthorized();
        if (displayName != null && !OpaqueTokens.matches(request.getHeader(PreauthStore.HEADER), binding.csrf)) {
            return error(403, "CSRF_INVALID", "Refresh the verification token.");
        }
        try {
            var profile = authentication.authorized(binding,
                account -> authentication.profile(account, binding.revision, displayName), true, false);
            return profile == null ? unauthorized()
                : ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(Map.of("data", profile));
        } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
            return error(503, "SERVICE_UNAVAILABLE", "Please try again.");
        }
    }

    private static ResponseEntity<?> unauthorized() {
        return error(401, "AUTH_REQUIRED", "Authentication required.");
    }

    record Login(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 256) String password) {
        @Override public String toString() { return "Login[redacted]"; }
    }
    record ProfilePatch(@NotBlank @Size(max = 120) String displayName) {}

    private ResponseEntity<?> issue(HttpServletRequest request, String email, String displayName, boolean register) {
        long started = System.nanoTime();
        if (!issuanceSource.allow(request.getRemoteAddr()) || !issuance.allow("") || !issuanceSlots.tryAcquire()) {
            return throttled();
        }
        try {
            if (!verification.deliveryAvailable()) {
                return error(503, "SERVICE_UNAVAILABLE", "Verification is unavailable.");
            }
            if (issuanceAddress.allow(email.toLowerCase(Locale.ROOT))) {
                try {
                    verification.request(email, displayName, register);
                } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
                    // No provider dispatch on failed or uncertain commit. Keep the public acknowledgement uniform.
                }
            }
        } finally {
            issuanceSlots.release();
        }
        long remaining = TimeUnit.MILLISECONDS.toNanos(300) - (System.nanoTime() - started);
        if (remaining > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(remaining);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        }
        return ResponseEntity.accepted().header(HttpHeaders.CACHE_CONTROL, "no-store").body(ACK);
    }

    private <T> T read(HttpServletRequest request, Class<T> type) throws InvalidRequest {
        return read(request, type, null);
    }

    private <T> T read(HttpServletRequest request, Class<T> type, String authCsrf) throws InvalidRequest {
        try {
            MediaType media = MediaType.parseMediaType(request.getContentType() == null ? "" : request.getContentType());
            if (!MediaType.APPLICATION_JSON.isCompatibleWith(media)) {
                throw new InvalidRequest(415, "UNSUPPORTED_MEDIA");
            }
        } catch (IllegalArgumentException failure) {
            throw new InvalidRequest(415, "UNSUPPORTED_MEDIA");
        }
        if (request.getContentLengthLong() > MAX_BODY) {
            throw new InvalidRequest(413, "BODY_TOO_LARGE");
        }
        try {
            byte[] content = request.getInputStream().readNBytes(MAX_BODY + 1);
            if (content.length > MAX_BODY) {
                throw new InvalidRequest(413, "BODY_TOO_LARGE");
            }
            String supplied = request.getHeader(PreauthStore.HEADER);
            if (authCsrf == null
                ? !preauth.validates(CookieSupport.value(request, PreauthStore.COOKIE), supplied)
                : !OpaqueTokens.matches(supplied, authCsrf)) {
                throw new InvalidRequest(403, "CSRF_INVALID");
            }
            T result = mapper.readerFor(type)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(content);
            if (result == null || !validator.validate(result).isEmpty()) {
                throw new InvalidRequest(400, "INVALID_REQUEST");
            }
            return result;
        } catch (IOException | IllegalArgumentException failure) {
            throw new InvalidRequest(400, "INVALID_REQUEST");
        }
    }

    private static ResponseEntity<?> throttled() {
        return ResponseEntity.status(429).header(HttpHeaders.RETRY_AFTER, "1")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(errorBody("ADMISSION_LIMIT", "Please retry later."));
    }

    private static ResponseEntity<?> error(int status, String code, String message) {
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store").body(errorBody(code, message));
    }

    private static Map<String, Object> errorBody(String code, String message) {
        return Map.of("error", Map.of("code", code, "message", message,
            "details", java.util.List.of(), "traceId", UUID.randomUUID().toString()));
    }

    record Registration(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 120) String displayName) {}
    record Resend(@NotBlank @Email @Size(max = 254) String email) {}
    record Verification(@NotBlank @Size(max = 128) String token,
                        @NotBlank @Size(max = 256) String newPassword) {
        @Override public String toString() { return "Verification[redacted]"; }
    }

    private static final class InvalidRequest extends Exception {
        private final int status;
        private final String code;

        InvalidRequest(int status, String code) {
            this.status = status;
            this.code = code;
        }

        ResponseEntity<?> response() {
            return error(status, code, "The request could not be processed.");
        }
    }
}
