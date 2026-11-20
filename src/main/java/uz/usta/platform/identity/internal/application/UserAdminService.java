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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uz.usta.platform.identity.IdentityEvents;
import uz.usta.platform.identity.internal.domain.IdentityErrorCode;
import uz.usta.platform.identity.internal.domain.User;
import uz.usta.platform.identity.internal.domain.UserStatus;
import uz.usta.platform.identity.internal.persistence.RefreshTokenRepository;
import uz.usta.platform.identity.internal.persistence.UserRepository;
import uz.usta.platform.shared.audit.AuditLog;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.outbox.OutboxPublisher;
import uz.usta.platform.shared.security.Role;
import uz.usta.platform.shared.security.UserRevocations;

@Service
public class UserAdminService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final OutboxPublisher outbox;
    private final AuditLog audit;
    private final Clock clock;
    private final UserRevocations revocations;

    UserAdminService(UserRepository users, RefreshTokenRepository refreshTokens, OutboxPublisher outbox,
                     AuditLog audit, Clock clock, UserRevocations revocations) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
        this.revocations = revocations;
    }

    public record UserFilter(String phone, Role role, UserStatus status) {
    }

    @Transactional(readOnly = true)
    public Page<User> search(UserFilter filter, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (filter.phone() != null && !filter.phone().isBlank()) {
                p.add(cb.like(root.get("phone"), "%" + filter.phone().replaceAll("[^0-9+]", "") + "%"));
            }
            if (filter.status() != null) {
                p.add(cb.equal(root.get("status"), filter.status()));
            }
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
    public User block(UUID actorId, UUID userId, String reason) {
        User user = find(userId);
        if (user.getRoles().contains(Role.ADMIN)) {
            throw new BusinessException(IdentityErrorCode.CANNOT_BLOCK_ADMIN);
        }
        if (!user.isBlocked()) {
            user.block(reason);
            refreshTokens.revokeAllForUser(userId, Instant.now(clock));
            outbox.publish(IdentityEvents.AGGREGATE, userId, IdentityEvents.USER_BLOCKED,
                    new IdentityEvents.UserBlocked(userId, reason));
            audit.record(actorId, "user.block", "user", userId, Map.of("reason", reason));
            afterCommit(() -> revocations.revoke(userId));
        }
        return users.saveAndFlush(user);
    }

    @Transactional
    public User unblock(UUID actorId, UUID userId) {
        User user = find(userId);
        if (user.isBlocked()) {
            user.unblock();
            outbox.publish(IdentityEvents.AGGREGATE, userId, IdentityEvents.USER_UNBLOCKED,
                    new IdentityEvents.UserUnblocked(userId));
            audit.record(actorId, "user.unblock", "user", userId, Map.of());
            afterCommit(() -> revocations.restore(userId));
        }
        return users.saveAndFlush(user);
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private User find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> BusinessException.notFound("User", userId));
    }
}
