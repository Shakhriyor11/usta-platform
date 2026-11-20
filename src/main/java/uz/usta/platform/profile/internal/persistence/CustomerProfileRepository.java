package uz.usta.platform.profile.internal.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.usta.platform.profile.internal.domain.CustomerProfile;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {
}
