package com.baseielts.platform.content.reading;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "base.reading.enabled", havingValue = "true")
class ReadingStaffSecurity {
    @Bean
    @Order(11)
    SecurityFilterChain readingTransport(HttpSecurity http, ReadingStaffApi api) throws Exception {
        var path = PathPatternRequestMatcher.withDefaults();
        var routes = new OrRequestMatcher(
            path.matcher(HttpMethod.GET, ReadingStaffApi.ROOT),
            path.matcher(HttpMethod.POST, ReadingStaffApi.ROOT),
            path.matcher(HttpMethod.POST, ReadingStaffApi.ROOT + ReadingStaffApi.CLONES),
            path.matcher(HttpMethod.GET, ReadingStaffApi.ROOT + ReadingStaffApi.DRAFT),
            path.matcher(HttpMethod.GET, ReadingStaffApi.ROOT + ReadingStaffApi.PREVIEW),
            path.matcher(HttpMethod.GET, ReadingStaffApi.ROOT + ReadingStaffApi.EDITION),
            path.matcher(HttpMethod.PUT, ReadingStaffApi.ROOT + ReadingStaffApi.DRAFT),
            path.matcher(HttpMethod.PUT, ReadingStaffApi.ROOT + ReadingStaffApi.PLACEMENT));
        return http.securityMatcher(routes)
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .build();
    }
}
