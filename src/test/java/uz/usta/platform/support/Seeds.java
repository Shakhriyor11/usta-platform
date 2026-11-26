package uz.usta.platform.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** V6 seed'dagi deterministik ID'lar: PostgreSQL {@code md5(key)::uuid} bilan bir xil. */
public final class Seeds {

    public static final UUID FAUCET_INSTALL = id("svc:faucet-install");
    public static final UUID PIPE_LEAK = id("svc:pipe-leak");
    public static final UUID TOILET_INSTALL = id("svc:toilet-install");
    public static final UUID PLUMBING = id("cat:plumbing");

    /** Minimal haqiqiy 1x1 PNG — storage turi va tuzilishini tekshiradi. */
    public static final byte[] PNG = HexFormat.of().parseHex(
            "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000b49444154789c63600002000005"
                    + "00017a5eab3f0000000049454e44ae426082");

    private Seeds() {
    }

    public static UUID id(String key) {
        try {
            String hex = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(key.getBytes(StandardCharsets.UTF_8)));
            return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                    + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
