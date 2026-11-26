package uz.usta.platform.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.error.CommonErrorCode;

/**
 * Bearer token tekshirilgandan keyin (yagona formatda): foydalanuvchi bloklangan/o'chirilgan bo'lsa — 403
 * USER_BLOCKED; rol yoki huquqlari token chiqarilgandan keyin o'zgargan bo'lsa — 401 TOKEN_OUTDATED.
 */
class RevokedUserFilter extends OncePerRequestFilter {

    private final UserRevocations revocations;
    private final HandlerExceptionResolver resolver;

    RevokedUserFilter(UserRevocations revocations, HandlerExceptionResolver resolver) {
        this.revocations = revocations;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            CommonErrorCode rejection = revocations.isRevoked(user.id()) ? CommonErrorCode.USER_BLOCKED
                    : revocations.isOutdated(user.id(), user.authzVersion()) ? CommonErrorCode.TOKEN_OUTDATED : null;
            if (rejection != null) {
                SecurityContextHolder.clearContext();
                resolver.resolveException(request, response, null, new BusinessException(rejection));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
