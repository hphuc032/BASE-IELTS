package com.baseielts.platform.content.reading;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.baseielts.platform.content.reading.ReadingDraftEdit.*;
import static com.baseielts.platform.content.reading.ReadingDraftInput.*;

// Stable identity has no Lesson foreign key. Catalog owns the optional placement record.
record ReadingPassage(UUID id, String slug, UUID authorId, UUID currentVersionId, Instant archivedAt, Instant createdAt) {
    record Version(UUID passageId, ReadingEdition edition, Passage content) {
        Version {
            Set<UUID> ids = new HashSet<>();
            List<Paragraph> paragraphs = content.paragraphs().stream().map(paragraph -> {
                if (paragraph.paragraphId() == null || !ids.add(paragraph.paragraphId())) {
                    throw new IllegalArgumentException("Persisted paragraphs must have distinct identities");
                }
                return new Paragraph(paragraph.paragraphId(), paragraph.heading(), paragraph.content(),
                        List.copyOf(paragraph.translations()));
            }).toList();
            content = withParagraphs(content, paragraphs);
        }

        Set<UUID> paragraphIds() {
            Set<UUID> ids = new HashSet<>();
            for (Paragraph paragraph : content.paragraphs()) {
                ids.add(paragraph.paragraphId());
            }
            return Set.copyOf(ids);
        }

        PinnedEdition pin() {
            return new PinnedEdition(passageId, edition.id(), paragraphIds());
        }
    }

    record Creation(ReadingPassage identity, Version version) {}
    record Edit(Version version, PassageChanges paragraphChanges) {}

    static Creation create(PassageCreate input, UUID actorId, Instant now, Supplier<UUID> ids) {
        var identity = new ReadingPassage(ids.get(), input.slug(), actorId, null, null, now);
        var draft = new Version(identity.id(), ReadingEdition.initial(ids.get()),
                new Passage(input.title(), null, null, null, Visibility.PRIVATE, List.of()));
        return new Creation(identity, draft);
    }

    Version cloneDraft(UUID requestedBaseId, Version base, boolean hasOpenDraft, int highestVersionNumber,
                       Supplier<UUID> ids) {
        ReadingEdition.checkSuccessor(id, requestedBaseId, base.passageId(), base.edition(), archivedAt != null,
                hasOpenDraft, highestVersionNumber);
        ReadingEdition edition = base.edition().successor(ids.get(), highestVersionNumber);
        Set<UUID> used = new HashSet<>(base.paragraphIds());
        List<Paragraph> cloned = base.content().paragraphs().stream()
                .map(p -> new Paragraph(newParagraphId(used, ids), p.heading(), p.content(), p.translations()))
                .toList();
        return new Version(id, edition, withParagraphs(base.content(), cloned));
    }

    Edit edit(UUID requestedId, Long expectedRevision, Version current, Passage input, Set<UUID> referenced,
              Supplier<UUID> ids) {
        Set<UUID> existing = current.paragraphIds();
        Set<UUID> used = new HashSet<>(existing);
        PassageSnapshot snapshot = new PassageSnapshot(current.passageId(), current.edition().id(),
                current.edition().revision(), current.edition().draft(), existing, referenced);
        PassageChanges changes = passage(id, requestedId, expectedRevision, snapshot, input,
                () -> newParagraphId(used, ids));
        List<Paragraph> ordered = changes.paragraphs().stream()
                .map(p -> new Paragraph(p.paragraphId(), p.heading(), p.content(), p.translations()))
                .toList();
        return new Edit(new Version(id, current.edition().revised(), withParagraphs(input, ordered)), changes);
    }

    private static Passage withParagraphs(Passage source, List<Paragraph> paragraphs) {
        return new Passage(source.title(), source.summary(), source.instructions(), source.topicId(), source.visibility(),
                paragraphs);
    }

    private static UUID newParagraphId(Set<UUID> used, Supplier<UUID> ids) {
        UUID newId = ids.get();
        if (newId == null || !used.add(newId)) {
            throw new IllegalStateException("ID generator repeated a paragraph identity");
        }
        return newId;
    }
}
