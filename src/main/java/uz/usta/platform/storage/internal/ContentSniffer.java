package uz.usta.platform.storage.internal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import uz.usta.platform.storage.FilePurpose.MediaTypes;

/** Faylning haqiqiy turini birinchi baytlaridan aniqlaydi (mijoz yuborgan Content-Type'ga ishonilmaydi). */
final class ContentSniffer {

    static final int HEADER_SIZE = 16;

    private ContentSniffer() {
    }

    static Optional<String> detect(byte[] h) {
        if (startsWith(h, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(MediaTypes.JPEG);
        }
        if (startsWith(h, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(MediaTypes.PNG);
        }
        if (ascii(h, 0, "RIFF") && ascii(h, 8, "WEBP")) {
            return Optional.of(MediaTypes.WEBP);
        }
        if (ascii(h, 0, "%PDF-")) {
            return Optional.of(MediaTypes.PDF);
        }
        if (ascii(h, 4, "ftyp")) {
            return Optional.of(MediaTypes.MP4);
        }
        return Optional.empty();
    }

    static String extension(String mediaType) {
        return switch (mediaType) {
            case MediaTypes.JPEG -> "jpg";
            case MediaTypes.PNG -> "png";
            case MediaTypes.WEBP -> "webp";
            case MediaTypes.PDF -> "pdf";
            case MediaTypes.MP4 -> "mp4";
            default -> "bin";
        };
    }

    private static boolean startsWith(byte[] h, int offset, int... sig) {
        if (h.length < offset + sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if ((h[offset + i] & 0xFF) != sig[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] h, int offset, String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        return h.length >= offset + b.length && Arrays.equals(h, offset, offset + b.length, b, 0, b.length);
    }
}
