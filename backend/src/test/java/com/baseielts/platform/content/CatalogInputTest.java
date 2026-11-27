package com.baseielts.platform.content;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogInputTest {
    @Test
    void normalizesCatalogFieldsAndKeepsOrderingExact() {
        var input = CatalogInput.parse(CatalogInput.Kind.COURSE, utf8("""
            {"slug":" READING-1 ","title":"Cafe\u0301","description":" First\\r\\nSecond ","displayOrder":2147483647}
            """));
        assertEquals(new CatalogInput("reading-1", "Café", "First\nSecond", Integer.MAX_VALUE), input);
        var level = CatalogInput.parse(CatalogInput.Kind.LEVEL,
            utf8("{\"code\":\" A1 \",\"name\":\"A1\",\"displayOrder\":1,\"description\":\"\\u00a0\"}"));
        assertNull(level.description());
        assertEquals(CatalogInput.Status.ARCHIVED, CatalogInput.parseStatus(utf8("{\"status\":\"ARCHIVED\"}")));
    }

    @Test
    void rejectsMassAssignmentDuplicateKeysAndMalformedBodies() {
        for (String invalid : new String[] {
            "{\"slug\":\"a\",\"title\":\"A\",\"displayOrder\":1,\"parentId\":null}",
            "{\"slug\":\"a\",\"slug\":\"b\",\"title\":\"A\",\"displayOrder\":1}",
            "{\"slug\":\"a\",\"title\":\"A\",\"displayOrder\":1,\"status\":\"ARCHIVED\"}",
            "{\"slug\":\"a\",\"title\":\"A\",\"displayOrder\":1} {}",
            "{\"slug\":\"a\",\"title\":\"A\\ud800\",\"displayOrder\":1}",
            "{\"slug\":\"a\",\"title\":\"A\\u0000\",\"displayOrder\":1}",
            "{\"slug\":\"a\",\"title\":\"A\",\"displayOrder\":1.5}",
            "{\"slug\":\"a\",\"title\":\"A\",\"displayOrder\":2147483648}",
            "{\"slug\":\"a\",\"title\":\"A\",\"displayOrder\":0}",
            "{\"slug\":\"é\",\"title\":\"A\",\"displayOrder\":1}"
        }) {
            assertThrows(IllegalArgumentException.class,
                () -> CatalogInput.parse(CatalogInput.Kind.COURSE, utf8(invalid)), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> CatalogInput.parse(CatalogInput.Kind.COURSE,
            new byte[CatalogInput.MAX_BODY_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> CatalogInput.parseStatus(utf8("{\"status\":\"archived\"}")));
        assertThrows(IllegalArgumentException.class, () -> CatalogInput.parseStatus(
            utf8("{\"status\":\"ACTIVE\",\"revision\":1}")));
    }

    @Test
    void countsSupplementaryCharactersAsScalars() {
        String body = "{\"slug\":\"a\",\"title\":\"%s\",\"displayOrder\":1}";
        String maximum = "😀".repeat(160);
        assertEquals(maximum, CatalogInput.parse(CatalogInput.Kind.LESSON, utf8(body.formatted(maximum))).label());
        assertThrows(IllegalArgumentException.class, () -> CatalogInput.parse(CatalogInput.Kind.LESSON,
            utf8(body.formatted(maximum + "😀"))));
        assertThrows(IllegalArgumentException.class, () -> CatalogInput.parse(CatalogInput.Kind.COURSE,
            utf8(body.formatted("a" + " ".repeat(12_000) + "b"))));
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
