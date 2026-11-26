package uz.usta.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.identity.internal.application.AuthService;
import uz.usta.platform.identity.internal.application.OtpService;
import uz.usta.platform.identity.internal.web.IdentityDtos.LogoutRequest;
import uz.usta.platform.identity.internal.web.IdentityDtos.OtpChallengeResponse;
import uz.usta.platform.identity.internal.web.IdentityDtos.OtpRequest;
import uz.usta.platform.identity.internal.web.IdentityDtos.OtpVerifyRequest;
import uz.usta.platform.identity.internal.web.IdentityDtos.RefreshRequest;
import uz.usta.platform.identity.internal.web.IdentityDtos.TokenResponse;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.ClientInfo;

@Tag(name = "Auth", description = "Telefon + SMS OTP orqali kirish, token yangilash")
@SecurityRequirements
@RestController
@RequestMapping(ApiPaths.AUTH)
class AuthController {

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @Operation(summary = "SMS orqali tasdiqlash kodini yuborish",
            description = "Qayta yuborish cooldown'i va raqam/IP bo'yicha limitlar bor (429 + Retry-After).")
    @PostMapping("/otp/request")
    OtpChallengeResponse requestOtp(@Valid @RequestBody OtpRequest body, HttpServletRequest request) {
        OtpService.Challenge c = auth.requestOtp(body.phone(), ClientInfo.from(request));
        return new OtpChallengeResponse(c.expiresInSeconds(), c.resendAfterSeconds(), c.codeLength());
    }

    @Operation(summary = "Kodni tasdiqlash va token olish",
            description = "Foydalanuvchi bo'lmasa — yaratiladi (newUser=true). Keyin rol tanlanadi: POST /api/v1/me/roles")
    @PostMapping("/otp/verify")
    TokenResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest body, HttpServletRequest request) {
        return TokenResponse.of(auth.verifyOtp(body.phone(), body.code(), ClientInfo.from(request)));
    }

    @Operation(summary = "Access tokenni yangilash (refresh token rotatsiyasi)",
            description = "Har chaqiruvda yangi refresh token qaytadi, eskisi bekor bo'ladi. Eski token qayta "
                    + "ishlatilsa — shu qurilmaning barcha sessiyalari bekor qilinadi.")
    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
        return TokenResponse.of(auth.refresh(body.refreshToken(), ClientInfo.from(request)));
    }

    @Operation(summary = "Chiqish (joriy sessiyani bekor qilish)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody LogoutRequest body) {
        auth.logout(body.refreshToken(), body.pushToken());
    }
}
