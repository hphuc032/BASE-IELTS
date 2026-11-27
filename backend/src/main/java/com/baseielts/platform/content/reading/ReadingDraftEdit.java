package com.baseielts.platform.content.reading;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.baseielts.platform.content.reading.ReadingDraftInput.*;

final class ReadingDraftEdit {
    private ReadingDraftEdit() {}

    // The adapter obtains this snapshot while holding the account fence and stable Passage lock.
    record PassageSnapshot(UUID passageId, UUID editionId, long revision, boolean draft,
                           Set<UUID> paragraphIds, Set<UUID> referencedParagraphIds) {}

    // The Exercise adapter acquires the Passage root lock before the Exercise root lock.
    record ExerciseSnapshot(UUID exerciseId, UUID editionId, UUID passageId, long revision, boolean draft) {}

    record PinnedEdition(UUID passageId, UUID editionId, Set<UUID> paragraphIds) {}

    record PositionedParagraph(UUID paragraphId, int position, String heading, String content,
                               List<Translation> translations) {}
    record PassageChanges(List<PositionedParagraph> paragraphs, Set<UUID> removedParagraphIds) {}

    record PassagePreview(String title, String summary, String instructions, List<ParagraphPreview> paragraphs) {}
    record ParagraphPreview(String heading, String content, List<Translation> translations) {}
    record ExercisePreview(String title, String summary, List<Mode> availableModes, Integer examDurationSeconds,
                           List<GroupPreview> groups) {}
    record GroupPreview(String instructions, List<QuestionPreview> questions) {}
    record QuestionPreview(Type type, String prompt, String instructions, List<Option> options, Integer maxWords) {}

    static final class Conflict extends IllegalArgumentException {
        private final int status;
        private final String code;

        Conflict(int status, String code) {
            super(code);
            this.status = status;
            this.code = code;
        }

        int status() { return status; }
        String code() { return code; }
    }

    static PassageChanges passage(UUID passageId, UUID editionId, Long expectedRevision, PassageSnapshot current,
                                  Passage input, Supplier<UUID> newId) {
        verify(passageId, editionId, expectedRevision, current.passageId(), current.editionId(), current.revision(),
                current.draft());
        Set<UUID> retained = new HashSet<>();
        for (Paragraph paragraph : input.paragraphs()) {
            UUID id = paragraph.paragraphId();
            if (id == null) {
                continue;
            }
            if (!current.paragraphIds().contains(id)) {
                throw new Conflict(404, "NOT_FOUND");
            }
            if (!retained.add(id)) {
                throw new Conflict(400, "INVALID_PARAGRAPH_ID");
            }
        }
        Set<UUID> removed = new HashSet<>(current.paragraphIds());
        removed.removeAll(retained);
        if (removed.stream().anyMatch(current.referencedParagraphIds()::contains)) {
            throw new Conflict(409, "PARAGRAPH_REFERENCED");
        }

        List<PositionedParagraph> positioned = new ArrayList<>();
        for (Paragraph paragraph : input.paragraphs()) {
            UUID id = paragraph.paragraphId() == null ? newId.get() : paragraph.paragraphId();
            positioned.add(new PositionedParagraph(id, positioned.size() + 1, paragraph.heading(), paragraph.content(),
                    paragraph.translations()));
        }
        return new PassageChanges(List.copyOf(positioned), Set.copyOf(removed));
    }

    static void exercise(UUID exerciseId, UUID editionId, Long expectedRevision, ExerciseSnapshot current,
                         PinnedEdition pin, Exercise input) {
        verify(exerciseId, editionId, expectedRevision, current.exerciseId(), current.editionId(), current.revision(),
                current.draft());
        checkExerciseContent(current.passageId(), pin, input);
    }

    static void checkExerciseContent(UUID passageId, PinnedEdition pin, Exercise input) {
        checkPin(passageId, input.passageVersionId(), pin);
        boolean invalidEvidence = input.groups().stream().flatMap(group -> group.questions().stream())
                .anyMatch(question -> new HashSet<>(question.evidence()).size() != question.evidence().size()
                        || question.evidence().stream().anyMatch(ref -> !pin.paragraphIds().contains(ref.paragraphId())));
        if (invalidEvidence) {
            throw new Conflict(400, "INVALID_EVIDENCE");
        }
    }

    static void checkPin(UUID passageId, UUID editionId, PinnedEdition pin) {
        if (pin == null || !passageId.equals(pin.passageId()) || !editionId.equals(pin.editionId())) {
            throw new Conflict(404, "NOT_FOUND");
        }
    }

    static PassagePreview preview(Passage passage) {
        return new PassagePreview(passage.title(), passage.summary(), passage.instructions(),
                passage.paragraphs().stream()
                        .map(p -> new ParagraphPreview(p.heading(), p.content(), p.translations())).toList());
    }

    static ExercisePreview preview(Exercise exercise) {
        List<GroupPreview> groups = exercise.groups().stream().map(group -> new GroupPreview(group.instructions(),
                group.questions().stream().map(question -> new QuestionPreview(question.type(), question.prompt(),
                        question.instructions(), question.options(), question.maxWords())).toList())).toList();
        return new ExercisePreview(exercise.title(), exercise.summary(), exercise.modes().availableModes(),
                exercise.modes().examDurationSeconds(), groups);
    }

    private static void verify(UUID rootId, UUID editionId, Long expectedRevision, UUID actualRoot, UUID actualEdition,
                               long actualRevision, boolean draft) {
        if (!rootId.equals(actualRoot) || !editionId.equals(actualEdition)) {
            throw new Conflict(404, "NOT_FOUND");
        }
        if (!draft) {
            throw new Conflict(409, "INVALID_STATE");
        }
        if (expectedRevision == null) {
            throw new Conflict(428, "PRECONDITION_REQUIRED");
        }
        if (expectedRevision != actualRevision) {
            throw new Conflict(412, "REVISION_MISMATCH");
        }
    }
}
