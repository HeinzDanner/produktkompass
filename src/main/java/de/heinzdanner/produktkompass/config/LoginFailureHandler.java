package de.heinzdanner.produktkompass.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;

/**
 * Unterscheidet auf der Login-Seite zwischen "Benutzername/Passwort falsch"
 * und "Konto vorübergehend gesperrt", damit Benutzer verstehen, warum ein
 * eigentlich korrektes Passwort gerade abgelehnt wird.
 */
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException, ServletException {
        setDefaultFailureUrl(exception instanceof LockedException ? "/login?locked" : "/login?error");
        super.onAuthenticationFailure(request, response, exception);
    }
}
