package uz.usta.platform.shared.persistence;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.domain.Persistable;
import uz.usta.platform.shared.util.Uuids;

/**
 * Barcha entity'lar uchun asos: ID ilova tomonida UUIDv7 sifatida yaratiladi, shuning uchun
 * entity persist qilinishidan oldin ham identifikatorga ega (eventlar va outbox uchun qulay).
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    private UUID id = Uuids.v7();

    @Transient
    private boolean isNew = true;

    protected BaseEntity() {
    }

    /** ID tashqaridan beriladigan entity'lar uchun (masalan profil: PK = user_id). */
    protected BaseEntity(UUID id) {
        this.id = Objects.requireNonNull(id);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> oClass = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisClass = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : getClass();
        return thisClass == oClass && o instanceof BaseEntity other && Objects.equals(id, other.getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(id);
    }
}
