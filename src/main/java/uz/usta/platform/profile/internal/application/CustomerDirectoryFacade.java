package uz.usta.platform.profile.internal.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.profile.CustomerDirectory;
import uz.usta.platform.profile.internal.domain.Address;
import uz.usta.platform.profile.internal.domain.CustomerProfile;
import uz.usta.platform.profile.internal.persistence.AddressRepository;
import uz.usta.platform.profile.internal.persistence.CustomerProfileRepository;
import uz.usta.platform.shared.error.BusinessException;

@Component
class CustomerDirectoryFacade implements CustomerDirectory {

    private final AddressRepository addresses;
    private final CustomerProfileRepository customers;

    CustomerDirectoryFacade(AddressRepository addresses, CustomerProfileRepository customers) {
        this.addresses = addresses;
        this.customers = customers;
    }

    @Override
    @Transactional(readOnly = true)
    public AddressSnapshot address(UUID userId, UUID addressId) {
        Address a = addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> BusinessException.notFound("Address", addressId));
        return new AddressSnapshot(a.getTitle(), a.getAddress(), a.getDetails(), a.getLatitude(), a.getLongitude());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> names(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return customers.findAllById(userIds).stream()
                .collect(Collectors.toMap(CustomerProfile::getId, CustomerProfile::getFullName));
    }
}
