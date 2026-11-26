package uz.usta.platform.identity.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.identity.internal.application.AdminManagementService;
import uz.usta.platform.identity.internal.web.IdentityDtos.AdminResponse;
import uz.usta.platform.identity.internal.web.IdentityDtos.AppointAdminRequest;
import uz.usta.platform.identity.internal.web.IdentityDtos.PermissionResponse;
import uz.usta.platform.identity.internal.web.IdentityDtos.PermissionsRequest;
import uz.usta.platform.shared.i18n.Messages;
import uz.usta.platform.shared.security.AdminPermission;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Super admin: Admins", description = "Adminlarni tayinlash va huquqlari (faqat SUPER_ADMIN)")
@RestController
@RequestMapping(ApiPaths.ADMIN + "/admins")
class AdminManagementController {

    private final AdminManagementService service;
    private final Messages messages;

    AdminManagementController(AdminManagementService service, Messages messages) {
        this.service = service;
        this.messages = messages;
    }

    @Operation(summary = "Adminlar ro'yxati (super adminlar bilan)")
    @GetMapping
    List<AdminResponse> admins() {
        return service.admins().stream().map(AdminResponse::of).toList();
    }

    @Operation(summary = "Beriladigan huquqlar ro'yxati (nomi so'rov tilida)")
    @GetMapping("/permissions")
    List<PermissionResponse> permissions() {
        return Arrays.stream(AdminPermission.values())
                .map(p -> new PermissionResponse(p, messages.get("permission." + p.name().toLowerCase(Locale.ROOT))))
                .toList();
    }

    @Operation(summary = "Admin tayinlash", description = "Telefon bo'yicha; hisob bo'lmasa yaratiladi")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdminResponse appoint(@AuthenticationPrincipal AuthenticatedUser actor,
                          @Valid @RequestBody AppointAdminRequest body) {
        return AdminResponse.of(service.appoint(actor.id(), body.phone(), body.permissions()));
    }

    @Operation(summary = "Huquqlarni almashtirish")
    @PutMapping("/{id}")
    AdminResponse update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
                         @Valid @RequestBody PermissionsRequest body) {
        return AdminResponse.of(service.updatePermissions(actor.id(), id, body.permissions()));
    }

    @Operation(summary = "Adminlikni olib tashlash", description = "Boshqa rollari (mijoz/usta) qoladi")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        service.remove(actor.id(), id);
    }
}
