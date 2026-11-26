package uz.usta.platform.admin.internal.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.shared.audit.AuditLog;
import uz.usta.platform.shared.outbox.OutboxDeadLetters;

/** Yetkazilmay qolgan eventlarni boshqarish (audit bilan). */
@Service
public class OutboxAdminService {

    private final OutboxDeadLetters deadLetters;
    private final AuditLog audit;

    OutboxAdminService(OutboxDeadLetters deadLetters, AuditLog audit) {
        this.deadLetters = deadLetters;
        this.audit = audit;
    }

    public List<OutboxDeadLetters.DeadEvent> dead(int limit) {
        return deadLetters.list(limit);
    }

    @Transactional
    public int retryDead(UUID adminId) {
        int count = deadLetters.requeueAll();
        audit.record(adminId, "outbox.retry-dead", "outbox", adminId, Map.of("count", count));
        return count;
    }
}
