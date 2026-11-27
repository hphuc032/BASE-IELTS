package com.baseielts.platform.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/** Exact transport routing. The controller always delegates authentication and scoped CSRF to StaffAuthentication. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "base.catalog.enabled", havingValue = "true")
class CatalogStaffSecurity {
    @Bean
    @Order(10)
    SecurityFilterChain catalogTransport(HttpSecurity http, CatalogStaffApi api) throws Exception {
        var path = PathPatternRequestMatcher.withDefaults().basePath("/api/v1/teacher");
        var supportedRoutes = new OrRequestMatcher(
            path.matcher(HttpMethod.GET, "/courses"),
            path.matcher(HttpMethod.GET, "/courses/{courseId}/levels"),
            path.matcher(HttpMethod.GET, "/levels/{levelId}/lessons"),
            path.matcher(HttpMethod.GET, "/{collection:courses|levels|lessons}/{id}"),
            path.matcher(HttpMethod.POST, "/courses"),
            path.matcher(HttpMethod.POST, "/courses/{courseId}/levels"),
            path.matcher(HttpMethod.POST, "/levels/{levelId}/lessons"),
            path.matcher(HttpMethod.POST, "/{collection:courses|levels|lessons}/{id}/status"),
            path.matcher(HttpMethod.PATCH, "/{collection:courses|levels|lessons}/{id}"));
        return http.securityMatcher(supportedRoutes)
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
