package com.baseielts.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import com.baseielts.platform.identity.IdentitySecurityContextRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    @Bean
    SecurityFilterChain applicationFilterChain(HttpSecurity http,
        @Value("${base.identity.enabled:false}") boolean identityEnabled, ObjectProvider<IdentitySecurityContextRepository> contexts) throws Exception {
        String[] verificationRoutes = {
            "/api/v1/auth/register", "/api/v1/auth/resend-verification", "/api/v1/auth/verify-email", "/api/v1/auth/login"
        };
        if (identityEnabled) {
            // These handlers check the scoped server-side PREAUTH token before binding JSON or writing.
            http.csrf(csrf -> csrf.ignoringRequestMatchers(verificationRoutes).ignoringRequestMatchers("/api/v1/users/me", "/api/v1/auth/logout"));
            http.securityContext(context -> context.requireExplicitSave(true).securityContextRepository(contexts.getObject()));
            http.addFilterBefore(new PrivateRequestAdmission(), SecurityContextHolderFilter.class);
            http.addFilterBefore(new IdentityRequestBoundary(), PrivateRequestAdmission.class);
        }
        return http.authorizeHttpRequests(requests -> {
                requests.requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll();
                if (identityEnabled) {
                    requests.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll();
                    requests.requestMatchers(HttpMethod.POST, verificationRoutes).permitAll();
                    requests.requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated();
                    requests.requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").authenticated();
                    requests.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated();
                }
                requests.anyRequest().denyAll();
            })
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, failure) ->
                    IdentityRequestBoundary.reject(response, HttpStatus.UNAUTHORIZED.value(),
                        "AUTH_REQUIRED", "Authentication required."))
                .accessDeniedHandler((request, response, failure) ->
                    IdentityRequestBoundary.reject(response, HttpStatus.FORBIDDEN.value(),
                        "FORBIDDEN", "Request not allowed.")))
            .build();
    }

    private static final class PrivateRequestAdmission extends OncePerRequestFilter {
        private final Semaphore inFlight = new Semaphore(16);

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain downstream)
                throws ServletException, IOException {
            if (!IdentitySecurityContextRepository.requiresAuth(request)) {
                downstream.doFilter(request, response);
                return;
            }
            if (!inFlight.tryAcquire()) {
                response.setHeader("Retry-After", "1");
                IdentityRequestBoundary.reject(response, 429, "ADMISSION_LIMIT", "Please retry later.");
                return;
            }
            try {
                downstream.doFilter(request, response);
            } finally {
                inFlight.release();
            }
        }
    }
}
