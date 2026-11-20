package uz.usta.platform.profile.internal.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.usta.platform.profile.internal.domain.CustomerProfile;
import uz.usta.platform.profile.internal.domain.ProfileErrorCode;
import uz.usta.platform.profile.internal.persistence.CustomerProfileRepository;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.storage.FilePurpose;
import uz.usta.platform.storage.FileStorage;

@Service
public class CustomerProfileService {

    private final CustomerProfileRepository profiles;
    private final FileStorage storage;

    CustomerProfileService(CustomerProfileRepository profiles, FileStorage storage) {
        this.profiles = profiles;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public CustomerProfile get(UUID userId) {
        return profiles.findById(userId).orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND));
    }

    /** Profilni yaratadi yoki yangilaydi. */
    @Transactional
    public CustomerProfile upsert(UUID userId, String fullName) {
        CustomerProfile profile = profiles.findById(userId).orElse(null);
        if (profile == null) {
            return profiles.save(new CustomerProfile(userId, fullName.strip()));
        }
        profile.rename(fullName.strip());
        return profiles.saveAndFlush(profile);
    }

    @Transactional
    public CustomerProfile changeAvatar(UUID userId, MultipartFile file) {
        CustomerProfile profile = get(userId);
        String key = storage.store(FilePurpose.AVATAR, userId, file).key();
        storage.deleteAfterCommit(profile.replaceAvatar(key));
        return profiles.saveAndFlush(profile);
    }
}
