package uz.usta.platform.shared.ops;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

/** Prod'da autentifikatsiyani chetlab o'tishga imkon beradigan sozlamalar bilan ilova ishga tushmaydi. */
class ProductionReadinessCheckTest {

    private static final String RANDOM_SECRET = "p3Qy7v0m2c8Kf1LxZr5Tn9WbHd4Js6AgUe0RiOtPq2s";

    @ParameterizedTest
    @ValueSource(strings = {"change-me-jwt-secret-at-least-32-bytes-long",
            "insecure-local-compose-secret-change-me-0123456789",
            "local-dev-only-secret-change-me-0123456789abcdef"})
    void rejectsPlaceholderJwtSecret(String secret) {
        MockEnvironment env = new MockEnvironment().withProperty("app.security.jwt.secret", secret);
        assertThatThrownBy(() -> ProductionReadinessCheck.rejectUnsafe(env))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsFixedOtpCode() {
        MockEnvironment env = new MockEnvironment().withProperty("app.security.jwt.secret", RANDOM_SECRET)
                .withProperty("app.identity.otp.fixed-code", "11111");
        assertThatThrownBy(() -> ProductionReadinessCheck.rejectUnsafe(env))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OTP_FIXED_CODE");
    }

    @Test
    void acceptsRandomSecretWithoutFixedCode() {
        MockEnvironment env = new MockEnvironment().withProperty("app.security.jwt.secret", RANDOM_SECRET)
                .withProperty("app.identity.otp.fixed-code", "");
        assertThatCode(() -> ProductionReadinessCheck.rejectUnsafe(env)).doesNotThrowAnyException();
    }
}
