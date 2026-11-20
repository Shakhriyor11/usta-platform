package uz.usta.platform.profile.internal.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.profile.internal.domain.Address;
import uz.usta.platform.profile.internal.domain.ProfileErrorCode;
import uz.usta.platform.profile.internal.persistence.AddressRepository;
import uz.usta.platform.shared.error.BusinessException;

/** Mijoz manzillari. Birinchi qo'shilgan manzil avtomatik asosiy bo'ladi; har doim ko'pi bilan bitta asosiy. */
@Service
public class AddressService {

    private final AddressRepository addresses;
    private final ProfileProperties props;

    AddressService(AddressRepository addresses, ProfileProperties props) {
        this.addresses = addresses;
        this.props = props;
    }

    public record AddressCommand(String title, String address, String details, double latitude, double longitude,
                                 boolean makeDefault) {
    }

    @Transactional(readOnly = true)
    public List<Address> list(UUID userId) {
        return addresses.findByUserIdOrderByCreatedAtAsc(userId);
    }

    @Transactional(readOnly = true)
    public Address get(UUID userId, UUID id) {
        return find(userId, id);
    }

    @Transactional
    public Address create(UUID userId, AddressCommand cmd) {
        long count = addresses.countByUserId(userId);
        if (count >= props.maxAddresses()) {
            throw new BusinessException(ProfileErrorCode.ADDRESS_LIMIT_EXCEEDED, props.maxAddresses());
        }
        Address address = new Address(userId, cmd.title().strip(), cmd.address().strip(), blankToNull(cmd.details()),
                cmd.latitude(), cmd.longitude());
        if (count == 0 || cmd.makeDefault()) {
            addresses.clearDefault(userId);
            address.markDefault();
        }
        return addresses.saveAndFlush(address);
    }

    @Transactional
    public Address update(UUID userId, UUID id, AddressCommand cmd) {
        Address address = find(userId, id);
        address.update(cmd.title().strip(), cmd.address().strip(), blankToNull(cmd.details()), cmd.latitude(),
                cmd.longitude());
        if (cmd.makeDefault() && !address.isDefaultAddress()) {
            addresses.clearDefault(userId);
            address.markDefault();
        }
        return addresses.saveAndFlush(address);
    }

    @Transactional
    public Address makeDefault(UUID userId, UUID id) {
        Address address = find(userId, id);
        if (!address.isDefaultAddress()) {
            addresses.clearDefault(userId);
            address.markDefault();
        }
        return addresses.saveAndFlush(address);
    }

    /** O'chiradi; asosiy manzil o'chirilsa — eng oxirgi qo'shilgani asosiy bo'ladi. */
    @Transactional
    public void delete(UUID userId, UUID id) {
        Address address = find(userId, id);
        boolean wasDefault = address.isDefaultAddress();
        addresses.delete(address);
        addresses.flush();
        if (wasDefault) {
            List<Address> rest = addresses.findByUserIdOrderByCreatedAtAsc(userId);
            if (!rest.isEmpty()) {
                rest.getLast().markDefault();
            }
        }
    }

    private Address find(UUID userId, UUID id) {
        return addresses.findByIdAndUserId(id, userId).orElseThrow(() -> BusinessException.notFound("Address", id));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
