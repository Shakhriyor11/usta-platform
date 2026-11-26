package uz.usta.platform.shared.security;

import static uz.usta.platform.shared.security.AdminPermission.CATALOG;
import static uz.usta.platform.shared.security.AdminPermission.COMPLAINTS;
import static uz.usta.platform.shared.security.AdminPermission.FINANCE;
import static uz.usta.platform.shared.security.AdminPermission.MASTERS;
import static uz.usta.platform.shared.security.AdminPermission.ORDERS;
import static uz.usta.platform.shared.security.AdminPermission.SYSTEM;
import static uz.usta.platform.shared.security.AdminPermission.USERS;

import java.util.List;
import org.springframework.http.HttpMethod;
import uz.usta.platform.shared.web.ApiPaths;

/**
 * Admin yo'llari va ularga kirish huquqlari — {@code SecurityConfig} va API hujjati uchun yagona manba. Tartib
 * muhim: birinchi mos kelgan qoida ishlaydi; ro'yxatda yo'q admin yo'li faqat super adminga ochiq (fail closed).
 */
public final class AdminRoutes {

    /**
     * @param method null — barcha metodlar
     * @param anyOf  shu huquqlardan bittasi yetarli; bo'sh — faqat super admin
     */
    public record Rule(HttpMethod method, List<String> patterns, List<AdminPermission> anyOf) {
    }

    public static final List<Rule> RULES = List.of(
            rule(List.of(), "/admins/**"),
            rule(List.of(USERS), "/users/**"),
            rule(List.of(MASTERS), "/masters/**"),
            rule(List.of(CATALOG), "/catalog/**"),
            new Rule(HttpMethod.GET, List.of(ApiPaths.ADMIN + "/orders/*/chat/messages"), List.of(ORDERS, COMPLAINTS)),
            rule(List.of(ORDERS), "/orders/**"),
            rule(List.of(FINANCE), "/finance/**"),
            rule(List.of(COMPLAINTS), "/complaints/**"),
            rule(List.of(SYSTEM), "/search/**", "/outbox/**"),
            rule(List.of(), "/**"));

    private AdminRoutes() {
    }

    private static Rule rule(List<AdminPermission> anyOf, String... paths) {
        return new Rule(null, List.of(paths).stream().map(p -> ApiPaths.ADMIN + p).toList(), anyOf);
    }
}
