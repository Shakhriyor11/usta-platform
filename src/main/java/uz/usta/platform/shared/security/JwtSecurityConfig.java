package uz.usta.platform.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
            List<String> claimRoles = jwt.getClaimAsStringList(AccessTokenIssuer.CLAIM_ROLES);
            Set<Role> roles = EnumSet.noneOf(Role.class);
            if (claimRoles != null) {
                for (String r : claimRoles) {
                    roles.add(Role.valueOf(r));
                }
            }
            AuthenticatedUser user = new AuthenticatedUser(UUID.fromString(jwt.getSubject()),
                    jwt.getClaimAsString(AccessTokenIssuer.CLAIM_PHONE), roles);
            return UsernamePasswordAuthenticationToken.authenticated(user, jwt,
                    roles.stream().map(r -> new SimpleGrantedAuthority(r.authority())).toList());
        };
    }

    private static SecretKey secretKey(JwtProperties props) {
        return new SecretKeySpec(props.secretBytes(), "HmacSHA256");
    }
}
