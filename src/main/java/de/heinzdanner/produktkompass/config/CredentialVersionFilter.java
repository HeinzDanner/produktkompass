package de.heinzdanner.produktkompass.config;

import de.heinzdanner.produktkompass.user.AppUserRepository;
import de.heinzdanner.produktkompass.user.VersionedUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;

public class CredentialVersionFilter extends OncePerRequestFilter {
    private final AppUserRepository users;

    public CredentialVersionFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof VersionedUserDetails principal
                && users.findActiveCredentialVersion(principal.getUsername())
                .filter(version -> version == principal.credentialVersion()).isEmpty()) {
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            response.setHeader("Cache-Control", "no-store");
            if (new UrlPathHelper().getPathWithinApplication(request).startsWith("/api/")) {
                response.sendError(401, "Bitte erneut anmelden.");
            } else {
                response.sendRedirect(request.getContextPath() + "/login?expired");
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
