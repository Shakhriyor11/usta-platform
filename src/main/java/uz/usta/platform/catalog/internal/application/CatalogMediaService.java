package uz.usta.platform.catalog.internal.application;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.usta.platform.catalog.internal.domain.CatalogMedia;
import uz.usta.platform.catalog.internal.domain.Category;
import uz.usta.platform.catalog.internal.domain.ServiceDefinition;
import uz.usta.platform.catalog.internal.persistence.CategoryRepository;
import uz.usta.platform.catalog.internal.persistence.ServiceDefinitionRepository;
import uz.usta.platform.shared.audit.AuditLog;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.storage.FilePurpose;
import uz.usta.platform.storage.FileStorage;

/**
 * Kategoriya va xizmat rasmi hamda admin yuklagan ikonka. Yuklash DB tranzaksiyasi tashqarisida bo'ladi, shuning
 * uchun element yozish paytida qayta o'qiladi (oraliqda o'chirilgan bo'lsa, fayl ham saqlanmaydi).
 */
@Service
public class CatalogMediaService {

    public enum Slot {
        IMAGE(FilePurpose.CATALOG_IMAGE, CatalogMedia::replaceImage),
        ICON(FilePurpose.CATALOG_ICON, CatalogMedia::replaceCustomIcon);

        private final FilePurpose purpose;
        private final BiFunction<CatalogMedia, String, String> replace;

        Slot(FilePurpose purpose, BiFunction<CatalogMedia, String, String> replace) {
            this.purpose = purpose;
            this.replace = replace;
        }
    }

    private final CategoryRepository categories;
    private final ServiceDefinitionRepository services;
    private final FileStorage storage;
    private final AuditLog audit;

    CatalogMediaService(CategoryRepository categories, ServiceDefinitionRepository services, FileStorage storage,
                        AuditLog audit) {
        this.categories = categories;
        this.services = services;
        this.storage = storage;
        this.audit = audit;
    }

    public Category uploadCategory(UUID adminId, UUID id, Slot slot, MultipartFile file) {
        category(id); // yuklashdan oldin tez rad etish
        return storage.storeAndSave(slot.purpose, id, file, stored -> {
            Category category = category(id);
            apply(adminId, "category", id, category.getMedia(), slot, stored.key());
            return categories.saveAndFlush(category);
        });
    }

    @Transactional
    public Category removeFromCategory(UUID adminId, UUID id, Slot slot) {
        Category category = category(id);
        apply(adminId, "category", id, category.getMedia(), slot, null);
        return categories.saveAndFlush(category);
    }

    public ServiceDefinition uploadService(UUID adminId, UUID id, Slot slot, MultipartFile file) {
        service(id);
        return storage.storeAndSave(slot.purpose, id, file, stored -> {
            ServiceDefinition service = service(id);
            apply(adminId, "service", id, service.getMedia(), slot, stored.key());
            return services.saveAndFlush(service);
        });
    }

    @Transactional
    public ServiceDefinition removeFromService(UUID adminId, UUID id, Slot slot) {
        ServiceDefinition service = service(id);
        apply(adminId, "service", id, service.getMedia(), slot, null);
        return services.saveAndFlush(service);
    }

    private void apply(UUID adminId, String entity, UUID id, CatalogMedia media, Slot slot, String key) {
        storage.deleteAfterCommit(slot.replace.apply(media, key));
        String action = "catalog." + entity + "." + slot.name().toLowerCase(Locale.ROOT)
                + (key == null ? ".remove" : ".upload");
        audit.record(adminId, action, entity, id, Map.of());
    }

    private Category category(UUID id) {
        return categories.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> BusinessException.notFound("Category", id));
    }

    private ServiceDefinition service(UUID id) {
        return services.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> BusinessException.notFound("Service", id));
    }
}
