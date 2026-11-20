package uz.usta.platform.search.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.search.internal.persistence.SearchIndexRepository;

/**
 * Qidiruv natijalari keshi: {@code search:v{versiya}:{sha256(so'rov)}}. Indeks o'zgarganda versiya
 * ({@code search:ver}) oshiriladi — eski kalitlar o'qilmay qoladi va TTL bilan o'chadi (KEYS/SCAN kerak emas).
 * Redis ishlamasa — kesh chetlab o'tiladi, qidiruv DB'dan ishlaydi.
 */
@Component
class SearchCache {

    private static final Logger log = LoggerFactory.getLogger(SearchCache.class);
    private static final String VERSION_KEY = "search:ver";

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final SearchProperties props;

    SearchCache(StringRedisTemplate redis, JsonMapper json, SearchProperties props) {
        this.redis = redis;
        this.json = json;
        this.props = props;
    }

    SearchIndexRepository.Page get(String query, Supplier<SearchIndexRepository.Page> loader) {
        String key;
        try {
            String version = Optional.ofNullable(redis.opsForValue().get(VERSION_KEY)).orElse("0");
            key = "search:v" + version + ":" + sha256(query);
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return json.readValue(cached, SearchIndexRepository.Page.class);
            }
        } catch (RuntimeException e) {
            log.warn("Search cache unavailable: {}", e.getMessage());
            return loader.get();
        }
        SearchIndexRepository.Page page = loader.get();
        try {
            redis.opsForValue().set(key, json.writeValueAsString(page), props.cacheTtl());
        } catch (RuntimeException e) {
            log.warn("Search cache write failed: {}", e.getMessage());
        }
        return page;
    }

    /** Joriy tranzaksiya commit bo'lgach keshni eskiradi (commit'dan oldin eskirsa — eski ma'lumot qayta keshlanishi mumkin). */
    void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate();
                }
            });
        } else {
            invalidate();
        }
    }

    private void invalidate() {
        try {
            redis.opsForValue().increment(VERSION_KEY);
        } catch (RuntimeException e) {
            log.warn("Search cache invalidation failed: {}", e.getMessage());
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
