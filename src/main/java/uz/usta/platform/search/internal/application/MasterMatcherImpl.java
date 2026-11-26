package uz.usta.platform.search.internal.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.search.MasterMatcher;
import uz.usta.platform.search.internal.persistence.SearchIndexRepository;

@Component
class MasterMatcherImpl implements MasterMatcher {

    private static final Logger log = LoggerFactory.getLogger(MasterMatcherImpl.class);

    private final SearchIndexRepository index;
    private final PresenceService presence;

    MasterMatcherImpl(SearchIndexRepository index, PresenceService presence) {
        this.index = index;
        this.presence = presence;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candidate> nearest(UUID serviceId, double latitude, double longitude, int limit,
                                   Collection<UUID> excludeMasterIds) {
        List<Candidate> result = new ArrayList<>(
                index.nearest(serviceId, latitude, longitude, limit, excludeMasterIds, onlineMasters()));
        if (result.size() < limit) {
            // online ustalar yetmadi — qolgan o'rinlar offline ustalarga (push ularni uyg'otadi)
            Set<UUID> exclude = new HashSet<>(excludeMasterIds);
            result.forEach(c -> exclude.add(c.masterId()));
            result.addAll(index.nearest(serviceId, latitude, longitude, limit - result.size(), exclude, null));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Candidate> match(UUID masterId, UUID serviceId, double latitude, double longitude) {
        return index.match(masterId, serviceId, latitude, longitude);
    }

    /** Redis ishlamasa taqsimlash to'xtamaydi — shunchaki online ustuvorligisiz. */
    private Set<UUID> onlineMasters() {
        try {
            return presence.allOnline();
        } catch (RuntimeException e) {
            log.warn("Presence unavailable, dispatching without online priority: {}", e.getMessage());
            return Set.of();
        }
    }
}
