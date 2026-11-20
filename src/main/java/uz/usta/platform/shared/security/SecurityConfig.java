package uz.usta.platform.shared.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;
import uz.usta.platform.shared.web.ApiPaths;

/**
 * Bazaviy xavfsizlik: stateless, CSRF o'chirilgan (faqat Bearer token), CORS, ochiq yo'llar
 * ro'yxati. 401/403 javoblari ham {@code GlobalExceptionHandler} orqali yagona formatda qaytadi.
 * Autentifikatsiya: {@code Authorization: Bearer <JWT>} (oauth2-resource-server, {@link JwtSecurityConfig}).
 *
 * <p>Yo'llar bo'yicha rollar: {@code /api/v1/admin/**} — ADMIN, {@code /api/v1/master/**} — MASTER,
 * {@code /api/v1/customer/**} — CUSTOMER, qolganlari — istalgan autentifikatsiyadan o'tgan foydalanuvchi.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    static final String[] PUBLIC_PATHS = {
        ApiPaths.PUBLIC + "/**",
        ApiPaths.AUTH + "/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/**", // actuator faqat ichki management portida (8081) — tashqariga chiqarilmaydi
        "/error",
        "/ws", // WebSocket handshake; autentifikatsiya STOMP CONNECT kadrida (JWT)
    };

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http,
                                    @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
                                    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
                                    UserRevocations revocations)
            throws Exception {
        AuthenticationEntryPoint entryPoint = (req, res, ex) -> resolver.resolveException(req, res, null, ex);
        AccessDeniedHandler deniedHandler = (req, res, ex) -> resolver.resolveException(req, res, null, ex);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults()) // 'corsConfigurationSource' bean'i ishlatiladi
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(o -> o
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterAfter(new RevokedUserFilter(revocations, resolver), BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(ApiPaths.ADMIN + "/**").hasRole(Role.ADMIN.name())
                        .requestMatchers(ApiPaths.MASTER + "/**").hasRole(Role.MASTER.name())
                        .requestMatchers(ApiPaths.CUSTOMER + "/**").hasRole(Role.CUSTOMER.name())
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(props.allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language", "X-Request-Id"));
        cfg.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(props.maxAge());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
