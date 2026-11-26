package uz.usta.platform.identity.internal.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.identity.UserDirectory;
import uz.usta.platform.identity.internal.domain.User;
import uz.usta.platform.identity.internal.persistence.UserRepository;

@Component
class JpaUserDirectory implements UserDirectory {

    private final UserRepository users;

    JpaUserDirectory(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> phones(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(userIds).stream().filter(u -> u.getPhone() != null) // o'chirilganlarda yo'q
                .collect(Collectors.toMap(User::getId, User::getPhone));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDeleted(UUID userId) {
        return users.findById(userId).filter(User::isDeleted).isPresent();
    }
}
