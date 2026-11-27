package com.baseielts.platform.content;

import com.baseielts.platform.identity.StaffAuthentication;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No runtime databases bean or route admission is supplied while G02 and G03 remain open. */
@RestController
@ConditionalOnProperty(name = "base.catalog.enabled", havingValue = "true")
@RequestMapping("/api/v1/teacher")
public final class CatalogStaffApi {
    private static final String COURSE_COLLECTION = "/courses";
    private static final String LEVEL_COLLECTION = "/courses/{courseId}/levels";
    private static final String LESSON_COLLECTION = "/levels/{levelId}/lessons";
    private static final String DETAIL = "/{collection:courses|levels|lessons}/{id}";
    private final StaffAuthentication auth;
    private final Databases databases;

    public CatalogStaffApi(StaffAuthentication auth, Databases databases) {
        this.auth = Objects.requireNonNull(auth);
        this.databases = Objects.requireNonNull(databases);
    }

    @GetMapping({COURSE_COLLECTION, LEVEL_COLLECTION, LESSON_COLLECTION})
    ResponseEntity<?> list(HttpServletRequest request, @PathVariable(required = false) String courseId,
                           @PathVariable(required = false) String levelId) {
        CatalogInput.Kind kind = collectionKind(courseId, levelId);
        var page = auth.execute(request, databases.reader(), (jdbc, actor) -> CatalogRoutines.list(jdbc, actor, kind,
            parentId(courseId, levelId), valueOrInvalid(() -> CatalogHttpInput.list(request.getParameterMap()))));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of("data", page.data(), "page", page.metadata()));
    }

    @GetMapping(DETAIL)
    ResponseEntity<?> detail(HttpServletRequest request, @PathVariable String collection, @PathVariable String id) {
        CatalogInput.Kind kind = detailKind(collection);
        var row = auth.execute(request, databases.reader(),
            (jdbc, actor) -> CatalogRoutines.detail(jdbc, actor, kind, resourceId(id)));
        return item(ResponseEntity.ok(), row);
    }

    @PostMapping({COURSE_COLLECTION, LEVEL_COLLECTION, LESSON_COLLECTION})
    ResponseEntity<?> create(HttpServletRequest request, @PathVariable(required = false) String courseId,
                             @PathVariable(required = false) String levelId) {
        byte[] body = CatalogHttpInput.body(request);
        CatalogInput.Kind kind = collectionKind(courseId, levelId);
        var row = auth.execute(request, databases.writer(), (jdbc, actor) -> CatalogRoutines.create(jdbc, actor, kind,
            parentId(courseId, levelId), valueOrInvalid(() -> CatalogHttpInput.write(body, kind)), UUID.randomUUID()));
        String collection = switch (kind) {
            case COURSE -> "courses";
            case LEVEL -> "levels";
            case LESSON -> "lessons";
        };
        return item(ResponseEntity.created(URI.create("/api/v1/teacher/" + collection + "/" + row.id())), row);
    }

    @PatchMapping(DETAIL)
    ResponseEntity<?> edit(HttpServletRequest request, @PathVariable String collection, @PathVariable String id) {
        byte[] body = CatalogHttpInput.body(request);
        CatalogInput.Kind kind = detailKind(collection);
        var row = auth.execute(request, databases.writer(), (jdbc, actor) -> CatalogRoutines.edit(jdbc, actor, kind,
            resourceId(id), valueOrInvalid(() -> CatalogHttpInput.write(body, kind)), revision(request),
            UUID.randomUUID()));
        return item(ResponseEntity.ok(), row);
    }

    @PostMapping(DETAIL + "/status")
    ResponseEntity<?> status(HttpServletRequest request, @PathVariable String collection, @PathVariable String id) {
        byte[] body = CatalogHttpInput.body(request);
        CatalogInput.Kind kind = detailKind(collection);
        var row = auth.execute(request, databases.writer(), (jdbc, actor) -> CatalogRoutines.changeStatus(jdbc, actor,
            kind, resourceId(id), valueOrInvalid(() -> CatalogHttpInput.status(body)), revision(request),
            UUID.randomUUID()));
        return item(ResponseEntity.ok(), row);
    }

    private static ResponseEntity<?> item(ResponseEntity.BodyBuilder response, CatalogRoutines.Row row) {
        return response.cacheControl(CacheControl.noStore()).eTag("\"" + row.revision() + "\"")
            .body(Map.of("data", row.data()));
    }

    private static CatalogInput.Kind collectionKind(String courseId, String levelId) {
        if (courseId != null) {
            return CatalogInput.Kind.LEVEL;
        }
        return levelId == null ? CatalogInput.Kind.COURSE : CatalogInput.Kind.LESSON;
    }

    private static UUID parentId(String courseId, String levelId) {
        return resourceId(courseId == null ? levelId : courseId);
    }

    private static CatalogInput.Kind detailKind(String collection) {
        return switch (collection) {
            case "courses" -> CatalogInput.Kind.COURSE;
            case "levels" -> CatalogInput.Kind.LEVEL;
            case "lessons" -> CatalogInput.Kind.LESSON;
            default -> throw new IllegalArgumentException("Unexpected catalog route");
        };
    }

    private static UUID resourceId(String value) {
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equalsIgnoreCase(value) ? uuid : null;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static <T> T valueOrInvalid(Supplier<T> parse) {
        try {
            return parse.get();
        } catch (CatalogHttpInput.Rejected invalid) {
            // A null required value instructs the scoped routine to reject syntax only after capability.
            return null;
        }
    }

    private static Long revision(HttpServletRequest request) {
        try {
            return CatalogHttpInput.expectedRevision(request);
        } catch (CatalogHttpInput.Rejected invalid) {
            if (invalid.status == 428) {
                return null;
            }
            return 0L;
        }
    }

    @ExceptionHandler(CatalogHttpInput.Rejected.class)
    ResponseEntity<?> inputError(CatalogHttpInput.Rejected invalid) {
        return error(invalid.status, invalid.getMessage());
    }

    @ExceptionHandler(StaffAuthentication.Denied.class)
    ResponseEntity<?> authError(StaffAuthentication.Denied denied) {
        return error(denied.error() == StaffAuthentication.Rejection.AUTH_REQUIRED ? 401 : 403, denied.error().name());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<?> databaseError(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return switch (sql.getSQLState()) {
                    case "CA001" -> error(400, "INVALID_REQUEST");
                    case "CA002" -> error(403, "FORBIDDEN");
                    case "CA003" -> error(404, "RESOURCE_NOT_FOUND");
                    case "CA004" -> error(409, "INVALID_STATE");
                    case "CA005" -> error(428, "PRECONDITION_REQUIRED");
                    case "CA006" -> error(412, "REVISION_MISMATCH");
                    case "CA007" -> error(400, "INVALID_PRECONDITION");
                    case "CA008" -> error(401, "AUTH_REQUIRED");
                    case "23505" -> error(409, "DUPLICATE_CATALOG_KEY");
                    default -> error(503, "SERVICE_UNAVAILABLE");
                };
            }
        }
        return error(503, "SERVICE_UNAVAILABLE");
    }

    private static ResponseEntity<?> error(int status, String code) {
        String message = switch (status) {
            case 401 -> "Authentication required.";
            case 403 -> "Request not allowed.";
            case 404 -> "Catalog resource not found.";
            case 503 -> "Catalog is temporarily unavailable.";
            default -> "Catalog request could not be completed.";
        };
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
            .body(Map.of("error", Map.of("code", code, "message", message, "details", Map.of(),
                "traceId", UUID.randomUUID().toString())));
    }

    public static final class Databases {
        private final StaffAuthentication.Database reader;
        private final StaffAuthentication.Database writer;

        /** Catalog exclusively uses these dedicated pools with fixed maximum sizes. */
        public Databases(HikariDataSource readerPool, HikariDataSource writerPool) {
            if (readerPool == writerPool) {
                throw new IllegalArgumentException("Catalog reads and writes require dedicated pools");
            }
            reader = new StaffAuthentication.Database(StaffAuthentication.Access.READ,
                new CatalogConnectionAdmission(readerPool));
            writer = new StaffAuthentication.Database(StaffAuthentication.Access.WRITE,
                new CatalogConnectionAdmission(writerPool));
        }

        StaffAuthentication.Database reader() { return reader; }

        StaffAuthentication.Database writer() { return writer; }
    }
}
