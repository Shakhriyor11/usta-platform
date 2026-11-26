package uz.usta.platform.chat.internal.web;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.usta.platform.chat.internal.application.ChatEvent;

/** WebSocket hodisasi hech bir REST javobida uchramaydi — sxemasini hujjatga qo'lda qo'shamiz. */
@Configuration(proxyBeanMethods = false)
class ChatApiDocs {

    @Bean
    GlobalOpenApiCustomizer chatEventSchema() {
        return api -> {
            if (api.getComponents() == null) {
                api.setComponents(new Components());
            }
            ModelConverters.getInstance(true).readAll(ChatEvent.class).forEach((name, schema) -> {
                if (api.getComponents().getSchemas() == null || !api.getComponents().getSchemas().containsKey(name)) {
                    api.getComponents().addSchemas(name, schema);
                }
            });
        };
    }
}
