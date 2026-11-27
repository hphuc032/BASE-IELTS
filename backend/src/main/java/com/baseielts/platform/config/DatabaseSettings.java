package com.baseielts.platform.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "spring.datasource")
@Validated
record DatabaseSettings(@NotBlank String url, @NotBlank String username, @NotBlank String password) {
}
