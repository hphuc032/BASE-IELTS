package com.baseielts.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "spring.datasource.url=jdbc:postgresql://localhost/base_ielts_test",
    "spring.datasource.username=fixture",
    "spring.datasource.password=fixture"
})
@AutoConfigureMockMvc
class FoundationSecurityTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void healthIsPublicAndDoesNotAllocateSession() throws Exception {
        mvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist())
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andExpect(result -> {
                if (result.getRequest().getSession(false) != null) {
                    throw new AssertionError("The health endpoint allocated a session");
                }
            });
    }

    @Test
    void privateAndMutatingRoutesFailClosed() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/health")).andExpect(status().isForbidden());
    }
}
