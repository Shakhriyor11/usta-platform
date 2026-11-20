package uz.usta.platform.profile.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import uz.usta.platform.profile.internal.application.MasterProfileService;
import uz.usta.platform.profile.internal.application.MasterVerificationService;
import uz.usta.platform.profile.internal.domain.DocumentType;
import uz.usta.platform.profile.internal.web.ProfileDtos.DocumentResponse;
import uz.usta.platform.profile.internal.web.ProfileDtos.MasterProfileRequest;
import uz.usta.platform.profile.internal.web.ProfileDtos.MasterProfileResponse;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.web.ApiPaths;
import uz.usta.platform.shared.web.GeoPoint;

@Tag(name = "Master: Profile & Verification", description = "Usta profili va verifikatsiya (MASTER roli)")
@RestController
@RequestMapping(ApiPaths.MASTER)
class MasterProfileController {

    private final MasterProfileService profiles;
    private final MasterVerificationService verification;
    private final ProfileWebMapper mapper;

    MasterProfileController(MasterProfileService profiles, MasterVerificationService verification,
                            ProfileWebMapper mapper) {
        this.profiles = profiles;
        this.verification = verification;
        this.mapper = mapper;
    }

    @Operation(summary = "Mening usta profilim")
    @GetMapping("/profile")
    MasterProfileResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.toResponse(profiles.get(user.id()));
    }

    @Operation(summary = "Profilni yaratish yoki yangilash")
    @PutMapping("/profile")
    MasterProfileResponse upsert(@AuthenticationPrincipal AuthenticatedUser user,
                                 @Valid @RequestBody MasterProfileRequest body) {
        GeoPoint loc = body.workLocation();
        return mapper.toResponse(profiles.upsert(user.id(), new MasterProfileService.MasterProfileCommand(
                body.fullName(), body.bio(), body.experienceYears(),
                loc == null ? null : loc.latitude(), loc == null ? null : loc.longitude(), body.workRadiusKm())));
    }

    @Operation(summary = "Avatar yuklash (JPEG/PNG/WEBP, 5MB gacha)")
    @PutMapping(path = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MasterProfileResponse avatar(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestPart("file") MultipartFile file) {
        return mapper.toResponse(profiles.changeAvatar(user.id(), file));
    }

    @Operation(summary = "Verifikatsiya hujjatlari")
    @GetMapping("/verification/documents")
    List<DocumentResponse> documents(@AuthenticationPrincipal AuthenticatedUser user) {
        return verification.documents(user.id()).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Hujjat yuklash (JPEG/PNG/WEBP/PDF, 10MB gacha)",
            description = "Faqat NOT_SUBMITTED yoki REJECTED holatda. Majburiy: PASSPORT, SELFIE (config).")
    @PostMapping(path = "/verification/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    DocumentResponse upload(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam DocumentType type,
                            @RequestPart("file") MultipartFile file) {
        return mapper.toResponse(verification.upload(user.id(), type, file));
    }

    @Operation(summary = "Hujjatni o'chirish")
    @DeleteMapping("/verification/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDocument(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        verification.deleteDocument(user.id(), id);
    }

    @Operation(summary = "Verifikatsiyaga yuborish",
            description = "Talablar: ish hududi (workLocation) va majburiy hujjatlar. Natija: PENDING.")
    @PostMapping("/verification/submit")
    MasterProfileResponse submit(@AuthenticationPrincipal AuthenticatedUser user) {
        return mapper.toResponse(verification.submit(user.id()));
    }
}
