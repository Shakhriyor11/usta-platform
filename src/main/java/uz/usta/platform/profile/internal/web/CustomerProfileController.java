package uz.usta.platform.profile.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.usta.platform.profile.internal.application.CustomerProfileService;
import uz.usta.platform.profile.internal.web.ProfileDtos.CustomerProfileRequest;
import uz.usta.platform.profile.internal.web.ProfileDtos.CustomerProfileResponse;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Customer: Profile", description = "Mijoz profili (CUSTOMER roli)")
@RestController
@RequestMapping(ApiPaths.CUSTOMER + "/profile")
class CustomerProfileController {

    private final CustomerProfileService service;
    private final ProfileWebMapper mapper;

    CustomerProfileController(CustomerProfileService service, ProfileWebMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Mening profilim", description = "Profil hali yaratilmagan bo'lsa — 404 PROFILE_NOT_FOUND")
    @GetMapping
    CustomerProfileResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.toResponse(service.get(user.id()));
    }

    @Operation(summary = "Profilni yaratish yoki yangilash")
    @PutMapping
    CustomerProfileResponse upsert(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody CustomerProfileRequest body) {
        return mapper.toResponse(service.upsert(user.id(), body.fullName()));
    }

    @Operation(summary = "Avatar yuklash (JPEG/PNG/WEBP, 5MB gacha)")
    @PutMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    CustomerProfileResponse avatar(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestPart("file") MultipartFile file) {
        return mapper.toResponse(service.changeAvatar(user.id(), file));
    }
}
