package uz.usta.platform.catalog.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Kategoriya/xizmatning ko'rinishi: rasm va ikonka. Ikonka yo tayyor to'plamdan ({@code icon} — kalit), yo admin
 * yuklagan fayl ({@code iconKey}) — bir vaqtda faqat bittasi.
 */
@Embeddable
public class CatalogMedia {

    @Column(name = "icon", length = 255)
    private String icon;

    @Column(name = "icon_key", length = 512)
    private String iconKey;

    @Column(name = "image_key", length = 512)
    private String imageKey;

    /**
     * To'plamdan ikonka tanlash. {@code null} — to'plam ikonkasi olib tashlanadi, yuklangani esa qoladi.
     *
     * @return almashtirilgan yuklangan ikonka kaliti (faylni o'chirish uchun) yoki {@code null}
     */
    public String choosePreset(String key) {
        icon = key;
        if (key == null) {
            return null;
        }
        String old = iconKey;
        iconKey = null;
        return old;
    }

    /** @return eski yuklangan ikonka kaliti (o'chirish uchun) */
    public String replaceCustomIcon(String key) {
        String old = iconKey;
        iconKey = key;
        if (key != null) {
            icon = null;
        }
        return old;
    }

    /** @return eski rasm kaliti (o'chirish uchun) */
    public String replaceImage(String key) {
        String old = imageKey;
        imageKey = key;
        return old;
    }

    /** Element o'chirilganda: barcha fayl kalitlari unutiladi. @return o'chiriladigan kalitlar */
    public List<String> clearFiles() {
        List<String> keys = new ArrayList<>();
        keys.add(replaceImage(null));
        keys.add(replaceCustomIcon(null));
        keys.removeIf(Objects::isNull);
        return keys;
    }

    public String getIcon() {
        return icon;
    }

    public String getIconKey() {
        return iconKey;
    }

    public String getImageKey() {
        return imageKey;
    }
}
