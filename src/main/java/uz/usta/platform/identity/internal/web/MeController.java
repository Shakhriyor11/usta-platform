package uz.usta.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.identity.internal.application.AuthService;
import uz.usta.platform.identity.internal.web.IdentityDtos.AccessTokenResponse;
import uz.usta.platform.identity.internal.web.IdentityDtos.SelectRoleRequest;
import uz.usta.platform.identity.internal.web.IdentityDtos.UserResponse;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Me", description = "Joriy foydalanuvchi")
@RestController
@RequestMapping(ApiPaths.V1 + "/me")
class MeController {

    private final AuthService auth;

    MeController(AuthService auth) {
        this.auth = auth;
    }

    @Operation(summary = "Joriy foydalanuvchi ma'lumotlari")
    @GetMapping
    UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return UserResponse.of(auth.getUser(user.id()));
    }

    @Operation(summary = "Rol tanlash (CUSTOMER yoki MASTER)",
            description = "Yangi rollar bilan access token qaytaradi — mijoz uni ishlatishi kerak. Refresh token o'zgarmaydi.")
    @PostMapping("/roles")
    AccessTokenResponse selectRole(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody SelectRoleRequest body) {
        AuthService.AccessTokenResult r = auth.selectRole(user.id(), body.role());
        return AccessTokenResponse.of(r.accessToken(), r.user());
    }
}
