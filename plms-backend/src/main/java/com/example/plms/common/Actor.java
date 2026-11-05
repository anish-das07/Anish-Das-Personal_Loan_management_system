package com.example.plms.common;

import com.example.plms.auth.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * The authenticated caller, derived only from the verified token (subject + role claims),
 * never from request payloads.
 */
public record Actor(String userId, Set<Role> roles) {

    public static Actor from(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw ApiException.unauthorized("Authentication is required.");
        }
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority a : auth.getAuthorities()) {
            String name = a.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                Role.parse(name.substring(5)).ifPresent(roles::add);
            }
        }
        return new Actor(auth.getName(), roles);
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }

    public boolean hasAny(Role... candidates) {
        return Arrays.stream(candidates).anyMatch(roles::contains);
    }

    public boolean isCustomer() {
        return roles.contains(Role.CUSTOMER);
    }

    public boolean isStaff() {
        return roles.stream().anyMatch(Role::isStaff);
    }
}
