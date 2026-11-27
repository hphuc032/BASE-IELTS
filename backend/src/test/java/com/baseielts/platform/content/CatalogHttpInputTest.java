package com.baseielts.platform.content;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class CatalogHttpInputTest {
    @Test
    void passesOnlyBoundedUtf8JsonToCatalogParser() {
        var request = new MockHttpServletRequest("POST", "/api/v1/teacher/courses");
        request.setContentType("application/json; charset=UTF-8");
        request.setContent("""
            {"slug":" READING ","title":"Đọc","displayOrder":1}
            """.getBytes(StandardCharsets.UTF_8));
        assertEquals(new CatalogInput("reading", "Đọc", null, 1), CatalogHttpInput.write(request, CatalogInput.Kind.COURSE));
        request.setContentType("application/json; charset=UTF-16");
        rejected(415, () -> CatalogHttpInput.write(request, CatalogInput.Kind.COURSE));
        request.setContentType("application/json");
        request.setContent(new byte[CatalogInput.MAX_BODY_BYTES + 1]);
        rejected(413, () -> CatalogHttpInput.write(request, CatalogInput.Kind.COURSE));
        request.setContent(new byte[] {(byte) 0xc3, '('});
        rejected(400, () -> CatalogHttpInput.write(request, CatalogInput.Kind.COURSE));
        request.setContent("""
            {"slug":"a","slug":"b","title":"Title","displayOrder":1}
            """.getBytes(StandardCharsets.UTF_8));
        rejected(400, () -> CatalogHttpInput.write(request, CatalogInput.Kind.COURSE));
    }

    @Test
    void acceptsOneStrongRevisionAndBoundedLiteralListFilters() {
        var request = new MockHttpServletRequest("PATCH", "/api/v1/teacher/courses/id");
        rejected(428, () -> CatalogHttpInput.expectedRevision(request));
        request.addHeader("If-Match", "\"12\"");
        assertEquals(12L, CatalogHttpInput.expectedRevision(request));
        request.addHeader("If-Match", "\"13\"");
        rejected(400, () -> CatalogHttpInput.expectedRevision(request));

        assertEquals(20, CatalogHttpInput.list(Map.of()).size());
        var filtered = CatalogHttpInput.list(Map.of(
            "page", new String[] {"99"}, "size", new String[] {"50"},
            "q", new String[] {"%_"}, "status", new String[] {"INACTIVE"}));
        assertEquals(4_950, filtered.offset());
        assertEquals("%_", filtered.literalSearch());
        assertEquals(CatalogInput.Status.INACTIVE, filtered.status());
        rejected(400, () -> CatalogHttpInput.list(Map.of("page", new String[] {"100"})));
        rejected(400, () -> CatalogHttpInput.list(Map.of("size", new String[] {"51"})));
        rejected(400, () -> CatalogHttpInput.list(Map.of("unknown", new String[] {"a"})));
        rejected(400, () -> CatalogHttpInput.list(Map.of("q", new String[] {"one", "two"})));
        rejected(400, () -> CatalogHttpInput.list(Map.of("q", new String[] {"x".repeat(101)})));
        rejected(400, () -> CatalogHttpInput.list(Map.of("q", new String[] {"\u00A0\u202F"})));
        String unicodeSearch = "𐐀".repeat(100);
        assertEquals(unicodeSearch, CatalogHttpInput.list(Map.of("q", new String[] {unicodeSearch})).literalSearch());
        rejected(400, () -> CatalogHttpInput.list(Map.of("q", new String[] {unicodeSearch + "𐐀"})));
    }

    private static void rejected(int status, Runnable request) {
        assertEquals(status, assertThrows(CatalogHttpInput.Rejected.class, request::run).status);
    }
}
