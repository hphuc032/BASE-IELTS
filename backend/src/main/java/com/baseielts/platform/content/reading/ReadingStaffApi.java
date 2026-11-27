package com.baseielts.platform.content.reading;

import com.baseielts.platform.identity.StaffAuthentication;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff Passage transport. No runtime database beans are installed pending G02 and G03. */
@RestController
@ConditionalOnProperty(name = "base.reading.enabled", havingValue = "true")
@RequestMapping(ReadingStaffApi.ROOT)
public final class ReadingStaffApi {
    static final String ROOT = "/api/v1/teacher/reading";
    static final String CLONES = "/{id}/drafts";
    static final String DRAFT = CLONES + "/{draftId}";
    static final String PREVIEW = DRAFT + "/preview";
    static final String EDITION = "/{id}/editions/{editionId}";
    static final String PLACEMENT = "/{id}/placement";

    private final StaffAuthentication auth;
    private final Databases databases;

    public ReadingStaffApi(StaffAuthentication auth, Databases databases) {
        this.auth = auth;
        this.databases = databases;
    }

    @GetMapping
    ResponseEntity<?> list(HttpServletRequest request) {
        var page = auth.execute(request, databases.reader(), (jdbc, actor) ->
            validated(jdbc, actor, false, () -> listPage(request, jdbc, actor)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(page);
    }

    private static ReadingStaffProjection.Page listPage(HttpServletRequest request, JdbcTemplate jdbc,
                                                        StaffAuthentication.Actor actor) {
        var parameters = request.getParameterMap();
        if (!Set.of("page", "size", "q", "topicId", "status").containsAll(parameters.keySet())
            || parameters.values().stream().anyMatch(values -> values.length != 1)) {
            throw new ReadingDraftInput.Invalid("INVALID_INPUT");
        }
        int number = pageParameter(request.getParameter("page"), 0, 0, 99);
        int size = pageParameter(request.getParameter("size"), 20, 1, 50);
        String query = request.getParameter("q");
        if (query != null && (query.isBlank() || query.codePointCount(0, query.length()) > 100
            || query.codePoints().anyMatch(Character::isISOControl))) {
            throw new ReadingDraftInput.Invalid("INVALID_INPUT");
        }
        String topic = request.getParameter("topicId");
        String state = request.getParameter("status");
        if (state != null && !Set.of("DRAFT", "FROZEN").contains(state)) {
            throw new ReadingDraftInput.Invalid("INVALID_INPUT");
        }
        return ReadingRoutines.list(jdbc, actor, number, size, query,
            topic == null ? null : ReadingDraftInput.filterUuid(topic), state);
    }

    @PostMapping
    ResponseEntity<?> create(HttpServletRequest request) {
        byte[] body = body(request);
        var result = auth.execute(request, databases.writer(), (jdbc, actor) ->
            validated(jdbc, actor, true, () -> ReadingRoutines.mutate(jdbc, actor, "CREATE", null, null,
                ReadingDraftInput.passageCreate(body), null)));
        return item(ResponseEntity.created(location(result)), result.revision(), result);
    }

    @PostMapping(CLONES)
    ResponseEntity<?> cloneDraft(HttpServletRequest request, @PathVariable String id) {
        byte[] body = body(request);
        var result = auth.execute(request, databases.writer(), (jdbc, actor) ->
            validated(jdbc, actor, true, () -> ReadingRoutines.mutate(jdbc, actor, "CLONE", ReadingDraftInput.routeUuid(id), null,
                ReadingDraftInput.cloneDraft(body), null)));
        return item(ResponseEntity.created(location(result)), result.revision(), result);
    }

    @GetMapping(DRAFT)
    ResponseEntity<?> draft(HttpServletRequest request, @PathVariable String id, @PathVariable String draftId) {
        var result = read(request, id, draftId, true);
        return item(ResponseEntity.ok(), result.revision(), result);
    }

    @GetMapping(PREVIEW)
    ResponseEntity<?> preview(HttpServletRequest request, @PathVariable String id, @PathVariable String draftId) {
        var result = auth.execute(request, databases.reader(), (jdbc, actor) ->
            validated(jdbc, actor, false, () -> ReadingRoutines.preview(jdbc, actor,
                ReadingDraftInput.routeUuid(id), ReadingDraftInput.routeUuid(draftId))));
        return item(ResponseEntity.ok(), result.revision(), result.preview());
    }

    @GetMapping(EDITION)
    ResponseEntity<?> edition(HttpServletRequest request, @PathVariable String id, @PathVariable String editionId) {
        var result = read(request, id, editionId, false);
        return item(ResponseEntity.ok(), result.revision(), result);
    }

    @PutMapping(DRAFT)
    ResponseEntity<?> update(HttpServletRequest request, @PathVariable String id, @PathVariable String draftId) {
        byte[] body = body(request);
        var result = auth.execute(request, databases.writer(), (jdbc, actor) ->
            validated(jdbc, actor, true, () -> ReadingRoutines.mutate(jdbc, actor, "EDIT", ReadingDraftInput.routeUuid(id),
                ReadingDraftInput.routeUuid(draftId), ReadingDraftInput.passage(body),
                expected(request, false))));
        return item(ResponseEntity.ok(), result.revision(), result);
    }

    @PutMapping(PLACEMENT)
    ResponseEntity<?> placement(HttpServletRequest request, @PathVariable String id) {
        byte[] body = body(request);
        var placement = auth.execute(request, databases.writer(), (jdbc, actor) ->
            validated(jdbc, actor, true, () -> ReadingRoutines.placement(jdbc, actor, ReadingDraftInput.routeUuid(id),
                ReadingDraftInput.placement(body), expected(request, true))));
        return item(ResponseEntity.ok(), placement.revision(), placement);
    }

    private ReadingStaffProjection.PassageAdmin read(HttpServletRequest request, String passage, String edition,
                                                      boolean draftOnly) {
        return auth.execute(request, databases.reader(), (jdbc, actor) ->
            validated(jdbc, actor, false, () -> ReadingRoutines.edition(jdbc, actor, ReadingDraftInput.routeUuid(passage),
                ReadingDraftInput.routeUuid(edition), draftOnly)));
    }

    private static URI location(ReadingStaffProjection.PassageAdmin result) {
        return URI.create(ROOT + "/" + result.stableId() + "/drafts/" + result.editionId());
    }

    private static ResponseEntity<?> item(ResponseEntity.BodyBuilder status, long revision, Object data) {
        return status.cacheControl(CacheControl.noStore()).eTag("\"" + revision + "\"").body(Map.of("data", data));
    }

    private static int pageParameter(String value, int defaultValue, int minimum, int maximum) {
        if (value == null) {
            return defaultValue;
        }
        if (!value.matches("[0-9]{1,2}")) {
            throw new ReadingDraftInput.Invalid("INVALID_INPUT");
        }
        int number = Integer.parseInt(value);
        if (number < minimum || number > maximum) {
            throw new ReadingDraftInput.Invalid("INVALID_INPUT");
        }
        return number;
    }

    private static <T> T validated(JdbcTemplate jdbc, StaffAuthentication.Actor actor, boolean editing,
                                   Supplier<T> action) {
        try {
            return action.get();
        } catch (ReadingDraftInput.Invalid invalid) {
            ReadingRoutines.checkAccess(jdbc, actor, editing);
            throw invalid;
        }
    }

    private static Long expected(HttpServletRequest request, boolean zeroAllowed) {
        try {
            return revision(request, zeroAllowed);
        } catch (ReadingDraftInput.Invalid invalid) {
            return invalid.code().equals("PRECONDITION_REQUIRED") ? null : -1L;
        }
    }

    static long revision(HttpServletRequest request, boolean zeroAllowed) {
        var headers = request.getHeaders(HttpHeaders.IF_MATCH);
        if (!headers.hasMoreElements()) {
            throw new ReadingDraftInput.Invalid("PRECONDITION_REQUIRED");
        }
        String value = headers.nextElement();
        boolean zero = zeroAllowed && "\"0\"".equals(value);
        if (headers.hasMoreElements() || (!zero && !value.matches("\"[1-9][0-9]{0,18}\""))) {
            throw new ReadingDraftInput.Invalid("INVALID_PRECONDITION");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException overflow) {
            throw new ReadingDraftInput.Invalid("INVALID_PRECONDITION");
        }
    }

    private static byte[] body(HttpServletRequest request) {
        try {
            MediaType type = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
            if (type == null || !MediaType.APPLICATION_JSON.equalsTypeAndSubtype(type)
                || (type.getCharset() != null && !StandardCharsets.UTF_8.equals(type.getCharset()))) {
                throw new ReadingDraftInput.Invalid("UNSUPPORTED_MEDIA");
            }
        } catch (IllegalArgumentException malformedMedia) {
            throw new ReadingDraftInput.Invalid("UNSUPPORTED_MEDIA");
        }
        try {
            int maximum = ReadingDraftInput.MAX_BODY_BYTES;
            if (request.getContentLengthLong() > maximum) {
                throw new ReadingDraftInput.Invalid("PAYLOAD_TOO_LARGE");
            }
            byte[] bytes = request.getInputStream().readNBytes(maximum + 1);
            if (bytes.length > maximum) {
                throw new ReadingDraftInput.Invalid("PAYLOAD_TOO_LARGE");
            }
            return bytes;
        } catch (IOException incomplete) {
            throw new ReadingDraftInput.Invalid("INVALID_INPUT");
        }
    }

    @ExceptionHandler(ReadingDraftInput.Invalid.class)
    ResponseEntity<?> invalid(ReadingDraftInput.Invalid failure) {
        int status = switch (failure.code()) {
            case "NOT_FOUND" -> 404;
            case "PRECONDITION_REQUIRED" -> 428;
            case "UNSUPPORTED_MEDIA" -> 415;
            case "PAYLOAD_TOO_LARGE" -> 413;
            default -> 400;
        };
        return error(status, failure.code());
    }

    @ExceptionHandler(StaffAuthentication.Denied.class)
    ResponseEntity<?> denied(StaffAuthentication.Denied failure) {
        return error(failure.error() == StaffAuthentication.Rejection.AUTH_REQUIRED ? 401 : 403, failure.error().name());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<?> unavailable(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return switch (sql.getSQLState()) {
                    case "RD001" -> error(400, "INVALID_INPUT");
                    case "RD002" -> error(403, "FORBIDDEN");
                    case "RD003" -> error(404, "NOT_FOUND");
                    case "RD004" -> error(409, "INVALID_STATE");
                    case "RD005" -> error(428, "PRECONDITION_REQUIRED");
                    case "RD007" -> error(400, "INVALID_PRECONDITION");
                    case "RD006" -> error(412, "REVISION_MISMATCH");
                    case "RD008" -> error(401, "AUTH_REQUIRED");
                    case "RD009" -> error(409, "DUPLICATE_SLUG");
                    default -> error(503, "SERVICE_UNAVAILABLE");
                };
            }
        }
        return error(503, "SERVICE_UNAVAILABLE");
    }

    private static ResponseEntity<?> error(int status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
            .body(Map.of("error", Map.of("code", code, "message", "Reading request rejected",
                "details", Map.of(), "traceId", UUID.randomUUID().toString())));
    }

    public record Databases(StaffAuthentication.Database reader, StaffAuthentication.Database writer) {}
}
