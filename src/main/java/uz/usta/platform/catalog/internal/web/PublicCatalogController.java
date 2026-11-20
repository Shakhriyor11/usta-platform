package uz.usta.platform.catalog.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.catalog.internal.application.CatalogQueryService;
import uz.usta.platform.catalog.internal.web.CatalogDtos.CategoryTreeResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.ServiceResponse;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Catalog", description = "Kategoriyalar va xizmatlar (autentifikatsiyasiz, Accept-Language bo'yicha)")
@RestController
@RequestMapping(ApiPaths.PUBLIC + "/catalog")
class PublicCatalogController {

    private final CatalogQueryService catalog;
    private final CatalogWebMapper mapper;

    PublicCatalogController(CatalogQueryService catalog, CatalogWebMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    @Operation(summary = "Faol kategoriyalar daraxti")
    @GetMapping("/categories")
    List<CategoryTreeResponse> categories() {
        return mapper.tree(catalog.activeTree());
    }

    @Operation(summary = "Kategoriyadagi faol xizmatlar")
    @GetMapping("/categories/{id}/services")
    List<ServiceResponse> services(@PathVariable UUID id) {
        return catalog.activeServices(id).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Xizmatlarni nomi bo'yicha qidirish (uz/ru/en)")
    @GetMapping("/services")
    List<ServiceResponse> search(@RequestParam("q") @NotBlank @Size(min = 2, max = 100) String query) {
        return catalog.search(query).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Xizmat")
    @GetMapping("/services/{id}")
    ServiceResponse service(@PathVariable UUID id) {
        return mapper.toResponse(catalog.activeService(id));
    }
}
