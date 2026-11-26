package uz.usta.platform.catalog.internal.web;

import java.util.List;
import org.springframework.stereotype.Component;
import uz.usta.platform.catalog.internal.application.CatalogQueryService.CategoryNode;
import uz.usta.platform.catalog.internal.application.IconLibrary;
import uz.usta.platform.catalog.internal.application.MasterCatalogService.MasterServiceView;
import uz.usta.platform.catalog.internal.domain.CatalogMedia;
import uz.usta.platform.catalog.internal.domain.Category;
import uz.usta.platform.catalog.internal.domain.PortfolioItem;
import uz.usta.platform.catalog.internal.domain.ServiceDefinition;
import uz.usta.platform.catalog.internal.web.CatalogDtos.CategoryAdminResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.CategoryTreeResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.IconResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.MasterServiceResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.PortfolioItemResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.PublicMasterServiceResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.ServiceAdminResponse;
import uz.usta.platform.catalog.internal.web.CatalogDtos.ServiceResponse;
import uz.usta.platform.shared.i18n.LocalizedText;
import uz.usta.platform.storage.FileStorage;

/**
 * Entity -> DTO. Ko'p tilli maydonlar ommaviy javoblarda joriy tilga ({@code Accept-Language}) o'giriladi,
 * admin javoblarida esa to'liq (uz/ru/en/uzCyrl) qaytadi. Bu yerda asosan "resolve" mantig'i bo'lgani uchun
 * MapStruct o'rniga oddiy mapper.
 */
@Component
class CatalogWebMapper {

    private final FileStorage storage;
    private final IconLibrary icons;

    CatalogWebMapper(FileStorage storage, IconLibrary icons) {
        this.storage = storage;
        this.icons = icons;
    }

    List<CategoryTreeResponse> tree(List<CategoryNode> nodes) {
        return nodes.stream()
                .map(n -> {
                    Category c = n.category();
                    return new CategoryTreeResponse(c.getId(), c.getSlug(), c.getName().current(),
                            c.getMedia().getIcon(), iconUrl(c.getMedia()), imageUrl(c.getMedia()), tree(n.children()));
                })
                .toList();
    }

    ServiceResponse toResponse(ServiceDefinition s) {
        return new ServiceResponse(s.getId(), s.getCategoryId(), s.getSlug(), s.getName().current(),
                current(s.getDescription()), s.getMedia().getIcon(), iconUrl(s.getMedia()), imageUrl(s.getMedia()));
    }

    MasterServiceResponse toResponse(MasterServiceView v) {
        return new MasterServiceResponse(v.service().getId(), v.service().getCategoryId(), v.service().getSlug(),
                v.service().getName().current(), v.price().getPriceFrom(), v.price().getDescription(),
                v.price().isActive(), v.service().isActive(), v.price().getUpdatedAt());
    }

    PublicMasterServiceResponse toPublic(MasterServiceView v) {
        return new PublicMasterServiceResponse(v.service().getId(), v.service().getCategoryId(),
                v.service().getName().current(), v.price().getPriceFrom(), v.price().getDescription());
    }

    PortfolioItemResponse toResponse(PortfolioItem p) {
        return new PortfolioItemResponse(p.getId(), storage.url(p.getFileKey()), p.getContentType(), p.getSizeBytes(),
                p.getDescription(), p.getCreatedAt());
    }

    CategoryAdminResponse toAdmin(Category c) {
        CatalogMedia media = c.getMedia();
        return new CategoryAdminResponse(c.getId(), c.getParentId(), c.getSlug(), c.getName(), media.getIcon(),
                iconUrl(media), media.getIconKey() != null, imageUrl(media), c.getSortOrder(), c.getStatus(),
                c.getCreatedAt(), c.getUpdatedAt());
    }

    ServiceAdminResponse toAdmin(ServiceDefinition s) {
        CatalogMedia media = s.getMedia();
        return new ServiceAdminResponse(s.getId(), s.getCategoryId(), s.getSlug(), s.getName(), s.getDescription(),
                media.getIcon(), iconUrl(media), media.getIconKey() != null, imageUrl(media), s.getSortOrder(),
                s.getStatus(), s.getCreatedAt(), s.getUpdatedAt());
    }

    IconResponse toResponse(IconLibrary.Icon icon) {
        return new IconResponse(icon.key(), icon.name().current(), icons.url(icon.key()));
    }

    private String iconUrl(CatalogMedia media) {
        return media.getIconKey() != null ? storage.url(media.getIconKey()) : icons.url(media.getIcon());
    }

    private String imageUrl(CatalogMedia media) {
        return storage.url(media.getImageKey());
    }

    private static String current(LocalizedText text) {
        return text == null ? null : text.current();
    }
}
