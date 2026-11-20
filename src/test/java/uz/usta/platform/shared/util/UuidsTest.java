package uz.usta.platform.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidsTest {

    @Test
    void generatesVersion7Rfc9562Uuids() {
        UUID id = Uuids.v7();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void timestampPrefixIsMonotonicAcrossMilliseconds() throws InterruptedException {
        UUID first = Uuids.v7();
        Thread.sleep(2);
        UUID second = Uuids.v7();
        assertThat(Long.compareUnsigned(first.getMostSignificantBits() >>> 16, second.getMostSignificantBits() >>> 16))
                .isNegative();
    }

    @Test
    void isUnique() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(Uuids.v7());
        }
        assertThat(new HashSet<>(ids)).hasSize(ids.size());
    }
}
