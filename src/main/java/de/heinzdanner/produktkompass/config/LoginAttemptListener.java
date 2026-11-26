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
 * <p>Fehler für unbekannte Konten werden nicht in der Benutzertabelle gespeichert.
 * Das separate IP-Rate-Limit begrenzt auch solche Anmeldeversuche.</p>
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
