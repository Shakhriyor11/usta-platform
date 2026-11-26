package uz.usta.platform.storage;

import java.util.Set;

/**
 * Fayl maqsadi — ruxsat etilgan turlar, maksimal hajm va rasm o'lchami qoidalarini belgilaydi. Tur mijoz yuborgan
 * {@code Content-Type} bo'yicha emas, faylning birinchi baytlari (magic bytes) bo'yicha aniqlanadi. SVG qabul
 * qilinmaydi (ichida skript bo'lishi mumkin).
 */
public enum FilePurpose {
    AVATAR("avatars", Set.of(MediaTypes.JPEG, MediaTypes.PNG, MediaTypes.WEBP), 5L * 1024 * 1024),
    VERIFICATION_DOCUMENT("verification", Set.of(MediaTypes.JPEG, MediaTypes.PNG, MediaTypes.WEBP, MediaTypes.PDF),
            10L * 1024 * 1024),
    PORTFOLIO("portfolio", Set.of(MediaTypes.JPEG, MediaTypes.PNG, MediaTypes.WEBP), 10L * 1024 * 1024),
    ORDER_ATTACHMENT("orders", Set.of(MediaTypes.JPEG, MediaTypes.PNG, MediaTypes.WEBP, MediaTypes.MP4),
            50L * 1024 * 1024),
    CHAT_IMAGE("chat", Set.of(MediaTypes.JPEG, MediaTypes.PNG, MediaTypes.WEBP), 10L * 1024 * 1024),
    /** Kategoriya va xizmat rasmi. */
    CATALOG_IMAGE("catalog", Set.of(MediaTypes.JPEG, MediaTypes.PNG, MediaTypes.WEBP), 5L * 1024 * 1024,
            new ImageRules(200, 4096, false)),
    /** Admin yuklagan ikonka: kvadrat, shaffof fonli formatlar. */
    CATALOG_ICON("icons", Set.of(MediaTypes.PNG, MediaTypes.WEBP), 512L * 1024, new ImageRules(48, 1024, true));

    private final String folder;
    private final Set<String> allowedTypes;
    private final long maxSizeBytes;
    private final ImageRules imageRules;

    FilePurpose(String folder, Set<String> allowedTypes, long maxSizeBytes) {
        // standart chegara: har qanday zamonaviy kamera surati sig'adi, "dekompressiya bombasi" yo'q
        this(folder, allowedTypes, maxSizeBytes, new ImageRules(1, 12_000, false));
    }

    FilePurpose(String folder, Set<String> allowedTypes, long maxSizeBytes, ImageRules imageRules) {
        this.folder = folder;
        this.allowedTypes = allowedTypes;
        this.maxSizeBytes = maxSizeBytes;
        this.imageRules = imageRules;
    }

    public String folder() {
        return folder;
    }

    public Set<String> allowedTypes() {
        return allowedTypes;
    }

    public long maxSizeBytes() {
        return maxSizeBytes;
    }

    public ImageRules imageRules() {
        return imageRules;
    }

    /** Rasm tomonlari (piksel). */
    public record ImageRules(int minSide, int maxSide, boolean square) {
    }

    /** Qo'llab-quvvatlanadigan media turlari. */
    public static final class MediaTypes {
        public static final String JPEG = "image/jpeg";
        public static final String PNG = "image/png";
        public static final String WEBP = "image/webp";
        public static final String PDF = "application/pdf";
        public static final String MP4 = "video/mp4";

        private MediaTypes() {
        }
    }
}
