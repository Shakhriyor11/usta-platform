package uz.usta.platform.catalog.internal.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uz.usta.platform.shared.error.BusinessException;

class CategoryHierarchyTest {

    // a -> b -> c (3 daraxt darajasi), d — alohida ildiz
    final UUID a = UUID.randomUUID();
    final UUID b = UUID.randomUUID();
    final UUID c = UUID.randomUUID();
    final UUID d = UUID.randomUUID();
    final CategoryHierarchy tree;

    CategoryHierarchyTest() {
        Map<UUID, UUID> parentOf = new HashMap<>();
        parentOf.put(a, null);
        parentOf.put(b, a);
        parentOf.put(c, b);
        parentOf.put(d, null);
        tree = new CategoryHierarchy(parentOf);
    }

    @Test
    void newCategoryUnderShallowParentIsAllowed() {
        assertThatCode(() -> tree.validateMove(null, b, 3)).doesNotThrowAnyException();
        assertThatCode(() -> tree.validateMove(null, null, 3)).doesNotThrowAnyException();
    }

    @Test
    void newCategoryBelowMaxDepthIsRejected() {
        assertError(() -> tree.validateMove(null, c, 3), CatalogErrorCode.CATEGORY_DEPTH_EXCEEDED);
    }

    @Test
    void movingUnderOwnDescendantIsACycle() {
        assertError(() -> tree.validateMove(a, c, 10), CatalogErrorCode.CATEGORY_CYCLE);
        assertError(() -> tree.validateMove(a, a, 10), CatalogErrorCode.CATEGORY_CYCLE);
    }

    @Test
    void movingSubtreeCountsItsHeight() {
        // b (balandligi 2) ni d ostiga: 1 + 2 = 3 — ruxsat
        assertThatCode(() -> tree.validateMove(b, d, 3)).doesNotThrowAnyException();
        // a (balandligi 3) ni d ostiga: 1 + 3 = 4 — oshib ketadi
        assertError(() -> tree.validateMove(a, d, 3), CatalogErrorCode.CATEGORY_DEPTH_EXCEEDED);
        // ildizga ko'chirish — faqat o'z balandligi
        assertThatCode(() -> tree.validateMove(c, null, 1)).doesNotThrowAnyException();
        assertError(() -> tree.validateMove(a, null, 2), CatalogErrorCode.CATEGORY_DEPTH_EXCEEDED);
    }

    private static void assertError(Runnable r, CatalogErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }
}
