package uz.usta.platform.identity.internal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import uz.usta.platform.shared.persistence.AuditableEntity;
import uz.usta.platform.shared.security.Role;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(name = "phone", nullable = false, updatable = false, length = 13)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Set<Role> roles = new HashSet<>();

    protected User() {
    }

    public static User register(String normalizedPhone) {
        User u = new User();
        u.phone = normalizedPhone;
        return u;
    }

    /** @return {@code true} — rol yangi qo'shilgan bo'lsa */
    public boolean grant(Role role) {
        return roles.add(role);
    }

    public void block(String reason) {
        this.status = UserStatus.BLOCKED;
        this.blockedReason = reason;
    }

    public void unblock() {
        this.status = UserStatus.ACTIVE;
        this.blockedReason = null;
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    public boolean isBlocked() {
        return status == UserStatus.BLOCKED;
    }

    public String getPhone() {
        return phone;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
