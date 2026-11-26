package uz.usta.platform.catalog.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.usta.platform.catalog.internal.application.CatalogAdminService;
import uz.usta.platform.catalog.internal.application.CatalogAdminService.CategoryCommand;
import uz.usta.platform.catalog.internal.application.CatalogAdminService.ServiceCommand;
import uz.usta.platform.catalog.internal.application.CatalogMediaService;
import uz.usta.platform.catalog.internal.application.CatalogMediaService.Slot;
import uz.usta.platform.catalog.internal.domain.CatalogStatus;
import uz.usta.platform.catalog.internal.web.CatalogDtos.CategoryAdminResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.CategoryRequest;
import uz.usta.platform.catalog.internal.web.CatalogDtos.ServiceAdminResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.ServiceRequest;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Admin: Catalog", description = """
        Kategoriya va xizmatlarni boshqarish (CATALOG huquqi). Vaqtincha yashirish — status=INACTIVE; o'chirish
        uchun qo'shimcha DELETE huquqi kerak (o'chirilgan element tarixda qoladi, qaytarib bo'lmaydi).""")
@RestController
@RequestMapping(ApiPaths.ADMIN + "/catalog")
class AdminCatalogController {

    private static final String CAN_DELETE = "hasAuthority('PERM_DELETE')";
    private static final String IMAGE_RULES = "multipart, maydon: file. JPEG/PNG/WEBP, 5 MB gacha, 200–4096 px";
    private static final String ICON_RULES =
            "multipart, maydon: file. Kvadrat PNG yoki WEBP (SVG qabul qilinmaydi), 512 KB gacha, 48–1024 px";

    private final CatalogAdminService service;
    private final CatalogMediaService media;
    private final CatalogWebMapper mapper;

    AdminCatalogController(CatalogAdminService service, CatalogMediaService media, CatalogWebMapper mapper) {
        this.service = service;
        this.media = media;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- kategoriyalar

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

    @Operation(summary = "Kategoriyani o'chirish", description = "Ichida kategoriya yoki xizmat bo'lsa 409; "
            + "?cascade=true — butun shox xizmatlari bilan o'chadi")
    @DeleteMapping("/categories/{id}")
    @PreAuthorize(CAN_DELETE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCategory(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                        @RequestParam(defaultValue = "false") boolean cascade) {
        service.deleteCategory(admin.id(), id, cascade);
    }

    @Operation(summary = "Kategoriya rasmini yuklash", description = IMAGE_RULES)
    @PutMapping(path = "/categories/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    CategoryAdminResponse categoryImage(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                        @RequestPart("file") MultipartFile file) {
        return mapper.toAdmin(media.uploadCategory(admin.id(), id, Slot.IMAGE, file));
    }

    @Operation(summary = "Kategoriya rasmini olib tashlash")
    @DeleteMapping("/categories/{id}/image")
    CategoryAdminResponse removeCategoryImage(@AuthenticationPrincipal AuthenticatedUser admin,
                                              @PathVariable UUID id) {
        return mapper.toAdmin(media.removeFromCategory(admin.id(), id, Slot.IMAGE));
    }

    @Operation(summary = "Kategoriyaga o'z ikonkasini yuklash", description = ICON_RULES)
    @PutMapping(path = "/categories/{id}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    CategoryAdminResponse categoryIcon(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                       @RequestPart("file") MultipartFile file) {
        return mapper.toAdmin(media.uploadCategory(admin.id(), id, Slot.ICON, file));
    }

    @Operation(summary = "Yuklangan kategoriya ikonkasini olib tashlash")
    @DeleteMapping("/categories/{id}/icon")
    CategoryAdminResponse removeCategoryIcon(@AuthenticationPrincipal AuthenticatedUser admin,
                                             @PathVariable UUID id) {
        return mapper.toAdmin(media.removeFromCategory(admin.id(), id, Slot.ICON));
    }

    // ---------------------------------------------------------------- xizmatlar

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

    @Operation(summary = "Xizmatni o'chirish", description = "Ustalarning shu xizmatga narxlari ham o'chadi; "
            + "eski buyurtmalar o'zgarmaydi")
    @DeleteMapping("/services/{id}")
    @PreAuthorize(CAN_DELETE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteService(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id) {
        service.deleteService(admin.id(), id);
    }

    @Operation(summary = "Xizmat rasmini yuklash", description = IMAGE_RULES)
    @PutMapping(path = "/services/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ServiceAdminResponse serviceImage(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                      @RequestPart("file") MultipartFile file) {
        return mapper.toAdmin(media.uploadService(admin.id(), id, Slot.IMAGE, file));
    }

    @Operation(summary = "Xizmat rasmini olib tashlash")
    @DeleteMapping("/services/{id}/image")
    ServiceAdminResponse removeServiceImage(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id) {
        return mapper.toAdmin(media.removeFromService(admin.id(), id, Slot.IMAGE));
    }

    @Operation(summary = "Xizmatga o'z ikonkasini yuklash", description = ICON_RULES)
    @PutMapping(path = "/services/{id}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ServiceAdminResponse serviceIcon(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                     @RequestPart("file") MultipartFile file) {
        return mapper.toAdmin(media.uploadService(admin.id(), id, Slot.ICON, file));
    }

    @Operation(summary = "Yuklangan xizmat ikonkasini olib tashlash")
    @DeleteMapping("/services/{id}/icon")
    ServiceAdminResponse removeServiceIcon(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id) {
        return mapper.toAdmin(media.removeFromService(admin.id(), id, Slot.ICON));
    }

    private static CategoryCommand toCommand(CategoryRequest b) {
        return new CategoryCommand(b.parentId(), b.slug(), b.name().toText(), CatalogDtos.blankToNull(b.icon()),
                b.sortOrder() == null ? 0 : b.sortOrder(), b.status() == null ? CatalogStatus.ACTIVE : b.status());
    }

    private static ServiceCommand toCommand(ServiceRequest b) {
        return new ServiceCommand(b.categoryId(), b.slug(), b.name().toText(),
                b.description() == null ? null : b.description().toText(), CatalogDtos.blankToNull(b.icon()),
                b.sortOrder() == null ? 0 : b.sortOrder(), b.status() == null ? CatalogStatus.ACTIVE : b.status());
    }
}
