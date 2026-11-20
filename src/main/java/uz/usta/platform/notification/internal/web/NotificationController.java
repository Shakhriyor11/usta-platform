package uz.usta.platform.notification.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.notification.internal.application.NotificationService;
import uz.usta.platform.notification.internal.application.NotificationType;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.PageResponse;

@Tag(name = "Notifications", description = "In-app bildirishnomalar va push qurilma tokenlari")
@RestController
class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @Schema(description = "title/body — Accept-Language tilida; data.orderId — bosilganda ochiladigan buyurtma")
    record NotificationResponse(UUID id, NotificationType type, String title, String body, Map<String, String> data,
                                boolean read, Instant createdAt) {
    }

    enum Platform { ANDROID, IOS, WEB }

    record PushTokenRequest(@NotBlank @Size(max = 512) String token, @NotNull Platform platform) {
    }

    @Operation(summary = "Bildirishnomalar (yangilari birinchi)")
    @GetMapping(ApiPaths.V1 + "/notifications")
    PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestParam(defaultValue = "false") boolean unreadOnly,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var items = service.page(user.id(), unreadOnly, page, size).stream()
                .map(v -> new NotificationResponse(v.id(), v.type(), v.title(), v.body(), v.data(), v.read(),
                        v.createdAt()))
                .toList();
        long total = service.count(user.id(), unreadOnly);
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    @Operation(summary = "O'qilmaganlar soni (badge uchun)")
    @GetMapping(ApiPaths.V1 + "/notifications/unread-count")
    Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("count", service.count(user.id(), true));
    }

    @Operation(summary = "O'qildi deb belgilash")
    @PostMapping(ApiPaths.V1 + "/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.markRead(user.id(), id);
    }

    @Operation(summary = "Hammasini o'qildi deb belgilash")
    @PostMapping(ApiPaths.V1 + "/notifications/read-all")
    Map<String, Integer> readAll(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("updated", service.markAllRead(user.id()));
    }

    @Operation(summary = "Push token ro'yxatdan o'tkazish (FCM)",
            description = "Ilova ishga tushganda va token yangilanganda chaqiriladi. Push tili — Accept-Language")
    @PostMapping(ApiPaths.V1 + "/push-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void register(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PushTokenRequest body) {
        service.registerToken(user.id(), body.token().strip(), body.platform().name(), LocaleContextHolder.getLocale());
    }

    @Operation(summary = "Push token o'chirish (chiqishda)")
    @DeleteMapping(ApiPaths.V1 + "/push-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unregister(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam @NotBlank String token) {
        service.unregisterToken(user.id(), token);
    }
}
