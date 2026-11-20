package uz.usta.platform.profile.internal.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.profile.internal.domain.MasterProfile;
import uz.usta.platform.profile.internal.domain.VerificationStatus;

public interface MasterProfileRepository extends JpaRepository<MasterProfile, UUID> {

    Page<MasterProfile> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    @Query("""
            select count(m) > 0 from MasterProfile m
            where m.id = :id and m.verificationStatus = uz.usta.platform.profile.internal.domain.VerificationStatus.APPROVED
              and m.active = true""")
    boolean isPublic(@Param("id") UUID id);

    @Query("""
            select m.id from MasterProfile m
            where m.verificationStatus = uz.usta.platform.profile.internal.domain.VerificationStatus.APPROVED
              and m.active = true and m.workLatitude is not null""")
    List<UUID> findSearchableIds();

    /**
     * Reytingga yangi bahoni qo'shadi. Bitta UPDATE — parallel baholar bir-birini yo'qotmaydi (qator lock'i),
     * o'rtacha qiymat aniq yig'indidan hisoblanadi.
     */
    @Modifying(flushAutomatically = true)
    @NativeQuery("""
            update master_profiles
               set rating_sum   = rating_sum + :rating,
                   rating_count = rating_count + 1,
                   rating_avg   = round((rating_sum + :rating)::numeric / (rating_count + 1), 2),
                   updated_at   = now()
             where user_id = :id""")
    int addRating(@Param("id") UUID id, @Param("rating") int rating);
}
