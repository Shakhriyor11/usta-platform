package uz.usta.platform.profile.internal.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.usta.platform.profile.ProfileEvents;
import uz.usta.platform.profile.internal.domain.MasterProfile;
import uz.usta.platform.profile.internal.domain.ProfileErrorCode;
import uz.usta.platform.profile.internal.persistence.MasterProfileRepository;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.outbox.OutboxPublisher;
import uz.usta.platform.storage.FilePurpose;
import uz.usta.platform.storage.FileStorage;

@Service
public class MasterProfileService {

    private final MasterProfileRepository profiles;
    private final FileStorage storage;
    private final OutboxPublisher outbox;

    MasterProfileService(MasterProfileRepository profiles, FileStorage storage, OutboxPublisher outbox) {
        this.profiles = profiles;
        this.storage = storage;
        this.outbox = outbox;
    }

    /**
     * @param latitude  ish hududi markazi (ixtiyoriy, lekin verifikatsiyaga yuborish uchun majburiy)
     */
    public record MasterProfileCommand(String fullName, String bio, int experienceYears, Double latitude,
                                       Double longitude, int workRadiusKm) {
    }

    @Transactional(readOnly = true)
    public MasterProfile get(UUID masterId) {
        return profiles.findById(masterId).orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND));
    }

    /** Faqat tasdiqlangan va faol ustalar mijozlarga ko'rinadi. */
    @Transactional(readOnly = true)
    public MasterProfile getPublic(UUID masterId) {
        return profiles.findById(masterId).filter(MasterProfile::isPublic)
                .orElseThrow(() -> BusinessException.notFound("Master", masterId));
    }

    /** Verifikatsiyaga yuborilgan/tasdiqlangan profilda ism o'zgarmaydi (admin rad etsa — yana tahrirlanadi). */
    @Transactional
    public MasterProfile upsert(UUID masterId, MasterProfileCommand cmd) {
        MasterProfile profile = profiles.findById(masterId)
                .orElseGet(() -> new MasterProfile(masterId, cmd.fullName().strip()));
        if (!profile.documentsEditable() && !profile.getFullName().equals(cmd.fullName().strip())) {
            throw new BusinessException(ProfileErrorCode.IDENTITY_LOCKED);
        }
        profile.updateDetails(cmd.fullName().strip(), blankToNull(cmd.bio()), cmd.experienceYears(), cmd.latitude(),
                cmd.longitude(), cmd.workRadiusKm());
        MasterProfile saved = profiles.saveAndFlush(profile);
        outbox.publish(ProfileEvents.AGGREGATE, masterId, ProfileEvents.MASTER_PROFILE_CHANGED,
                new ProfileEvents.MasterProfileChanged(masterId));
        return saved;
    }

    /** Fayl DB tranzaksiyasi tashqarisida yuklanadi. Rasm ham ism kabi verifikatsiyadan keyin qulflanadi. */
    public MasterProfile changeAvatar(UUID masterId, MultipartFile file) {
        requireIdentityEditable(get(masterId));
        return storage.storeAndSave(FilePurpose.AVATAR, masterId, file, stored -> {
            MasterProfile profile = get(masterId);
            requireIdentityEditable(profile);
            storage.deleteAfterCommit(profile.replaceAvatar(stored.key()));
            MasterProfile saved = profiles.saveAndFlush(profile);
            outbox.publish(ProfileEvents.AGGREGATE, masterId, ProfileEvents.MASTER_PROFILE_CHANGED,
                    new ProfileEvents.MasterProfileChanged(masterId));
            return saved;
        });
    }

    private static void requireIdentityEditable(MasterProfile profile) {
        if (!profile.documentsEditable()) {
            throw new BusinessException(ProfileErrorCode.IDENTITY_LOCKED);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
