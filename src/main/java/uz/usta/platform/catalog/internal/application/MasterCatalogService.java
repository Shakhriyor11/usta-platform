package uz.usta.platform.catalog.internal.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.usta.platform.catalog.CatalogEvents;
import uz.usta.platform.catalog.internal.domain.CatalogErrorCode;
import uz.usta.platform.catalog.internal.domain.MasterServicePrice;
import uz.usta.platform.catalog.internal.domain.PortfolioItem;
import uz.usta.platform.catalog.internal.domain.ServiceDefinition;
import uz.usta.platform.catalog.internal.persistence.MasterServicePriceRepository;
import uz.usta.platform.catalog.internal.persistence.PortfolioItemRepository;
import uz.usta.platform.catalog.internal.persistence.ServiceDefinitionRepository;
import uz.usta.platform.profile.MasterProfiles;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.outbox.OutboxPublisher;
import uz.usta.platform.storage.FilePurpose;
import uz.usta.platform.storage.FileStorage;
import uz.usta.platform.storage.StoredFile;

/** Ustaning xizmatlari, narxlari va portfoliosi. */
@Service
public class MasterCatalogService {

    private final MasterServicePriceRepository prices;
    private final ServiceDefinitionRepository services;
    private final PortfolioItemRepository portfolio;
    private final MasterProfiles masters;
    private final FileStorage storage;
    private final OutboxPublisher outbox;
    private final CatalogProperties props;

    MasterCatalogService(MasterServicePriceRepository prices, ServiceDefinitionRepository services,
                         PortfolioItemRepository portfolio, MasterProfiles masters, FileStorage storage,
                         OutboxPublisher outbox, CatalogProperties props) {
        this.prices = prices;
        this.services = services;
        this.portfolio = portfolio;
        this.masters = masters;
        this.storage = storage;
        this.outbox = outbox;
        this.props = props;
    }

    /** Usta xizmati va katalogdagi xizmat ta'rifi. */
    public record MasterServiceView(MasterServicePrice price, ServiceDefinition service) {
    }

    // ---------------------------------------------------------------- xizmatlar

    @Transactional(readOnly = true)
    public List<MasterServiceView> myServices(UUID masterId) {
        return withDefinitions(prices.findByMasterIdOrderByCreatedAtAsc(masterId), false);
    }

    /** Mijozlar uchun: faqat ko'rinadigan usta, faol narxlar va faol xizmatlar. */
    @Transactional(readOnly = true)
    public List<MasterServiceView> publicServices(UUID masterId) {
        requirePublic(masterId);
        return withDefinitions(prices.findByMasterIdAndActiveTrueOrderByPriceFromAsc(masterId), true);
    }

    @Transactional
    public MasterServiceView upsertService(UUID masterId, UUID serviceId, BigDecimal priceFrom, String description,
                                           boolean active) {
        masters.requireProfile(masterId);
        ServiceDefinition service = services.findById(serviceId).filter(ServiceDefinition::isActive)
                .orElseThrow(() -> new BusinessException(CatalogErrorCode.SERVICE_NOT_AVAILABLE));
        MasterServicePrice price = prices.findByMasterIdAndServiceId(masterId, serviceId).orElse(null);
        if (price == null) {
            if (prices.countByMasterId(masterId) >= props.maxServicesPerMaster()) {
                throw new BusinessException(CatalogErrorCode.MASTER_SERVICE_LIMIT_EXCEEDED, props.maxServicesPerMaster());
            }
            price = new MasterServicePrice(masterId, serviceId);
        }
        price.update(priceFrom, description == null || description.isBlank() ? null : description.strip(), active);
        MasterServicePrice saved = prices.saveAndFlush(price);
        servicesChanged(masterId);
        return new MasterServiceView(saved, service);
    }

    @Transactional
    public void removeService(UUID masterId, UUID serviceId) {
        MasterServicePrice price = prices.findByMasterIdAndServiceId(masterId, serviceId)
                .orElseThrow(() -> BusinessException.notFound("MasterService", serviceId));
        prices.delete(price);
        servicesChanged(masterId);
    }

    private List<MasterServiceView> withDefinitions(List<MasterServicePrice> list, boolean onlyActiveServices) {
        Map<UUID, ServiceDefinition> defs = services.findAllById(list.stream().map(MasterServicePrice::getServiceId).toList())
                .stream().collect(Collectors.toMap(ServiceDefinition::getId, Function.identity()));
        return list.stream()
                .filter(p -> defs.containsKey(p.getServiceId()))
                .filter(p -> !onlyActiveServices || defs.get(p.getServiceId()).isActive())
                .map(p -> new MasterServiceView(p, defs.get(p.getServiceId())))
                .toList();
    }

    private void servicesChanged(UUID masterId) {
        outbox.publish("master_profile", masterId, CatalogEvents.MASTER_SERVICES_CHANGED,
                new CatalogEvents.MasterServicesChanged(masterId));
    }

    // ---------------------------------------------------------------- portfolio

    @Transactional(readOnly = true)
    public List<PortfolioItem> myPortfolio(UUID masterId) {
        return portfolio.findByMasterIdOrderByCreatedAtDesc(masterId);
    }

    @Transactional(readOnly = true)
    public List<PortfolioItem> publicPortfolio(UUID masterId) {
        requirePublic(masterId);
        return portfolio.findByMasterIdOrderByCreatedAtDesc(masterId);
    }

    @Transactional
    public PortfolioItem addPortfolioItem(UUID masterId, MultipartFile file, String description) {
        masters.requireProfile(masterId);
        if (portfolio.countByMasterId(masterId) >= props.maxPortfolioItems()) {
            throw new BusinessException(CatalogErrorCode.PORTFOLIO_LIMIT_EXCEEDED, props.maxPortfolioItems());
        }
        StoredFile stored = storage.store(FilePurpose.PORTFOLIO, masterId, file);
        return portfolio.saveAndFlush(new PortfolioItem(masterId, stored.key(), stored.contentType(), stored.sizeBytes(),
                description == null || description.isBlank() ? null : description.strip()));
    }

    @Transactional
    public void removePortfolioItem(UUID masterId, UUID itemId) {
        PortfolioItem item = portfolio.findByIdAndMasterId(itemId, masterId)
                .orElseThrow(() -> BusinessException.notFound("PortfolioItem", itemId));
        portfolio.delete(item);
        storage.deleteAfterCommit(item.getFileKey());
    }

    private void requirePublic(UUID masterId) {
        if (!masters.isPublic(masterId)) {
            throw BusinessException.notFound("Master", masterId);
        }
    }
}
