package de.heinzdanner.produktkompass.web;

import de.heinzdanner.produktkompass.usertag.UserTagService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verwaltung persönlicher Tags: Anlegen/Löschen eigener Tags sowie Zuordnung
 * zu Produkten und Marken. Alle Aktionen erfordern einen angemeldeten
 * Benutzer; die eigentliche Besitzprüfung erfolgt in {@link UserTagService}.
 */
@Controller
public class UserTagWebController {

    private final UserTagService userTagService;

    public UserTagWebController(UserTagService userTagService) {
        this.userTagService = userTagService;
    }

    @GetMapping("/tags")
    public String listTags(Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        model.addAttribute("tags", userTagService.getTagsForUser(principal.getName()));
        model.addAttribute("maxTags", userTagService.getMaxTagsPerUser());
        return "tags";
    }

    @PostMapping("/tags")
    public String createTag(@RequestParam String name, Principal principal,
                           RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        try {
            userTagService.createTag(principal.getName(), name);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("tagError", ex.getMessage());
        }
        return "redirect:/tags";
    }

    @PostMapping("/tags/{id}/delete")
    public String deleteTag(@PathVariable Long id, Principal principal,
                           RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        try {
            userTagService.deleteTag(principal.getName(), id);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("tagError", ex.getMessage());
        }
        return "redirect:/tags";
    }

    @PostMapping("/tags/products/{productId}")
    public String updateProductTags(@PathVariable Long productId,
                                   @RequestParam(name = "tagIds", required = false) List<Long> tagIds,
                                   Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        Set<Long> selectedTagIds = tagIds == null ? Set.of() : new HashSet<>(tagIds);
        userTagService.syncProductTags(principal.getName(), productId, selectedTagIds);
        return "redirect:/products/" + productId;
    }

    @PostMapping("/tags/brands/{brandId}")
    public String updateBrandTags(@PathVariable Long brandId,
                                 @RequestParam(name = "tagIds", required = false) List<Long> tagIds,
                                 Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        Set<Long> selectedTagIds = tagIds == null ? Set.of() : new HashSet<>(tagIds);
        userTagService.syncBrandTags(principal.getName(), brandId, selectedTagIds);
        return "redirect:/brands/" + brandId;
    }
}
