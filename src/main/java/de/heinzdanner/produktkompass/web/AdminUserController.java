package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.user.AppUserService;
import de.heinzdanner.produktkompass.user.Role;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class AdminUserController {

    private final AppUserService appUserService;

    public AdminUserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @GetMapping("/admin/users")
    public String users(Model model) {
        model.addAttribute("users", appUserService.getAllUsers());
        model.addAttribute("roles", Role.values());
        return "admin-users";
    }

    @PostMapping("/admin/users/{id}/role")
    public String updateRole(@PathVariable Long id,
                             @RequestParam Role role,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        try {
            appUserService.updateRole(id, role, principal.getName());
            redirectAttributes.addFlashAttribute("adminSuccess", "Benutzerrolle wurde aktualisiert.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("adminError", exception.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{id}/unlock")
    public String unlockUser(@PathVariable Long id,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        try {
            appUserService.unlockUser(id, principal.getName());
            redirectAttributes.addFlashAttribute("adminSuccess", "Benutzerkonto wurde entsperrt.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("adminError", exception.getMessage());
        }
        return "redirect:/admin/users";
    }
}
