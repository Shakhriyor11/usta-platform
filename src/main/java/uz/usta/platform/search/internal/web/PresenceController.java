package uz.usta.platform.search.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.search.internal.application.PresenceService;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Master: Presence", description = "Online holat (MASTER roli)")
@RestController
@RequestMapping(ApiPaths.MASTER + "/presence")
class PresenceController {

    private final PresenceService presence;

    PresenceController(PresenceService presence) {
        this.presence = presence;
    }

    @Operation(summary = "Heartbeat — online", description = "Ilova ochiq bo'lganda har ~30 s da yuboriladi; 90 s kelmasa — offline")
    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void heartbeat(@AuthenticationPrincipal AuthenticatedUser user) {
        presence.heartbeat(user.id());
    }

    @Operation(summary = "Offline bo'lish")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void offline(@AuthenticationPrincipal AuthenticatedUser user) {
        presence.offline(user.id());
    }
}
