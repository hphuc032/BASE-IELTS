package com.baseielts.platform.content.reading;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class ReadingDraftInput {
    static final int MAX_BODY_BYTES = 768 * 1024;
    private static final int MAX_PARAGRAPHS = 32;
    private static final int MAX_TRANSLATIONS_PER_PARAGRAPH = 2;
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final Pattern KEY = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    enum Visibility { PUBLIC, PRIVATE }

    record PassageCreate(String slug, String title) {}
    record Clone(UUID baseVersionId) {}
    record Placement(UUID lessonId, Integer position) {}
    record Passage(String title, String summary, String instructions, UUID topicId, Visibility visibility,
                   List<Paragraph> paragraphs) {}
    record Paragraph(UUID paragraphId, String heading, String content, List<Translation> translations) {}
    record Translation(String language, String text) {}

    static final class Invalid extends IllegalArgumentException {
        private final String code;

        Invalid(String code) {
            super(code);
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    static PassageCreate passageCreate(byte[] body) {
        JsonNode input = object(parse(body), "slug", "title");
        return new PassageCreate(key(input.get("slug"), 80), text(input, "title", 160, true, false));
    }

    static Passage passage(byte[] body) {
        JsonNode input = object(parse(body), "title", "summary", "instructions", "topicId", "visibility", "paragraphs");
        List<Paragraph> paragraphs = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        var items = array(input.get("paragraphs"));
        if (items.size() > MAX_PARAGRAPHS) {
            throw new Invalid("INVALID_INPUT");
        }
        for (JsonNode item : items) {
            object(item, "paragraphId", "heading", "content", "translations");
            UUID id = item.has("paragraphId") ? uuid(item.get("paragraphId"), "INVALID_PARAGRAPH_ID") : null;
            if (id != null && !ids.add(id)) {
                throw new Invalid("INVALID_PARAGRAPH_ID");
            }
            paragraphs.add(new Paragraph(id, text(item, "heading", 160, false, false),
                    text(item, "content", 8000, true, true), translations(item.get("translations"))));
        }
        return new Passage(text(input, "title", 160, true, false), text(input, "summary", 1000, false, true),
                text(input, "instructions", 2000, false, true), optionalUuid(input.get("topicId")),
                visibility(input), List.copyOf(paragraphs));
    }

    static Clone cloneDraft(byte[] body) {
        JsonNode input = object(parse(body), "baseVersionId");
        return new Clone(uuid(input.get("baseVersionId"), "INVALID_INPUT"));
    }

    static Placement placement(byte[] body) {
        JsonNode input = object(parse(body), "lessonId", "position");
        JsonNode lesson = input.get("lessonId");
        if (lesson == null || (lesson.isNull() && input.has("position"))) {
            throw new Invalid("INVALID_INPUT");
        }
        if (lesson.isNull()) {
            return new Placement(null, null);
        }
        return new Placement(uuid(lesson, "INVALID_INPUT"),
                integer(input.get("position"), 1, Integer.MAX_VALUE, "INVALID_INPUT"));
    }

    static UUID routeUuid(String value) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw new Invalid("NOT_FOUND");
        }
        return UUID.fromString(value);
    }

    static UUID filterUuid(String value) {
        if (!UUID_TEXT.matcher(value).matches()) {
            throw new Invalid("INVALID_INPUT");
        }
        return UUID.fromString(value);
    }

    private static List<Translation> translations(JsonNode input) {
        List<Translation> result = new ArrayList<>();
        Set<String> languages = new HashSet<>();
        var items = optionalArray(input);
        if (items.size() > MAX_TRANSLATIONS_PER_PARAGRAPH) {
            throw new Invalid("INVALID_INPUT");
        }
        for (JsonNode item : items) {
            object(item, "language", "text");
            String language;
            try {
                language = new Locale.Builder().setLanguageTag(text(item, "language", 255, true, false))
                        .build().toLanguageTag();
            } catch (IllformedLocaleException invalid) {
                throw new Invalid("INVALID_INPUT");
            }
            if (!languages.add(language)) {
                throw new Invalid("INVALID_INPUT");
            }
            result.add(new Translation(language, text(item, "text", 8000, true, true)));
        }
        return List.copyOf(result);
    }

    private static int integer(JsonNode node, int min, int max, String code) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()
                || node.intValue() < min || node.intValue() > max) {
            throw new Invalid(code);
        }
        return node.intValue();
    }

    private static Visibility visibility(JsonNode node) {
        return enumeration(Visibility.class, node.get("visibility"), "INVALID_INPUT");
    }

    private static <E extends Enum<E>> E enumeration(Class<E> type, JsonNode node, String code) {
        if (node == null || !node.isTextual()) {
            throw new Invalid(code);
        }
        try {
            return Enum.valueOf(type, node.textValue());
        } catch (IllegalArgumentException invalid) {
            throw new Invalid(code);
        }
    }

    private static UUID optionalUuid(JsonNode node) {
        return node == null || node.isNull() ? null : uuid(node, "INVALID_INPUT");
    }

    private static UUID uuid(JsonNode node, String code) {
        if (node == null || !node.isTextual() || !UUID_TEXT.matcher(node.textValue()).matches()) {
            throw new Invalid(code);
        }
        return UUID.fromString(node.textValue());
    }

    private static String key(JsonNode node, int limit) {
        String value = normalize(node, limit, true, false);
        if (!value.codePoints().allMatch(cp -> cp < 128)) {
            throw new Invalid("INVALID_INPUT");
        }
        value = value.toLowerCase(Locale.ROOT);
        if (!KEY.matcher(value).matches()) {
            throw new Invalid("INVALID_INPUT");
        }
        return value;
    }

    private static String text(JsonNode node, String field, int limit, boolean required, boolean multiline) {
        return normalize(node.get(field), limit, required, multiline);
    }

    private static String normalize(JsonNode node, int limit, boolean required, boolean multiline) {
        if (!required && (node == null || node.isNull())) {
            return null;
        }
        if (node == null || !node.isTextual()) {
            throw new Invalid("INVALID_INPUT");
        }
        String value = node.textValue();
        if (multiline) {
            value = value.replace("\r\n", "\n");
        }
        if (value.codePoints().anyMatch(cp -> (Character.isISOControl(cp)
                && !(multiline && (cp == '\n' || cp == '\t'))) || (cp >= 0xd800 && cp <= 0xdfff))) {
            throw new Invalid("INVALID_INPUT");
        }
        value = trimWhitespace(Normalizer.normalize(value, Normalizer.Form.NFC));
        if (value.isEmpty() && !required) {
            return null;
        }
        if (value.isEmpty() || value.codePointCount(0, value.length()) > limit) {
            throw new Invalid("INVALID_INPUT");
        }
        return value;
    }

    private static String trimWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && whiteSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && whiteSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean whiteSpace(char value) {
        return Character.isSpaceChar(value) || value == 0x85 || (value >= 0x09 && value <= 0x0d);
    }

    private static JsonNode parse(byte[] body) {
        if (body.length > MAX_BODY_BYTES) {
            throw new Invalid("PAYLOAD_TOO_LARGE");
        }
        try {
            JsonNode input = JSON.readTree(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString());
            if (input == null || !input.isObject()) {
                throw new Invalid("INVALID_INPUT");
            }
            if (JSON.writeValueAsBytes(input).length > 512 * 1024) {
                throw new Invalid("INVALID_INPUT");
            }
            return input;
        } catch (IOException invalid) {
            throw new Invalid("INVALID_INPUT");
        }
    }

    private static JsonNode object(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            throw new Invalid("INVALID_INPUT");
        }
        Set<String> allowed = Set.of(fields);
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new Invalid("INVALID_INPUT");
            }
        });
        return node;
    }

    private static JsonNode optionalArray(JsonNode node) {
        return node == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode() : array(node);
    }

    private static JsonNode array(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new Invalid("INVALID_INPUT");
        }
        return node;
    }
}
