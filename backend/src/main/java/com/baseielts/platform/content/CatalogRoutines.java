package com.baseielts.platform.content;

import com.baseielts.platform.identity.StaffAuthentication.Actor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Catalog operations on the already fenced staff transaction. No connection or table access of its own. */
final class CatalogRoutines {
    private CatalogRoutines() {}

    static Page list(JdbcTemplate jdbc, Actor actor, CatalogInput.Kind kind, UUID parent, CatalogHttpInput.ListFilter filter) {
        return jdbc.query("select * from catalog_staff.list_items(?,?,?,?,?,?,?,?)", result -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            if (!result.next()) {
                throw new IllegalStateException("Missing catalog page count");
            }
            long total = result.getLong("total_count");
            do {
                if (result.getObject("id", UUID.class) != null) {
                    rows.add(row(result, kind).data());
                }
            } while (result.next());
            return new Page(List.copyOf(rows), filter.page(), filter.size(), total);
        }, kind.name(), actor.id(), actor.securityRevision(), parent, filter == null ? null : filter.page(),
            filter == null ? null : filter.size(), filter == null ? null : filter.literalSearch(),
            filter == null || filter.status() == null ? null : filter.status().name());
    }

    static Row detail(JdbcTemplate jdbc, Actor actor, CatalogInput.Kind kind, UUID id) {
        return jdbc.queryForObject("select * from catalog_staff.get_item(?,?,?,?)", (result, index) -> row(result, kind),
            kind.name(), actor.id(), actor.securityRevision(), id);
    }

    static Row create(JdbcTemplate jdbc, Actor actor, CatalogInput.Kind kind, UUID parent, CatalogInput input, UUID requestId) {
        return mutate(jdbc, actor, kind, "CREATE", parent, input, null, null, requestId);
    }

    static Row edit(JdbcTemplate jdbc, Actor actor, CatalogInput.Kind kind, UUID id, CatalogInput input,
                    Long revision, UUID requestId) {
        return mutate(jdbc, actor, kind, "EDIT", id, input, null, revision, requestId);
    }

    static Row changeStatus(JdbcTemplate jdbc, Actor actor, CatalogInput.Kind kind, UUID id,
                            CatalogInput.Status status, Long revision, UUID requestId) {
        return mutate(jdbc, actor, kind, "CHANGE_STATUS", id, null, status, revision, requestId);
    }

    private static Row mutate(JdbcTemplate jdbc, Actor actor, CatalogInput.Kind kind, String action, UUID target,
                              CatalogInput input, CatalogInput.Status status, Long revision, UUID requestId) {
        return jdbc.queryForObject("select * from catalog_staff.mutate_item(?,?,?,?,?,?,?,?,?,?,?,?)",
            (result, index) -> row(result, kind), kind.name(), action, actor.id(), actor.securityRevision(), target,
            input == null ? null : input.key(), input == null ? null : input.label(),
            input == null ? null : input.description(), input == null ? null : input.displayOrder(),
            status == null ? null : status.name(), revision, requestId);
    }

    private static Row row(ResultSet result, CatalogInput.Kind kind) throws SQLException {
        Map<String, Object> data = new LinkedHashMap<>();
        UUID id = result.getObject("id", UUID.class);
        long revision = result.getLong("revision");
        data.put("id", id);
        if (kind != CatalogInput.Kind.COURSE) {
            data.put("parentId", result.getObject("parent_id", UUID.class));
        }
        boolean level = kind == CatalogInput.Kind.LEVEL;
        data.put(level ? "code" : "slug", result.getString("item_key"));
        data.put(level ? "name" : "title", result.getString("label"));
        String description = result.getString("description");
        if (description != null) {
            data.put("description", description);
        }
        data.put("displayOrder", result.getInt("display_order"));
        data.put("status", result.getString("status"));
        data.put("revision", revision);
        data.put("createdAt", timestamp(result, "created_at"));
        data.put("updatedAt", timestamp(result, "updated_at"));
        return new Row(id, revision, Map.copyOf(data));
    }

    private static Instant timestamp(ResultSet result, String name) throws SQLException {
        return result.getObject(name, OffsetDateTime.class).toInstant();
    }

    record Row(UUID id, long revision, Map<String, Object> data) {}

    record Page(List<Map<String, Object>> data, int number, int size, long total) {
        Map<String, Object> metadata() {
            return Map.of("number", number, "size", size, "totalElements", total,
                "totalPages", total / size + (total % size == 0 ? 0 : 1));
        }
    }
}
