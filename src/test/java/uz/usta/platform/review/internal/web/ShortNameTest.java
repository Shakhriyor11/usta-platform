package uz.usta.platform.review.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShortNameTest {

    @Test
    void hidesSurname() {
        assertThat(ReviewController.shortName("Aziz Karimov")).isEqualTo("Aziz K.");
        assertThat(ReviewController.shortName("  Aziz   Karimov Olimovich ")).isEqualTo("Aziz K.");
        assertThat(ReviewController.shortName("Aziz")).isEqualTo("Aziz");
        assertThat(ReviewController.shortName(" ")).isNull();
        assertThat(ReviewController.shortName(null)).isNull();
    }
}
