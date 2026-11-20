package uz.usta.platform.search.internal.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.search.MasterMatcher;
import uz.usta.platform.search.internal.persistence.SearchIndexRepository;

@Component
class MasterMatcherImpl implements MasterMatcher {

    private final SearchIndexRepository index;

    MasterMatcherImpl(SearchIndexRepository index) {
        this.index = index;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candidate> nearest(UUID serviceId, double latitude, double longitude, int limit,
                                   Collection<UUID> excludeMasterIds) {
        return index.nearest(serviceId, latitude, longitude, limit, excludeMasterIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Candidate> match(UUID masterId, UUID serviceId, double latitude, double longitude) {
        return index.match(masterId, serviceId, latitude, longitude);
    }
}
