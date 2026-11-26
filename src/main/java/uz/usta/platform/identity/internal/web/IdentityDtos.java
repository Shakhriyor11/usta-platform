package uz.usta.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import uz.usta.platform.identity.internal.application.AuthResult;
import uz.usta.platform.identity.internal.domain.User;
import uz.usta.platform.identity.internal.domain.UserStatus;
import uz.usta.platform.shared.security.AccessToken;
import uz.usta.platform.shared.security.AdminPermission;
import uz.usta.platform.shared.security.Role;
import uz.usta.platform.shared.validation.PlainText;

final class IdentityDtos {

    private IdentityDtos() {
    }

    record OtpRequest(
            @Schema(example = "+998901234567", description = "Turli formatlar qabul qilinadi: 901234567, 998901234567")
            @NotBlank @Size(max = 25) String phone) {
    }

    record OtpChallengeResponse(long expiresInSeconds, long resendAfterSeconds, int codeLength) {
    }

    record OtpVerifyRequest(
            @Schema(example = "+998901234567") @NotBlank @Size(max = 25) String phone,
            @Schema(example = "12345") @NotBlank @Pattern(regexp = "\\d{4,8}") String code) {
    }

    record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
    }

    record LogoutRequest(@NotBlank @Size(max = 200) String refreshToken,
                         @Schema(description = "Ixtiyoriy: shu qurilmaning push tokeni — o'chiriladi")
                         @Size(max = 512) String pushToken) {
    }

    record SelectRoleRequest(@Schema(allowableValues = {"CUSTOMER", "MASTER"}) @NotNull Role role) {
    }

    record BlockUserRequest(@NotBlank @Size(max = 500) @PlainText String reason) {
    }

    record AppointAdminRequest(
            @Schema(example = "+998901234567") @NotBlank @Size(max = 25) String phone,
            @Schema(description = "DELETE — o'chirish uchun qo'shimcha huquq, bo'lim huquqi bilan birga beriladi",
                    example = "[\"CATALOG\", \"DELETE\"]")
            @NotEmpty Set<@NotNull AdminPermission> permissions) {
    }

    record PermissionsRequest(@NotEmpty Set<@NotNull AdminPermission> permissions) {
    }

    record PermissionResponse(AdminPermission code, String name) {
    }

    record AdminResponse(UUID id, String phone, boolean superAdmin, Set<AdminPermission> permissions,
                         UserStatus status, Instant lastLoginAt, Instant createdAt) {
        static AdminResponse of(User u) {
            return new AdminResponse(u.getId(), u.getPhone(), u.isSuperAdmin(), u.getPermissions(), u.getStatus(),
                    u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    @Schema(description = "permissions — admin huquqlari (admin bo'lmaganda bo'sh, super adminda hammasi)")
    record UserResponse(UUID id, String phone, Set<Role> roles, Set<AdminPermission> permissions, UserStatus status,
                        Instant createdAt) {
        static UserResponse of(User u) {
            return new UserResponse(u.getId(), u.getPhone(), u.getRoles(), u.getPermissions(), u.getStatus(),
                    u.getCreatedAt());
        }
    }

    record AdminUserResponse(UUID id, String phone, Set<Role> roles, UserStatus status, String blockedReason,
                             Instant lastLoginAt, Instant createdAt) {
        static AdminUserResponse of(User u) {
            return new AdminUserResponse(u.getId(), u.getPhone(), u.getRoles(), u.getStatus(), u.getBlockedReason(),
                    u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    @Schema(description = "roles bo'sh bo'lsa — foydalanuvchi hali rol tanlamagan (POST /api/v1/me/roles)")
    record TokenResponse(String tokenType, String accessToken, Instant accessTokenExpiresAt, String refreshToken,
                         Instant refreshTokenExpiresAt, boolean newUser, UserResponse user) {
        static TokenResponse of(AuthResult r) {
            return new TokenResponse("Bearer", r.accessToken().value(), r.accessToken().expiresAt(), r.refreshToken(),
                    r.refreshTokenExpiresAt(), r.newUser(), UserResponse.of(r.user()));
        }
    }

    record AccessTokenResponse(String tokenType, String accessToken, Instant accessTokenExpiresAt, UserResponse user) {
        static AccessTokenResponse of(AccessToken token, User user) {
            return new AccessTokenResponse("Bearer", token.value(), token.expiresAt(), UserResponse.of(user));
        }
    }
}
