package uz.usta.platform.profile.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.profile.internal.application.MasterProfileService;
import uz.usta.platform.profile.internal.web.ProfileDtos.PublicMasterResponse;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Masters", description = "Ustalar kartochkasi (faqat tasdiqlangan va faol)")
@RestController
@RequestMapping(ApiPaths.V1 + "/masters")
class PublicMasterController {

    private final MasterProfileService profiles;
    private final ProfileWebMapper mapper;

    PublicMasterController(MasterProfileService profiles, ProfileWebMapper mapper) {
        this.profiles = profiles;
        this.mapper = mapper;
    }

    @Operation(summary = "Usta kartochkasi")
    @GetMapping("/{id}")
    PublicMasterResponse get(@PathVariable UUID id) {
        return mapper.toPublic(profiles.getPublic(id));
    }
}
