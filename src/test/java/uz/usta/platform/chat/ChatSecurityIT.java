package uz.usta.platform.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.shared.outbox.OutboxRelay;
import uz.usta.platform.support.ApiClient;
import uz.usta.platform.support.ApiClient.Session;
import uz.usta.platform.support.Fixtures;
import uz.usta.platform.support.Fixtures.Point;
import uz.usta.platform.support.IntegrationTest;
import uz.usta.platform.support.RecordingSmsSender;
import uz.usta.platform.support.Seeds;

/** WebSocket ruxsatlari: mavzuga to'g'ridan-to'g'ri yozib bo'lmaydi, sobiq usta va bloklangan foydalanuvchi uziladi. */
@IntegrationTest
class ChatSecurityIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingSmsSender sms;
    @Autowired
    JsonMapper json;
    @Autowired
    OutboxRelay relay;
    @Value("${local.server.port}")
    int port;

    ApiClient api;
    Fixtures fx;
    Session customer;
    Session master;
    Point home;
    WebSocketStompClient stomp;
    ThreadPoolTaskScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, sms, json);
        fx = new Fixtures(mvc, api, relay);
        customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        home = Fixtures.randomArea();
        master = fx.approvedMaster("Chat ustasi", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new ByteArrayMessageConverter() {
            @Override
            protected boolean supportsMimeType(MessageHeaders headers) {
                return true;
            }
        });
        stomp.setTaskScheduler(scheduler);
    }

    @AfterEach
    void tearDown() {
        stomp.stop();
        scheduler.shutdown();
    }

    @Test
    void nobodyCanPublishDirectlyToTheChatTopic() throws Exception {
        String orderId = fx.acceptedOrder(customer, home, master);
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        subscribe(connect(master), topic(orderId), received);

        // begona foydalanuvchi ham, ishtirokchining o'zi ham mavzuga yoza olmaydi
        Session stranger = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        for (Session sender : new Session[] {stranger, customer}) {
            StompSession ws = connect(sender);
            sendFrame(ws, topic(orderId), forged(orderId));
            assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
        }

        // SEND o'rniga server kadri (MESSAGE) bilan ham
        BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        WebSocketSession raw = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                replies.add(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws").get(5, TimeUnit.SECONDS);
        raw.sendMessage(new TextMessage("CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:"
                + stranger.bearer() + "\n\n\0"));
        assertThat(replies.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED");
        raw.sendMessage(new TextMessage("MESSAGE\ndestination:" + topic(orderId)
                + "\nmessage-id:1\nsubscription:0\ncontent-type:application/json\n\n" + forged(orderId) + "\0"));
        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();

        // qonuniy yo'l ishlayveradi
        send(customer, orderId, "haqiqiy").andExpect(status().isCreated());
        assertThat(received.poll(5, TimeUnit.SECONDS).get("message").get("content").asString()).isEqualTo("haqiqiy");
    }

    @Test
    void replacedMasterStopsReceivingAndHasASeparateChat() throws Exception {
        Session next = fx.approvedMaster("Yangi usta", home.north(2), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        String orderId = fx.acceptedOrder(customer, home, master);
        // tanlangan usta voz kechgach taklif boshqalarga boradi
        BlockingQueue<JsonNode> oldMaster = new LinkedBlockingQueue<>();
        subscribe(connect(master), topic(orderId), oldMaster);
        send(customer, orderId, "birinchi ustaga").andExpect(status().isCreated());
        assertThat(oldMaster.poll(5, TimeUnit.SECONDS)).isNotNull();

        mvc.perform(post("/api/v1/master/orders/{id}/cancel", orderId).header("Authorization", master.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"band\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/master/offers/{id}/accept", orderId).header("Authorization", next.bearer()))
                .andExpect(status().isOk());

        BlockingQueue<JsonNode> newMaster = new LinkedBlockingQueue<>();
        subscribe(connect(next), topic(orderId), newMaster);
        send(customer, orderId, "ikkinchi ustaga").andExpect(status().isCreated());
        assertThat(newMaster.poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(oldMaster.poll(1, TimeUnit.SECONDS)).isNull();

        messages(next, orderId).andExpect(jsonPath("$[*].content", contains("ikkinchi ustaga")));
        messages(customer, orderId).andExpect(jsonPath("$[*].content", contains("ikkinchi ustaga")));
        messages(master, orderId).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/orders/{id}/chat/messages", orderId).header("Authorization", fx.admin().bearer()))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void blockedUserIsDisconnected() throws Exception {
        String orderId = fx.acceptedOrder(customer, home, master);
        StompSession ws = connect(master);
        subscribe(ws, topic(orderId), new LinkedBlockingQueue<>());

        mvc.perform(post("/api/v1/admin/users/{id}/block", master.userId())
                .header("Authorization", fx.admin().bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"test\"}")).andExpect(status().isOk());
        relay.relayPending();

        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (ws.isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertThat(ws.isConnected()).isFalse();
    }

    @Test
    void invalidStompMessageIsReportedToTheSender() throws Exception {
        String orderId = fx.acceptedOrder(customer, home, master);
        StompSession ws = connect(customer);
        BlockingQueue<JsonNode> errors = new LinkedBlockingQueue<>();
        subscribe(ws, "/user/queue/errors", errors);

        sendFrame(ws, "/app/orders." + orderId + ".chat", json.writeValueAsString(
                Map.of("content", "a".repeat(2001))));
        assertThat(errors.poll(5, TimeUnit.SECONDS).get("code").asString()).isEqualTo("VALIDATION_FAILED");
        sendFrame(ws, "/app/orders." + orderId + ".chat", "{buzuq");
        assertThat(errors.poll(5, TimeUnit.SECONDS).get("code").asString()).isEqualTo("MALFORMED_REQUEST");
    }

    // ---------------------------------------------------------------- yordamchilar

    private static String topic(String orderId) {
        return "/topic/orders." + orderId + ".chat";
    }

    private String forged(String orderId) {
        return "{\"type\":\"MESSAGE\",\"message\":{\"id\":\"" + UUID.randomUUID() + "\",\"orderId\":\"" + orderId
                + "\",\"senderId\":\"" + customer.userId() + "\",\"content\":\"soxta\"}}";
    }

    private StompSession connect(Session user) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", user.bearer());
        return stomp.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    private void sendFrame(StompSession session, String destination, String body) {
        StompHeaders h = new StompHeaders();
        h.setDestination(destination);
        h.setContentType(MimeTypeUtils.APPLICATION_JSON);
        session.send(h, body.getBytes(StandardCharsets.UTF_8));
    }

    /** Simple broker SUBSCRIBE uchun RECEIPT yubormaydi — obuna ro'yxatga olinishini qisqa kutamiz. */
    private void subscribe(StompSession session, String destination, BlockingQueue<JsonNode> sink) throws Exception {
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                sink.add(json.readTree(new String((byte[]) payload, StandardCharsets.UTF_8)));
            }
        });
        Thread.sleep(500);
        assertThat(session.isConnected()).isTrue();
    }

    private ResultActions send(Session user, String orderId, String content) throws Exception {
        return mvc.perform(post("/api/v1/orders/{id}/chat/messages", orderId).header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("content", content))));
    }

    private ResultActions messages(Session user, String orderId) throws Exception {
        return mvc.perform(get("/api/v1/orders/{id}/chat/messages", orderId).header("Authorization", user.bearer()));
    }
}
