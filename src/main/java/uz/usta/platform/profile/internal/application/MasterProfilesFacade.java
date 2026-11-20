package uz.usta.platform.profile.internal.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.profile.MasterProfiles;
import uz.usta.platform.profile.ProfileEvents;
import uz.usta.platform.profile.internal.domain.ProfileErrorCode;
import uz.usta.platform.profile.internal.persistence.MasterProfileRepository;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.outbox.OutboxPublisher;

@Component
class MasterProfilesFacade implements MasterProfiles {

    private final MasterProfileRepository profiles;
    private final OutboxPublisher outbox;

    MasterProfilesFacade(MasterProfileRepository profiles, OutboxPublisher outbox) {
        this.profiles = profiles;
        this.outbox = outbox;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireProfile(UUID masterId) {
        if (!profiles.existsById(masterId)) {
            throw new BusinessException(ProfileErrorCode.MASTER_PROFILE_REQUIRED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPublic(UUID masterId) {
        return profiles.isPublic(masterId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MasterSnapshot> searchable(UUID masterId) {
        return profiles.findById(masterId)
                .filter(m -> m.isPublic() && m.hasWorkArea())
                .map(m -> new MasterSnapshot(m.getId(), m.getFullName(), m.getAvatarKey(), m.getExperienceYears(),
                        m.getWorkLatitude(), m.getWorkLongitude(), m.getWorkRadiusKm(), m.getRatingAvg(),
                        m.getRatingCount()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> searchableIds() {
        return profiles.findSearchableIds();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MasterSummary> summaries(Collection<UUID> masterIds) {
        if (masterIds.isEmpty()) {
            return Map.of();
        }
        return profiles.findAllById(masterIds).stream()
                .map(m -> new MasterSummary(m.getId(), m.getFullName(), m.getAvatarKey(), m.getRatingAvg(),
                        m.getRatingCount()))
                .collect(Collectors.toMap(MasterSummary::masterId, Function.identity()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyRating(UUID masterId, int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be 1..5");
        }
        if (profiles.addRating(masterId, rating) != 1) {
            throw BusinessException.notFound("Master", masterId);
        }
        outbox.publish(ProfileEvents.AGGREGATE, masterId, ProfileEvents.MASTER_PROFILE_CHANGED,
                new ProfileEvents.MasterProfileChanged(masterId));
    }
}
