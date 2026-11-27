package com.baseielts.platform.content;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** The flat catalog request contract. These limits are not a route activation switch. */
final class CatalogHttpInput {
    private static final Set<String> FILTERS = Set.of("page", "size", "q", "status");
    private static final int MAX_PAGE = 99;
    private static final int MAX_SIZE = 50;

    private CatalogHttpInput() {}

    static CatalogInput write(HttpServletRequest request, CatalogInput.Kind kind) {
        return parseBody(request, body -> CatalogInput.parse(kind, body));
    }

    static CatalogInput.Status status(HttpServletRequest request) {
        return parseBody(request, CatalogInput::parseStatus);
    }

    static ListFilter list(Map<String, String[]> parameters) {
        if (!FILTERS.containsAll(parameters.keySet()) || parameters.values().stream().anyMatch(values -> values.length != 1)) {
            throw rejected(400, "INVALID_REQUEST");
        }
        int page = number(parameters, "page", 0, 0, MAX_PAGE);
        int size = number(parameters, "size", 20, 1, MAX_SIZE);
        String query = value(parameters, "q");
        if (query != null && (query.isBlank() || query.codePointCount(0, query.length()) > 100
            || query.codePoints().anyMatch(Character::isISOControl))) {
            throw rejected(400, "INVALID_REQUEST");
        }
        CatalogInput.Status status = null;
        if (value(parameters, "status") != null) {
            try {
                status = CatalogInput.Status.valueOf(value(parameters, "status"));
            } catch (IllegalArgumentException invalid) {
                throw rejected(400, "INVALID_REQUEST");
            }
        }
        return new ListFilter(page, size, query, status);
    }

    static long expectedRevision(HttpServletRequest request) {
        var headers = request.getHeaders(HttpHeaders.IF_MATCH);
        if (!headers.hasMoreElements()) {
            throw rejected(428, "PRECONDITION_REQUIRED");
        }
        String value = headers.nextElement();
        if (headers.hasMoreElements() || !value.matches("\"[1-9][0-9]{0,18}\"")) {
            throw rejected(400, "INVALID_REQUEST");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException overflow) {
            throw rejected(400, "INVALID_REQUEST");
        }
    }

    private static <T> T parseBody(HttpServletRequest request, Function<byte[], T> parser) {
        MediaType type;
        try {
            type = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
        } catch (IllegalArgumentException malformed) {
            throw rejected(415, "UNSUPPORTED_MEDIA");
        }
        if (type == null || !MediaType.APPLICATION_JSON.equalsTypeAndSubtype(type)
            || (type.getCharset() != null && !StandardCharsets.UTF_8.equals(type.getCharset()))) {
            throw rejected(415, "UNSUPPORTED_MEDIA");
        }
        if (request.getContentLengthLong() > CatalogInput.MAX_BODY_BYTES) {
            throw rejected(413, "BODY_TOO_LARGE");
        }
        try {
            byte[] body = request.getInputStream().readNBytes(CatalogInput.MAX_BODY_BYTES + 1);
            if (body.length > CatalogInput.MAX_BODY_BYTES) {
                throw rejected(413, "BODY_TOO_LARGE");
            }
            return parser.apply(body);
        } catch (IOException | IllegalArgumentException malformed) {
            throw rejected(400, "INVALID_REQUEST");
        }
    }

    private static int number(Map<String, String[]> parameters, String field, int missing, int min, int max) {
        String value = value(parameters, field);
        if (value == null) {
            return missing;
        }
        if (!value.matches("[0-9]{1,2}")) {
            throw rejected(400, "INVALID_REQUEST");
        }
        int number = Integer.parseInt(value);
        if (number < min || number > max) {
            throw rejected(400, "INVALID_REQUEST");
        }
        return number;
    }

    private static String value(Map<String, String[]> parameters, String name) {
        String[] entries = parameters.get(name);
        return entries == null ? null : entries[0];
    }

    private static Rejected rejected(int status, String code) {
        return new Rejected(status, code);
    }

    record ListFilter(int page, int size, String literalSearch, CatalogInput.Status status) {
        int offset() {
            return page * size;
        }
    }

    static final class Rejected extends RuntimeException {
        final int status;

        Rejected(int status, String code) {
            super(code);
            this.status = status;
        }
    }
}
