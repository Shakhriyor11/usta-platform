package uz.usta.platform.shared.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Admin va xavfsizlikka oid amallar jurnali ({@code audit_logs}). Biznes o'zgarish bilan bir
 * tranzaksiyada yoziladi.
 */
public interface AuditLog {

    /**
     * @param actorId    amalni bajargan foydalanuvchi (tizim bo'lsa {@code null})
     * @param action     masalan {@code "master.approve"}
     * @param entityType masalan {@code "master_profile"}
     */
    void record(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> details);
}
