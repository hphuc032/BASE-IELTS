package com.baseielts.platform.content.reading;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.baseielts.platform.content.reading.ReadingDraftEdit.Conflict;

// Lifecycle of an edition loaded from storage. Writers still need repository-level revision CAS.
record ReadingEdition(UUID id, int versionNumber, long revision, UUID baseVersionId, Instant frozenAt) {
    ReadingEdition {
        Objects.requireNonNull(id);
        if (versionNumber < 1 || revision < 1 || id.equals(baseVersionId)) {
            throw new IllegalArgumentException("Invalid edition metadata");
        }
    }

    static ReadingEdition initial(UUID id) {
        return new ReadingEdition(id, 1, 1, null, null);
    }

    boolean draft() {
        return frozenAt == null;
    }

    ReadingEdition revised() {
        if (!draft()) {
            throw new Conflict(409, "INVALID_STATE");
        }
        return new ReadingEdition(id, versionNumber, Math.incrementExact(revision), baseVersionId, null);
    }

    static void checkSuccessor(UUID rootId, UUID requestedBaseId, UUID baseRootId, ReadingEdition base,
                               boolean archived, boolean hasOpenDraft, int highestVersionNumber) {
        if (!rootId.equals(baseRootId) || !requestedBaseId.equals(base.id())) {
            throw new Conflict(404, "NOT_FOUND");
        }
        if (archived || base.draft()) {
            throw new Conflict(409, "INVALID_STATE");
        }
        if (hasOpenDraft) {
            throw new Conflict(409, "DRAFT_EXISTS");
        }
        if (highestVersionNumber < base.versionNumber()) {
            throw new IllegalArgumentException("Latest edition number precedes the base edition");
        }
    }

    ReadingEdition successor(UUID newId, int highestVersionNumber) {
        return new ReadingEdition(newId, Math.incrementExact(highestVersionNumber), 1, id, null);
    }
}
