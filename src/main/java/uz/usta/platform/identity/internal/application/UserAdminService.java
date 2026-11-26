package uz.usta.platform.identity.internal.application;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.identity.IdentityEvents;
import uz.usta.platform.identity.UserDeletionGuard;
import uz.usta.platform.identity.internal.domain.IdentityErrorCode;
import uz.usta.platform.identity.internal.domain.User;
import uz.usta.platform.identity.internal.domain.UserStatus;
import uz.usta.platform.identity.internal.persistence.RefreshTokenRepository;
import uz.usta.platform.identity.internal.persistence.UserRepository;
import uz.usta.platform.shared.audit.AuditLog;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.outbox.OutboxPublisher;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.security.Role;
import uz.usta.platform.shared.security.UserRevocations;

/**
 * Admin: foydalanuvchilarni ko'rish, bloklash, o'chirish. Admin hisoblari bilan faqat super admin ishlaydi;
 * super admin hisobini hech kim bloklay yoki o'chira olmaydi.
 */
@Service
public class UserAdminService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final OutboxPublisher outbox;
    private final AuditLog audit;
    private final Clock clock;
    private final UserRevocations revocations;
    private final IdentityProperties props;
    private final List<UserDeletionGuard> deletionGuards;

    UserAdminService(UserRepository users, RefreshTokenRepository refreshTokens, OutboxPublisher outbox,
                     AuditLog audit, Clock clock, UserRevocations revocations, IdentityProperties props,
                     List<UserDeletionGuard> deletionGuards) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
        this.revocations = revocations;
        this.props = props;
        this.deletionGuards = deletionGuards;
    }

    public record UserFilter(String phone, Role role, UserStatus status) {
    }

    /** O'chirilgan hisoblar faqat {@code status=DELETED} so'ralganda ko'rinadi. */
    @Transactional(readOnly = true)
    public Page<User> search(UserFilter filter, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (filter.phone() != null && !filter.phone().isBlank()) {
                p.add(cb.like(root.get("phone"), "%" + filter.phone().replaceAll("[^0-9+]", "") + "%"));
            }
            p.add(filter.status() != null ? cb.equal(root.get("status"), filter.status())
                    : cb.notEqual(root.get("status"), UserStatus.DELETED));
            if (filter.role() != null) {
                Join<User, Role> roles = root.join("roles");
                p.add(cb.equal(roles, filter.role()));
                query.distinct(true);
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return users.findAll(spec, pageable);
    }

    /**
     * Bloklaydi: barcha refresh tokenlar bekor qilinadi, hali amal qilayotgan access tokenlar esa commit'dan keyin
     * darhol rad etila boshlaydi ({@link UserRevocations}).
     */
    @Transactional
    public User block(AuthenticatedUser actor, UUID userId, String reason) {
        User user = find(userId);
        requireManageable(actor, user, IdentityErrorCode.CANNOT_BLOCK_ADMIN);
        if (!user.isBlocked()) {
            user.block(reason);
            refreshTokens.revokeAllForUser(userId, Instant.now(clock));
            outbox.publish(IdentityEvents.AGGREGATE, userId, IdentityEvents.USER_BLOCKED,
                    new IdentityEvents.UserBlocked(userId, reason));
            audit.record(actor.id(), "user.block", "user", userId, Map.of("reason", reason));
            AfterCommit.run(() -> revocations.revoke(userId));
        }
        return users.saveAndFlush(user);
    }

    @Transactional
    public User unblock(AuthenticatedUser actor, UUID userId) {
        User user = find(userId);
        requireManageable(actor, user, IdentityErrorCode.CANNOT_BLOCK_ADMIN);
        if (user.isBlocked()) {
            user.unblock();
            outbox.publish(IdentityEvents.AGGREGATE, userId, IdentityEvents.USER_UNBLOCKED,
                    new IdentityEvents.UserUnblocked(userId));
            audit.record(actor.id(), "user.unblock", "user", userId, Map.of());
            AfterCommit.run(() -> revocations.restore(userId));
        }
        return users.saveAndFlush(user);
    }

    /**
     * Hisobni anonimlashtiradi (qaytarib bo'lmaydi): telefon, rollar va sessiyalar shu yerda, qolgan shaxsiy
     * ma'lumot va fayllarni boshqa modullar {@code identity.user-deleted} eventi bo'yicha o'chiradi. Buyurtma,
     * komissiya va sharh tarixi egasiz saqlanadi.
     */
    @Transactional
    public void delete(AuthenticatedUser actor, UUID userId) {
        if (actor.id().equals(userId)) {
            throw new BusinessException(IdentityErrorCode.CANNOT_DELETE_SELF);
        }
        User user = find(userId);
        requireManageable(actor, user, IdentityErrorCode.SUPER_ADMIN_IMMUTABLE);
        deletionGuards.forEach(guard -> guard.requireDeletable(userId));
        refreshTokens.deleteAllForUser(userId); // IP va qurilma ma'lumoti ham o'chadi
        user = find(userId); // bulk delete persistence context'ni tozalaydi
        user.markDeleted(Instant.now(clock));
        users.saveAndFlush(user);
        outbox.publish(IdentityEvents.AGGREGATE, userId, IdentityEvents.USER_DELETED,
                new IdentityEvents.UserDeleted(userId));
        audit.record(actor.id(), "user.delete", "user", userId, Map.of()); // shaxsiy ma'lumot yozilmaydi
        AfterCommit.run(() -> revocations.revoke(userId));
    }

    private void requireManageable(AuthenticatedUser actor, User target, IdentityErrorCode superAdminError) {
        if (target.isDeleted()) {
            throw new BusinessException(IdentityErrorCode.USER_DELETED);
        }
        // konfiguratsiyadan olib tashlangan sobiq super admin oddiy admin kabi boshqariladi
        if (target.isSuperAdmin() && props.superAdminPhones().contains(target.getPhone())) {
            throw new BusinessException(superAdminError);
        }
        if (target.isAdmin() && !actor.hasRole(Role.SUPER_ADMIN)) {
            throw new BusinessException(IdentityErrorCode.ADMIN_REQUIRES_SUPER_ADMIN);
        }
    }

    private User find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> BusinessException.notFound("User", userId));
    }
}
