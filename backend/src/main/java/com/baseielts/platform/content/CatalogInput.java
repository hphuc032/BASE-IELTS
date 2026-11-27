package com.baseielts.platform.content;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates the catalog write DTOs independently of HTTP admission and persistence. */
record CatalogInput(String key, String label, String description, int displayOrder) {
    // 2,240 possible text scalars including the key. 32 KiB covers JSON-escaped supplementary text.
    static final int MAX_BODY_BYTES = 32 * 1024;
    private static final Pattern KEY = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2).build())
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();

    enum Kind {
        COURSE("slug", "title", 80), LEVEL("code", "name", 32), LESSON("slug", "title", 80);

        private final String keyField;
        private final String labelField;
        private final int maxKey;

        Kind(String keyField, String labelField, int maxKey) {
            this.keyField = keyField;
            this.labelField = labelField;
            this.maxKey = maxKey;
        }
    }

    enum Status { ACTIVE, INACTIVE, ARCHIVED }

    static CatalogInput parse(Kind kind, byte[] body) {
        Objects.requireNonNull(kind);
        JsonNode input = object(body, Set.of(kind.keyField, kind.labelField, "description", "displayOrder"));
        String key = text(input.get(kind.keyField), kind.keyField, kind.maxKey, false);
        if (!key.chars().allMatch(c -> c <= 127)) {
            throw invalid(kind.keyField);
        }
        key = key.toLowerCase(Locale.ROOT);
        if (!KEY.matcher(key).matches()) {
            throw invalid(kind.keyField);
        }
        String label = text(input.get(kind.labelField), kind.labelField, 160, false);
        String description = text(input.get("description"), "description", 2_000, true);
        JsonNode order = input.get("displayOrder");
        if (order == null || !order.isIntegralNumber() || !order.canConvertToInt() || order.intValue() <= 0) {
            throw invalid("displayOrder");
        }
        return new CatalogInput(key, label, description, order.intValue());
    }

    static Status parseStatus(byte[] body) {
        JsonNode input = object(body, Set.of("status"));
        JsonNode status = input.get("status");
        if (status == null || !status.isTextual()) {
            throw invalid("status");
        }
        return switch (status.textValue()) {
            case "ACTIVE" -> Status.ACTIVE;
            case "INACTIVE" -> Status.INACTIVE;
            case "ARCHIVED" -> Status.ARCHIVED;
            default -> throw invalid("status");
        };
    }

    private static JsonNode object(byte[] body, Set<String> fields) {
        if (body == null || body.length > MAX_BODY_BYTES) {
            throw invalid("body");
        }
        JsonNode input;
        try {
            input = MAPPER.readTree(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString());
        } catch (IOException malformed) {
            throw invalid("body");
        }
        if (input == null || !input.isObject()) {
            throw invalid("body");
        }
        var names = input.fieldNames();
        while (names.hasNext()) {
            if (!fields.contains(names.next())) {
                throw invalid("body");
            }
        }
        return input;
    }

    private static String text(JsonNode value, String field, int maxLength, boolean multiline) {
        if (value == null || value.isNull()) {
            if (multiline) {
                return null;
            }
            throw invalid(field);
        }
        if (!value.isTextual()) {
            throw invalid(field);
        }
        String raw = value.textValue();
        String normalized = Normalizer.normalize(multiline ? raw.replace("\r\n", "\n") : raw, Normalizer.Form.NFC);
        if (normalized.codePoints().anyMatch(cp ->
            (Character.isISOControl(cp) && !(multiline && (cp == '\n' || cp == '\t')))
                || cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE)) {
            throw invalid(field);
        }
        String trimmed = trimWhitespace(normalized);
        if (trimmed.codePointCount(0, trimmed.length()) > maxLength) {
            throw invalid(field);
        }
        if (trimmed.isEmpty()) {
            if (multiline) {
                return null;
            }
            throw invalid(field);
        }
        return trimmed;
    }

    private static String trimWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isWhitespace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (start < end && isWhitespace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    private static boolean isWhitespace(int codePoint) {
        // Unicode NEL is an ISO control and has already been rejected.
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static IllegalArgumentException invalid(String field) {
        return new IllegalArgumentException("Invalid catalog input: " + field);
    }
}
