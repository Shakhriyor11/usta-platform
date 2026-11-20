package uz.usta.platform.identity.internal.application;

import java.time.Instant;
import uz.usta.platform.identity.internal.domain.User;
import uz.usta.platform.shared.security.AccessToken;

/** Kirish/yangilash natijasi. {@code refreshToken} — faqat shu javobda ochiq ko'rinishda qaytadi. */
public record AuthResult(AccessToken accessToken, String refreshToken, Instant refreshTokenExpiresAt, User user,
                         boolean newUser) {
}
