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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import uz.usta.platform.shared.persistence.AuditableEntity;
import uz.usta.platform.shared.security.AdminPermission;
import uz.usta.platform.shared.security.Role;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    /** O'chirilgan (anonimlashtirilgan) hisobda {@code null}. */
    @Column(name = "phone", length = 13)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Rol/huquq o'zgarganda oshadi — eski access tokenlarni rad etish uchun. */
    @Column(name = "authz_version", nullable = false)
    private int authzVersion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Set<Role> roles = new HashSet<>();

    /** Faqat oddiy admin uchun; super adminda hamma huquq bor va bu yerda saqlanmaydi. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 16)
    private Set<AdminPermission> permissions = new HashSet<>();

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

    public void appointAdmin(Collection<AdminPermission> granted) {
        roles.add(Role.ADMIN);
        permissions.clear();
        permissions.addAll(granted);
        authzVersion++;
    }

    /** Admin va super admin huquqlarini olib tashlaydi (boshqa rollari qoladi). */
    public void removeAdmin() {
        roles.remove(Role.ADMIN);
        roles.remove(Role.SUPER_ADMIN);
        permissions.clear();
        authzVersion++;
    }

    /** @return {@code true} — oldin super admin emas edi */
    public boolean makeSuperAdmin() {
        boolean changed = roles.add(Role.SUPER_ADMIN);
        changed |= roles.add(Role.ADMIN);
        if (changed) {
            permissions.clear();
            authzVersion++;
        }
        return changed;
    }

    public void block(String reason) {
        this.status = UserStatus.BLOCKED;
        this.blockedReason = reason;
    }

    public void unblock() {
        this.status = UserStatus.ACTIVE;
        this.blockedReason = null;
    }

    /** Qaytarib bo'lmaydi: telefon, rollar va huquqlar o'chadi; raqam bilan yangi hisob ochish mumkin. */
    public void markDeleted(Instant now) {
        this.status = UserStatus.DELETED;
        this.phone = null;
        this.blockedReason = null;
        this.lastLoginAt = null;
        this.deletedAt = now;
        roles.clear();
        permissions.clear();
        authzVersion++;
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    public boolean isBlocked() {
        return status == UserStatus.BLOCKED;
    }

    public boolean isDeleted() {
        return status == UserStatus.DELETED;
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }

    public boolean isSuperAdmin() {
        return roles.contains(Role.SUPER_ADMIN);
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

    public int getAuthzVersion() {
        return authzVersion;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    /** Amaldagi huquqlar (super adminda — hammasi). */
    public Set<AdminPermission> getPermissions() {
        return isSuperAdmin() ? Set.of(AdminPermission.values()) : Collections.unmodifiableSet(permissions);
    }
}
