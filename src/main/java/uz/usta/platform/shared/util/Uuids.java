package uz.usta.platform.shared.util;

import java.security.SecureRandom;
import java.util.UUID;

/** RFC 9562 UUIDv7 generatori (vaqt bo'yicha tartiblanadi — B-tree indekslar uchun qulay). */
public final class Uuids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuids() {
    }

    public static UUID v7() {
        long millis = System.currentTimeMillis();
        byte[] rnd = new byte[10];
        RANDOM.nextBytes(rnd);

        long msb = (millis & 0xFFFF_FFFF_FFFFL) << 16;
        msb |= 0x7000L;                                   // version 7
        msb |= ((rnd[0] & 0x0FL) << 8) | (rnd[1] & 0xFFL); // rand_a (12 bit)

        long lsb = 0;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (rnd[i] & 0xFFL);
        }
        lsb = (lsb & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L; // variant 10
        return new UUID(msb, lsb);
    }
}
