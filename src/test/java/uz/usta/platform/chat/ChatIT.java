package uz.usta.platform.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
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
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
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

@IntegrationTest
class ChatIT {

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
        // xom baytlar (JSON) — har qanday content-type'ni qabul qiladi; JSON'ni testning o'zi o'qiydi
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
    void restChatLifecycle() throws Exception {
        String orderId = fx.order(customer, home, master);
        // usta qabul qilmaguncha chat yo'q
        send(customer, orderId, "Salom").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_AVAILABLE"));
        mvc.perform(post("/api/v1/master/offers/{id}/accept", orderId).header("Authorization", master.bearer()))
                .andExpect(status().isOk());

        send(customer, orderId, "Salom, soat 15:00 da kela olasizmi?").andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(customer.userId()));
        send(master, orderId, "Ha, boraman").andExpect(status().isCreated());
        send(master, orderId, "   ").andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/orders/{id}/chat", orderId).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.writable").value(true))
                .andExpect(jsonPath("$.unread").value(1));
        mvc.perform(post("/api/v1/orders/{id}/chat/read", orderId).header("Authorization", customer.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/orders/{id}/chat", orderId).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/v1/orders/{id}/chat", orderId).header("Authorization", master.bearer()))
                .andExpect(jsonPath("$.otherPartyReadAt").isNotEmpty());

        // tarix: yangilaridan eskilariga, keyset pagination
        for (int i = 1; i <= 3; i++) {
            send(customer, orderId, "xabar " + i).andExpect(status().isCreated());
        }
        String page1 = mvc.perform(get("/api/v1/orders/{id}/chat/messages", orderId)
                        .header("Authorization", master.bearer()).param("limit", "2"))
                .andExpect(jsonPath("$[*].content", contains("xabar 3", "xabar 2")))
                .andReturn().getResponse().getContentAsString();
        String oldest = json.readTree(page1).get(1).get("id").asString();
        mvc.perform(get("/api/v1/orders/{id}/chat/messages", orderId).header("Authorization", master.bearer())
                        .param("before", oldest).param("limit", "10"))
                .andExpect(jsonPath("$[*].content", contains("xabar 1", "Ha, boraman", "Salom, soat 15:00 da kela olasizmi?")));

        // begona foydalanuvchi va admin
        Session stranger = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        mvc.perform(get("/api/v1/orders/{id}/chat/messages", orderId).header("Authorization", stranger.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/orders/{id}/chat/messages", orderId).header("Authorization", fx.admin().bearer()))
                .andExpect(jsonPath("$", hasSize(5)));

        // yangi xabar — qabul qiluvchiga faqat push (in-app ro'yxatga tushmaydi)
        relay.relayPending();
        mvc.perform(get("/api/v1/notifications").header("Authorization", master.bearer()))
                .andExpect(jsonPath("$.items[?(@.type == 'CHAT_MESSAGE')]", hasSize(0)));

        // buyurtma yopilgach chat faqat o'qish uchun
        mvc.perform(post("/api/v1/customer/orders/{id}/cancel", orderId).header("Authorization", customer.bearer()))
                .andExpect(status().isOk());
        send(customer, orderId, "Kechirasiz").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_CLOSED"));
        mvc.perform(get("/api/v1/orders/{id}/chat/messages", orderId).header("Authorization", customer.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void realtimeOverStompWebSocket() throws Exception {
        String orderId = fx.acceptedOrder(customer, home, master);
        String topic = "/topic/orders." + orderId + ".chat";

        StompSession masterWs = connect(master);
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        subscribe(masterWs, topic, received);

        // REST orqali yuborilgan xabar WebSocket obunachisiga keladi
        send(customer, orderId, "REST orqali").andExpect(status().isCreated());
        JsonNode e1 = received.poll(5, TimeUnit.SECONDS);
        assertThat(e1).isNotNull();
        assertThat(e1.get("type").asString()).isEqualTo("MESSAGE");
        assertThat(e1.get("message").get("content").asString()).isEqualTo("REST orqali");

        // STOMP SEND orqali
        StompSession customerWs = connect(customer);
        StompHeaders h = new StompHeaders();
        h.setDestination("/app/orders." + orderId + ".chat");
        h.setContentType(MimeTypeUtils.APPLICATION_JSON);
        customerWs.send(h, "{\"content\":\"WebSocket orqali\"}".getBytes(StandardCharsets.UTF_8));
        JsonNode e2 = received.poll(5, TimeUnit.SECONDS);
        assertThat(e2).isNotNull();
        assertThat(e2.get("message").get("content").asString()).isEqualTo("WebSocket orqali");
        assertThat(e2.get("message").get("senderId").asString()).isEqualTo(customer.userId());

        // o'qildi hodisasi
        mvc.perform(post("/api/v1/orders/{id}/chat/read", orderId).header("Authorization", customer.bearer()))
                .andExpect(status().isNoContent());
        JsonNode e3 = received.poll(5, TimeUnit.SECONDS);
        assertThat(e3.get("type").asString()).isEqualTo("READ");
        assertThat(e3.get("readerId").asString()).isEqualTo(customer.userId());

        mvc.perform(get("/api/v1/orders/{id}/chat/messages", orderId).header("Authorization", master.bearer()))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void webSocketSecurity() throws Exception {
        String orderId = fx.acceptedOrder(customer, home, master);

        // tokensiz va noto'g'ri token bilan ulanib bo'lmaydi
        assertThatThrownBy(() -> stomp.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                new StompHeaders(), new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        StompHeaders bad = new StompHeaders();
        bad.add("Authorization", "Bearer not-a-jwt");
        assertThatThrownBy(() -> stomp.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                bad, new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);

        // begona foydalanuvchi obuna bo'la olmaydi — server sessiyani yopadi, xabar kelmaydi
        Session stranger = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        StompSession ws = connect(stranger);
        BlockingQueue<JsonNode> leaked = new LinkedBlockingQueue<>();
        ws.subscribe("/topic/orders." + orderId + ".chat", handler(leaked));
        send(customer, orderId, "maxfiy").andExpect(status().isCreated());
        assertThat(leaked.poll(2, TimeUnit.SECONDS)).isNull();
        assertThat(ws.isConnected()).isFalse();
    }

    // ---------------------------------------------------------------- yordamchilar

    private StompSession connect(Session user) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", user.bearer());
        return stomp.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    /**
     * Simple broker SUBSCRIBE uchun RECEIPT yubormaydi; inbound kanal asinxron — obuna ro'yxatga olinishi uchun
     * qisqa kutamiz (sessiya ochiq qolgani — obuna rad etilmaganini ham bildiradi).
     */
    private void subscribe(StompSession session, String topic, BlockingQueue<JsonNode> sink) throws Exception {
        session.subscribe(topic, handler(sink));
        Thread.sleep(500);
        assertThat(session.isConnected()).isTrue();
    }

    private StompFrameHandler handler(BlockingQueue<JsonNode> sink) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                sink.add(json.readTree(new String((byte[]) payload, StandardCharsets.UTF_8)));
            }
        };
    }

    private ResultActions send(Session user, String orderId, String content) throws Exception {
        return mvc.perform(post("/api/v1/orders/{id}/chat/messages", orderId).header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("content", content))));
    }
}
