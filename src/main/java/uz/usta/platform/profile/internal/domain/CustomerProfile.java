package uz.usta.platform.profile.internal.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import uz.usta.platform.shared.persistence.AuditableEntity;

@Entity
@Table(name = "customer_profiles")
@AttributeOverride(name = "id", column = @Column(name = "user_id"))
public class CustomerProfile extends AuditableEntity {

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "avatar_key", length = 512)
    private String avatarKey;

    protected CustomerProfile() {
    }

    public CustomerProfile(UUID userId, String fullName) {
        super(userId);
        this.fullName = fullName;
    }

    public void rename(String fullName) {
        this.fullName = fullName;
    }

    /** @return eski avatar kaliti (o'chirish uchun) */
    public String replaceAvatar(String key) {
        String old = avatarKey;
        avatarKey = key;
        return old;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAvatarKey() {
        return avatarKey;
    }
}
