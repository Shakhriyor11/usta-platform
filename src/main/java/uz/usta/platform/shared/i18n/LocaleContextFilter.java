package uz.usta.platform.shared.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.servlet.filter.OrderedFilter;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Tilni so'rov boshida (Spring Security filtrlaridan oldin) aniqlaydi. Aks holda DispatcherServlet'gacha
 * bo'lgan xatolar (401/403, rate limit) {@code LocaleContextHolder} bo'sh bo'lgani uchun serverning
 * tizim tilida qaytadi.
 *
 * <p>Tartib: Spring'ning {@code RequestContextFilter}'idan keyin (u tilni {@code request.getLocale()}, ya'ni
 * server tili bilan o'rnatadi) va Spring Security filtrlaridan (-100) oldin.
 */
@Component
@Order(LocaleContextFilter.ORDER)
class LocaleContextFilter extends OncePerRequestFilter {

    /** {@code OrderedRequestContextFilter} = -105, Spring Security = -100. */
    static final int ORDER = OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 104;

    private final LocaleResolver localeResolver;

    LocaleContextFilter(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        LocaleContext previous = LocaleContextHolder.getLocaleContext();
        LocaleContextHolder.setLocale(localeResolver.resolveLocale(request));
        try {
            chain.doFilter(request, response);
        } finally {
            LocaleContextHolder.setLocaleContext(previous);
        }
    }
}
