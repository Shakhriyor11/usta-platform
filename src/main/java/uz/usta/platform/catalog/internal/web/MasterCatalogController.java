package uz.usta.platform.catalog.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
import uz.usta.platform.catalog.internal.application.MasterCatalogService;
import uz.usta.platform.catalog.internal.web.CatalogDtos.MasterServiceRequest;
import uz.usta.platform.catalog.internal.web.CatalogDtos.MasterServiceResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.PortfolioItemResponse;
import uz.usta.platform.shared.security.AuthenticatedUser;
import uz.usta.platform.shared.validation.PlainText;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Master: Services & Portfolio", description = "Ustaning xizmatlari, narxlari va portfoliosi (MASTER roli)")
@RestController
@RequestMapping(ApiPaths.MASTER)
class MasterCatalogController {

    private final MasterCatalogService service;
    private final CatalogWebMapper mapper;

    MasterCatalogController(MasterCatalogService service, CatalogWebMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Mening xizmatlarim (nofaollari ham)")
    @GetMapping("/services")
    List<MasterServiceResponse> services(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.myServices(user.id()).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Xizmat qo'shish yoki narxini yangilash",
            description = "Idempotent: xizmat bo'lmasa qo'shiladi, bo'lsa yangilanadi. Avval usta profili kerak.")
    @PutMapping("/services/{serviceId}")
    MasterServiceResponse upsert(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID serviceId,
                                 @Valid @RequestBody MasterServiceRequest body) {
        return mapper.toResponse(service.upsertService(user.id(), serviceId, body.priceFrom(), body.description(),
                body.active() == null || body.active()));
    }

    @Operation(summary = "Xizmatni olib tashlash")
    @DeleteMapping("/services/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID serviceId) {
        service.removeService(user.id(), serviceId);
    }

    @Operation(summary = "Mening portfoliom")
    @GetMapping("/portfolio")
    List<PortfolioItemResponse> portfolio(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.myPortfolio(user.id()).stream().map(mapper::toResponse).toList();
    }

    @Operation(summary = "Portfolioga rasm qo'shish (JPEG/PNG/WEBP, 10MB gacha)")
    @PostMapping(path = "/portfolio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    PortfolioItemResponse addPortfolio(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestPart("file") MultipartFile file,
                                       @RequestParam(required = false) @Size(max = 500) @PlainText String description) {
        return mapper.toResponse(service.addPortfolioItem(user.id(), file, description));
    }

    @Operation(summary = "Portfoliodan o'chirish")
    @DeleteMapping("/portfolio/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removePortfolio(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.removePortfolioItem(user.id(), id);
    }
}
