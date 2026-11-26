package uz.usta.platform.identity.internal.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.identity.internal.domain.IdentityErrorCode;
import uz.usta.platform.identity.internal.domain.PhoneNumber;
import uz.usta.platform.identity.internal.domain.User;
import uz.usta.platform.identity.internal.persistence.UserRepository;
import uz.usta.platform.shared.audit.AuditLog;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.security.AdminPermission;
import uz.usta.platform.shared.security.Role;
import uz.usta.platform.shared.security.UserRevocations;

/**
 * Super admin: adminlarni tayinlash, huquqlarini o'zgartirish va olib tashlash. O'zgarish darhol kuchga kiradi —
 * adminning eski access tokenlari rad etiladi. Super admin hisoblari faqat konfiguratsiya orqali boshqariladi.
 */
@Service
public class AdminManagementService {

    private final UserRepository users;
    private final AuditLog audit;
    private final UserRevocations revocations;

    AdminManagementService(UserRepository users, AuditLog audit, UserRevocations revocations) {
        this.users = users;
        this.audit = audit;
        this.revocations = revocations;
    }

    @Transactional(readOnly = true)
    public List<User> admins() {
        return users.findAllWithRole(Role.ADMIN);
    }

    /** Raqam bo'yicha tayinlash: hisob hali bo'lmasa yaratiladi (keyin shu raqam bilan kiradi). */
    @Transactional
    public User appoint(UUID actorId, String rawPhone, Set<AdminPermission> permissions) {
        String phone = PhoneNumber.normalize(rawPhone);
        User user = users.findByPhone(phone).orElseGet(() -> users.save(User.register(phone)));
        if (user.isBlocked()) {
            throw new BusinessException(IdentityErrorCode.CANNOT_APPOINT_BLOCKED);
        }
        return apply(actorId, requireChangeable(user), permissions, "admin.appoint");
    }

    @Transactional
    public User updatePermissions(UUID actorId, UUID userId, Set<AdminPermission> permissions) {
        return apply(actorId, requireChangeable(findAdmin(userId)), permissions, "admin.permissions");
    }

    @Transactional
    public void remove(UUID actorId, UUID userId) {
        User user = requireChangeable(findAdmin(userId));
        user.removeAdmin();
        users.saveAndFlush(user);
        audit.record(actorId, "admin.remove", "user", userId, Map.of());
        publishVersion(user);
    }

    private User apply(UUID actorId, User user, Set<AdminPermission> permissions, String action) {
        user.appointAdmin(permissions);
        User saved = users.saveAndFlush(user);
        audit.record(actorId, action, "user", saved.getId(),
                Map.of("permissions", permissions.stream().map(Enum::name).sorted().toList()));
        publishVersion(saved);
        return saved;
    }

    private void publishVersion(User user) {
        UUID userId = user.getId();
        int version = user.getAuthzVersion();
        AfterCommit.run(() -> revocations.authzChanged(userId, version));
    }

    private User findAdmin(UUID userId) {
        return users.findById(userId).filter(User::isAdmin)
                .orElseThrow(() -> BusinessException.notFound("Admin", userId));
    }

    private static User requireChangeable(User user) {
        if (user.isSuperAdmin()) {
            throw new BusinessException(IdentityErrorCode.SUPER_ADMIN_IMMUTABLE);
        }
        return user;
    }
}
