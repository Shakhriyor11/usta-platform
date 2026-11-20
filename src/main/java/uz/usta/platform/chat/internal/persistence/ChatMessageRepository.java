package uz.usta.platform.chat.internal.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.chat.internal.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("select m from ChatMessage m where m.chatId = :chatId order by m.createdAt desc, m.id desc")
    List<ChatMessage> latest(@Param("chatId") UUID chatId, Pageable limit);

    /** Keyset pagination: berilgan xabardan oldingilar (cheksiz scroll, OFFSET'siz). */
    @Query("""
            select m from ChatMessage m
            where m.chatId = :chatId and (m.createdAt < :at or (m.createdAt = :at and m.id < :id))
            order by m.createdAt desc, m.id desc""")
    List<ChatMessage> before(@Param("chatId") UUID chatId, @Param("at") Instant at, @Param("id") UUID id,
                             Pageable limit);

    @Query("select count(m) from ChatMessage m where m.chatId = :chatId and m.senderId <> :userId")
    long countFromOthers(@Param("chatId") UUID chatId, @Param("userId") UUID userId);

    @Query("""
            select count(m) from ChatMessage m
            where m.chatId = :chatId and m.senderId <> :userId and m.createdAt > :since""")
    long countFromOthersSince(@Param("chatId") UUID chatId, @Param("userId") UUID userId,
                              @Param("since") Instant since);
}
