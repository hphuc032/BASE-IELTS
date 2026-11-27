package com.baseielts.platform.content.reading;

import com.baseielts.platform.identity.StaffAuthentication;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/** Accesses only scoped routines through the connection already held by StaffAuthentication. */
final class ReadingRoutines {
    private static final ObjectMapper JSON = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        .build();

    private ReadingRoutines() {}

    static ReadingStaffProjection.Page list(JdbcTemplate jdbc, StaffAuthentication.Actor actor,
                                            int number, int size, String query, UUID topicId, String state) {
        return read(jdbc, ReadingStaffProjection.Page.class,
            "select reading_staff.list_passages(?,?,?,?,?,?,?)::text",
            actor.id(), actor.securityRevision(), number, size, query, topicId, state);
    }

    static ReadingStaffProjection.PassageAdmin edition(JdbcTemplate jdbc, StaffAuthentication.Actor actor,
                                                       UUID passage, UUID edition, boolean draftOnly) {
        return read(jdbc, ReadingStaffProjection.PassageAdmin.class,
            "select reading_staff.get_edition(?,?,?,?,?)::text",
            actor.id(), actor.securityRevision(), passage, edition, draftOnly);
    }

    static ReadingStaffProjection.VersionedPreview preview(JdbcTemplate jdbc, StaffAuthentication.Actor actor,
                                                           UUID passage, UUID edition) {
        return read(jdbc, ReadingStaffProjection.VersionedPreview.class,
            "select reading_staff.get_preview(?,?,?,?)::text",
            actor.id(), actor.securityRevision(), passage, edition);
    }

    static ReadingStaffProjection.PassageAdmin mutate(JdbcTemplate jdbc, StaffAuthentication.Actor actor,
                                                       String action, UUID passage, UUID edition, Object payload,
                                                       Long expected) {
        final String json;
        try {
            json = JSON.writeValueAsString(payload);
        } catch (JsonProcessingException encoding) {
            throw new DataRetrievalFailureException("Cannot encode normalized draft", encoding);
        }
        return read(jdbc, ReadingStaffProjection.PassageAdmin.class,
            "select reading_staff.mutate_passage(?,?,?,?,?,?::jsonb,?,?)::text",
            action, actor.id(), actor.securityRevision(), passage, edition, json, expected, UUID.randomUUID());
    }

    static ReadingStaffProjection.Placement placement(JdbcTemplate jdbc, StaffAuthentication.Actor actor,
                                                      UUID passage, ReadingDraftInput.Placement target, Long expected) {
        return read(jdbc, ReadingStaffProjection.Placement.class,
            "select reading_staff.set_placement(?,?,?,?,?,?,?)::text",
            actor.id(), actor.securityRevision(), passage, target.lessonId(), target.position(), expected,
            UUID.randomUUID());
    }

    static void checkAccess(JdbcTemplate jdbc, StaffAuthentication.Actor actor, boolean editing) {
        Boolean permitted = jdbc.queryForObject("select reading_staff.check_access(?,?,?)", Boolean.class,
            actor.id(), actor.securityRevision(), editing);
        if (!Boolean.TRUE.equals(permitted)) {
            throw new DataRetrievalFailureException("Missing staff authorization result");
        }
    }

    private static <T> T read(JdbcTemplate jdbc, Class<T> type, String sql, Object... parameters) {
        String json = jdbc.queryForObject(sql, String.class, parameters);
        try {
            if (json != null) {
                T decoded = JSON.readValue(json, type);
                if (decoded != null) {
                    return decoded;
                }
            }
        } catch (JsonProcessingException malformed) {
            throw new DataRetrievalFailureException("Invalid Reading routine result", malformed);
        }
        throw new DataRetrievalFailureException("Missing Reading routine result");
    }
}
