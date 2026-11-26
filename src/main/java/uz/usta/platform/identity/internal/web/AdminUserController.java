package uz.usta.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.identity.internal.application.UserAdminService;
import uz.usta.platform.identity.internal.domain.UserStatus;
import uz.usta.platform.identity.internal.web.IdentityDtos.AdminUserResponse;
import uz.usta.platform.identity.internal.web.IdentityDtos.BlockUserRequest;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.security.Role;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.PageResponse;

@Tag(name = "Admin: Users", description = "Foydalanuvchilarni boshqarish")
@RestController
@RequestMapping(ApiPaths.ADMIN + "/users")
class AdminUserController {

    private final UserAdminService service;

    AdminUserController(UserAdminService service) {
        this.service = service;
    }

    @Operation(summary = "Foydalanuvchilar ro'yxati (filtr: telefon qismi, rol, status)")
    @GetMapping
    PageResponse<AdminUserResponse> list(@RequestParam(required = false) String phone,
                                         @RequestParam(required = false) Role role,
                                         @RequestParam(required = false) UserStatus status,
                                         @ParameterObject @PageableDefault(sort = "createdAt",
                                                 direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.search(new UserAdminService.UserFilter(phone, role, status), pageable),
                AdminUserResponse::of);
    }

    @Operation(summary = "Bloklash", description = "Barcha refresh tokenlar bekor qilinadi. Adminni faqat super "
            + "admin bloklaydi, super adminni hech kim.")
    @PostMapping("/{id}/block")
    AdminUserResponse block(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                            @Valid @RequestBody BlockUserRequest body) {
        return AdminUserResponse.of(service.block(admin, id, body.reason()));
    }

    @Operation(summary = "Blokdan chiqarish")
    @PostMapping("/{id}/unblock")
    AdminUserResponse unblock(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id) {
        return AdminUserResponse.of(service.unblock(admin, id));
    }

    @Operation(summary = "Hisobni o'chirish (USERS va DELETE huquqlari)",
            description = "Qaytarib bo'lmaydi: telefon, ism, manzillar, hujjat va rasmlar o'chiriladi; buyurtma va "
                    + "komissiya tarixi egasiz saqlanadi. Boshlangan ishi yoki komissiya qarzi bo'lsa — 409")
    @PreAuthorize("hasAuthority('PERM_DELETE')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id) {
        service.delete(admin, id);
    }
}
