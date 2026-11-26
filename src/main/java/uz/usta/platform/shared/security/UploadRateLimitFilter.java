package uz.usta.platform.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.ratelimit.RateLimiter;
import uz.usta.platform.shared.web.ApiPaths;

/** Fayl yuklashlarni foydalanuvchi bo'yicha cheklaydi — fayl tanasi o'qilishidan oldin. */
class UploadRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final UploadProperties props;
    private final HandlerExceptionResolver resolver;

    UploadRateLimitFilter(RateLimiter rateLimiter, UploadProperties props, HandlerExceptionResolver resolver) {
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String type = request.getContentType();
        return type == null || !type.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user && !isAdminUpload(request, user)) {
            try {
                rateLimiter.consume("upload", user.id().toString(), props.limit(), props.window());
            } catch (BusinessException e) {
                resolver.resolveException(request, response, null, e);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** Katalog to'ldirilayotganda admin o'nlab rasm yuklaydi; admin amallari huquq va audit bilan nazoratda. */
    private static boolean isAdminUpload(HttpServletRequest request, AuthenticatedUser user) {
        return user.roles().contains(Role.ADMIN) && request.getRequestURI()
                .startsWith(request.getContextPath() + ApiPaths.ADMIN + "/");
    }
}
