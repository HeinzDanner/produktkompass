package de.heinzdanner.produktkompass.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Stellt Login-Status, Benutzername und Admin-Berechtigung für die Navigation
 * in allen Thymeleaf-Seiten bereit, ohne dass jeder Controller das selbst
 * ausrechnen muss.
 */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
public class NavigationModelAdvice {

    @ModelAttribute
    public void addNavigationAttributes(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !isAnonymous(authentication);

        model.addAttribute("navAuthenticated", authenticated);
        model.addAttribute("navUsername", authenticated ? authentication.getName() : null);
        model.addAttribute("navIsAdmin", authenticated && hasAnyRole(authentication, "ROLE_ADMIN", "ROLE_EDITOR"));
        model.addAttribute("navIsAdministrator", authenticated && hasAnyRole(authentication, "ROLE_ADMIN"));
    }

    private boolean isAnonymous(Authentication authentication) {
        return "anonymousUser".equals(authentication.getPrincipal());
    }

    private boolean hasAnyRole(Authentication authentication, String... roles) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            for (String role : roles) {
                if (role.equals(authority.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }
}
