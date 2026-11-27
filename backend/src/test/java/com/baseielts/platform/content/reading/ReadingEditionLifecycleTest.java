package com.baseielts.platform.content.reading;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.baseielts.platform.content.reading.ReadingDraftEdit.*;
import static com.baseielts.platform.content.reading.ReadingDraftInput.*;
import static org.assertj.core.api.Assertions.*;

class ReadingEditionLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-11-27T00:00:00Z");
    private final UUID actor = UUID.randomUUID();

    @Test
    void createsStablePassageAndItsInitialPrivateDraft() {
        var passage = newPassage();

        assertThat(passage.identity().slug()).isEqualTo("river-delta");
        assertThat(passage.identity().authorId()).isEqualTo(actor);
        assertThat(passage.identity().createdAt()).isEqualTo(NOW);
        assertThat(passage.identity().currentVersionId()).isNull();
        assertThat(passage.version().passageId()).isEqualTo(passage.identity().id());
        assertThat(passage.version().edition().versionNumber()).isEqualTo(1);
        assertThat(passage.version().edition().revision()).isEqualTo(1);
        assertThat(passage.version().edition().baseVersionId()).isNull();
        assertThat(passage.version().edition().draft()).isTrue();
        assertThat(passage.version().content().visibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void retainsParagraphIdsAcrossEditsAndAdvancesOnlyTheReturnedRevision() {
        var passage = newPassage();
        var content = addParagraphs(passage);
        UUID first = content.content().paragraphs().get(0).paragraphId();
        UUID second = content.content().paragraphs().get(1).paragraphId();
        var reorder = passage(bytes("""
                {"title":"Delta","visibility":"PRIVATE","paragraphs":[
                  {"paragraphId":"%s","content":"Updated second"},
                  {"paragraphId":"%s","content":"First"}]}
                """.formatted(second, first)));

        var changed = passage.identity().edit(content.edition().id(), 2L, content, reorder, Set.of(second),
                () -> fail("A reorder must not allocate paragraph IDs"));

        assertThat(changed.version().edition().revision()).isEqualTo(3);
        assertThat(changed.version().edition().id()).isEqualTo(content.edition().id());
        assertThat(changed.paragraphChanges().paragraphs()).extracting(PositionedParagraph::position).containsExactly(1, 2);
        assertThat(changed.version().content().paragraphs()).extracting(Paragraph::paragraphId).containsExactly(second, first);
        assertThat(changed.version().content().paragraphs().getFirst().content()).isEqualTo("Updated second");
        assertThat(content.content().paragraphs()).extracting(Paragraph::paragraphId).containsExactly(first, second);
        assertThat(content.edition().revision()).isEqualTo(2);

        assertConflict(412, "REVISION_MISMATCH", () -> passage.identity().edit(content.edition().id(), 2L,
                changed.version(), reorder, Set.of(), UUID::randomUUID));
    }

    @Test
    void referencedParagraphDeletionFailsBeforeGeneratingAnyIds() {
        var passage = newPassage();
        var content = addParagraphs(passage);
        UUID first = content.content().paragraphs().getFirst().paragraphId();
        var removal = passage(bytes("""
                {"title":"Delta","visibility":"PRIVATE","paragraphs":[{"content":"New"}]}
                """));

        assertConflict(409, "PARAGRAPH_REFERENCED", () -> passage.identity().edit(content.edition().id(), 2L,
                content, removal, Set.of(first), () -> fail("Rejected edit allocated a paragraph ID")));
        assertThat(content.paragraphIds()).contains(first).hasSize(2);
        assertThat(content.edition().revision()).isEqualTo(2);
    }

    @Test
    void successorClonesOnlyAnOwnedFrozenEditionAndAssignsNewParagraphIds() {
        var passage = newPassage();
        var content = addParagraphs(passage);
        var base = new ReadingPassage.Version(content.passageId(), frozen(content.edition()), content.content());

        var successor = passage.identity().cloneDraft(base.edition().id(), base, false, 3, UUID::randomUUID);
        assertThat(successor.edition().versionNumber()).isEqualTo(4);
        assertThat(successor.edition().revision()).isEqualTo(1);
        assertThat(successor.edition().baseVersionId()).isEqualTo(base.edition().id());
        assertThat(successor.edition().draft()).isTrue();
        assertThat(successor.paragraphIds()).doesNotContainAnyElementsOf(base.paragraphIds());
        assertThat(successor.content().paragraphs()).extracting(Paragraph::content).containsExactly("First", "Second");
        assertThat(base.edition().draft()).isFalse();
        assertThat(passage.identity().currentVersionId()).isNull();

        assertConflict(409, "DRAFT_EXISTS", () -> passage.identity().cloneDraft(base.edition().id(), base, true, 3,
                () -> fail("Duplicate draft allocated an ID")));
        assertConflict(409, "INVALID_STATE", () -> passage.identity().cloneDraft(content.edition().id(), content, false, 3,
                () -> fail("A draft may not be cloned")));
        assertConflict(404, "NOT_FOUND", () -> newPassage().identity().cloneDraft(base.edition().id(), base, false, 3,
                () -> fail("Foreign base allocated an ID")));
        assertConflict(404, "NOT_FOUND", () -> passage.identity().cloneDraft(UUID.randomUUID(), base, false, 3,
                () -> fail("Mismatched base allocated an ID")));
        ReadingPassage archived = new ReadingPassage(passage.identity().id(), "river-delta", actor, null, NOW, NOW);
        assertConflict(409, "INVALID_STATE", () -> archived.cloneDraft(base.edition().id(), base, false, 3,
                () -> fail("Archived Passage allocated a draft ID")));
    }

    @Test
    void recordsExactPinAndResolvesPrivateMarkingWithoutProjectingIt() throws Exception {
        var pin = addParagraphs(newPassage()).pin();
        var exercise = newExercise(pin);
        var revised = saveQuestions(exercise, pin);
        var group = revised.groups().getFirst();
        var question = group.questions().getFirst();

        assertThat(exercise.identity().authorId()).isEqualTo(actor);
        assertThat(exercise.identity().passageId()).isEqualTo(pin.passageId());
        assertThat(exercise.identity().currentVersionId()).isNull();
        assertThat(exercise.version().edition().revision()).isEqualTo(1);
        assertThat(exercise.version().groups()).isEmpty();
        assertThat(revised.edition().revision()).isEqualTo(2);
        assertThat(revised.passageVersionId()).isEqualTo(pin.editionId());
        assertThat(revised.modes().availableModes()).containsExactly(Mode.LEARNING, Mode.EXAM);
        assertThat(revised.modes().examDurationSeconds()).isEqualTo(86400);
        assertThat(group.position()).isEqualTo(1);
        assertThat(question.position()).isEqualTo(1);
        assertThat(question.options()).extracting(ReadingExercise.StoredOption::position).containsExactly(1, 2);
        assertThat(question.marking()).isEqualTo(new ReadingExercise.CorrectOption(question.options().get(1).id()));
        assertThat(question.explanation()).isEqualTo("PRIVATE EXPLANATION");
        assertThat(question.evidence()).singleElement().satisfies(ref -> {
            assertThat(ref.exerciseVersionId()).isEqualTo(revised.edition().id());
            assertThat(ref.passageVersionId()).isEqualTo(pin.editionId());
            assertThat(ref.questionId()).isEqualTo(question.id());
            assertThat(ref.paragraphId()).isIn(pin.paragraphIds());
        });
        assertThat(new ObjectMapper().writeValueAsString(revised.preview()))
                .contains("question text", "Available option")
                .doesNotContain("marking", "evidence", "explanation", "PRIVATE EXPLANATION", "PRIVATE LOCATOR", "points");
    }

    @Test
    void rejectsCrossEditionEvidenceAndAcceptsOnlyAnExplicitReplacementGraphForRepin() {
        var passage = newPassage();
        var source = addParagraphs(passage);
        var original = source.pin();
        var exercise = newExercise(original);
        var before = saveQuestions(exercise, original);
        var frozen = new ReadingPassage.Version(source.passageId(), frozen(source.edition()), source.content());
        var target = passage.identity().cloneDraft(frozen.edition().id(), frozen, false, 1, UUID::randomUUID).pin();
        UUID previousParagraph = original.paragraphIds().iterator().next();
        UUID targetParagraph = target.paragraphIds().iterator().next();

        assertConflict(400, "INVALID_EVIDENCE", () -> exercise.identity().edit(before.edition().id(), 2L, before, target,
                questions(target.editionId(), previousParagraph), () -> fail("Invalid graph allocated an ID")));
        assertThat(before.passageVersionId()).isEqualTo(original.editionId());
        assertThat(before.edition().revision()).isEqualTo(2);

        var repinned = exercise.identity().edit(before.edition().id(), 2L, before, target,
                questions(target.editionId(), targetParagraph), UUID::randomUUID);
        assertThat(repinned.passageVersionId()).isEqualTo(target.editionId());
        assertThat(rePinnedParagraph(repinned)).isEqualTo(targetParagraph);
    }

    @Test
    void exerciseSuccessorRemapsPrivateOptionAndQuestionIdsWithoutRepinning() {
        var pin = addParagraphs(newPassage()).pin();
        var exercise = newExercise(pin);
        var saved = saveQuestions(exercise, pin);
        var base = new ReadingExercise.Version(saved.exerciseId(), saved.passageId(), frozen(saved.edition()),
                saved.passageVersionId(), saved.title(), saved.summary(), saved.visibility(), saved.modes(), saved.groups());
        var baseQuestion = base.groups().getFirst().questions().getFirst();

        var cloned = exercise.identity().cloneDraft(base.edition().id(), base, pin, false, 1, UUID::randomUUID);
        var clonedQuestion = cloned.groups().getFirst().questions().getFirst();

        assertThat(cloned.edition().baseVersionId()).isEqualTo(base.edition().id());
        assertThat(cloned.edition().versionNumber()).isEqualTo(2);
        assertThat(cloned.edition().revision()).isEqualTo(1);
        assertThat(cloned.passageVersionId()).isEqualTo(pin.editionId());
        assertThat(clonedQuestion.id()).isNotEqualTo(baseQuestion.id());
        assertThat(clonedQuestion.marking()).isNotEqualTo(baseQuestion.marking());
        assertThat(clonedQuestion.marking()).isEqualTo(new ReadingExercise.CorrectOption(clonedQuestion.options().get(1).id()));
        assertThat(clonedQuestion.evidence().getFirst().questionId()).isEqualTo(clonedQuestion.id());
        assertThat(clonedQuestion.evidence().getFirst().exerciseVersionId()).isEqualTo(cloned.edition().id());
        assertThat(clonedQuestion.evidence().getFirst().paragraphId()).isEqualTo(baseQuestion.evidence().getFirst().paragraphId());
    }

    private ReadingPassage.Creation newPassage() {
        return ReadingPassage.create(passageCreate(bytes("""
                {"slug":"river-delta","title":"Delta"}
                """)), actor, NOW, UUID::randomUUID);
    }

    private static ReadingPassage.Version addParagraphs(ReadingPassage.Creation passage) {
        return passage.identity().edit(passage.version().edition().id(), 1L, passage.version(), passage(bytes("""
                {"title":"Delta","visibility":"PRIVATE","paragraphs":[{"content":"First"},{"content":"Second"}]}
                """)), Set.of(), UUID::randomUUID).version();
    }

    private ReadingExercise.Creation newExercise(PinnedEdition pin) {
        return ReadingExercise.create(exerciseCreate(bytes("""
                {"slug":"delta-exercise","title":"Delta","passageId":"%s","passageVersionId":"%s",
                 "availableModes":["LEARNING"]}
                """.formatted(pin.passageId(), pin.editionId()))), actor, pin, UUID::randomUUID);
    }

    private static ReadingExercise.Version saveQuestions(ReadingExercise.Creation exercise, PinnedEdition pin) {
        return exercise.identity().edit(exercise.version().edition().id(), 1L, exercise.version(), pin,
                questions(pin.editionId(), pin.paragraphIds().iterator().next()), UUID::randomUUID);
    }

    private static Exercise questions(UUID edition, UUID paragraph) {
        return exercise(bytes("""
                {"title":"Delta","visibility":"PRIVATE","passageVersionId":"%s",
                  "availableModes":["EXAM","LEARNING"],"examDurationSeconds":86400,
                  "groups":[{"questions":[{"type":"MULTIPLE_CHOICE_SINGLE","prompt":"question text","points":1,
                    "options":[{"key":"a","content":"Available option"},{"key":"b","content":"Another option"}],
                    "marking":{"correctOptionKey":"b"},"explanation":"PRIVATE EXPLANATION",
                    "evidence":[{"paragraphId":"%s","locator":"PRIVATE LOCATOR"}]}]}]}
                """.formatted(edition, paragraph)));
    }

    private static UUID rePinnedParagraph(ReadingExercise.Version version) {
        return version.groups().getFirst().questions().getFirst().evidence().getFirst().paragraphId();
    }

    private static ReadingEdition frozen(ReadingEdition draft) {
        return new ReadingEdition(draft.id(), draft.versionNumber(), draft.revision(), draft.baseVersionId(), NOW);
    }

    private static void assertConflict(int status, String code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(Conflict.class, error -> {
            assertThat(error.status()).isEqualTo(status);
            assertThat(error.code()).isEqualTo(code);
        });
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
