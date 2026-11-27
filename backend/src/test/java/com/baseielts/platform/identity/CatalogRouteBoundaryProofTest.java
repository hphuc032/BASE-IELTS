package com.baseielts.platform.identity;

import com.baseielts.platform.content.CatalogStaffApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "base.catalog.enabled=true",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "spring.datasource.url=jdbc:postgresql://localhost/base_ielts_test",
    "spring.datasource.username=fixture",
    "spring.datasource.password=fixture"
})
@AutoConfigureMockMvc
class CatalogRouteBoundaryProofTest {
    private static final String STAFF = "/api/v1/teacher";
    private static final String ID = "10000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private StaffAuthentication staff;
    @MockitoBean
    private CatalogStaffApi.Databases databases;

    @Test
    void onlyExactEndpointsEnterCatalogAuth() throws Exception {
        when(staff.execute(any(), nullable(StaffAuthentication.Database.class), any()))
            .thenThrow(StaffAuthentication.denied(StaffAuthentication.Rejection.AUTH_REQUIRED));
        for (String collection : new String[] {"/courses", "/courses/" + ID + "/levels", "/levels/" + ID + "/lessons"}) {
            mvc.perform(get(STAFF + collection).queryParam("q", "reading")).andExpect(status().isUnauthorized());
            verifyCatalogAuth();
            mvc.perform(post(STAFF + collection).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
            verifyCatalogAuth();
        }
        for (String collection : new String[] {"courses", "levels", "lessons"}) {
            String item = STAFF + "/" + collection + "/" + ID;
            mvc.perform(get(item)).andExpect(status().isUnauthorized());
            verifyCatalogAuth();
            mvc.perform(patch(item).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
            verifyCatalogAuth();
            mvc.perform(post(item + "/status").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
            verifyCatalogAuth();
        }

        mvc.perform(get(STAFF + "/reading/" + ID)).andExpect(status().isUnauthorized());
        mvc.perform(delete(STAFF + "/courses/" + ID)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        verifyNoInteractions(staff);
    }

    @Test
    void rejectsUnsupportedMediaAndOversizedCatalogRequestsBeforeStaffDatabaseAccess() throws Exception {
        mvc.perform(post(STAFF + "/courses").contentType("text/plain").content("{}"))
            .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(STAFF + "/courses").contentType("application/json").content(" ".repeat(32 * 1024 + 1)))
            .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(staff);
    }

    private void verifyCatalogAuth() {
        verify(staff).execute(any(), nullable(StaffAuthentication.Database.class), any());
        clearInvocations(staff);
    }
}
