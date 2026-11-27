package com.baseielts.platform.content.reading;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import com.baseielts.platform.identity.StaffAuthentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PassageAuthoringContractTest {
    private static final String KEPT = "af81d798-fbe6-455b-896f-09944d0d4d65";

    @Test
    void preservesSuppliedParagraphIdentityAndLeavesAllocationToDatabase() {
        var draft = ReadingDraftInput.passage(bytes("""
            {"title":" Café ", "visibility":"PRIVATE", "paragraphs":[
              {"content":"New paragraph"},
              {"paragraphId":"%s","content":"Retained",
               "translations":[{"language":"vi-vn","text":"Tiếng Việt"}]}
            ]}
            """.formatted(KEPT)));
        assertEquals("Café", draft.title());
        assertNull(draft.paragraphs().get(0).paragraphId());
        assertEquals(UUID.fromString(KEPT), draft.paragraphs().get(1).paragraphId());
        assertEquals("vi-VN", draft.paragraphs().get(1).translations().getFirst().language());
    }

    @Test
    void rejectsNullAndDuplicateParagraphIdentifiers() {
        assertEquals("INVALID_PARAGRAPH_ID", paragraphError("""
            {"paragraphId":null,"content":"one"}
            """));
        assertEquals("INVALID_PARAGRAPH_ID", paragraphError("""
            {"paragraphId":"%1$s","content":"one"}, {"paragraphId":"%1$s","content":"two"}
            """.formatted(KEPT)));
    }

    @Test
    void rejectsUnknownOrDuplicatePropertiesAndIllFormedUtf8() {
        assertThrows(ReadingDraftInput.Invalid.class, () -> ReadingDraftInput.passageCreate(
            bytes("{\"slug\":\"first\",\"slug\":\"last\",\"title\":\"Reading\"}")));
        assertThrows(ReadingDraftInput.Invalid.class, () -> ReadingDraftInput.passageCreate(
            bytes("{\"slug\":\"test\",\"title\":\"Reading\",\"revision\":1}")));
        assertThrows(ReadingDraftInput.Invalid.class, () -> ReadingDraftInput.passageCreate(
            new byte[]{(byte) 0xc3, (byte) 0x28}));
    }

    @Test
    void requiresExplicitUnassignmentAndPositiveIntegralPosition() {
        assertNull(ReadingDraftInput.placement(bytes("{\"lessonId\":null}")).lessonId());
        assertThrows(ReadingDraftInput.Invalid.class, () -> ReadingDraftInput.placement(bytes("{}")));
        assertThrows(ReadingDraftInput.Invalid.class,
            () -> ReadingDraftInput.placement(bytes("{\"lessonId\":null,\"position\":2}")));
        assertThrows(ReadingDraftInput.Invalid.class,
            () -> ReadingDraftInput.placement(bytes("{\"lessonId\":\"" + KEPT + "\",\"position\":2.0}")));
    }

    @Test
    void acceptsStrongRevisionAndOnlyPlacementAcceptsZero() {
        var request = new MockHttpServletRequest();
        assertEquals("PRECONDITION_REQUIRED", assertThrows(ReadingDraftInput.Invalid.class,
            () -> ReadingStaffApi.revision(request, true)).code());
        request.addHeader("If-Match", "\"0\"");
        assertEquals(0, ReadingStaffApi.revision(request, true));
        assertThrows(ReadingDraftInput.Invalid.class, () -> ReadingStaffApi.revision(request, false));
        var valid = new MockHttpServletRequest();
        valid.addHeader("If-Match", "\"17\"");
        assertEquals(17, ReadingStaffApi.revision(valid, false));
        valid.addHeader("If-Match", "\"18\"");
        assertThrows(ReadingDraftInput.Invalid.class, () -> ReadingStaffApi.revision(valid, false));
    }

    @Test
    void previewOnlySerializesSafeFieldsAndHasExplicitDraftMarker() throws Exception {
        UUID root = UUID.randomUUID();
        UUID edition = UUID.randomUUID();
        var actor = new StaffAuthentication.Actor(UUID.randomUUID(), 12);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("select reading_staff.get_preview(?,?,?,?)::text", String.class,
            actor.id(), actor.securityRevision(), root, edition)).thenReturn("""
            {"revision":4,"preview":{"draft":true,"title":"Title","summary":"Summary","visibility":"PRIVATE",
             "stableId":"%1$s","instructions":"private",
             "paragraphs":[{"paragraphId":"%2$s","body":"Body","heading":"Heading",
               "translations":[{"language":"vi","text":"Nội dung"}]}]}}
            """.formatted(root, KEPT));
        var projection = ReadingRoutines.preview(jdbc, actor, root, edition);
        var mapper = JsonMapper.builder().build();
        var result = mapper.readTree(mapper.writeValueAsBytes(projection.preview()));
        assertEquals(4, projection.revision());
        assertTrue(result.path("draft").asBoolean());
        assertFalse(result.has("stableId"));
        assertFalse(result.has("instructions"));
        assertFalse(result.has("visibility"));
        assertFalse(result.path("paragraphs").get(0).has("paragraphId"));
        assertEquals("Body", result.path("paragraphs").get(0).path("body").asText());
    }

    private static String paragraphError(String paragraphs) {
        return assertThrows(ReadingDraftInput.Invalid.class, () -> ReadingDraftInput.passage(bytes(
            "{\"title\":\"Title\",\"visibility\":\"PRIVATE\",\"paragraphs\":[" + paragraphs + "]}"))).code();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
