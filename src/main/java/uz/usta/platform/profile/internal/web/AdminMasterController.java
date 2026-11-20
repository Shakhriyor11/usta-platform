package uz.usta.platform.profile.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.identity.UserDirectory;
import uz.usta.platform.profile.internal.application.MasterVerificationService;
import uz.usta.platform.profile.internal.domain.MasterProfile;
import uz.usta.platform.profile.internal.domain.VerificationStatus;
import uz.usta.platform.profile.internal.web.ProfileDtos.AdminMasterDetailResponse;
import uz.usta.platform.profile.internal.web.ProfileDtos.AdminMasterResponse;
import uz.usta.platform.profile.internal.web.ProfileDtos.MasterProfileResponse;
import uz.usta.platform.profile.internal.web.ProfileDtos.RejectMasterRequest;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.PageResponse;

@Tag(name = "Admin: Masters", description = "Ustalarni tasdiqlash / rad etish")
@RestController
@RequestMapping(ApiPaths.ADMIN + "/masters")
class AdminMasterController {

    private final MasterVerificationService service;
    private final UserDirectory users;
    private final ProfileWebMapper mapper;

    AdminMasterController(MasterVerificationService service, UserDirectory users, ProfileWebMapper mapper) {
        this.service = service;
        this.users = users;
        this.mapper = mapper;
    }

    @Operation(summary = "Ustalar ro'yxati", description = "Tasdiqlash navbati: ?status=PENDING&sort=submittedAt,asc")
    @GetMapping
    PageResponse<AdminMasterResponse> list(@RequestParam(required = false) VerificationStatus status,
                                           @ParameterObject @PageableDefault(sort = "createdAt",
                                                   direction = Sort.Direction.DESC) Pageable pageable) {
        Page<MasterProfile> page = service.search(status, pageable);
        Map<UUID, String> phones = users.phones(page.map(MasterProfile::getId).getContent());
        return PageResponse.of(page, m -> new AdminMasterResponse(phones.get(m.getId()), mapper.toResponse(m)));
    }

    @Operation(summary = "Usta tafsilotlari va hujjatlari (presigned URL bilan)")
    @GetMapping("/{id}")
    AdminMasterDetailResponse get(@PathVariable UUID id) {
        MasterProfile m = service.get(id);
        return new AdminMasterDetailResponse(users.phones(List.of(id)).get(id), mapper.toResponse(m),
                service.documents(id).stream().map(mapper::toResponse).toList());
    }

    @Operation(summary = "Tasdiqlash (PENDING -> APPROVED)")
    @PostMapping("/{id}/approve")
    MasterProfileResponse approve(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id) {
        return mapper.toResponse(service.approve(admin.id(), id));
    }

    @Operation(summary = "Rad etish (PENDING|APPROVED -> REJECTED)", description = "Sabab ustaga ko'rsatiladi")
    @PostMapping("/{id}/reject")
    MasterProfileResponse reject(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                 @Valid @RequestBody RejectMasterRequest body) {
        return mapper.toResponse(service.reject(admin.id(), id, body.reason()));
    }
}
