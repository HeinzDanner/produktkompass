package de.heinzdanner.produktkompass.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public record VersionedUserDetails(UserDetails delegate, long credentialVersion)
        implements UserDetails, CredentialsContainer {
    @Override public void eraseCredentials() {
        if (delegate instanceof CredentialsContainer credentials) credentials.eraseCredentials();
    }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return delegate.getAuthorities(); }
    @Override public String getPassword() { return delegate.getPassword(); }
    @Override public String getUsername() { return delegate.getUsername(); }
    @Override public boolean isAccountNonExpired() { return delegate.isAccountNonExpired(); }
    @Override public boolean isAccountNonLocked() { return delegate.isAccountNonLocked(); }
    @Override public boolean isCredentialsNonExpired() { return delegate.isCredentialsNonExpired(); }
    @Override public boolean isEnabled() { return delegate.isEnabled(); }
}
