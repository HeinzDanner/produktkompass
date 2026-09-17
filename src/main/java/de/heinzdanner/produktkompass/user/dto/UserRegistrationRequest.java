package de.heinzdanner.produktkompass.user.dto;

public record UserRegistrationRequest(String username, String password, String role) {
}
