package com.memento3d.identity.infrastructure.security;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Getter
public class CustomUserDetails implements UserDetails, OidcUser {

    private final UUID id;
    private final String email;
    private final String password;
    private final String fullName;
    private final Role role;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Map<String, Object> attributes;
    private final User user;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    public CustomUserDetails(User user) {
        this(user, Collections.emptyMap(), null, null);
    }

    public CustomUserDetails(User user, Map<String, Object> attributes) {
        this(user, attributes, null, null);
    }

    public CustomUserDetails(User user, Map<String, Object> attributes, OidcIdToken idToken, OidcUserInfo userInfo) {
        this.user = user;
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.fullName = user.getFullName();
        this.role = user.getRole();
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()));
        this.attributes = attributes != null ? attributes : Collections.emptyMap();
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    @Override
    public Map<String, Object> getClaims() {
        return attributes;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return email != null ? email : (id != null ? id.toString() : "");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
