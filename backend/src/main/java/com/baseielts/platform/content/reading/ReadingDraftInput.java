package com.baseielts.platform.content.reading;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class ReadingDraftInput {
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();
    private static final Pattern BLANK = Pattern.compile("_{3,}");
    private static final Pattern SPACE = Pattern.compile("\\p{IsWhite_Space}+");
    private static final Pattern KEY = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    enum Visibility { PUBLIC, PRIVATE }
    enum Mode { LEARNING, EXAM }
    enum Type { MULTIPLE_CHOICE_SINGLE, TRUE_FALSE_NOT_GIVEN, YES_NO_NOT_GIVEN, SHORT_ANSWER, SENTENCE_COMPLETION }

    record PassageCreate(String slug, String title) {}
    record ExerciseCreate(String slug, String title, UUID passageId, UUID passageVersionId, Modes modes) {}
    record Passage(String title, String summary, String instructions, UUID topicId, Visibility visibility,
                   List<Paragraph> paragraphs) {}
    record Paragraph(UUID paragraphId, String heading, String content, List<Translation> translations) {}
    record Translation(String language, String text) {}
    record Exercise(String title, String summary, Visibility visibility, UUID passageVersionId, Modes modes,
                    List<Group> groups) {}
    record Modes(List<Mode> availableModes, Integer examDurationSeconds) {}
    record Group(String instructions, List<Question> questions) {}
    record Question(Type type, String prompt, String instructions, BigDecimal points, List<Option> options,
                    Integer maxWords, Marking marking, String explanation, List<Evidence> evidence) {}
    record Option(String key, String content) {}
    sealed interface Marking permits Choice, Classification, TextAnswers {}
    record Choice(String correctOptionKey) implements Marking {}
    record Classification(String value) implements Marking {}
    record TextAnswers(List<String> acceptedAnswers, boolean caseSensitive) implements Marking {}
    record Evidence(UUID paragraphId, String locator) {}

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

    static ExerciseCreate exerciseCreate(byte[] body) {
        JsonNode input = object(parse(body), "slug", "title", "passageId", "passageVersionId",
                "availableModes", "examDurationSeconds");
        return new ExerciseCreate(key(input.get("slug"), 80), text(input, "title", 160, true, false),
                uuid(input.get("passageId"), "INVALID_INPUT"),
                uuid(input.get("passageVersionId"), "INVALID_INPUT"), modes(input));
    }

    static Passage passage(byte[] body) {
        JsonNode input = object(parse(body), "title", "summary", "instructions", "topicId", "visibility", "paragraphs");
        List<Paragraph> paragraphs = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (JsonNode item : array(input.get("paragraphs"), Integer.MAX_VALUE)) {
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

    static Exercise exercise(byte[] body) {
        JsonNode input = object(parse(body), "title", "summary", "visibility", "passageVersionId",
                "availableModes", "examDurationSeconds", "groups");
        List<Group> groups = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int questionCount = 0;
        for (JsonNode group : array(input.get("groups"), 20)) {
            object(group, "instructions", "questions");
            List<Question> questions = new ArrayList<>();
            for (JsonNode item : array(group.get("questions"), 100 - questionCount)) {
                Question question = question(item);
                total = total.add(question.points());
                questionCount++;
                questions.add(question);
            }
            groups.add(new Group(text(group, "instructions", 2000, false, true), List.copyOf(questions)));
        }
        if (total.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new Invalid("INVALID_INPUT");
        }
        return new Exercise(text(input, "title", 160, true, false), text(input, "summary", 1000, false, true),
                visibility(input), uuid(input.get("passageVersionId"), "INVALID_INPUT"),
                modes(input), List.copyOf(groups));
    }

    private static List<Translation> translations(JsonNode input) {
        List<Translation> result = new ArrayList<>();
        Set<String> languages = new HashSet<>();
        for (JsonNode item : optionalArray(input, Integer.MAX_VALUE)) {
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

    private static Question question(JsonNode item) {
        object(item, "type", "prompt", "instructions", "points", "options", "maxWords", "marking",
                "explanation", "evidence");
        Type type = enumeration(Type.class, item.get("type"), "INVALID_INPUT");
        String prompt = text(item, "prompt", 2000, true, true);
        boolean textType = type == Type.SHORT_ANSWER || type == Type.SENTENCE_COMPLETION;
        Integer maxWords = null;
        if (textType) {
            maxWords = integer(item.get("maxWords"), 1, 50, "INVALID_INPUT");
        } else if (item.has("maxWords")) {
            throw new Invalid("INVALID_INPUT");
        }
        if (type == Type.SENTENCE_COMPLETION && BLANK.matcher(prompt).results().count() != 1) {
            throw new Invalid("INVALID_INPUT");
        }
        List<Option> options = options(item, type);
        return new Question(type, prompt, text(item, "instructions", 2000, false, true), points(item.get("points")),
                options, maxWords, marking(item.get("marking"), type, options, maxWords),
                text(item, "explanation", 4000, false, true), evidence(item.get("evidence")));
    }

    private static List<Evidence> evidence(JsonNode input) {
        Set<Evidence> references = new java.util.LinkedHashSet<>();
        for (JsonNode entry : optionalArray(input, Integer.MAX_VALUE)) {
            object(entry, "paragraphId", "locator");
            var reference = new Evidence(uuid(entry.get("paragraphId"), "INVALID_PARAGRAPH_ID"),
                    text(entry, "locator", 240, false, false));
            if (!references.add(reference)) {
                throw new Invalid("INVALID_EVIDENCE");
            }
        }
        return List.copyOf(references);
    }

    private static List<Option> options(JsonNode item, Type type) {
        if (type != Type.MULTIPLE_CHOICE_SINGLE) {
            if (item.has("options")) {
                throw new Invalid("INVALID_INPUT");
            }
            return List.of();
        }
        List<Option> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<String> contents = new HashSet<>();
        for (JsonNode option : array(item.get("options"), 6)) {
            object(option, "key", "content");
            String key = key(option.get("key"), 24);
            String content = text(option, "content", 1000, true, false);
            if (!keys.add(key) || !contents.add(content)) {
                throw new Invalid("INVALID_INPUT");
            }
            result.add(new Option(key, content));
        }
        if (result.size() < 2) {
            throw new Invalid("INVALID_INPUT");
        }
        return List.copyOf(result);
    }

    private static Marking marking(JsonNode input, Type type, List<Option> options, Integer maxWords) {
        if (input == null || input.isNull()) {
            return null;
        }
        return switch (type) {
            case MULTIPLE_CHOICE_SINGLE -> {
                object(input, "correctOptionKey");
                String correct = key(input.get("correctOptionKey"), 24);
                if (options.stream().noneMatch(option -> option.key().equals(correct))) {
                    throw new Invalid("INVALID_INPUT");
                }
                yield new Choice(correct);
            }
            case TRUE_FALSE_NOT_GIVEN -> classification(input, Set.of("TRUE", "FALSE", "NOT_GIVEN"));
            case YES_NO_NOT_GIVEN -> classification(input, Set.of("YES", "NO", "NOT_GIVEN"));
            case SHORT_ANSWER, SENTENCE_COMPLETION -> textAnswers(input, maxWords);
        };
    }

    private static Classification classification(JsonNode input, Set<String> values) {
        object(input, "value");
        String value = input.path("value").asText();
        if (!input.path("value").isTextual() || !values.contains(value)) {
            throw new Invalid("INVALID_INPUT");
        }
        return new Classification(value);
    }

    private static TextAnswers textAnswers(JsonNode input, int maxWords) {
        object(input, "acceptedAnswers", "caseSensitive");
        JsonNode sensitivity = input.path("caseSensitive");
        if (!sensitivity.isBoolean()) {
            throw new Invalid("INVALID_INPUT");
        }
        List<String> answers = new ArrayList<>();
        Set<String> canonical = new HashSet<>();
        for (JsonNode variant : array(input.get("acceptedAnswers"), 12)) {
            String answer = normalize(variant, 2000, true, false);
            String[] words = SPACE.split(answer);
            String collapsed = String.join(" ", words);
            if (!sensitivity.booleanValue()) {
                collapsed = collapsed.toLowerCase(Locale.ROOT);
            }
            if (words.length > maxWords || !canonical.add(collapsed)) {
                throw new Invalid("INVALID_INPUT");
            }
            answers.add(answer);
        }
        if (answers.isEmpty()) {
            throw new Invalid("INVALID_INPUT");
        }
        return new TextAnswers(List.copyOf(answers), sensitivity.booleanValue());
    }

    private static Modes modes(JsonNode input) {
        JsonNode items = input.path("availableModes");
        if (!items.isArray() || items.isEmpty() || items.size() > Mode.values().length) {
            throw new Invalid("INVALID_AVAILABLE_MODES");
        }
        Set<Mode> modes = new HashSet<>();
        for (JsonNode item : items) {
            if (!modes.add(enumeration(Mode.class, item, "INVALID_AVAILABLE_MODES"))) {
                throw new Invalid("INVALID_AVAILABLE_MODES");
            }
        }
        JsonNode duration = input.path("examDurationSeconds");
        if (modes.contains(Mode.EXAM)) {
            return new Modes(Arrays.stream(Mode.values()).filter(modes::contains).toList(),
                    integer(duration, 1, 86400, "INVALID_EXAM_DURATION"));
        }
        if (!duration.isMissingNode() && !duration.isNull()) {
            throw new Invalid("INVALID_EXAM_DURATION");
        }
        return new Modes(List.of(Mode.LEARNING), null);
    }

    private static BigDecimal points(JsonNode node) {
        if (node == null || !node.isNumber()) {
            throw new Invalid("INVALID_INPUT");
        }
        BigDecimal value = node.decimalValue();
        if (value.scale() > 2 || value.compareTo(new BigDecimal("0.01")) < 0
                || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new Invalid("INVALID_INPUT");
        }
        return value;
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
        if (body.length > 768 * 1024) {
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

    private static Iterable<JsonNode> optionalArray(JsonNode node, int limit) {
        return node == null ? List.of() : array(node, limit);
    }

    private static JsonNode array(JsonNode node, int limit) {
        if (node == null || !node.isArray() || node.size() > limit) {
            throw new Invalid("INVALID_INPUT");
        }
        return node;
    }
}
