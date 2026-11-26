package uz.usta.platform.search.internal.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.catalog.CatalogApi;
import uz.usta.platform.profile.CustomerDirectory;
import uz.usta.platform.search.internal.persistence.SearchIndexRepository;
import uz.usta.platform.search.internal.persistence.SearchIndexRepository.Criteria;
import uz.usta.platform.search.internal.persistence.SearchIndexRepository.Sort;
import uz.usta.platform.shared.error.BusinessException;

/** Ustalarni qidirish: xizmat/kategoriya, nuqta va radius, filtrlar, saralash, pagination, kesh. */
@Service
public class MasterSearchService {

    private final SearchIndexRepository index;
    private final CatalogApi catalog;
    private final CustomerDirectory customers;
    private final PresenceService presence;
    private final SearchCache cache;
    private final SearchProperties props;

    MasterSearchService(SearchIndexRepository index, CatalogApi catalog, CustomerDirectory customers,
                        PresenceService presence, SearchCache cache, SearchProperties props) {
        this.index = index;
        this.catalog = catalog;
        this.customers = customers;
        this.presence = presence;
        this.cache = cache;
        this.props = props;
    }

    /** Saralash: RATING — reyting ↓, baholar soni ↓, masofa ↑ (standart); DISTANCE; PRICE. */
    public enum SearchSort { RATING, DISTANCE, PRICE }

    /** @param excludeSelf so'rovchi ham usta — o'zini natijada ko'rmaydi */
    public record Query(UUID userId, boolean excludeSelf, UUID serviceId, UUID categoryId, Double latitude,
                        Double longitude, UUID addressId, Integer radiusKm, BigDecimal minRating, BigDecimal priceMin,
                        BigDecimal priceMax, boolean onlineOnly, SearchSort sort, int page, int size) {
    }

    public record Hit(UUID masterId, String fullName, String avatarKey, int experienceYears, BigDecimal ratingAvg,
                      int ratingCount, double distanceMeters, BigDecimal priceFrom, boolean online) {
    }

    public record Result(List<Hit> hits, long total) {
    }

    @Transactional(readOnly = true)
    public Result search(Query q) {
        if ((q.serviceId() == null) == (q.categoryId() == null)) {
            throw new BusinessException(SearchErrorCode.TARGET_REQUIRED);
        }
        if (q.priceMin() != null && q.priceMax() != null && q.priceMin().compareTo(q.priceMax()) > 0) {
            throw new BusinessException(SearchErrorCode.INVALID_PRICE_RANGE);
        }
        double lat;
        double lng;
        if (q.addressId() != null) {
            CustomerDirectory.AddressSnapshot a = customers.address(q.userId(), q.addressId());
            lat = a.latitude();
            lng = a.longitude();
        } else if (q.latitude() != null && q.longitude() != null) {
            lat = q.latitude();
            lng = q.longitude();
        } else {
            throw new BusinessException(SearchErrorCode.LOCATION_REQUIRED);
        }
        List<UUID> serviceIds = q.serviceId() != null
                ? List.of(catalog.requireActiveService(q.serviceId()).id())
                : catalog.activeServiceIdsInCategory(q.categoryId()).stream().sorted().toList();
        if (serviceIds.isEmpty()) {
            return new Result(List.of(), 0);
        }
        int radiusKm = Math.min(q.radiusKm() == null ? props.defaultRadiusKm() : q.radiusKm(), props.maxRadiusKm());
        Sort sort = q.sort() == null ? Sort.RATING : Sort.valueOf(q.sort().name());

        // faqat usta o'zini natijadan chiqaradi; boshqalarning bir xil so'rovi umumiy kesh kalitiga tushadi
        UUID excludeId = q.excludeSelf() ? q.userId() : null;
        SearchIndexRepository.Page page;
        if (q.onlineOnly()) {
            // online ro'yxat tez o'zgaradi — keshlanmaydi
            page = index.search(new Criteria(serviceIds, lat, lng, radiusKm * 1000, q.minRating(), q.priceMin(),
                    q.priceMax(), presence.allOnline(), excludeId, sort, q.page(), q.size()));
        } else {
            Criteria c = new Criteria(serviceIds, round(lat), round(lng), radiusKm * 1000, q.minRating(),
                    q.priceMin(), q.priceMax(), null, excludeId, sort, q.page(), q.size());
            page = cache.get(c.toString(), () -> index.search(c));
        }
        Set<UUID> online = presence.online(page.rows().stream().map(SearchIndexRepository.Row::masterId).toList());
        List<Hit> hits = page.rows().stream().map(r -> new Hit(r.masterId(), r.fullName(), r.avatarKey(),
                r.experienceYears(), r.ratingAvg(), r.ratingCount(), r.distanceMeters(), r.priceFrom(),
                online.contains(r.masterId()))).toList();
        return new Result(hits, page.total());
    }

    /** Kesh samaradorligi uchun koordinata ~11 m aniqlikkacha yaxlitlanadi. */
    private static double round(double v) {
        return Math.round(v * 10_000d) / 10_000d;
    }
}
