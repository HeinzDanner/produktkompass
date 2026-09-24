package de.heinzdanner.produktkompass.config;

import de.heinzdanner.produktkompass.user.AppUserService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Reagiert auf Login-Ereignisse, um Brute-Force-Angriffe zu erschweren:
 * Nach {@link AppUserService#MAX_FAILED_LOGIN_ATTEMPTS} aufeinanderfolgenden
 * falschen Passwörtern wird das betroffene Konto für
 * {@link AppUserService#LOCKOUT_DURATION} gesperrt. Ein erfolgreicher Login
 * setzt den Fehlversuchszähler wieder zurück.
 *
 * <p>Es wird bewusst auf {@link AbstractAuthenticationFailureEvent} (statt nur
 * auf die konkrete Unterklasse für falsche Passwörter) gehört, damit auch
 * andere Fehlerarten (z. B. unbekannter Benutzername) mitgezählt werden –
 * so lässt sich nicht durch Ausprobieren "gültiger" gegenüber "ungültiger"
 * Benutzernamen unterscheiden.</p>
 */
@Component
public class LoginAttemptListener {

    private final AppUserService appUserService;

    public LoginAttemptListener(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        if (username != null && !username.isBlank()) {
            appUserService.registerFailedLogin(username);
        }
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        if (username != null && !username.isBlank()) {
            appUserService.registerSuccessfulLogin(username);
        }
    }
}
