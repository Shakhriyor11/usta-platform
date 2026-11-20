package uz.usta.platform.search.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.search.internal.application.SearchIndexer;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Admin: Search", description = "Qidiruv indeksini boshqarish")
@RestController
@RequestMapping(ApiPaths.ADMIN + "/search")
class AdminSearchController {

    private final SearchIndexer indexer;

    AdminSearchController(SearchIndexer indexer) {
        this.indexer = indexer;
    }

    @Operation(summary = "Indeksni to'liq qayta qurish", description = "Odatda kerak emas — indeks eventlar orqali yangilanadi")
    @PostMapping("/reindex")
    Map<String, Integer> reindex() {
        return Map.of("processed", indexer.rebuild());
    }
}
