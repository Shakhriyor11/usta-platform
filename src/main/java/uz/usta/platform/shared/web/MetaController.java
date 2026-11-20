package uz.usta.platform.shared.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.shared.i18n.Messages;

@Tag(name = "Meta", description = "Servis haqida umumiy ma'lumot")
@RestController
@RequestMapping(ApiPaths.PUBLIC + "/meta")
class MetaController {

    private final Messages messages;
    private final Clock clock;
    private final String version;

    MetaController(Messages messages, Clock clock, ObjectProvider<BuildProperties> build) {
        this.messages = messages;
        this.clock = clock;
        BuildProperties props = build.getIfAvailable();
        this.version = props != null ? props.getVersion() : "dev";
    }

    @Operation(summary = "Servis versiyasi, server vaqti va joriy til")
    @GetMapping
    MetaResponse meta() {
        return new MetaResponse("usta-platform", version, Instant.now(clock),
                LocaleContextHolder.getLocale().toLanguageTag(), messages.get("app.welcome"));
    }

    record MetaResponse(String service, String version, Instant serverTime, String locale, String message) {
    }
}
