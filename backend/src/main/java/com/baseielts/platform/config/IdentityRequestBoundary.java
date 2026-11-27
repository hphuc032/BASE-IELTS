package com.baseielts.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public final class IdentityRequestBoundary extends OncePerRequestFilter {
    static final int MAX_JSON_BODY = 8192;
    private static final String UNDELIVERED = IdentityRequestBoundary.class.getName() + ".undelivered";
    private static final Set<String> JSON_POSTS = Set.of(
        "/api/v1/auth/register", "/api/v1/auth/resend-verification", "/api/v1/auth/verify-email", "/api/v1/auth/login");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain downstream)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean json = ("PATCH".equals(request.getMethod()) && "/api/v1/users/me".equals(path))
            || ("POST".equals(request.getMethod()) && JSON_POSTS.contains(path));
        if (!json) {
            forward(request, response, downstream);
            return;
        }
        if (!isJson(request.getContentType())) {
            reject(response, 415, "UNSUPPORTED_MEDIA", "Send a JSON request.");
            return;
        }
        if (request.getContentLengthLong() > MAX_JSON_BODY) {
            reject(response, 413, "BODY_TOO_LARGE", "Request is too large.");
            return;
        }
        byte[] content = request.getInputStream().readNBytes(MAX_JSON_BODY + 1);
        if (content.length > MAX_JSON_BODY) {
            reject(response, 413, "BODY_TOO_LARGE", "Request is too large.");
            return;
        }
        forward(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var bytes = new ByteArrayInputStream(content);
                return new ServletInputStream() {
                    @Override public boolean isFinished() { return bytes.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public int read() { return bytes.read(); }
                    @Override public int read(byte[] b, int offset, int length) { return bytes.read(b, offset, length); }
                    @Override public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Identity JSON uses synchronous request processing");
                    }
                };
            }
        }, response, downstream);
    }

    public static void reclaimIfUndelivered(HttpServletRequest request, Runnable reclaim) {
        request.setAttribute(UNDELIVERED, reclaim);
    }

    private static void forward(HttpServletRequest request, HttpServletResponse response, FilterChain downstream)
            throws ServletException, IOException {
        try {
            downstream.doFilter(request, response);
            if (!response.isCommitted() && response.getStatus() >= 400) reclaim(request);
        } catch (IOException | ServletException | RuntimeException failure) {
            if (!response.isCommitted()) reclaim(request);
            throw failure;
        } finally {
            request.removeAttribute(UNDELIVERED);
        }
    }

    private static void reclaim(HttpServletRequest request) {
        if (request.getAttribute(UNDELIVERED) instanceof Runnable reclaim) {
            reclaim.run();
        }
    }

    private static boolean isJson(String type) {
        try {
            if (type == null) return false;
            var media = MediaType.parseMediaType(type);
            return MediaType.APPLICATION_JSON.getType().equals(media.getType())
                && MediaType.APPLICATION_JSON.getSubtype().equals(media.getSubtype());
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    static void reject(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
            {"error":{"code":"%s","message":"%s","details":[],"traceId":"%s"}}
            """.formatted(error,message,UUID.randomUUID()));
    }
}
