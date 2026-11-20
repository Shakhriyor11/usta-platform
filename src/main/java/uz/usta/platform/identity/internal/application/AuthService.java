package uz.usta.platform.identity.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.identity.IdentityEvents;
import uz.usta.platform.identity.internal.domain.IdentityErrorCode;
import uz.usta.platform.identity.internal.domain.PhoneNumber;
import uz.usta.platform.identity.internal.domain.RefreshToken;
import uz.usta.platform.identity.internal.domain.User;
import uz.usta.platform.identity.internal.persistence.RefreshTokenRepository;
import uz.usta.platform.identity.internal.persistence.UserRepository;
import uz.usta.platform.shared.audit.AuditLog;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.outbox.OutboxPublisher;
import uz.usta.platform.shared.security.AccessToken;
import uz.usta.platform.shared.security.Role;
import uz.usta.platform.shared.util.Uuids;
import uz.usta.platform.shared.web.ClientInfo;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final OtpService otp;
    private final TokenService tokens;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final OutboxPublisher outbox;
    private final AuditLog audit;
    private final IdentityProperties props;
    private final Clock clock;

    AuthService(OtpService otp, TokenService tokens, UserRepository users, RefreshTokenRepository refreshTokens,
                OutboxPublisher outbox, AuditLog audit, IdentityProperties props, Clock clock) {
        this.otp = otp;
        this.tokens = tokens;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.outbox = outbox;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    public OtpService.Challenge requestOtp(String rawPhone, ClientInfo client) {
        return otp.request(PhoneNumber.normalize(rawPhone), client.ip());
    }

    /** OTP to'g'ri bo'lsa — foydalanuvchini topadi yoki yaratadi va token juftligini qaytaradi. */
    @Transactional
    public AuthResult verifyOtp(String rawPhone, String code, ClientInfo client) {
        String phone = PhoneNumber.normalize(rawPhone);
        otp.verify(phone, code, client.ip());

        User existing = users.findByPhone(phone).orElse(null);
        boolean newUser = existing == null;
        User user = newUser ? users.save(User.register(phone)) : existing;
        if (user.isBlocked()) {
            throw new BusinessException(IdentityErrorCode.USER_BLOCKED);
        }
        if (props.adminPhones().contains(phone) && user.grant(Role.ADMIN)) {
            audit.record(user.getId(), "user.admin-bootstrap", "user", user.getId(), Map.of());
            log.warn("ADMIN role granted to {} via app.identity.admin-phones", PhoneNumber.mask(phone));
        }
        user.recordLogin(Instant.now(clock));
        users.flush();
        return issue(user, Uuids.v7(), client, newUser);
    }

    /**
     * Refresh token rotatsiyasi. Bekor qilingan token qayta kelsa (o'g'irlik belgisi) — butun family bekor
     * qilinadi; shuning uchun xatoda ham tranzaksiya commit bo'ladi.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResult refresh(String rawToken, ClientInfo client) {
        Instant now = Instant.now(clock);
        RefreshToken current = refreshTokens.findByHashForUpdate(TokenService.sha256(rawToken))
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.INVALID_REFRESH_TOKEN));
        if (current.isRevoked()) {
            boolean withinGrace = current.getReplacedBy() != null
                    && current.getRevokedAt().plus(props.refreshReuseGrace()).isAfter(now);
            if (!withinGrace) {
                int revoked = refreshTokens.revokeFamily(current.getFamilyId(), now);
                log.warn("Refresh token reuse detected for user {} — revoked {} tokens of family {}",
                        current.getUserId(), revoked, current.getFamilyId());
                throw new BusinessException(IdentityErrorCode.REFRESH_TOKEN_REUSED);
            }
            throw new BusinessException(IdentityErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (current.isExpired(now)) {
            throw new BusinessException(IdentityErrorCode.INVALID_REFRESH_TOKEN);
        }
        User user = users.findById(current.getUserId())
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.INVALID_REFRESH_TOKEN));
        if (user.isBlocked()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new BusinessException(IdentityErrorCode.USER_BLOCKED);
        }
        AccessToken access = tokens.access(user);
        TokenService.IssuedRefresh next = tokens.refresh(user, current.getFamilyId(), client);
        current.rotate(now, next.id());
        return new AuthResult(access, next.raw(), next.expiresAt(), user, false);
    }

    /** Joriy qurilma sessiyasini (refresh token family) bekor qiladi. Noma'lum token — jim o'tkaziladi. */
    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(TokenService.sha256(rawToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now(clock)));
    }

    /**
     * Birinchi kirishda rol tanlash (CUSTOMER yoki MASTER; ikkalasi ham bo'lishi mumkin).
     * Yangi rollar bilan access token qaytaradi.
     */
    @Transactional
    public AccessTokenResult selectRole(UUID userId, Role role) {
        if (role == Role.ADMIN) {
            throw new BusinessException(IdentityErrorCode.ROLE_NOT_SELECTABLE);
        }
        User user = getUser(userId);
        if (user.grant(role)) {
            outbox.publish(IdentityEvents.AGGREGATE, userId, IdentityEvents.ROLE_GRANTED,
                    new IdentityEvents.RoleGranted(userId, role.name()));
            users.flush();
        }
        return new AccessTokenResult(tokens.access(user), user);
    }

    @Transactional(readOnly = true)
    public User getUser(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> BusinessException.notFound("User", userId));
        if (user.isBlocked()) {
            throw new BusinessException(IdentityErrorCode.USER_BLOCKED);
        }
        return user;
    }

    public record AccessTokenResult(AccessToken accessToken, User user) {
    }

    private AuthResult issue(User user, UUID familyId, ClientInfo client, boolean newUser) {
        AccessToken access = tokens.access(user);
        TokenService.IssuedRefresh refresh = tokens.refresh(user, familyId, client);
        return new AuthResult(access, refresh.raw(), refresh.expiresAt(), user, newUser);
    }
}
