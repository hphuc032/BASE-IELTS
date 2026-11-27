package com.baseielts.platform.content.reading;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.baseielts.platform.content.reading.ReadingDraftInput.*;
import static com.baseielts.platform.content.reading.ReadingDraftEdit.*;
import static org.assertj.core.api.Assertions.*;

class ReadingDraftContractTest {
    private final UUID passageId = UUID.randomUUID();
    private final UUID version = UUID.randomUUID();

    @Test
    void paragraphEditKeepsRetainedIdsAcrossReorderAndAllocatesOnlyForNewRows() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        PassageSnapshot snapshot = new PassageSnapshot(passageId, version, 3, true, Set.of(first, second), Set.of(first));
        Passage input = passage(bytes("""
                {"title":"Passage","visibility":"PRIVATE","paragraphs":[
                  {"paragraphId":"%s","content":"Second"},
                  {"paragraphId":"%s","content":"Updated","translations":[{"language":"vi-vn","text":"Dịch"}]},
                  {"content":"New"}]}
                """.formatted(second, first)));
        PassageChanges result = ReadingDraftEdit.passage(passageId, version, 3L, snapshot, input, () -> newId);
        assertThat(result.paragraphs()).extracting(PositionedParagraph::paragraphId).containsExactly(second, first, newId);
        assertThat(result.paragraphs()).extracting(PositionedParagraph::position).containsExactly(1, 2, 3);
        assertThat(result.paragraphs().get(1).translations()).containsExactly(new Translation("vi-VN", "Dịch"));
        assertThat(result.removedParagraphIds()).isEmpty();
    }

    @Test
    void revisionScopeAndReferenceFailuresNeverAllocateAnId() {
        UUID paragraph = UUID.randomUUID();
        PassageSnapshot snapshot = new PassageSnapshot(passageId, version, 3, true, Set.of(paragraph), Set.of(paragraph));
        Passage replace = passage(bytes("""
                {"title":"Passage","visibility":"PRIVATE","paragraphs":[{"content":"New"}]}
                """));
        assertConflict(404, () -> ReadingDraftEdit.passage(passageId, UUID.randomUUID(), 3L, snapshot, replace,
                ReadingDraftContractTest::unexpectedId));
        assertConflict(428, () -> ReadingDraftEdit.passage(passageId, version, null, snapshot, replace,
                ReadingDraftContractTest::unexpectedId));
        assertConflict(412, () -> ReadingDraftEdit.passage(passageId, version, 2L, snapshot, replace,
                ReadingDraftContractTest::unexpectedId));
        assertConflict(409, () -> ReadingDraftEdit.passage(passageId, version, 3L, snapshot, replace,
                ReadingDraftContractTest::unexpectedId));
    }

    @Test
    void rejectsMalformedDuplicateAndForeignParagraphIdentifiers() {
        UUID paragraph = UUID.randomUUID();
        assertInput("INVALID_PARAGRAPH_ID", () -> passage(bytes("""
                {"title":"P","visibility":"PRIVATE","paragraphs":[{"paragraphId":null,"content":"x"}]}
                """)));
        assertInput("INVALID_PARAGRAPH_ID", () -> passage(bytes("""
                {"title":"P","visibility":"PRIVATE","paragraphs":[
                  {"paragraphId":"%s","content":"x"},{"paragraphId":"%s","content":"y"}]}
                """.formatted(paragraph, paragraph))));
        Passage foreign = passage(bytes("""
                {"title":"P","visibility":"PRIVATE","paragraphs":[{"paragraphId":"%s","content":"x"}]}
                """.formatted(paragraph)));
        PassageSnapshot empty = new PassageSnapshot(passageId, version, 1, true, Set.of(), Set.of());
        assertConflict(404, () -> ReadingDraftEdit.passage(passageId, version, 1L, empty, foreign,
                ReadingDraftContractTest::unexpectedId));
    }

    @Test
    void supportsFiveQuestionTypesAndPreviewOmitsPrivateFields() throws Exception {
        UUID paragraph = UUID.randomUUID();
        Exercise input = exerciseDraft("""
                {"type":"MULTIPLE_CHOICE_SINGLE","prompt":"Choose","points":1,
                 "options":[{"key":"a","content":"One"},{"key":"b","content":"Two"}],
                 "marking":{"correctOptionKey":"a"},"explanation":"private-sentinel",
                 "evidence":[{"paragraphId":"%s","locator":"private-locator"}]},
                {"type":"TRUE_FALSE_NOT_GIVEN","prompt":"Check","points":1,"marking":{"value":"TRUE"}},
                {"type":"YES_NO_NOT_GIVEN","prompt":"Check","points":1,"marking":{"value":"NO"}},
                {"type":"SHORT_ANSWER","prompt":"Name","points":0.25,"maxWords":2,
                 "marking":{"acceptedAnswers":["hidden-variant"],"caseSensitive":false}},
                {"type":"SENTENCE_COMPLETION","prompt":"The ___ rose.","points":2,"maxWords":1,
                 "marking":{"acceptedAnswers":["sun"],"caseSensitive":true}}
                """.formatted(paragraph), "\"EXAM\",\"LEARNING\"", ",\"examDurationSeconds\":86400");
        assertThat(input.groups().getFirst().questions()).extracting(Question::type).containsExactly(Type.values());
        assertThat(input.modes().availableModes()).containsExactly(Mode.LEARNING, Mode.EXAM);
        String preview = new ObjectMapper().writeValueAsString(ReadingDraftEdit.preview(input));
        assertThat(preview).contains("Choose").doesNotContain("marking", "correctOptionKey", "explanation", "evidence",
                "private-sentinel", "private-locator", "hidden-variant");
        UUID exerciseId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        ExerciseSnapshot snapshot = new ExerciseSnapshot(exerciseId, draftId, passageId, 1, true);
        ReadingDraftEdit.exercise(exerciseId, draftId, 1L, snapshot,
                new PinnedEdition(passageId, version, Set.of(paragraph)), input);
        assertConflict(400, () -> ReadingDraftEdit.exercise(exerciseId, draftId, 1L, snapshot,
                new PinnedEdition(passageId, version, Set.of(UUID.randomUUID())), input));
        assertConflict(404, () -> ReadingDraftEdit.exercise(exerciseId, draftId, 1L, snapshot,
                new PinnedEdition(passageId, UUID.randomUUID(), Set.of(paragraph)), input));
    }

    @Test
    void rejectsModeDurationAndQuestionShapeErrorsBeforeCoercion() {
        assertThat(exerciseDraft("", "\"EXAM\"", ",\"examDurationSeconds\":1").modes().examDurationSeconds()).isEqualTo(1);
        assertThat(exerciseDraft("", "\"LEARNING\"", "").modes().examDurationSeconds()).isNull();
        for (String invalid : List.of("", ",\"examDurationSeconds\":1.0", ",\"examDurationSeconds\":0",
                ",\"examDurationSeconds\":86401", ",\"examDurationSeconds\":\"60\"")) {
            assertInput("INVALID_EXAM_DURATION", () -> exerciseDraft("", "\"EXAM\"", invalid));
        }
        assertInput("INVALID_EXAM_DURATION", () -> exerciseDraft("", "\"LEARNING\"", ",\"examDurationSeconds\":1"));
        for (String invalid : List.of("", "\"EXAM\",\"EXAM\"", "null", "\"OTHER\"")) {
            assertInput("INVALID_AVAILABLE_MODES", () -> exerciseDraft("", invalid, ""));
        }
        assertInput("INVALID_INPUT", () -> exerciseDraft("""
                {"type":"TRUE_FALSE_NOT_GIVEN","prompt":"Check","points":1,"marking":{"value":"YES"}}
                """, "\"LEARNING\"", ""));
        assertInput("INVALID_INPUT", () -> exerciseDraft("""
                {"type":"SHORT_ANSWER","prompt":"Name","points":1,"maxWords":2,
                 "marking":{"caseSensitive":false,"acceptedAnswers":["a b","A  B"]}}
                """, "\"LEARNING\"", ""));
        assertInput("INVALID_INPUT", () -> exerciseDraft("""
                {"type":"SENTENCE_COMPLETION","prompt":"___ and ___","points":1,"maxWords":2}
                """, "\"LEARNING\"", ""));
    }

    @Test
    void rejectsUnrecognizedDuplicateAndOversizedInput() {
        assertInput("INVALID_INPUT", () -> passageCreate(bytes("""
                {"slug":"survey","title":"Original","title":"Overwritten"}
                """)));
        assertInput("INVALID_INPUT", () -> passageCreate(bytes("""
                {"slug":"survey","title":"Title","authorId":"caller-supplied"}
                """)));
        assertInput("INVALID_INPUT", () -> passageCreate("""
                {"slug":"survey","title":"Title"}
                """.getBytes(StandardCharsets.UTF_16LE)));
        assertInput("PAYLOAD_TOO_LARGE", () -> passageCreate(new byte[768 * 1024 + 1]));
        assertInput("INVALID_INPUT", () -> exerciseDraft("""
                {"type":"TRUE_FALSE_NOT_GIVEN","prompt":"Check","points":1.000}
                """, "\"LEARNING\"", ""));
        assertThat(passageCreate(bytes("""
                {"slug":" Survey-1 ","title":"Cafe\u0301"}
                """))).isEqualTo(new PassageCreate("survey-1", "Café"));
    }

    private Exercise exerciseDraft(String questions, String modes, String duration) {
        return exercise(bytes("""
                {"title":"Exercise","visibility":"PRIVATE","passageVersionId":"%s","availableModes":[%s]%s,
                 "groups":[{"questions":[%s]}]}
                """.formatted(version, modes, duration, questions)));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static UUID unexpectedId() {
        throw new AssertionError("Invalid edit allocated a paragraph ID");
    }

    private static void assertInput(String code, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(Invalid.class, error -> assertThat(error.code()).isEqualTo(code));
    }

    private static void assertConflict(int status, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(Conflict.class, error -> assertThat(error.status()).isEqualTo(status));
    }
}
