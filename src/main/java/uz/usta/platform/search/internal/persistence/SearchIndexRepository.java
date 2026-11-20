package uz.usta.platform.search.internal.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import uz.usta.platform.catalog.CatalogApi.Offering;
import uz.usta.platform.profile.MasterProfiles.MasterSnapshot;
import uz.usta.platform.search.MasterMatcher.Candidate;

/** Qidiruv read-modeli ({@code search_masters}, {@code search_master_services}) — PostGIS so'rovlari. */
@Repository
public class SearchIndexRepository {

    /** Ustaning ish radiusi cheklovi (V4: work_radius_km <= 200) — GIST indeksdan foydalanish uchun. */
    private static final int MAX_WORK_RADIUS_M = 200_000;

    private static final String POINT = "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography";

    private final JdbcClient jdbc;

    SearchIndexRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public enum Sort { RATING, DISTANCE, PRICE }

    public record Criteria(Collection<UUID> serviceIds, double latitude, double longitude, int radiusMeters,
                           BigDecimal minRating, BigDecimal priceMin, BigDecimal priceMax, Collection<UUID> onlyIds,
                           UUID excludeId, Sort sort, int page, int size) {
    }

    public record Row(UUID masterId, String fullName, String avatarKey, int experienceYears, BigDecimal ratingAvg,
                      int ratingCount, double distanceMeters, BigDecimal priceFrom) {
    }

    public record Page(List<Row> rows, long total) {
    }

    // ---------------------------------------------------------------- yozish

    public void upsert(MasterSnapshot m, List<Offering> offerings) {
        jdbc.sql("""
                        insert into search_masters (master_id, full_name, avatar_key, experience_years, rating_avg,
                                                    rating_count, latitude, longitude, work_radius_km, indexed_at)
                        values (:id, :name, :avatar, :exp, :ratingAvg, :ratingCount, :lat, :lng, :radius, now())
                        on conflict (master_id) do update set
                            full_name = excluded.full_name, avatar_key = excluded.avatar_key,
                            experience_years = excluded.experience_years, rating_avg = excluded.rating_avg,
                            rating_count = excluded.rating_count, latitude = excluded.latitude,
                            longitude = excluded.longitude, work_radius_km = excluded.work_radius_km,
                            indexed_at = now()""")
                .param("id", m.masterId()).param("name", m.fullName()).param("avatar", m.avatarKey())
                .param("exp", m.experienceYears()).param("ratingAvg", m.ratingAvg())
                .param("ratingCount", m.ratingCount()).param("lat", m.latitude()).param("lng", m.longitude())
                .param("radius", m.workRadiusKm())
                .update();
        jdbc.sql("delete from search_master_services where master_id = :id").param("id", m.masterId()).update();
        for (Offering o : offerings) {
            jdbc.sql("insert into search_master_services (master_id, service_id, price_from) values (:m, :s, :p)")
                    .param("m", m.masterId()).param("s", o.serviceId()).param("p", o.priceFrom())
                    .update();
        }
    }

    public boolean delete(UUID masterId) {
        return jdbc.sql("delete from search_masters where master_id = :id").param("id", masterId).update() > 0;
    }

    public List<UUID> indexedIds() {
        return jdbc.sql("select master_id from search_masters").query(UUID.class).list();
    }

    // ---------------------------------------------------------------- qidiruv

    /**
     * Bitta so'rov: natijalar va jami soni ({@code count(*) OVER ()}). Masofa sferik modelda (spheroid'dan ~3×
     * arzon, xato < 0.5%) va har usta uchun bir marta hisoblanadi. Yuklama testida (docs/performance.md) eski
     * variant (2 so'rov, spheroid) DB CPU'ni ~300 RPS da to'ldirgan edi.
     */
    public Page search(Criteria c) {
        Map<String, Object> params = new HashMap<>();
        params.put("lat", c.latitude());
        params.put("lng", c.longitude());
        params.put("serviceIds", c.serviceIds());
        params.put("radius", c.radiusMeters());
        List<String> inner = new ArrayList<>(List.of("ST_DWithin(m.location, p.g, :radius, false)"));
        List<String> priceFilter = new ArrayList<>();
        if (c.minRating() != null) {
            inner.add("m.rating_avg >= :minRating");
            params.put("minRating", c.minRating());
        }
        if (c.priceMin() != null) {
            priceFilter.add("min(ms.price_from) >= :priceMin");
            params.put("priceMin", c.priceMin());
        }
        if (c.priceMax() != null) {
            priceFilter.add("min(ms.price_from) <= :priceMax");
            params.put("priceMax", c.priceMax());
        }
        if (c.onlyIds() != null) {
            if (c.onlyIds().isEmpty()) {
                return new Page(List.of(), 0);
            }
            inner.add("m.master_id in (:onlyIds)");
            params.put("onlyIds", c.onlyIds());
        }
        if (c.excludeId() != null) {
            inner.add("m.master_id <> :excludeId");
            params.put("excludeId", c.excludeId());
        }
        String orderBy = switch (c.sort()) {
            case RATING -> "x.rating_avg desc, x.rating_count desc, x.distance_m asc";
            case DISTANCE -> "x.distance_m asc, x.rating_avg desc, x.rating_count desc";
            case PRICE -> "x.price_from asc, x.rating_avg desc, x.distance_m asc";
        };
        params.put("limit", c.size());
        params.put("offset", (long) c.page() * c.size());
        String sql = """
                select x.*, count(*) over () as total from (
                    select m.master_id, m.full_name, m.avatar_key, m.experience_years, m.rating_avg, m.rating_count,
                           m.work_radius_km, ST_Distance(m.location, p.g, false) as distance_m, s.price_from
                    from search_masters m
                    cross join (select %s as g) p
                    join (select ms.master_id, min(ms.price_from) as price_from from search_master_services ms
                          where ms.service_id in (:serviceIds) group by ms.master_id %s) s on s.master_id = m.master_id
                    where %s
                ) x
                where x.distance_m <= x.work_radius_km * 1000
                order by %s, x.master_id
                limit :limit offset :offset""".formatted(POINT,
                priceFilter.isEmpty() ? "" : "having " + String.join(" and ", priceFilter),
                String.join("\n  and ", inner), orderBy);
        long[] total = {-1};
        List<Row> rows = jdbc.sql(sql).params(params)
                .query((rs, i) -> {
                    total[0] = rs.getLong("total");
                    return new Row(rs.getObject("master_id", UUID.class), rs.getString("full_name"),
                            rs.getString("avatar_key"), rs.getInt("experience_years"), rs.getBigDecimal("rating_avg"),
                            rs.getInt("rating_count"), rs.getDouble("distance_m"), rs.getBigDecimal("price_from"));
                })
                .list();
        if (total[0] < 0) {
            // sahifa bo'sh: jami sonini alohida aniqlaymiz (faqat oxiridan keyingi sahifa so'ralganda)
            total[0] = c.page() == 0 ? 0 : jdbc.sql("select count(*) from (" + sql.replace("limit :limit offset :offset", "")
                    + ") t").params(params).query(Long.class).single();
        }
        return new Page(rows, total[0]);
    }

    // ---------------------------------------------------------------- taqsimlash nomzodlari

    public List<Candidate> nearest(UUID serviceId, double lat, double lng, int limit, Collection<UUID> exclude) {
        String excludeSql = exclude.isEmpty() ? "" : "and m.master_id not in (:exclude)";
        var spec = jdbc.sql("""
                        select m.master_id, ST_Distance(m.location, p.g, false) as distance_m, ms.price_from
                        from search_masters m
                        cross join (select %s as g) p
                        join search_master_services ms on ms.master_id = m.master_id and ms.service_id = :serviceId
                        where ST_DWithin(m.location, p.g, :maxRadius, false)
                          and ST_DWithin(m.location, p.g, m.work_radius_km * 1000, false)
                          %s
                        order by distance_m asc, m.rating_avg desc, m.rating_count desc
                        limit :limit""".formatted(POINT, excludeSql))
                .param("lat", lat).param("lng", lng).param("serviceId", serviceId)
                .param("maxRadius", MAX_WORK_RADIUS_M).param("limit", limit);
        if (!exclude.isEmpty()) {
            spec = spec.param("exclude", exclude);
        }
        return spec.query((rs, i) -> new Candidate(rs.getObject("master_id", UUID.class),
                (int) Math.round(rs.getDouble("distance_m")), rs.getBigDecimal("price_from"))).list();
    }

    public Optional<Candidate> match(UUID masterId, UUID serviceId, double lat, double lng) {
        return jdbc.sql("""
                        select m.master_id, ST_Distance(m.location, p.g, false) as distance_m, ms.price_from
                        from search_masters m
                        cross join (select %s as g) p
                        join search_master_services ms on ms.master_id = m.master_id and ms.service_id = :serviceId
                        where m.master_id = :masterId
                          and ST_DWithin(m.location, p.g, m.work_radius_km * 1000, false)""".formatted(POINT))
                .param("lat", lat).param("lng", lng).param("serviceId", serviceId).param("masterId", masterId)
                .query((rs, i) -> new Candidate(rs.getObject("master_id", UUID.class),
                        (int) Math.round(rs.getDouble("distance_m")), rs.getBigDecimal("price_from")))
                .optional();
    }
}
