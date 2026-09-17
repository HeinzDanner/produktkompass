package de.heinzdanner.produktkompass.user.dto;

import de.heinzdanner.produktkompass.user.AppUser;
import de.heinzdanner.produktkompass.user.Role;

public record UserResponse(Long id, String username, Role role) {
    public static UserResponse fromEntity(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}
