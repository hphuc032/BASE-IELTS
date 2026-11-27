package com.baseielts.platform.content.reading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.baseielts.platform.content.reading.ReadingDraftEdit.*;
import static com.baseielts.platform.content.reading.ReadingDraftInput.*;

// Domain state for a fenced authoring transaction. Expose preview(), never the private graph, to a safe reader.
record ReadingExercise(UUID id, String slug, UUID passageId, UUID authorId, UUID currentVersionId, Instant archivedAt) {
    record Version(UUID exerciseId, UUID passageId, ReadingEdition edition, UUID passageVersionId, String title,
                   String summary, Visibility visibility, Modes modes, List<QuestionGroup> groups) {
        Version {
            modes = new Modes(List.copyOf(modes.availableModes()), modes.examDurationSeconds());
            groups = List.copyOf(groups);
            for (QuestionGroup group : groups) {
                for (StoredQuestion question : group.questions()) {
                    if (question.evidence().stream().anyMatch(ref -> !edition.id().equals(ref.exerciseVersionId())
                            || !question.id().equals(ref.questionId()) || !passageVersionId.equals(ref.passageVersionId()))) {
                        throw new IllegalArgumentException("Evidence does not belong to the stored edition and question");
                    }
                }
            }
        }

        ExercisePreview preview() {
            return new ExercisePreview(title, summary, modes.availableModes(), modes.examDurationSeconds(),
                    groups.stream().map(group -> new GroupPreview(group.instructions(), group.questions().stream()
                            .map(question -> new QuestionPreview(question.type(), question.prompt(),
                                    question.instructions(), question.options().stream().map(StoredOption::option).toList(),
                                    question.maxWords())).toList())).toList());
        }
    }

    record QuestionGroup(UUID id, int position, String instructions, List<StoredQuestion> questions) {
        QuestionGroup {
            questions = List.copyOf(questions);
        }
    }

    record StoredQuestion(UUID id, int position, Type type, String prompt, String instructions, BigDecimal points,
                          Integer maxWords, List<StoredOption> options, PrivateMarking marking, String explanation,
                          List<EvidenceReference> evidence) {
        StoredQuestion {
            options = List.copyOf(options);
            evidence = List.copyOf(evidence);
        }
    }

    record StoredOption(UUID id, int position, Option option) {}
    record EvidenceReference(UUID exerciseVersionId, UUID questionId, UUID passageVersionId, UUID paragraphId,
                             String locator) {}
    sealed interface PrivateMarking permits CorrectOption, EnumAnswer, TextVariants {}
    record CorrectOption(UUID optionId) implements PrivateMarking {}
    record EnumAnswer(Classification value) implements PrivateMarking {}
    record TextVariants(TextAnswers value) implements PrivateMarking {
        TextVariants {
            value = new TextAnswers(List.copyOf(value.acceptedAnswers()), value.caseSensitive());
        }
    }

    record Creation(ReadingExercise identity, Version version) {}

    static Creation create(ExerciseCreate input, UUID actorId, PinnedEdition pin, Supplier<UUID> ids) {
        checkPin(input.passageId(), input.passageVersionId(), pin);
        var identity = new ReadingExercise(ids.get(), input.slug(), input.passageId(), actorId, null, null);
        var draft = new Version(identity.id(), input.passageId(), ReadingEdition.initial(ids.get()),
                input.passageVersionId(), input.title(), null, Visibility.PRIVATE, input.modes(), List.of());
        return new Creation(identity, draft);
    }

    Version edit(UUID requestedId, Long expectedRevision, Version current, PinnedEdition pin, Exercise input,
                 Supplier<UUID> ids) {
        requirePassage(current);
        ExerciseSnapshot snapshot = new ExerciseSnapshot(current.exerciseId(), current.edition().id(),
                current.passageId(), current.edition().revision(), current.edition().draft());
        exercise(id, requestedId, expectedRevision, snapshot, pin, input);
        return materialize(current.edition().revised(), input, freshGraphIds(current, ids));
    }

    Version cloneDraft(UUID requestedBaseId, Version base, PinnedEdition pin, boolean hasOpenDraft,
                       int highestVersionNumber, Supplier<UUID> ids) {
        requirePassage(base);
        ReadingEdition.checkSuccessor(id, requestedBaseId, base.exerciseId(), base.edition(), archivedAt != null,
                hasOpenDraft, highestVersionNumber);
        Exercise input = copyInput(base);
        // The source is immutable. Revalidate the exact stored pin and references before allocating a successor.
        checkExerciseContent(passageId, pin, input);
        ReadingEdition edition = base.edition().successor(ids.get(), highestVersionNumber);
        return materialize(edition, input, freshGraphIds(base, ids));
    }

    private void requirePassage(Version version) {
        if (!passageId.equals(version.passageId())) {
            throw new Conflict(404, "NOT_FOUND");
        }
    }

    private Version materialize(ReadingEdition edition, Exercise input, Supplier<UUID> ids) {
        List<QuestionGroup> groups = new ArrayList<>();
        for (Group group : input.groups()) {
            UUID groupId = ids.get();
            List<StoredQuestion> questions = new ArrayList<>();
            for (Question question : group.questions()) {
                UUID questionId = ids.get();
                List<StoredOption> options = new ArrayList<>();
                for (Option option : question.options()) {
                    options.add(new StoredOption(ids.get(), options.size() + 1, option));
                }
                List<EvidenceReference> evidence = question.evidence().stream().map(ref -> new EvidenceReference(
                        edition.id(), questionId, input.passageVersionId(), ref.paragraphId(), ref.locator())).toList();
                questions.add(new StoredQuestion(questionId, questions.size() + 1, question.type(), question.prompt(),
                        question.instructions(), question.points(), question.maxWords(), options,
                        resolveMarking(question.marking(), options), question.explanation(), evidence));
            }
            groups.add(new QuestionGroup(groupId, groups.size() + 1, group.instructions(), questions));
        }
        return new Version(id, passageId, edition, input.passageVersionId(), input.title(), input.summary(),
                input.visibility(), input.modes(), groups);
    }

    private static PrivateMarking resolveMarking(Marking marking, List<StoredOption> options) {
        return switch (marking) {
            case null -> null;
            case Choice choice -> new CorrectOption(options.stream()
                    .filter(option -> option.option().key().equals(choice.correctOptionKey()))
                    .findFirst().orElseThrow().id());
            case Classification value -> new EnumAnswer(value);
            case TextAnswers value -> new TextVariants(value);
        };
    }

    private static Exercise copyInput(Version source) {
        return new Exercise(source.title(), source.summary(), source.visibility(), source.passageVersionId(), source.modes(),
                source.groups().stream().map(group -> new Group(group.instructions(),
                        group.questions().stream().map(ReadingExercise::copyQuestion).toList())).toList());
    }

    static Question copyQuestion(StoredQuestion source) {
        Marking marking = switch (source.marking()) {
            case null -> null;
            case CorrectOption choice -> new Choice(source.options().stream()
                    .filter(option -> option.id().equals(choice.optionId())).findFirst().orElseThrow().option().key());
            case EnumAnswer answer -> answer.value();
            case TextVariants variants -> variants.value();
        };
        return new Question(source.type(), source.prompt(), source.instructions(), source.points(),
                source.options().stream().map(StoredOption::option).toList(), source.maxWords(), marking,
                source.explanation(), source.evidence().stream().map(ref -> new Evidence(ref.paragraphId(), ref.locator())).toList());
    }

    private static Supplier<UUID> freshGraphIds(Version previous, Supplier<UUID> source) {
        Set<UUID> used = new HashSet<>();
        for (QuestionGroup group : previous.groups()) {
            used.add(group.id());
            for (StoredQuestion question : group.questions()) {
                used.add(question.id());
                question.options().forEach(option -> used.add(option.id()));
            }
        }
        return () -> {
            UUID id = source.get();
            if (id == null || !used.add(id)) {
                throw new IllegalStateException("ID generator repeated an Exercise graph identity");
            }
            return id;
        };
    }
}
