package uz.usta.platform.shared.security;

import java.time.Instant;

/** Imzolangan access token va uning amal qilish muddati. */
public record AccessToken(String value, Instant expiresAt) {
}
