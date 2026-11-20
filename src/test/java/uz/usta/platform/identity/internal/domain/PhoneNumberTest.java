package uz.usta.platform.identity.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uz.usta.platform.shared.error.BusinessException;

class PhoneNumberTest {

    @ParameterizedTest
    @ValueSource(strings = {"+998901234567", "998901234567", "901234567", " +998 (90) 123-45-67 ", "90 123 45 67"})
    void normalizesUzbekNumbers(String input) {
        assertThat(PhoneNumber.normalize(input)).isEqualTo("+998901234567");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "12345", "+79991234567", "+9989012345678", "+99890123456a", "8901234567"})
    void rejectsInvalidNumbers(String input) {
        assertThatThrownBy(() -> PhoneNumber.normalize(input))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(IdentityErrorCode.INVALID_PHONE_NUMBER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"+998901234567"})
    void masksForLogs(String phone) {
        assertThat(PhoneNumber.mask(phone)).isEqualTo("+99890***4567");
    }
}
