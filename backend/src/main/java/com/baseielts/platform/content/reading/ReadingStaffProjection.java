package com.baseielts.platform.content.reading;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.baseielts.platform.content.reading.ReadingDraftInput.*;

/** Materialize inside the already authorized staff transaction. Never use for a preview or anonymous route. */
final class ReadingStaffProjection {
    private ReadingStaffProjection() {}

    enum State { DRAFT, FROZEN }

    record PassageAdmin(UUID stableId, UUID editionId, int versionNumber, long revision, State state, String slug,
                        UUID currentVersionId, String title, String summary, String instructions, UUID topicId,
                        Visibility visibility, List<ParagraphAdmin> paragraphs) {}

    record ParagraphAdmin(UUID paragraphId, String content, int position, String heading, List<Translation> translations) {}

    record ExerciseAdmin(UUID stableId, UUID editionId, int versionNumber, long revision, State state, String slug,
                         UUID currentVersionId, UUID passageId, UUID passageVersionId, String title, String summary,
                         Visibility visibility, List<Mode> availableModes, Integer examDurationSeconds,
                         List<GroupAdmin> groups) {}

    record GroupAdmin(int position, String instructions, List<QuestionAdmin> questions) {}

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record QuestionAdmin(int position, Type type, String prompt, String instructions, BigDecimal points,
                         List<OptionAdmin> options, Integer maxWords, Marking marking, String explanation,
                         List<Evidence> evidence) {}

    record OptionAdmin(int position, String key, String content) {}

    static PassageAdmin passage(ReadingPassage root, UUID requestedEdition, ReadingPassage.Version version) {
        if (!root.id().equals(version.passageId()) || !requestedEdition.equals(version.edition().id())) {
            throw concealed();
        }
        var content = version.content();
        List<ParagraphAdmin> paragraphs = new ArrayList<>(content.paragraphs().size());
        for (var paragraph : content.paragraphs()) {
            paragraphs.add(new ParagraphAdmin(paragraph.paragraphId(), paragraph.content(), paragraphs.size() + 1,
                    paragraph.heading(), List.copyOf(paragraph.translations())));
        }
        var edition = version.edition();
        return new PassageAdmin(root.id(), edition.id(), edition.versionNumber(), edition.revision(), state(edition),
                root.slug(), root.currentVersionId(), content.title(), content.summary(), content.instructions(),
                content.topicId(), content.visibility(), List.copyOf(paragraphs));
    }

    static ExerciseAdmin exercise(ReadingExercise root, UUID requestedEdition, ReadingExercise.Version version) {
        if (!root.id().equals(version.exerciseId()) || !root.passageId().equals(version.passageId())
                || !requestedEdition.equals(version.edition().id())) {
            throw concealed();
        }
        var edition = version.edition();
        return new ExerciseAdmin(root.id(), edition.id(), edition.versionNumber(), edition.revision(), state(edition),
                root.slug(), root.currentVersionId(), root.passageId(), version.passageVersionId(), version.title(),
                version.summary(), version.visibility(), version.modes().availableModes(),
                version.modes().examDurationSeconds(), version.groups().stream().map(group ->
                    new GroupAdmin(group.position(), group.instructions(), group.questions().stream()
                            .map(ReadingStaffProjection::question).toList())).toList());
    }

    private static QuestionAdmin question(ReadingExercise.StoredQuestion stored) {
        var question = ReadingExercise.copyQuestion(stored);
        var options = stored.options().stream()
                .map(option -> new OptionAdmin(option.position(), option.option().key(), option.option().content())).toList();
        return new QuestionAdmin(stored.position(), question.type(), question.prompt(), question.instructions(),
                question.points(), options, question.maxWords(), question.marking(), question.explanation(), question.evidence());
    }

    private static State state(ReadingEdition edition) {
        return edition.draft() ? State.DRAFT : State.FROZEN;
    }

    private static ReadingDraftEdit.Conflict concealed() {
        return new ReadingDraftEdit.Conflict(404, "NOT_FOUND");
    }
}
