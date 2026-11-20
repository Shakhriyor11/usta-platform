package uz.usta.platform.shared.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import uz.usta.platform.shared.util.Uuids;

/**
 * Access token (JWT, HS256) chiqaradi. Tekshirish {@link JwtSecurityConfig} dagi decoder orqali —
 * ikkalasi ham shu paketda bo'lgani uchun claim nomlari bir joyda.
 */
@Component
public class AccessTokenIssuer {

    static final String CLAIM_PHONE = "phone";
    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_TYPE = "typ";
    static final String TYPE_ACCESS = "access";

    private final JwtEncoder encoder;
    private final JwtProperties props;
    private final Clock clock;

    AccessTokenIssuer(JwtEncoder encoder, JwtProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public AccessToken issue(UUID userId, String phone, Collection<Role> roles) {
        Instant now = Instant.now(clock);
        Instant exp = now.plus(props.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(Uuids.v7().toString())
                .issuer(props.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(exp)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_PHONE, phone)
                .claim(CLAIM_ROLES, roles.stream().map(Role::name).sorted().toList())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AccessToken(token, exp);
    }
}
