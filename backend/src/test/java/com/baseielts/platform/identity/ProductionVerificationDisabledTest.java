package com.baseielts.platform.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"base.identity.enabled=true", "base.identity.verification.capture=true"})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@EnabledIfEnvironmentVariable(named = "BASE_TEST_POSTGRES", matches = "true")
class ProductionVerificationDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.context.ApplicationContext context;

    @Test
    void productionRegistrationAndResendStayDisabledEvenWithCaptureFlag() throws Exception {
        assertNull(context.getBeanProvider(DevelopmentVerificationMailbox.class).getIfAvailable());
        var bootstrap = mvc.perform(get("/api/v1/auth/csrf").param("scope", "PREAUTH"))
            .andExpect(status().isOk()).andReturn();
        String token = json.readTree(bootstrap.getResponse().getContentAsString()).path("data").path("token").asText();
        var cookie = bootstrap.getResponse().getCookie(PreauthStore.COOKIE);
        String email = "disabled-" + UUID.randomUUID() + "@identity-test.invalid";
        mvc.perform(post("/api/v1/auth/register").cookie(cookie).header(PreauthStore.HEADER, token)
            .contentType("application/json")
            .content(json.writeValueAsString(new VerificationController.Registration(email, "Disabled fixture"))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
        mvc.perform(post("/api/v1/auth/resend-verification").cookie(cookie).header(PreauthStore.HEADER, token)
            .contentType("application/json").content(json.writeValueAsString(new VerificationController.Resend(email))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
    }
}
