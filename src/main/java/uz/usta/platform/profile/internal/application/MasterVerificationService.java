package uz.usta.platform.profile.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.usta.platform.profile.ProfileEvents;
import uz.usta.platform.profile.internal.domain.DocumentType;
import uz.usta.platform.profile.internal.domain.MasterProfile;
import uz.usta.platform.profile.internal.domain.ProfileErrorCode;
import uz.usta.platform.profile.internal.domain.VerificationDocument;
import uz.usta.platform.profile.internal.domain.VerificationStatus;
import uz.usta.platform.profile.internal.persistence.MasterProfileRepository;
import uz.usta.platform.profile.internal.persistence.VerificationDocumentRepository;
import uz.usta.platform.shared.audit.AuditLog;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.outbox.OutboxPublisher;
import uz.usta.platform.storage.FilePurpose;
import uz.usta.platform.storage.FileStorage;
import uz.usta.platform.storage.StoredFile;

/**
 * Usta verifikatsiyasi: hujjat yuklash -> yuborish (PENDING) -> admin tasdiqlaydi/rad etadi.
 * Har bir o'tish outbox event (bildirishnoma uchun) va admin amallari audit jurnaliga yoziladi.
 */
@Service
public class MasterVerificationService {

    private final MasterProfileRepository profiles;
    private final VerificationDocumentRepository documents;
    private final FileStorage storage;
    private final OutboxPublisher outbox;
    private final AuditLog audit;
    private final ProfileProperties props;
    private final Clock clock;

    MasterVerificationService(MasterProfileRepository profiles, VerificationDocumentRepository documents,
                              FileStorage storage, OutboxPublisher outbox, AuditLog audit, ProfileProperties props,
                              Clock clock) {
        this.profiles = profiles;
        this.documents = documents;
        this.storage = storage;
        this.outbox = outbox;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- usta

    @Transactional(readOnly = true)
    public List<VerificationDocument> documents(UUID masterId) {
        return documents.findByMasterIdOrderByCreatedAtAsc(masterId);
    }

    @Transactional
    public VerificationDocument upload(UUID masterId, DocumentType type, MultipartFile file) {
        MasterProfile profile = profile(masterId);
        if (!profile.documentsEditable()) {
            throw new BusinessException(ProfileErrorCode.DOCUMENTS_LOCKED, profile.getVerificationStatus().name());
        }
        if (documents.countByMasterId(masterId) >= props.maxDocuments()) {
            throw new BusinessException(ProfileErrorCode.DOCUMENT_LIMIT_EXCEEDED, props.maxDocuments());
        }
        StoredFile stored = storage.store(FilePurpose.VERIFICATION_DOCUMENT, masterId, file);
        return documents.saveAndFlush(new VerificationDocument(masterId, type, stored.key(), stored.contentType(),
                stored.sizeBytes()));
    }

    @Transactional
    public void deleteDocument(UUID masterId, UUID documentId) {
        MasterProfile profile = profile(masterId);
        if (!profile.documentsEditable()) {
            throw new BusinessException(ProfileErrorCode.DOCUMENTS_LOCKED, profile.getVerificationStatus().name());
        }
        VerificationDocument doc = documents.findByIdAndMasterId(documentId, masterId)
                .orElseThrow(() -> BusinessException.notFound("Document", documentId));
        documents.delete(doc);
        storage.deleteAfterCommit(doc.getFileKey());
    }

    @Transactional
    public MasterProfile submit(UUID masterId) {
        MasterProfile profile = profile(masterId);
        Set<DocumentType> present = documents.findByMasterIdOrderByCreatedAtAsc(masterId).stream()
                .map(VerificationDocument::getType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DocumentType.class)));
        Set<DocumentType> missing = EnumSet.noneOf(DocumentType.class);
        props.requiredDocuments().stream().filter(t -> !present.contains(t)).forEach(missing::add);
        if (!missing.isEmpty()) {
            String list = missing.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
            throw new BusinessException(ProfileErrorCode.VERIFICATION_MISSING_DOCUMENTS,
                    Map.of("missingDocuments", missing.stream().map(Enum::name).sorted().toList()), list);
        }
        profile.submitForVerification(Instant.now(clock));
        outbox.publish(ProfileEvents.AGGREGATE, masterId, ProfileEvents.VERIFICATION_SUBMITTED,
                new ProfileEvents.VerificationSubmitted(masterId));
        return profiles.saveAndFlush(profile);
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public Page<MasterProfile> search(VerificationStatus status, Pageable pageable) {
        return status == null ? profiles.findAll(pageable) : profiles.findByVerificationStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public MasterProfile get(UUID masterId) {
        return profiles.findById(masterId).orElseThrow(() -> BusinessException.notFound("Master", masterId));
    }

    @Transactional
    public MasterProfile approve(UUID adminId, UUID masterId) {
        MasterProfile profile = get(masterId);
        profile.approve(adminId, Instant.now(clock));
        outbox.publish(ProfileEvents.AGGREGATE, masterId, ProfileEvents.MASTER_APPROVED,
                new ProfileEvents.MasterApproved(masterId, adminId));
        audit.record(adminId, "master.approve", "master_profile", masterId, Map.of());
        return profiles.saveAndFlush(profile);
    }

    @Transactional
    public MasterProfile reject(UUID adminId, UUID masterId, String reason) {
        MasterProfile profile = get(masterId);
        profile.reject(adminId, reason.strip(), Instant.now(clock));
        outbox.publish(ProfileEvents.AGGREGATE, masterId, ProfileEvents.MASTER_REJECTED,
                new ProfileEvents.MasterRejected(masterId, adminId, reason.strip()));
        audit.record(adminId, "master.reject", "master_profile", masterId, Map.of("reason", reason.strip()));
        return profiles.saveAndFlush(profile);
    }

    private MasterProfile profile(UUID masterId) {
        return profiles.findById(masterId)
                .orElseThrow(() -> new BusinessException(ProfileErrorCode.MASTER_PROFILE_REQUIRED));
    }
}
