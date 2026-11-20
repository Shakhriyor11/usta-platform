package uz.usta.platform.profile.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.usta.platform.profile.internal.domain.VerificationDocument;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, UUID> {

    List<VerificationDocument> findByMasterIdOrderByCreatedAtAsc(UUID masterId);

    Optional<VerificationDocument> findByIdAndMasterId(UUID id, UUID masterId);

    long countByMasterId(UUID masterId);
}
