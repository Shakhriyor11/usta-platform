package uz.usta.platform.search.internal.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Usta online holati. Ilova har ~30 s da heartbeat yuboradi; {@code onlineTtl} ichida kelmasa — offline.
 * <ul>
 *   <li>{@code online:{masterId}} — TTL bilan kalit (bitta ustani tekshirish, MGET)</li>
 *   <li>{@code online:masters} — sorted set (score = oxirgi heartbeat, ms) — "faqat online" filtri uchun</li>
 * </ul>
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final String ZSET = "online:masters";

    private final StringRedisTemplate redis;
    private final SearchProperties props;
    private final Clock clock;

    PresenceService(StringRedisTemplate redis, SearchProperties props, Clock clock) {
        this.redis = redis;
        this.props = props;
        this.clock = clock;
    }

    public void heartbeat(UUID masterId) {
        long now = clock.millis();
        redis.opsForValue().set(key(masterId), "1", props.onlineTtl());
        redis.opsForZSet().add(ZSET, masterId.toString(), now);
        redis.opsForZSet().removeRangeByScore(ZSET, 0, now - props.onlineTtl().toMillis());
    }

    public void offline(UUID masterId) {
        redis.delete(key(masterId));
        redis.opsForZSet().remove(ZSET, masterId.toString());
    }

    /** Berilganlardan qaysilari online. Redis ishlamasa — bo'sh to'plam (qidiruv ishlashda davom etadi). */
    public Set<UUID> online(Collection<UUID> masterIds) {
        if (masterIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = new ArrayList<>(masterIds);
        try {
            List<String> values = redis.opsForValue().multiGet(ids.stream().map(PresenceService::key).toList());
            Set<UUID> result = new HashSet<>();
            for (int i = 0; i < ids.size(); i++) {
                if (values != null && values.get(i) != null) {
                    result.add(ids.get(i));
                }
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("Presence lookup failed: {}", e.getMessage());
            return Set.of();
        }
    }

    /** Hozir online bo'lgan barcha ustalar. */
    public Set<UUID> allOnline() {
        long from = clock.millis() - props.onlineTtl().toMillis();
        Set<String> members = redis.opsForZSet().rangeByScore(ZSET, from, Double.POSITIVE_INFINITY);
        Set<UUID> result = new HashSet<>();
        if (members != null) {
            members.forEach(s -> result.add(UUID.fromString(s)));
        }
        return result;
    }

    private static String key(UUID masterId) {
        return "online:" + masterId;
    }
}
