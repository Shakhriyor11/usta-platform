package uz.usta.platform.shared.audit;

import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.shared.util.Uuids;
import uz.usta.platform.shared.web.RequestCorrelationFilter;

@Component
class JdbcAuditLog implements AuditLog {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcAuditLog(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> details) {
        jdbc.sql("""
                        INSERT INTO audit_logs (id, actor_id, action, entity, entity_id, details, trace_id)
                        VALUES (:id, :actor, :action, :entity, :entityId, CAST(:details AS jsonb), :traceId)
                        """)
                .param("id", Uuids.v7())
                .param("actor", actorId)
                .param("action", action)
                .param("entity", entityType)
                .param("entityId", entityId)
                .param("details", json.writeValueAsString(details == null ? Map.of() : details))
                .param("traceId", MDC.get(RequestCorrelationFilter.MDC_KEY))
                .update();
    }
}
