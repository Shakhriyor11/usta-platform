package uz.usta.platform.identity.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.identity.internal.domain.User;
import uz.usta.platform.shared.security.Role;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByPhone(String phone);

    @Query("select u from User u where :role member of u.roles order by u.createdAt, u.id")
    List<User> findAllWithRole(@Param("role") Role role);
}
