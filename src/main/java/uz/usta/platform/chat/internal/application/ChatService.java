package uz.usta.platform.chat.internal.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.usta.platform.chat.ChatEvents;
import uz.usta.platform.chat.internal.application.ChatEvent.MessageView;
import uz.usta.platform.chat.internal.domain.Chat;
import uz.usta.platform.chat.internal.domain.ChatErrorCode;
import uz.usta.platform.chat.internal.domain.ChatMessage;
import uz.usta.platform.chat.internal.persistence.ChatMessageRepository;
import uz.usta.platform.chat.internal.persistence.ChatRepository;
import uz.usta.platform.order.OrderApi;
import uz.usta.platform.order.OrderApi.Participants;
import uz.usta.platform.shared.error.BusinessException;
import uz.usta.platform.shared.error.CommonErrorCode;
import uz.usta.platform.shared.outbox.OutboxPublisher;
import uz.usta.platform.shared.ratelimit.RateLimiter;
import uz.usta.platform.shared.util.Uuids;
import uz.usta.platform.shared.validation.PlainTextValidator;
import uz.usta.platform.storage.FilePurpose;
import uz.usta.platform.storage.FileStorage;
import uz.usta.platform.storage.StoredFile;

/**
 * Buyurtma chati. Chat usta biriktirilgandan keyin ochiladi. Yozish faqat ish faol bo'lganda mumkin
 * (ACCEPTED, IN_PROGRESS, COMPLETED, COMMISSION, DISPUTED); keyin chat faqat o'qish uchun qoladi.
 * Ishtirokchilar — buyurtma mijozi va biriktirilgan usta; admin nizolarni ko'rib chiqish uchun o'qiy oladi.
 */
@Service
public class ChatService {

    static final Set<String> WRITABLE = Set.of("ACCEPTED", "IN_PROGRESS", "COMPLETED", "COMMISSION", "DISPUTED");
    private static final int MAX_PAGE = 100;
    private static final int MAX_LENGTH = 2000;
    private static final int PREVIEW_LENGTH = 100;
    private static final int RATE_LIMIT = 30;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final ChatRepository chats;
    private final ChatMessageRepository messages;
    private final OrderApi orders;
    private final OutboxPublisher outbox;
    private final ChatBroadcaster broadcaster;
    private final RateLimiter rateLimiter;
    private final FileStorage storage;
    private final Clock clock;

    ChatService(ChatRepository chats, ChatMessageRepository messages, OrderApi orders, OutboxPublisher outbox,
                ChatBroadcaster broadcaster, RateLimiter rateLimiter, FileStorage storage, Clock clock) {
        this.storage = storage;
        this.chats = chats;
        this.messages = messages;
        this.orders = orders;
        this.outbox = outbox;
        this.broadcaster = broadcaster;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public record ChatInfo(UUID orderId, boolean writable, long unread, Instant otherPartyReadAt,
                           Instant lastMessageAt) {
    }

    /** WebSocket obunasi va REST o'qish uchun: foydalanuvchi shu buyurtma chatining ishtirokchisimi. */
    @Transactional(readOnly = true)
    public boolean canRead(UUID orderId, UUID userId) {
        try {
            Participants p = orders.participants(orderId);
            return p.masterId() != null && p.isParticipant(userId);
        } catch (BusinessException e) {
            return false;
        }
    }

    @Transactional
    public MessageView send(UUID orderId, UUID senderId, String content) {
        String text = validText(content);
        if (text.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED);
        }
        Participants p = writer(orderId, senderId);
        rateLimiter.consume("chat-send", senderId.toString(), RATE_LIMIT, RATE_WINDOW);
        return post(p, senderId, text, null);
    }

    /**
     * Rasmli xabar. Fayl DB tranzaksiyasi tashqarisida yuklanadi; yozishdan oldin ruxsat qayta tekshiriladi
     * (yuklash davomida buyurtma yopilishi yoki usta almashishi mumkin).
     */
    public MessageView sendImage(UUID orderId, UUID senderId, MultipartFile file, String caption) {
        String text = validText(caption);
        writer(orderId, senderId); // yuklashdan oldin tez rad etish
        rateLimiter.consume("chat-send", senderId.toString(), RATE_LIMIT, RATE_WINDOW);
        return storage.storeAndSave(FilePurpose.CHAT_IMAGE, orderId, file,
                stored -> post(writer(orderId, senderId), senderId, text, stored));
    }

    /** Push uchun qisqa matn; izohsiz rasmda bo'sh. Belgi juftligi (emoji) o'rtasidan kesilmaydi. */
    private static String preview(String text) {
        if (text.length() <= PREVIEW_LENGTH) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(PREVIEW_LENGTH - 1)) ? PREVIEW_LENGTH - 1 : PREVIEW_LENGTH;
        return text.substring(0, end) + "…";
    }

    /** STOMP yo'lida Bean Validation yo'q — uzunlik va belgilar shu yerda tekshiriladi. */
    private static String validText(String content) {
        String text = content == null ? "" : content.strip();
        if (text.length() > MAX_LENGTH || !PlainTextValidator.isPlain(text)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED);
        }
        return text;
    }

    private Participants writer(UUID orderId, UUID senderId) {
        Participants p = participant(orderId, senderId);
        if (!WRITABLE.contains(p.status())) {
            throw new BusinessException(ChatErrorCode.CHAT_CLOSED, p.status());
        }
        return p;
    }

    private MessageView post(Participants p, UUID senderId, String text, StoredFile image) {
        UUID orderId = p.orderId();
        Chat chat = chat(p);
        // DB timestamptz mikrosekund aniqlikda — keyset pagination uchun bir xil qiymat bo'lsin
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        ChatMessage m = new ChatMessage(chat.getId(), senderId, text, now);
        if (image != null) {
            m.attachImage(image.key(), image.contentType(), image.sizeBytes());
        }
        messages.save(m);
        chat.messageAt(now);
        chat.markRead(senderId, now);
        chats.save(chat);
        UUID recipient = chat.otherParty(senderId);
        outbox.publish(ChatEvents.AGGREGATE, chat.getId(), ChatEvents.MESSAGE_SENT,
                new ChatEvents.MessageSent(m.getId(), orderId, senderId, recipient, preview(text)));
        MessageView view = view(orderId, m);
        broadcaster.publishAfterCommit(orderId, ChatEvent.message(view));
        return view;
    }

    /**
     * Eng yangi xabarlardan boshlab; {@code beforeId} — oldingi sahifaning eng eski xabari. Ishtirokchi faqat
     * hozirgi usta bilan chatni ko'radi; admin — buyurtmaning barcha chatlarini (usta almashgan bo'lsa ham).
     */
    @Transactional(readOnly = true)
    public List<MessageView> history(UUID orderId, UUID userId, UUID beforeId, int limit, boolean asAdmin) {
        List<UUID> chatIds = asAdmin
                ? chats.findIdsByOrderId(orderId)
                : find(participant(orderId, userId)).map(c -> List.of(c.getId())).orElse(List.of());
        if (chatIds.isEmpty()) {
            return List.of();
        }
        PageRequest page = PageRequest.ofSize(Math.min(Math.max(limit, 1), MAX_PAGE));
        List<ChatMessage> list;
        if (beforeId == null) {
            list = messages.latest(chatIds, page);
        } else {
            ChatMessage anchor = messages.findById(beforeId).filter(x -> chatIds.contains(x.getChatId()))
                    .orElseThrow(() -> BusinessException.notFound("Message", beforeId));
            list = messages.before(chatIds, anchor.getCreatedAt(), anchor.getId(), page);
        }
        return list.stream().map(m -> view(orderId, m)).toList();
    }

    @Transactional
    public void markRead(UUID orderId, UUID userId) {
        Participants p = participant(orderId, userId);
        find(p).ifPresent(chat -> {
            Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            chat.markRead(userId, now);
            chats.save(chat);
            broadcaster.publishAfterCommit(orderId, ChatEvent.read(userId, now));
        });
    }

    @Transactional(readOnly = true)
    public ChatInfo info(UUID orderId, UUID userId) {
        Participants p = participant(orderId, userId);
        Chat chat = find(p).orElse(null);
        if (chat == null) {
            return new ChatInfo(orderId, WRITABLE.contains(p.status()), 0, null, null);
        }
        Instant myRead = userId.equals(chat.getCustomerId()) ? chat.lastReadBy(chat.getCustomerId())
                : chat.lastReadBy(chat.getMasterId());
        long unread = myRead == null ? messages.countFromOthers(chat.getId(), userId)
                : messages.countFromOthersSince(chat.getId(), userId, myRead);
        return new ChatInfo(orderId, WRITABLE.contains(p.status()), unread, chat.lastReadBy(chat.otherParty(userId)),
                chat.getLastMessageAt());
    }

    private Participants participant(UUID orderId, UUID userId) {
        Participants p = orders.participants(orderId);
        if (!p.isParticipant(userId)) {
            throw BusinessException.notFound("Order", orderId);
        }
        if (p.masterId() == null) {
            throw new BusinessException(ChatErrorCode.CHAT_NOT_AVAILABLE);
        }
        return p;
    }

    private Optional<Chat> find(Participants p) {
        return chats.findByOrderIdAndMasterId(p.orderId(), p.masterId());
    }

    private Chat chat(Participants p) {
        return find(p).orElseGet(() -> {
            chats.insertIfAbsent(Uuids.v7(), p.orderId(), p.customerId(), p.masterId());
            return find(p).orElseThrow();
        });
    }

    private MessageView view(UUID orderId, ChatMessage m) {
        return new MessageView(m.getId(), orderId, m.getSenderId(), m.getContent(),
                m.getImageKey() == null ? null : storage.url(m.getImageKey()), m.getCreatedAt());
    }
}
