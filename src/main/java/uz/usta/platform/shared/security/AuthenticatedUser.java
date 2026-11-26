package uz.usta.platform.shared.security;

import java.util.Set;
import java.util.UUID;

/**
 * Joriy foydalanuvchi (access token'dan). Controller'larda:
 * {@code @AuthenticationPrincipal AuthenticatedUser user}.
 *
 * @param permissions  admin huquqlari (super adminda — hammasi, admin bo'lmaganda — bo'sh)
 * @param authzVersion token chiqarilgandagi rol/huquq versiyasi
 */
public record AuthenticatedUser(UUID id, String phone, Set<Role> roles, Set<AdminPermission> permissions,
                                int authzVersion) {

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
