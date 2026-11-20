package uz.usta.platform.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Mijoz IP manzili va User-Agent. IP nginx'ning X-Forwarded-For'idan olinadi
 * ({@code server.forward-headers-strategy=framework}).
 */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > 255) {
            ua = ua.substring(0, 255);
        }
        return new ClientInfo(request.getRemoteAddr(), ua);
    }
}
