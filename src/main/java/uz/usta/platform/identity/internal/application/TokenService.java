package uz.usta.platform.identity.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.identity.internal.domain.RefreshToken;
import uz.usta.platform.identity.internal.domain.User;
import uz.usta.platform.identity.internal.persistence.RefreshTokenRepository;
import uz.usta.platform.shared.security.AccessToken;
import uz.usta.platform.shared.security.AccessTokenIssuer;
import uz.usta.platform.shared.web.ClientInfo;

/** Access + refresh token juftligini chiqaradi. Refresh token — 256-bit tasodifiy, DB'da SHA-256 hash. */
@Service
class TokenService {

    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenRepository refreshTokens;
    private final IdentityProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    TokenService(AccessTokenIssuer accessTokens, RefreshTokenRepository refreshTokens, IdentityProperties props,
                 Clock clock) {
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.props = props;
        this.clock = clock;
    }

    record IssuedRefresh(UUID id, String raw, Instant expiresAt) {
    }

    AccessToken access(User user) {
        return accessTokens.issue(user.getId(), user.getPhone(), user.getRoles(), user.getPermissions(),
                user.getAuthzVersion());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    IssuedRefresh refresh(User user, UUID familyId, ClientInfo client) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now(clock).plus(props.refreshTokenTtl());
        RefreshToken token = refreshTokens.save(RefreshToken.create(user.getId(), familyId, sha256(raw), expiresAt,
                client.ip(), client.userAgent()));
        return new IssuedRefresh(token.getId(), raw, expiresAt);
    }

    static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
