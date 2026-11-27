package com.baseielts.platform.content.reading;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.baseielts.platform.content.reading.ReadingDraftInput.*;
import static org.assertj.core.api.Assertions.*;

class ReadingStaffProjectionTest {
    private final UUID passage = UUID.randomUUID();
    private final UUID pin = UUID.randomUUID();
    private final UUID paragraph = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    @Test
    void passageAdminIncludesExactTopicAndOrderedTranslations() {
        var initial = ReadingPassage.create(new PassageCreate("the-bay", "The bay"), actor, Instant.EPOCH,
                UUID::randomUUID);
        UUID topic = UUID.randomUUID();
        var input = passage(bytes("""
                {"title":"The bay","topicId":"%s","visibility":"PRIVATE","paragraphs":[
                  {"content":"Two boats.","translations":[{"language":"VI-vn","text":"Hai chiếc thuyền."}]},
                  {"content":"Three boats."}]}
                """.formatted(topic)));
        var edit = initial.identity().edit(initial.version().edition().id(), 1L, initial.version(), input, Set.of(),
                UUID::randomUUID);
        var view = ReadingStaffProjection.passage(initial.identity(), edit.version().edition().id(), edit.version());
        assertThat(view.topicId()).isEqualTo(topic);
        assertThat(view.revision()).isEqualTo(2);
        assertThat(view.paragraphs()).extracting(ReadingStaffProjection.ParagraphAdmin::position).containsExactly(1, 2);
        assertThat(view.paragraphs().getFirst().translations()).containsExactly(new Translation("vi-VN", "Hai chiếc thuyền."));
        assertThat(view.paragraphs().getFirst().paragraphId()).isEqualTo(edit.version().content().paragraphs().getFirst().paragraphId());
        var foreignRoot = ReadingPassage.create(new PassageCreate("other", "Other"), actor, Instant.EPOCH, UUID::randomUUID);
        assertThatThrownBy(() -> ReadingStaffProjection.passage(foreignRoot.identity(), view.editionId(), edit.version()))
                .isInstanceOfSatisfying(ReadingDraftEdit.Conflict.class, error -> assertThat(error.status()).isEqualTo(404));
    }

    @Test
    void privateStaffGraphRoundTripsMarkingWhilePreviewOmitsItForAllQuestionTypes() throws Exception {
        var selector = new ReadingDraftEdit.PinnedEdition(passage, pin, Set.of(paragraph));
        var initial = ReadingExercise.create(new ExerciseCreate("bay-quiz", "Bay quiz", passage, pin,
                new Modes(List.of(Mode.LEARNING), null)), actor, selector, UUID::randomUUID);
        var input = parseExercise("""
                {"type":"MULTIPLE_CHOICE_SINGLE","prompt":"Choose","points":1,"options":[
                  {"key":"a","content":"One"},{"key":"b","content":"Two"}],
                  "marking":{"correctOptionKey":"b"},"explanation":"private-rationale",
                  "evidence":[{"paragraphId":"%s","locator":"private-locator"}]},
                {"type":"TRUE_FALSE_NOT_GIVEN","prompt":"Check","points":1,"marking":{"value":"FALSE"}},
                {"type":"YES_NO_NOT_GIVEN","prompt":"Check","points":1,"marking":{"value":"NO"}},
                {"type":"SHORT_ANSWER","prompt":"Name","points":1,"maxWords":2,
                  "marking":{"acceptedAnswers":["private-variant"],"caseSensitive":false}},
                {"type":"SENTENCE_COMPLETION","prompt":"The ___ rose.","points":1,"maxWords":1,
                  "marking":{"acceptedAnswers":["sun"],"caseSensitive":true}}
                """.formatted(paragraph));
        var saved = initial.identity().edit(initial.version().edition().id(), 1L, initial.version(), selector, input,
                UUID::randomUUID);
        var json = new ObjectMapper();
        var staff = json.valueToTree(ReadingStaffProjection.exercise(initial.identity(), saved.edition().id(), saved));
        assertThat(staff.path("state").asText()).isEqualTo("DRAFT");
        assertThat(staff.path("passageVersionId").asText()).isEqualTo(pin.toString());
        var questions = staff.at("/groups/0/questions");
        assertThat(questions.size()).isEqualTo(5);
        assertThat(questions.at("/0/marking/correctOptionKey").asText()).isEqualTo("b");
        assertThat(questions.at("/0/options/1/position").asInt()).isEqualTo(2);
        assertThat(questions.at("/0/explanation").asText()).isEqualTo("private-rationale");
        assertThat(questions.at("/1/marking/value").asText()).isEqualTo("FALSE");
        assertThat(questions.at("/2/marking/value").asText()).isEqualTo("NO");
        assertThat(questions.at("/3/marking/acceptedAnswers/0").asText()).isEqualTo("private-variant");
        assertThat(questions.at("/4/marking/caseSensitive").asBoolean()).isTrue();
        assertThat(json.writeValueAsString(saved.preview())).doesNotContain("marking", "explanation", "evidence",
                "private-rationale", "private-locator", "private-variant");
        assertThatThrownBy(() -> ReadingStaffProjection.exercise(initial.identity(), UUID.randomUUID(), saved))
                .isInstanceOfSatisfying(ReadingDraftEdit.Conflict.class, error -> assertThat(error.status()).isEqualTo(404));
    }

    @Test
    void rejectsDuplicateEvidenceAfterNormalizationAndAllowsDistinctLocators() {
        assertThatThrownBy(() -> parseExercise(questionWithEvidence("Section", " Section ")))
                .isInstanceOfSatisfying(Invalid.class, error -> assertThat(error.code()).isEqualTo("INVALID_EVIDENCE"));
        var valid = parseExercise(questionWithEvidence("Section", "Second sentence"));
        var question = valid.groups().getFirst().questions().getFirst();
        assertThat(question.evidence()).extracting(Evidence::locator).containsExactly("Section", "Second sentence");
        var unchecked = new Exercise(valid.title(), null, valid.visibility(), pin, valid.modes(),
                List.of(new Group(null, List.of(new Question(question.type(), question.prompt(), null, question.points(),
                        question.options(), question.maxWords(), null, null,
                        List.of(question.evidence().getFirst(), question.evidence().getFirst()))))));
        var exactPin = new ReadingDraftEdit.PinnedEdition(passage, pin, Set.of(paragraph));
        assertThatThrownBy(() -> ReadingDraftEdit.checkExerciseContent(passage, exactPin, unchecked))
                .isInstanceOfSatisfying(ReadingDraftEdit.Conflict.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_EVIDENCE"));
    }

    @Test
    void rejectsIncompatibleMarkingAndQuestionFieldsForEveryDiscriminator() {
        for (String question : List.of(
                """
                {"type":"MULTIPLE_CHOICE_SINGLE","prompt":"Choose","points":1,"options":[
                  {"key":"a","content":"One"},{"key":"b","content":"Two"}],"marking":{"correctOptionKey":"c"}}
                """,
                """
                {"type":"TRUE_FALSE_NOT_GIVEN","prompt":"Check","points":1,"marking":{"value":"YES"}}
                """,
                """
                {"type":"YES_NO_NOT_GIVEN","prompt":"Check","points":1,"options":[],"marking":{"value":"NO"}}
                """,
                """
                {"type":"SHORT_ANSWER","prompt":"Name","points":1,"maxWords":1,
                  "marking":{"acceptedAnswers":["two words"],"caseSensitive":false}}
                """,
                """
                {"type":"SENTENCE_COMPLETION","prompt":"There is no blank.","points":1,"maxWords":1}
                """)) {
            assertThatThrownBy(() -> parseExercise(question)).isInstanceOf(Invalid.class);
        }
    }

    private String questionWithEvidence(String first, String second) {
        return """
                {"type":"TRUE_FALSE_NOT_GIVEN","prompt":"Check","points":1,"evidence":[
                  {"paragraphId":"%s","locator":"%s"},{"paragraphId":"%s","locator":"%s"}]}
                """.formatted(paragraph, first, paragraph, second);
    }

    private Exercise parseExercise(String questions) {
        return exercise(bytes("""
                {"title":"The bay","visibility":"PRIVATE","passageVersionId":"%s","availableModes":["LEARNING"],
                 "groups":[{"questions":[%s]}]}
                """.formatted(pin, questions)));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
