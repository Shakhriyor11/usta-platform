package uz.usta.platform.catalog.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.usta.platform.catalog.internal.application.MasterCatalogService;
import uz.usta.platform.catalog.internal.web.CatalogDtos.PortfolioItemResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.PublicMasterServiceResponse;
import uz.usta.platform.shared.web.ApiPaths;

@Tag(name = "Masters", description = "Ustalar kartochkasi (faqat tasdiqlangan va faol)")
@RestController
@RequestMapping(ApiPaths.V1 + "/masters")
class PublicMasterCatalogController {

    private final MasterCatalogService service;
    private final CatalogWebMapper mapper;

    PublicMasterCatalogController(MasterCatalogService service, CatalogWebMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Operation(summary = "Usta xizmatlari va narxlari (arzonidan)")
    @GetMapping("/{id}/services")
    List<PublicMasterServiceResponse> services(@PathVariable UUID id) {
        return service.publicServices(id).stream().map(mapper::toPublic).toList();
    }

    @Operation(summary = "Usta portfoliosi")
    @GetMapping("/{id}/portfolio")
    List<PortfolioItemResponse> portfolio(@PathVariable UUID id) {
        return service.publicPortfolio(id).stream().map(mapper::toResponse).toList();
    }
}
