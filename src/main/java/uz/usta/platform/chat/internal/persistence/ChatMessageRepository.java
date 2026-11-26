package uz.usta.platform.chat.internal.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.usta.platform.chat.internal.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("select m from ChatMessage m where m.chatId in :chatIds order by m.createdAt desc, m.id desc")
    List<ChatMessage> latest(@Param("chatIds") Collection<UUID> chatIds, Pageable limit);

    /** Keyset pagination: berilgan xabardan oldingilar (cheksiz scroll, OFFSET'siz). */
    @Query("""
            select m from ChatMessage m
            where m.chatId in :chatIds and (m.createdAt < :at or (m.createdAt = :at and m.id < :id))
            order by m.createdAt desc, m.id desc""")
    List<ChatMessage> before(@Param("chatIds") Collection<UUID> chatIds, @Param("at") Instant at, @Param("id") UUID id,
                             Pageable limit);

    @Query("select m.imageKey from ChatMessage m where m.senderId = :senderId and m.imageKey is not null")
    List<String> findImageKeys(@Param("senderId") UUID senderId);

    /** Izohsiz rasmli xabarlar (rasmsiz mazmuni qolmaydi). */
    @Modifying
    @NativeQuery("delete from messages where sender_id = :senderId and image_key is not null and btrim(content) = ''")
    int deleteImageOnly(@Param("senderId") UUID senderId);

    @Modifying
    @NativeQuery("update messages set image_key = null, image_type = null, image_size = null "
            + "where sender_id = :senderId and image_key is not null")
    int removeImages(@Param("senderId") UUID senderId);

    @Query("select count(m) from ChatMessage m where m.chatId = :chatId and m.senderId <> :userId")
    long countFromOthers(@Param("chatId") UUID chatId, @Param("userId") UUID userId);

    @Query("""
            select count(m) from ChatMessage m
            where m.chatId = :chatId and m.senderId <> :userId and m.createdAt > :since""")
    long countFromOthersSince(@Param("chatId") UUID chatId, @Param("userId") UUID userId,
                              @Param("since") Instant since);
}
