package uz.usta.platform.profile.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.profile.internal.domain.Address;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    /**
     * "Asosiy manzil" bayrog'ini olib tashlaydi. Bulk update darhol bajariladi — shuning uchun yangi asosiy
     * manzil keyin (flush'da) belgilanganda {@code uq_addresses_default} buzilmaydi.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Address a set a.defaultAddress = false where a.userId = :userId and a.defaultAddress = true")
    int clearDefault(@Param("userId") UUID userId);
}
