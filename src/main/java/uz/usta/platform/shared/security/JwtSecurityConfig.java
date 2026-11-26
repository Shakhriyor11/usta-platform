package uz.usta.platform.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** JWT encoder/decoder va token -> {@link AuthenticatedUser} konvertori. */
@Configuration(proxyBeanMethods = false)
class JwtSecurityConfig {

    @Bean
    JwtEncoder jwtEncoder(JwtProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> accessOnly = jwt -> AccessTokenIssuer.TYPE_ACCESS.equals(
                jwt.getClaimAsString(AccessTokenIssuer.CLAIM_TYPE))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not an access token", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.issuer()), accessOnly));
        return decoder;
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            Set<Role> roles = EnumSet.noneOf(Role.class);
            claimList(jwt, AccessTokenIssuer.CLAIM_ROLES).forEach(r -> roles.add(Role.valueOf(r)));
            Set<AdminPermission> permissions = EnumSet.noneOf(AdminPermission.class);
            if (roles.contains(Role.SUPER_ADMIN)) {
                roles.add(Role.ADMIN);
                permissions.addAll(EnumSet.allOf(AdminPermission.class));
            } else if (roles.contains(Role.ADMIN)) {
                claimList(jwt, AccessTokenIssuer.CLAIM_PERMISSIONS)
                        .forEach(p -> permissions.add(AdminPermission.valueOf(p)));
            }
            Number version = jwt.getClaim(AccessTokenIssuer.CLAIM_AUTHZ_VERSION);
            AuthenticatedUser user = new AuthenticatedUser(UUID.fromString(jwt.getSubject()),
                    jwt.getClaimAsString(AccessTokenIssuer.CLAIM_PHONE), roles, permissions,
                    version == null ? 0 : version.intValue());
            List<SimpleGrantedAuthority> authorities = Stream.concat(roles.stream().map(Role::authority),
                    permissions.stream().map(AdminPermission::authority)).map(SimpleGrantedAuthority::new).toList();
            return UsernamePasswordAuthenticationToken.authenticated(user, jwt, authorities);
        };
    }

    private static List<String> claimList(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? List.of() : values;
    }

    private static SecretKey secretKey(JwtProperties props) {
        return new SecretKeySpec(props.secretBytes(), "HmacSHA256");
    }
}
