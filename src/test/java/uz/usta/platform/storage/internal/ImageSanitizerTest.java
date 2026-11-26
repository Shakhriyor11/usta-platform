package uz.usta.platform.storage.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import uz.usta.platform.storage.FilePurpose.MediaTypes;

class ImageSanitizerTest {

    private static final String SECRET = "GPS-41.311081,69.240562";

    @Test
    void jpegLosesLocationCommentAndTrailingDataButKeepsOrientation() throws IOException {
        byte[] plain = encode("jpg", 40, 30);
        ByteArrayOutputStream dirty = new ByteArrayOutputStream();
        dirty.write(plain, 0, 2);
        dirty.writeBytes(segment(0xE1, exif(6)));
        dirty.writeBytes(segment(0xE1, bytes("http://ns.adobe.com/xap/1.0/\0" + SECRET)));
        dirty.writeBytes(segment(0xED, bytes("Photoshop 3.0\0" + SECRET)));
        dirty.writeBytes(segment(0xFE, bytes(SECRET)));
        dirty.write(plain, 2, plain.length - 2);
        dirty.writeBytes(bytes(SECRET)); // EOI dan keyin yopishtirilgan mazmun

        ImageSanitizer.Sanitized result = ImageSanitizer.sanitize(MediaTypes.JPEG, dirty.toByteArray()).orElseThrow();

        assertThat(result.width()).isEqualTo(40);
        assertThat(result.height()).isEqualTo(30);
        assertThat(text(result.data())).doesNotContain("GPS-").contains("Exif");
        // faqat burilish belgisi qolgan: TIFF IFD'da bitta yozuv (0x0112 = 6)
        assertThat(text(result.data())).contains(text(new byte[] {0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6}));
        assertThat(result.data()).endsWith((byte) 0xFF, (byte) 0xD9);
        assertReadable(result.data(), 40, 30);
    }

    @Test
    void jpegWithoutExifStaysDecodable() throws IOException {
        ImageSanitizer.Sanitized result =
                ImageSanitizer.sanitize(MediaTypes.JPEG, encode("jpg", 17, 9)).orElseThrow();
        assertThat(text(result.data())).doesNotContain("Exif");
        assertReadable(result.data(), 17, 9);
    }

    @Test
    void pngLosesTextChunksAndTrailingData() throws IOException {
        byte[] plain = encode("png", 20, 10);
        ByteArrayOutputStream dirty = new ByteArrayOutputStream();
        int afterHeader = 8 + 25; // imzo + IHDR
        dirty.write(plain, 0, afterHeader);
        dirty.writeBytes(pngChunk("tEXt", bytes("Comment\0" + SECRET)));
        dirty.writeBytes(pngChunk("eXIf", bytes(SECRET)));
        dirty.write(plain, afterHeader, plain.length - afterHeader);
        dirty.writeBytes(bytes("<script>" + SECRET));

        ImageSanitizer.Sanitized result = ImageSanitizer.sanitize(MediaTypes.PNG, dirty.toByteArray()).orElseThrow();

        assertThat(result.data()).isEqualTo(plain);
        assertThat(result.width()).isEqualTo(20);
        assertThat(result.height()).isEqualTo(10);
    }

    @Test
    void webpLosesExifAndClearsItsFlag() {
        byte[] vp8x = riffChunk("VP8X", new byte[] {0x0C, 0, 0, 0, 99, 0, 0, 49, 0, 0});
        byte[] vp8 = riffChunk("VP8 ", new byte[] {0, 0, 0, (byte) 0x9D, 1, 0x2A, 100, 0, 50, 0});
        byte[] webp = riff(vp8x, vp8, riffChunk("EXIF", bytes(SECRET)), riffChunk("XMP ", bytes(SECRET)));

        ImageSanitizer.Sanitized result = ImageSanitizer.sanitize(MediaTypes.WEBP, webp).orElseThrow();

        assertThat(result.width()).isEqualTo(100);
        assertThat(result.height()).isEqualTo(50);
        assertThat(text(result.data())).doesNotContain("GPS-", "EXIF", "XMP ");
        assertThat(result.data()[20]).isZero(); // VP8X bayroqlari
        assertThat(ByteBuffer.wrap(result.data(), 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt())
                .isEqualTo(result.data().length - 8);
    }

    @Test
    void brokenOrDisguisedFilesAreRejected() throws IOException {
        byte[] png = encode("png", 8, 8);
        byte[] jpeg = encode("jpg", 8, 8);
        assertThat(ImageSanitizer.sanitize(MediaTypes.PNG, Arrays.copyOf(png, png.length - 20))).isEmpty();
        assertThat(ImageSanitizer.sanitize(MediaTypes.PNG, jpeg)).isEmpty();
        assertThat(ImageSanitizer.sanitize(MediaTypes.JPEG, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                .isEmpty();
        assertThat(ImageSanitizer.sanitize(MediaTypes.JPEG, Arrays.copyOf(jpeg, 20))).isEmpty();
        assertThat(ImageSanitizer.sanitize(MediaTypes.WEBP, bytes("RIFF\0\0\0\0WEBPxxxxxxxx"))).isEmpty();
    }

    /** Tasodifiy buzilgan baytlar faqat "rad etildi" bo'lishi mumkin — exception yoki cheksiz sikl emas. */
    @Test
    void randomCorruptionNeverThrows() throws IOException {
        var random = new java.util.Random(42);
        for (String[] kind : new String[][] {{"jpg", MediaTypes.JPEG}, {"png", MediaTypes.PNG}}) {
            byte[] source = encode(kind[0], 32, 32);
            for (int i = 0; i < 5_000; i++) {
                byte[] broken = Arrays.copyOf(source, 1 + random.nextInt(source.length));
                for (int j = 0; j < 4; j++) {
                    broken[random.nextInt(broken.length)] = (byte) random.nextInt();
                }
                ImageSanitizer.sanitize(kind[1], broken);
                ImageSanitizer.sanitize(MediaTypes.WEBP, broken);
            }
        }
    }

    // ---------------------------------------------------------------- yordamchilar

    private static byte[] encode(String format, int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    private static void assertReadable(byte[] data, int width, int height) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        assertThat(image.getWidth()).isEqualTo(width);
        assertThat(image.getHeight()).isEqualTo(height);
    }

    /** Orientation va GPS matni bor kichik EXIF (little-endian TIFF). */
    private static byte[] exif(int orientation) {
        ByteBuffer b = ByteBuffer.allocate(6 + 8 + 2 + 12 + 4 + SECRET.length()).order(ByteOrder.LITTLE_ENDIAN);
        b.put(bytes("Exif\0\0")).put(bytes("II")).putShort((short) 42).putInt(8);
        b.putShort((short) 1).putShort((short) 0x0112).putShort((short) 3).putInt(1).putInt(orientation);
        b.putInt(0).put(bytes(SECRET));
        return b.array();
    }

    private static byte[] segment(int marker, byte[] payload) {
        return ByteBuffer.allocate(payload.length + 4).put((byte) 0xFF).put((byte) marker)
                .putShort((short) (payload.length + 2)).put(payload).array();
    }

    private static byte[] pngChunk(String type, byte[] data) {
        ByteBuffer b = ByteBuffer.allocate(data.length + 12).putInt(data.length).put(bytes(type)).put(data);
        CRC32 crc = new CRC32();
        crc.update(b.array(), 4, data.length + 4);
        return b.putInt((int) crc.getValue()).array();
    }

    private static byte[] riffChunk(String type, byte[] data) {
        return ByteBuffer.allocate(8 + data.length + (data.length & 1)).order(ByteOrder.LITTLE_ENDIAN)
                .put(bytes(type)).putInt(data.length).put(data).array();
    }

    private static byte[] riff(byte[]... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(bytes("WEBP"));
        for (byte[] c : chunks) {
            body.writeBytes(c);
        }
        return ByteBuffer.allocate(8 + body.size()).order(ByteOrder.LITTLE_ENDIAN).put(bytes("RIFF"))
                .putInt(body.size()).put(body.toByteArray()).array();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String text(byte[] data) {
        return new String(data, StandardCharsets.ISO_8859_1);
    }
}
