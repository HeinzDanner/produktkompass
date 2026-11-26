package de.heinzdanner.produktkompass.user;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@Controller
public class AccountRecoveryController {
    private static final Logger LOG = LoggerFactory.getLogger(AccountRecoveryController.class);
    private final AccountRecoveryService recovery;
    private final AppUserRepository users;
    private final ThreadPoolTaskExecutor executor;

    public AccountRecoveryController(AccountRecoveryService recovery, AppUserRepository users,
                                     @Qualifier("recoveryExecutor") ThreadPoolTaskExecutor executor) {
        this.recovery = recovery;
        this.users = users;
        this.executor = executor;
    }

    @GetMapping("/account/security")
    public String account(Principal principal, Model model) {
        var user = users.findByUsername(principal.getName()).orElseThrow();
        model.addAttribute("email", user.getRecoveryEmail());
        model.addAttribute("verified", user.isRecoveryEmailVerified());
        model.addAttribute("emailRequest", new EmailRequest("", ""));
        return "account-security";
    }

    @PostMapping("/account/security/email")
    public String email(Principal principal, @Valid @ModelAttribute("emailRequest") EmailRequest request,
                        BindingResult errors, Model model) {
        if (errors.hasErrors()) {
            var user = users.findByUsername(principal.getName()).orElseThrow();
            model.addAttribute("email", user.getRecoveryEmail());
            model.addAttribute("verified", user.isRecoveryEmailVerified());
            return "account-security";
        }
        recovery.requestVerification(principal.getName(), request.password(), request.email());
        return "redirect:/account/security?sent";
    }

    @GetMapping("/forgot-password")
    public String forgot(Model model) {
        model.addAttribute("forgotRequest", new ForgotRequest(""));
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgot(@Valid @ModelAttribute("forgotRequest") ForgotRequest request, BindingResult errors) {
        if (errors.hasErrors()) return "forgot-password";
        try {
            // Queue every valid request, including unknown accounts, so SMTP latency cannot reveal account existence.
            executor.execute(() -> {
                try {
                    recovery.requestPasswordReset(request.username());
                } catch (RecoveryMailService.DeliveryException exception) {
                    LOG.error("Passwort-Recovery: E-Mail-Zustellung fehlgeschlagen.");
                } catch (RuntimeException exception) {
                    LOG.error("Passwort-Recovery: Verarbeitung fehlgeschlagen ({}).",
                            exception.getClass().getSimpleName());
                }
            });
        } catch (TaskRejectedException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Wiederherstellung ist derzeit ausgelastet. Bitte später erneut versuchen.");
        }
        return "redirect:/forgot-password?submitted";
    }

    @GetMapping("/verify-email")
    public String verify(@RequestParam(defaultValue = "") String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("verification", true);
        return "recovery-token";
    }

    @PostMapping("/verify-email")
    public String verify(@RequestParam String token) {
        recovery.verifyEmail(token);
        return "redirect:/login?emailVerified";
    }

    @GetMapping("/reset-password")
    public String reset(@RequestParam(defaultValue = "") String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("verification", false);
        return "recovery-token";
    }

    @PostMapping("/reset-password")
    public String reset(@RequestParam String token, @RequestParam String password,
                        @RequestParam String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Passwörter stimmen nicht überein.");
        }
        recovery.resetPassword(token, password);
        return "redirect:/login?passwordReset";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String invalid(ResponseStatusException exception, Model model, HttpServletResponse response) {
        response.setStatus(exception.getStatusCode().value());
        if (exception.getStatusCode().value() == 429) response.setHeader("Retry-After", "60");
        model.addAttribute("recoveryError", exception.getReason());
        return "recovery-error";
    }

    @ExceptionHandler(RecoveryMailService.DeliveryException.class)
    public String delivery(Model model, HttpServletResponse response) {
        LOG.error("Konto-Recovery: E-Mail-Zustellung fehlgeschlagen.");
        response.setStatus(503);
        model.addAttribute("recoveryError", "E-Mail-Versand ist derzeit nicht verfügbar. Bitte später erneut versuchen.");
        return "recovery-error";
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 72) String password) {}
    public record ForgotRequest(@NotBlank @Size(max = 50) String username) {}
}
