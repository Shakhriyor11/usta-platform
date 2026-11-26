package uz.usta.platform.notification.internal.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.shared.util.Uuids;

/** {@code notifications} va {@code push_tokens} jadvallari (JDBC: jsonb ustunlar va ON CONFLICT uchun qulay). */
@Repository
public class NotificationStore {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    NotificationStore(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Row(UUID id, UUID userId, String type, List<String> args, Map<String, String> data, boolean read,
                      Instant createdAt) {
    }

    public record PushToken(String token, String platform, String locale) {
    }

    /**
     * Idempotent: bitta outbox event bitta foydalanuvchiga bir martagina yoziladi.
     *
     * @return yangi yozuv ID'si; allaqachon mavjud bo'lsa — bo'sh
     */
    public Optional<UUID> insert(UUID sourceEventId, UUID userId, String type, String title, String body,
                                 List<String> args, Map<String, String> data) {
        return jdbc.sql("""
                        insert into notifications (id, user_id, type, title, body, args, data, source_event_id)
                        values (:id, :userId, :type, :title, :body, cast(:args as jsonb), cast(:data as jsonb), :eventId)
                        on conflict (source_event_id, user_id) do nothing
                        returning id""")
                .param("id", Uuids.v7()).param("userId", userId).param("type", type)
                .param("title", truncate(title, 200)).param("body", truncate(body, 1000))
                .param("args", json.writeValueAsString(args)).param("data", json.writeValueAsString(data))
                .param("eventId", sourceEventId)
                .query(UUID.class).optional();
    }

    public List<Row> page(UUID userId, boolean unreadOnly, int limit, long offset) {
        return jdbc.sql("""
                        select id, user_id, type, args::text, data::text, is_read, created_at from notifications
                        where user_id = :userId %s
                        order by created_at desc, id desc limit :limit offset :offset"""
                        .formatted(unreadOnly ? "and not is_read" : ""))
                .param("userId", userId).param("limit", limit).param("offset", offset)
                .query((rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        json.readValue(rs.getString(4), new TypeReference<List<String>>() { }),
                        json.readValue(rs.getString(5), new TypeReference<Map<String, String>>() { }),
                        rs.getBoolean(6), rs.getTimestamp(7).toInstant()))
                .list();
    }

    public long count(UUID userId, boolean unreadOnly) {
        return jdbc.sql("select count(*) from notifications where user_id = :userId"
                        + (unreadOnly ? " and not is_read" : ""))
                .param("userId", userId).query(Long.class).single();
    }

    public boolean markRead(UUID userId, UUID id, Instant now) {
        return jdbc.sql("""
                        update notifications set is_read = true, read_at = coalesce(read_at, :now)
                        where id = :id and user_id = :userId""")
                .param("id", id).param("userId", userId).param("now", Timestamp.from(now)).update() > 0;
    }

    public int markAllRead(UUID userId, Instant now) {
        return jdbc.sql("update notifications set is_read = true, read_at = :now where user_id = :userId and not is_read")
                .param("userId", userId).param("now", Timestamp.from(now)).update();
    }

    // ---------------------------------------------------------------- push tokenlar

    /** Token boshqa foydalanuvchida bo'lsa (qurilmada akkaunt almashgan) — joriy foydalanuvchiga o'tkaziladi. */
    public void upsertToken(UUID userId, String token, String platform, String locale) {
        jdbc.sql("""
                        insert into push_tokens (id, user_id, token, platform, locale)
                        values (:id, :userId, :token, :platform, :locale)
                        on conflict (token) do update set user_id = excluded.user_id, platform = excluded.platform,
                                                          locale = excluded.locale, last_seen_at = now()""")
                .param("id", Uuids.v7()).param("userId", userId).param("token", token).param("platform", platform)
                .param("locale", locale)
                .update();
    }

    public void deleteToken(UUID userId, String token) {
        jdbc.sql("delete from push_tokens where token = :token and user_id = :userId")
                .param("token", token).param("userId", userId).update();
    }

    public void deleteToken(String token) {
        jdbc.sql("delete from push_tokens where token = :token").param("token", token).update();
    }

    /** Hisob o'chirilganda: bildirishnomalar va qurilma tokenlari. */
    public void deleteAllFor(UUID userId) {
        jdbc.sql("delete from notifications where user_id = :userId").param("userId", userId).update();
        jdbc.sql("delete from push_tokens where user_id = :userId").param("userId", userId).update();
    }

    public List<PushToken> tokens(UUID userId) {
        return jdbc.sql("select token, platform, locale from push_tokens where user_id = :userId")
                .param("userId", userId)
                .query((rs, i) -> new PushToken(rs.getString(1), rs.getString(2), rs.getString(3)))
                .list();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
