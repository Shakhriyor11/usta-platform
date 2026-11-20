package uz.usta.platform.shared.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.usta.platform.shared.web.ApiPaths;

/**
 * Swagger UI: {@code /swagger-ui.html} (lokal — http://localhost:8080, docker — https://localhost).
 * Yuqori o'ngdagi "Select a definition" — rollar bo'yicha guruhlar. Production'da {@code SWAGGER_ENABLED=false}.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "Usta Platform API",
                version = "v1",
                description = """
                        Usta chaqirish platformasi backend API.

                        **Token olish (Authorize):**
                        1. `POST /api/v1/auth/otp/request` — `{"phone":"+998901234567"}`.
                           Lokal/docker'da SMS yuborilmaydi: kod logda — `docker compose logs api | grep "SMS stub"`
                           (IntelliJ'da — konsolda).
                        2. `POST /api/v1/auth/otp/verify` — `{"phone":"...","code":"12345"}` → `accessToken`.
                        3. Birinchi kirishda rol: `POST /api/v1/me/roles` — `{"role":"CUSTOMER"}` yoki `MASTER`;
                           javobdagi **yangi** `accessToken` ni oling (rollar token ichida).
                        4. Yuqoridagi **Authorize** tugmasi → tokenni kiriting (`Bearer` so'zisiz). Token sahifa
                           yangilanganda saqlanadi, 15 daqiqa amal qiladi (`/api/v1/auth/refresh`).

                        Admin: `ADMIN_PHONES` dagi raqam bilan kiring.

                        Xatoliklar RFC 9457 (`application/problem+json`) formatida, `code` va `traceId` bilan.
                        Til — `Accept-Language` (uz — standart, ru, en). Real vaqt chat — STOMP over WebSocket `/ws`
                        ("Chat" bo'limiga qarang)."""),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    @Bean
    GroupedOpenApi allApi() {
        return group("all", "0. Hammasi", ApiPaths.V1 + "/**");
    }

    @Bean
    GroupedOpenApi publicApi() {
        return group("public", "1. Public & Auth", ApiPaths.AUTH + "/**", ApiPaths.PUBLIC + "/**", ApiPaths.V1 + "/me/**",
                ApiPaths.V1 + "/search/**", ApiPaths.V1 + "/masters/**", ApiPaths.V1 + "/notifications/**",
                ApiPaths.V1 + "/push-tokens/**", ApiPaths.V1 + "/orders/**", ApiPaths.V1 + "/complaints/**");
    }

    @Bean
    GroupedOpenApi customerApi() {
        return group("customer", "2. Mijoz (CUSTOMER)", ApiPaths.CUSTOMER + "/**");
    }

    @Bean
    GroupedOpenApi masterApi() {
        return group("master", "3. Usta (MASTER)", ApiPaths.MASTER + "/**");
    }

    @Bean
    GroupedOpenApi adminApi() {
        return group("admin", "4. Admin (ADMIN)", ApiPaths.ADMIN + "/**");
    }

    private static GroupedOpenApi group(String id, String displayName, String... paths) {
        return GroupedOpenApi.builder().group(id).displayName(displayName).pathsToMatch(paths).build();
    }
}
