package uz.usta.platform.catalog.internal.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import uz.usta.platform.shared.error.BusinessException;

/**
 * Kategoriya daraxti qoidalari (sof funksiya — DB'siz test qilinadi): sikl bo'lmasligi va maksimal chuqurlik.
 */
public final class CategoryHierarchy {

    private final Map<UUID, UUID> parentOf;
    private final Map<UUID, List<UUID>> childrenOf = new HashMap<>();

    /** @param parentOf barcha kategoriyalar: id -> parentId (ildiz uchun null) */
    public CategoryHierarchy(Map<UUID, UUID> parentOf) {
        this.parentOf = new HashMap<>(parentOf);
        this.parentOf.forEach((id, parent) -> {
            if (parent != null) {
                childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(id);
            }
        });
    }

    /**
     * {@code categoryId} ni (yangi bo'lsa — null) {@code newParentId} ostiga qo'yish mumkinligini tekshiradi.
     *
     * @throws BusinessException CATEGORY_CYCLE yoki CATEGORY_DEPTH_EXCEEDED
     */
    public void validateMove(UUID categoryId, UUID newParentId, int maxDepth) {
        if (newParentId == null) {
            if (categoryId != null && height(categoryId) > maxDepth) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_DEPTH_EXCEEDED, maxDepth);
            }
            return;
        }
        int parentDepth = 0;
        Set<UUID> seen = new HashSet<>();
        for (UUID cur = newParentId; cur != null; cur = parentOf.get(cur)) {
            if (cur.equals(categoryId) || !seen.add(cur)) {
                throw new BusinessException(CatalogErrorCode.CATEGORY_CYCLE);
            }
            parentDepth++;
        }
        int subtreeHeight = categoryId == null ? 1 : height(categoryId);
        if (parentDepth + subtreeHeight > maxDepth) {
            throw new BusinessException(CatalogErrorCode.CATEGORY_DEPTH_EXCEEDED, maxDepth);
        }
    }

    /** Kategoriya va uning eng chuqur avlodigacha bo'lgan darajalar soni (bargda 1). */
    private int height(UUID id) {
        int max = 0;
        for (UUID child : childrenOf.getOrDefault(id, List.of())) {
            max = Math.max(max, height(child));
        }
        return 1 + max;
    }
}
