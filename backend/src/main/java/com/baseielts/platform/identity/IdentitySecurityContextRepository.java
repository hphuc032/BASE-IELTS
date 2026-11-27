package com.baseielts.platform.identity;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Authentication is reconstructed from the approved binding and DB revision, never an HttpSession. */
@Component
@ConditionalOnProperty(name = "base.identity.enabled", havingValue = "true")
public final class IdentitySecurityContextRepository implements SecurityContextRepository {
    private final AuthSessions sessions;
    private final AuthenticationService authentication;

    IdentitySecurityContextRepository(AuthSessions sessions, AuthenticationService authentication) {
        this.sessions = sessions;
        this.authentication = authentication;
    }

    @Override
    public SecurityContext loadContext(HttpRequestResponseHolder holder) {
        HttpServletRequest request = holder.getRequest();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        if (request.getDispatcherType() != DispatcherType.REQUEST || !requiresAuth(request)) {
            return context;
        }
        AuthSessions.Binding binding = sessions.lookup(CookieSupport.value(request, AuthSessions.COOKIE));
        if (binding != null) {
            try {
                var profile = authentication.authorized(binding,
                    account -> authentication.profile(account, binding.revision, null), false, false);
                if (profile != null) {
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        binding.account, null, profile.roles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
                }
            } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException unavailable) {
                // Missing DB authority must not restore any cached context.
            }
        }
        return context;
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        // Only login after a known database commit can change the binding registry.
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.REQUEST && requiresAuth(request)
            && sessions.lookup(CookieSupport.value(request, AuthSessions.COOKIE)) != null;
    }

    public static boolean requiresAuth(HttpServletRequest request) {
        return switch (request.getRequestURI().substring(request.getContextPath().length())) {
            case "/api/v1/users/me", "/api/v1/auth/logout" -> true;
            case "/api/v1/auth/csrf" -> "AUTH".equals(request.getParameter("scope"));
            default -> false;
        };
    }
}
