package com.baseielts.platform.content.reading;

import com.baseielts.platform.identity.StaffAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "base.reading.enabled=true",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "spring.datasource.url=jdbc:postgresql://localhost/base_ielts_test",
    "spring.datasource.username=fixture",
    "spring.datasource.password=fixture"
})
@AutoConfigureMockMvc
class ReadingRouteBoundaryTest {
    private static final String ROOT = "/api/v1/teacher/reading";
    private static final String PASSAGE = ROOT + "/10000000-0000-4000-8000-000000000001";
    private static final String EDITION = "10000000-0000-4000-8000-000000000002";
    private static final String DRAFT = PASSAGE + "/drafts/" + EDITION;

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private StaffAuthentication staff;
    @MockitoBean
    private ReadingStaffApi.Databases databases;

    @Test
    void routesOnlyTheApprovedReadingMethodsThroughStaffAuth() throws Exception {
        var denied = mock(StaffAuthentication.Denied.class);
        when(denied.error()).thenReturn(StaffAuthentication.Rejection.AUTH_REQUIRED);
        when(staff.execute(any(), nullable(StaffAuthentication.Database.class), any())).thenThrow(denied);
        for (String endpoint : new String[] {ROOT, DRAFT, DRAFT + "/preview", PASSAGE + "/editions/" + EDITION}) {
            mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
            verifyReadingAuth();
        }
        for (String endpoint : new String[] {ROOT, PASSAGE + "/drafts"}) {
            mvc.perform(post(endpoint).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
            verifyReadingAuth();
        }
        for (String endpoint : new String[] {DRAFT, PASSAGE + "/placement"}) {
            mvc.perform(put(endpoint).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
            verifyReadingAuth();
        }
        mvc.perform(post(DRAFT + "/publish").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/teacher/exercises").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete(DRAFT)).andExpect(status().isForbidden());
        verifyNoInteractions(staff);
    }

    @Test
    void rejectsInvalidMediaBeforeStaffExecution() throws Exception {
        mvc.perform(post(ROOT).contentType("text/plain").content("{}"))
            .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(ROOT).contentType("application/json").content(" ".repeat(ReadingDraftInput.MAX_BODY_BYTES + 1)))
            .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(staff);
    }

    private void verifyReadingAuth() {
        verify(staff).execute(any(), nullable(StaffAuthentication.Database.class), any());
        clearInvocations(staff);
    }
}
