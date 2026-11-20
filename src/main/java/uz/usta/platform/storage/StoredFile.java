package uz.usta.platform.storage;

/** Saqlangan fayl: {@code key} — bucket ichidagi kalit (DB'da shu saqlanadi, URL emas). */
public record StoredFile(String key, String contentType, long sizeBytes) {
}
