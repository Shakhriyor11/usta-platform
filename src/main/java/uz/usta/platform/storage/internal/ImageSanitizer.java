package uz.usta.platform.storage.internal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import uz.usta.platform.storage.FilePurpose.MediaTypes;

/**
 * Yuklangan rasmning tuzilishini tekshiradi, o'lchamini aniqlaydi va metama'lumotlarni olib tashlaydi: telefon
 * suratlaridagi GPS koordinatasi, qurilma ma'lumoti, izohlar hamda fayl oxiriga yopishtirilgan boshqa mazmun
 * boshqa foydalanuvchilarga yetib bormaydi. Piksellar qayta kodlanmaydi (sifat o'zgarmaydi); JPEG'da faqat
 * burilish (orientation) belgisi saqlanadi, aks holda surat yonboshlab qoladi.
 */
final class ImageSanitizer {

    record Sanitized(byte[] data, int width, int height) {
    }

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final Set<String> PNG_METADATA = Set.of("eXIf", "tEXt", "zTXt", "iTXt", "tIME");

    private ImageSanitizer() {
    }

    /** @return bo'sh — fayl buzilgan yoki ko'rsatilgan turga mos emas */
    static Optional<Sanitized> sanitize(String mediaType, byte[] data) {
        try {
            Sanitized result = switch (mediaType) {
                case MediaTypes.JPEG -> jpeg(data);
                case MediaTypes.PNG -> png(data);
                case MediaTypes.WEBP -> webp(data);
                default -> null;
            };
            return result == null || result.width() <= 0 || result.height() <= 0
                    ? Optional.empty() : Optional.of(result);
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- PNG

    private static Sanitized png(byte[] in) {
        require(in.length > 8 && Arrays.equals(in, 0, 8, PNG_SIGNATURE, 0, 8));
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, 8);
        int pos = 8;
        int width = 0;
        int height = 0;
        boolean hasData = false;
        while (true) {
            int length = intBe(in, pos);
            require(length >= 0 && length <= in.length - pos - 12);
            String type = ascii(in, pos + 4, 4);
            if (pos == 8) {
                require(type.equals("IHDR") && length == 13);
                width = intBe(in, pos + 8);
                height = intBe(in, pos + 12);
            }
            hasData |= type.equals("IDAT");
            if (!PNG_METADATA.contains(type)) {
                out.write(in, pos, length + 12);
            }
            pos += length + 12;
            if (type.equals("IEND")) {
                require(hasData);
                return new Sanitized(out.toByteArray(), width, height); // IEND dan keyingi baytlar tashlanadi
            }
        }
    }

    // ---------------------------------------------------------------- JPEG

    private static Sanitized jpeg(byte[] in) {
        require(u8(in, 0) == 0xFF && u8(in, 1) == 0xD8);
        ByteArrayOutputStream body = new ByteArrayOutputStream(in.length);
        int pos = 2;
        int width = 0;
        int height = 0;
        int orientation = 1;
        int jfifLength = 0; // APP0 bo'lsa, u birinchi bo'lib qolishi kerak
        while (pos < in.length) {
            require(u8(in, pos) == 0xFF);
            while (u8(in, pos + 1) == 0xFF) { // to'ldiruvchi baytlar
                pos++;
            }
            int marker = u8(in, pos + 1);
            if (marker == 0xD9) { // EOI — keyingi baytlar (masalan "motion photo" videosi) tashlanadi
                break;
            }
            if (marker == 0x01 || marker >= 0xD0 && marker <= 0xD7) { // uzunliksiz markerlar
                body.write(in, pos, 2);
                pos += 2;
                continue;
            }
            int length = u16(in, pos + 2);
            require(length >= 2 && pos + 2 + length <= in.length);
            int end = pos + 2 + length;
            if (marker == 0xE1) {
                orientation = exifOrientation(in, pos + 4, end).orElse(orientation);
            } else if (marker != 0xED && marker != 0xFE) { // IPTC va izoh ham tashlanadi
                if (isStartOfFrame(marker)) {
                    height = u16(in, pos + 5);
                    width = u16(in, pos + 7);
                }
                if (marker == 0xE0 && body.size() == 0) {
                    jfifLength = end - pos;
                }
                body.write(in, pos, end - pos);
            }
            pos = end;
            if (marker == 0xDA) { // skan: keyingi markergacha siqilgan ma'lumot
                int scanEnd = nextMarker(in, pos);
                body.write(in, pos, scanEnd - pos);
                pos = scanEnd;
            }
        }
        byte[] segments = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(segments.length + 40);
        out.write(0xFF);
        out.write(0xD8);
        out.write(segments, 0, jfifLength);
        if (orientation > 1 && orientation <= 8) {
            out.writeBytes(orientationOnlyExif(orientation));
        }
        out.write(segments, jfifLength, segments.length - jfifLength);
        out.write(0xFF);
        out.write(0xD9);
        return new Sanitized(out.toByteArray(), width, height);
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    /** Siqilgan ma'lumotda {@code FF 00} va {@code FF D0..D7} marker emas. */
    private static int nextMarker(byte[] in, int pos) {
        for (int i = pos; i < in.length - 1; i++) {
            if (u8(in, i) == 0xFF) {
                int next = u8(in, i + 1);
                if (next != 0x00 && !(next >= 0xD0 && next <= 0xD7)) {
                    return i;
                }
            }
        }
        return in.length;
    }

    private static Optional<Integer> exifOrientation(byte[] in, int start, int end) {
        if (end - start < 14 || !ascii(in, start, 4).equals("Exif")) {
            return Optional.empty();
        }
        int tiff = start + 6;
        boolean little = ascii(in, tiff, 2).equals("II");
        long ifd = u32(in, tiff + 4, little);
        if (ifd < 8 || ifd > end - tiff - 2) {
            return Optional.empty();
        }
        int entries = tiff + (int) ifd;
        int count = u16(in, entries, little);
        for (int i = 0; i < count; i++) {
            int entry = entries + 2 + i * 12;
            if (entry + 12 > end) {
                break;
            }
            if (u16(in, entry, little) == 0x0112) {
                return Optional.of(u16(in, entry + 8, little));
            }
        }
        return Optional.empty();
    }

    private static byte[] orientationOnlyExif(int orientation) {
        return new byte[] {(byte) 0xFF, (byte) 0xE1, 0, 34, 'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0, 42, 0, 0, 0, 8,                          // TIFF sarlavhasi, IFD0 8-baytda
                0, 1,                                                  // bitta yozuv
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
                0, 0, 0, 0};                                           // keyingi IFD yo'q
    }

    // ---------------------------------------------------------------- WebP

    private static Sanitized webp(byte[] in) {
        require(in.length >= 20 && ascii(in, 0, 4).equals("RIFF") && ascii(in, 8, 4).equals("WEBP"));
        long declared = u32(in, 4, true) + 8;
        require(declared <= in.length);
        ByteArrayOutputStream chunks = new ByteArrayOutputStream(in.length);
        int width = 0;
        int height = 0;
        boolean hasImage = false;
        int pos = 12;
        while (pos + 8 <= declared) {
            String type = ascii(in, pos, 4);
            long size = u32(in, pos + 4, true);
            require(size <= declared - pos - 8);
            int padded = (int) Math.min(size + (size & 1), declared - pos - 8);
            int data = pos + 8;
            switch (type) {
                case "VP8X" -> {
                    width = u24(in, data + 4) + 1;
                    height = u24(in, data + 7) + 1;
                }
                case "VP8 " -> {
                    hasImage = true;
                    if (width == 0) {
                        require(u8(in, data + 3) == 0x9D && u8(in, data + 4) == 0x01 && u8(in, data + 5) == 0x2A);
                        width = u16(in, data + 6, true) & 0x3FFF;
                        height = u16(in, data + 8, true) & 0x3FFF;
                    }
                }
                case "VP8L" -> {
                    hasImage = true;
                    if (width == 0) {
                        require(u8(in, data) == 0x2F);
                        long bits = u32(in, data + 1, true);
                        width = (int) (bits & 0x3FFF) + 1;
                        height = (int) (bits >> 14 & 0x3FFF) + 1;
                    }
                }
                case "ANMF" -> hasImage = true;
                default -> {
                }
            }
            if (!type.equals("EXIF") && !type.equals("XMP ")) {
                byte[] chunk = Arrays.copyOfRange(in, pos, data + padded);
                if (type.equals("VP8X")) {
                    chunk[8] &= (byte) ~0x0C; // "EXIF bor" va "XMP bor" bayroqlari o'chiriladi
                }
                chunks.writeBytes(chunk);
            }
            pos = data + padded;
        }
        require(hasImage);
        byte[] body = chunks.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.length + 12);
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        int riffSize = body.length + 4;
        out.write(riffSize);
        out.write(riffSize >>> 8);
        out.write(riffSize >>> 16);
        out.write(riffSize >>> 24);
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(body);
        return new Sanitized(out.toByteArray(), width, height);
    }

    // ---------------------------------------------------------------- baytlar

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("malformed image");
        }
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static int u16(byte[] b, int i) {
        return u8(b, i) << 8 | u8(b, i + 1);
    }

    private static int u16(byte[] b, int i, boolean little) {
        return little ? u8(b, i) | u8(b, i + 1) << 8 : u16(b, i);
    }

    private static int u24(byte[] b, int i) {
        return u8(b, i) | u8(b, i + 1) << 8 | u8(b, i + 2) << 16;
    }

    private static int intBe(byte[] b, int i) {
        return u16(b, i) << 16 | u16(b, i + 2);
    }

    private static long u32(byte[] b, int i, boolean little) {
        return little
                ? (u16(b, i, true) | (long) u16(b, i + 2, true) << 16)
                : intBe(b, i) & 0xFFFFFFFFL;
    }

    private static String ascii(byte[] b, int i, int length) {
        if (i < 0 || i + length > b.length) {
            throw new IndexOutOfBoundsException(i);
        }
        return new String(b, i, length, StandardCharsets.US_ASCII);
    }
}
