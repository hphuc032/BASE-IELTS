package com.baseielts.platform.content.reading;

import java.util.List;
import java.util.UUID;

/** Explicit response shapes. Raw database JSON never becomes the HTTP response. */
final class ReadingStaffProjection {
    private ReadingStaffProjection() {}

    record Translation(String language, String text) {}

    record Paragraph(UUID paragraphId, String content, int position, String heading,
                     List<Translation> translations) {}

    record PassageAdmin(UUID stableId, UUID editionId, int versionNumber, long revision, String state,
                        String slug, UUID currentVersionId, String title, String summary, String instructions,
                        UUID topicId, String visibility, List<Paragraph> paragraphs) {}

    record Summary(UUID stableId, UUID editionId, String slug, String title, String state, long revision,
                   UUID currentVersionId, UUID topicId) {}

    record Page(List<Summary> data, PageMetadata page) {}

    record PageMetadata(int number, int size, long totalElements, long totalPages) {}

    record Placement(UUID lessonId, Integer position, long revision) {}

    record PreviewParagraph(String heading, String body, List<Translation> translations) {}

    record Preview(boolean draft, String title, String summary, List<PreviewParagraph> paragraphs) {}

    record VersionedPreview(long revision, Preview preview) {}
}
