package uz.usta.platform.shared.security;

import java.util.Set;
import java.util.UUID;

/**
 * Joriy foydalanuvchi (access token'dan). Controller'larda:
 * {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(UUID id, String phone, Set<Role> roles) {

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
