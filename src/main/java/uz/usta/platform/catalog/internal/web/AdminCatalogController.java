package uz.usta.platform.catalog.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.catalog.internal.application.CatalogAdminService;
import uz.usta.platform.catalog.internal.application.CatalogAdminService.CategoryCommand;
import uz.usta.platform.catalog.internal.application.CatalogAdminService.ServiceCommand;
import uz.usta.platform.catalog.internal.domain.CatalogStatus;
import uz.usta.platform.catalog.internal.web.CatalogDtos.CategoryAdminResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.CategoryRequest;
import uz.usta.platform.catalog.internal.web.CatalogDtos.ServiceAdminResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.ServiceRequest;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Admin: Catalog", description = "Kategoriya va xizmatlarni boshqarish. O'chirish yo'q — status=INACTIVE")
@RestController
@RequestMapping(ApiPaths.ADMIN + "/catalog")
class AdminCatalogController {

    private final CatalogAdminService service;
    private final CatalogWebMapper mapper;

    AdminCatalogController(CatalogAdminService service, CatalogWebMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Barcha kategoriyalar (tekis ro'yxat, nofaollari ham)")
    @GetMapping("/categories")
    List<CategoryAdminResponse> categories() {
        return service.allCategories().stream().map(mapper::toAdmin).toList();
    }

    @Operation(summary = "Kategoriya yaratish")
    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryAdminResponse createCategory(@AuthenticationPrincipal AuthenticatedUser admin,
                                         @Valid @RequestBody CategoryRequest body) {
        return mapper.toAdmin(service.createCategory(admin.id(), toCommand(body)));
    }

    @Operation(summary = "Kategoriyani yangilash (ko'chirish, nomini o'zgartirish, faolsizlantirish)")
    @PutMapping("/categories/{id}")
    CategoryAdminResponse updateCategory(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                         @Valid @RequestBody CategoryRequest body) {
        return mapper.toAdmin(service.updateCategory(admin.id(), id, toCommand(body)));
    }

    @Operation(summary = "Xizmatlar (nofaollari ham)", description = "?categoryId= bo'yicha filtr")
    @GetMapping("/services")
    List<ServiceAdminResponse> services(@RequestParam(required = false) UUID categoryId) {
        return service.services(categoryId).stream().map(mapper::toAdmin).toList();
    }

    @Operation(summary = "Xizmat yaratish")
    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    ServiceAdminResponse createService(@AuthenticationPrincipal AuthenticatedUser admin,
                                       @Valid @RequestBody ServiceRequest body) {
        return mapper.toAdmin(service.createService(admin.id(), toCommand(body)));
    }

    @Operation(summary = "Xizmatni yangilash")
    @PutMapping("/services/{id}")
    ServiceAdminResponse updateService(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                       @Valid @RequestBody ServiceRequest body) {
        return mapper.toAdmin(service.updateService(admin.id(), id, toCommand(body)));
    }

    private static CategoryCommand toCommand(CategoryRequest b) {
        return new CategoryCommand(b.parentId(), b.slug(), b.name().toText(), CatalogDtos.blankToNull(b.icon()),
                b.sortOrder() == null ? 0 : b.sortOrder(), b.status() == null ? CatalogStatus.ACTIVE : b.status());
    }

    private static ServiceCommand toCommand(ServiceRequest b) {
        return new ServiceCommand(b.categoryId(), b.slug(), b.name().toText(),
                b.description() == null ? null : b.description().toText(),
                b.sortOrder() == null ? 0 : b.sortOrder(), b.status() == null ? CatalogStatus.ACTIVE : b.status());
    }
}
