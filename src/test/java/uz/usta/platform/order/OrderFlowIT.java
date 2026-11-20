package uz.usta.platform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.order.internal.application.OrderMaintenance;
import uz.usta.platform.shared.outbox.OutboxRelay;
import uz.usta.platform.support.ApiClient;
import uz.usta.platform.support.ApiClient.Session;
import uz.usta.platform.support.Fixtures;
import uz.usta.platform.support.Fixtures.Point;
import uz.usta.platform.support.IntegrationTest;
import uz.usta.platform.support.RecordingSmsSender;
import uz.usta.platform.support.Seeds;

/** Buyurtma hayot sikli: yaratish -> takliflar -> qabul (lock) -> bajarish -> komissiya -> baho, va muqobil yo'llar. */
@IntegrationTest
class OrderFlowIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingSmsSender sms;
    @Autowired
    JsonMapper json;
    @Autowired
    OutboxRelay relay;
    @Autowired
    OrderMaintenance maintenance;
    @Autowired
    JdbcClient jdbc;

    ApiClient api;
    Fixtures fx;
    Session customer;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, sms, json);
        fx = new Fixtures(mvc, api, relay);
        customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
    }

    @Test
    void happyPathFromOrderToReview() throws Exception {
        Point home = Fixtures.randomArea();
        Session m1 = fx.approvedMaster("Birinchi usta", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 150_000));
        Session m2 = fx.approvedMaster("Ikkinchi usta", home.north(2), 10, Map.of(Seeds.FAUCET_INSTALL, 120_000));

        String orderId = createOrder(home, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.serviceName").value("Joʻmrak oʻrnatish"))
                .andExpect(jsonPath("$.master").value(nullValue()))
                .andExpect(jsonPath("$.history[*].to", contains("CREATED", "SEARCHING")))
                .andReturn().getResponse().getContentAsString();
        orderId = json.readTree(orderId).get("id").asString();

        // ikkala ustaga taklif keldi; aniq manzil va telefon hali yopiq
        for (Session m : List.of(m1, m2)) {
            mvc.perform(get("/api/v1/master/offers").header("Authorization", m.bearer()))
                    .andExpect(jsonPath("$[*].orderId", hasItem(orderId)));
        }
        mvc.perform(get("/api/v1/master/orders/{id}", orderId).header("Authorization", m2.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.details").value(nullValue()))
                .andExpect(jsonPath("$.address.location").value(nullValue()))
                .andExpect(jsonPath("$.customer").value(nullValue()))
                .andExpect(jsonPath("$.offerStatus").value("PENDING"))
                .andExpect(jsonPath("$.distanceKm").value(2.0));

        accept(m1, orderId).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.priceFrom").value(150000))
                .andExpect(jsonPath("$.address.details").value("2-podyezd, 15-xonadon"))
                .andExpect(jsonPath("$.customer.phone").value(customer.phone()));
        accept(m2, orderId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_TAKEN"));
        mvc.perform(get("/api/v1/master/offers").header("Authorization", m2.bearer()))
                .andExpect(jsonPath("$[*].orderId", not(hasItem(orderId))));

        mvc.perform(get("/api/v1/customer/orders/{id}", orderId).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.master.fullName").value("Birinchi usta"))
                .andExpect(jsonPath("$.master.phone").value(m1.phone()));

        // ish boshlangach mijoz bekor qila olmaydi va fayl qo'sha olmaydi
        masterAction(m1, orderId, "start", null).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(post("/api/v1/customer/orders/{id}/cancel", orderId).header("Authorization", customer.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        mvc.perform(multipart("/api/v1/customer/orders/{id}/attachments", orderId)
                        .file(new MockMultipartFile("file", "a.png", "image/png", Seeds.PNG))
                        .header("Authorization", customer.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_EDITABLE"));

        // baholash hali mumkin emas
        review(orderId, 5).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_REVIEWABLE"));

        masterAction(m1, orderId, "complete", "{\"price\":200000}")
                .andExpect(jsonPath("$.status").value("COMMISSION"))
                .andExpect(jsonPath("$.price").value(200000))
                .andExpect(jsonPath("$.commissionAmount").value(20000)); // 10%

        // baho: faqat egasi
        Session stranger = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        mvc.perform(post("/api/v1/customer/orders/{id}/review", orderId).header("Authorization", stranger.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":1}"))
                .andExpect(status().isNotFound());
        review(orderId, 6).andExpect(status().isBadRequest());
        review(orderId, 5).andExpect(status().isCreated()).andExpect(jsonPath("$.rating").value(5));
        review(orderId, 4).andExpect(status().isConflict());

        mvc.perform(get("/api/v1/customer/orders/{id}", orderId).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.status").value("REVIEWED"))
                .andExpect(jsonPath("$.master.phone").value(nullValue()))
                .andExpect(jsonPath("$.history[*].to", contains("CREATED", "SEARCHING", "ACCEPTED", "IN_PROGRESS",
                        "COMPLETED", "COMMISSION", "REVIEWED")));

        // reyting yangilandi; ikkinchi buyurtma — o'rtacha
        mvc.perform(get("/api/v1/masters/{id}", m1.userId()).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.ratingAvg").value(5.0)).andExpect(jsonPath("$.ratingCount").value(1));
        String second = completedOrder(home, m1);
        review(second, 4).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/masters/{id}", m1.userId()).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.ratingAvg").value(4.5)).andExpect(jsonPath("$.ratingCount").value(2));
        mvc.perform(get("/api/v1/masters/{id}/reviews", m1.userId()).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.items", hasSize(2)));

        // bildirishnomalar (outbox -> notification)
        relay.relayPending();
        mvc.perform(get("/api/v1/notifications").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.items[*].type", hasItem("ORDER_ACCEPTED")))
                .andExpect(jsonPath("$.items[*].type", hasItem("ORDER_STARTED")))
                .andExpect(jsonPath("$.items[*].type", hasItem("ORDER_COMPLETED")));
        mvc.perform(get("/api/v1/notifications").header("Authorization", m1.bearer()).header("Accept-Language", "ru"))
                .andExpect(jsonPath("$.items[?(@.type == 'NEW_REVIEW')].body", hasItem("Клиент поставил вам оценку 5")))
                .andExpect(jsonPath("$.items[?(@.type == 'NEW_OFFER')].data.orderId", hasItem(orderId)));
        // qayta yetkazish (at-least-once) dublikat yaratmaydi
        int before = countNotifications(m1);
        jdbc.sql("update outbox_events set published_at = null where aggregate_id = cast(:id as uuid)")
                .param("id", orderId).update();
        relay.relayPending();
        assertThat(countNotifications(m1)).isEqualTo(before);

        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.count").isNumber());
        mvc.perform(post("/api/v1/notifications/read-all").header("Authorization", customer.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void onlyOneOfConcurrentAcceptsWins() throws Exception {
        Point home = Fixtures.randomArea();
        List<Session> masters = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            masters.add(fx.approvedMaster("Poyga " + i, home.north(1 + i), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000)));
        }
        String orderId = idOf(createOrder(home, null));

        ExecutorService pool = Executors.newFixedThreadPool(masters.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (Session m : masters) {
            Callable<Integer> task = () -> {
                start.await();
                return accept(m, orderId).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsOnlyOnce(200).filteredOn(s -> s != 200).allMatch(s -> s == 409);
        assertThat(jdbc.sql("select count(*) from order_master_offers where order_id = cast(:id as uuid) and offer_status = 'ACCEPTED'")
                .param("id", orderId).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void preferredMasterFirstThenRedispatchOnReject() throws Exception {
        Point home = Fixtures.randomArea();
        Session chosen = fx.approvedMaster("Tanlangan", home.north(5), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        Session nearer = fx.approvedMaster("Yaqinroq", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));

        String orderId = idOf(createOrder(home, UUID.fromString(chosen.userId())));
        offers(nearer).andExpect(jsonPath("$[*].orderId", not(hasItem(orderId))));
        offers(chosen).andExpect(jsonPath("$[*].orderId", hasItem(orderId)));

        mvc.perform(post("/api/v1/master/offers/{id}/reject", orderId).header("Authorization", chosen.bearer()))
                .andExpect(status().isNoContent());
        // darhol keyingi ustalarga (rad etgan ustaga qayta yuborilmaydi)
        offers(nearer).andExpect(jsonPath("$[*].orderId", hasItem(orderId)));
        accept(chosen, orderId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFER_NOT_AVAILABLE"));

        // usta tanlanmagan joyga mos bo'lmasa — buyurtma yaratilmaydi
        createOrder(home.north(100), UUID.fromString(chosen.userId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MASTER_NOT_AVAILABLE"));
    }

    @Test
    void retriesWhenMastersAppearAndExpiresAfterTimeout() throws Exception {
        Point home = Fixtures.randomArea();
        String orderId = idOf(createOrder(home, null)); // hududda hali usta yo'q
        maintenance.run();
        Session late = fx.approvedMaster("Kech qolgan", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        offers(late).andExpect(jsonPath("$[*].orderId", not(hasItem(orderId))));

        maintenance.run(); // retry: endi mos usta bor
        offers(late).andExpect(jsonPath("$[*].orderId", hasItem(orderId)));

        // taklif muddati tugadi -> EXPIRED, usta endi qabul qila olmaydi
        jdbc.sql("update order_master_offers set expires_at = now() - interval '1 second' where order_id = cast(:id as uuid)")
                .param("id", orderId).update();
        maintenance.run();
        accept(late, orderId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFER_NOT_AVAILABLE"));

        // qidiruv muddati tugadi -> buyurtma EXPIRED
        jdbc.sql("update orders set search_deadline = now() - interval '1 second' where id = cast(:id as uuid)")
                .param("id", orderId).update();
        maintenance.run();
        mvc.perform(get("/api/v1/customer/orders/{id}", orderId).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
        relay.relayPending();
        mvc.perform(get("/api/v1/notifications").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.items[*].type", hasItem("ORDER_EXPIRED")));
    }

    @Test
    void cancellationAttachmentsAndAccess() throws Exception {
        Point home = Fixtures.randomArea();
        Session m = fx.approvedMaster("Usta", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        String orderId = idOf(createOrder(home, null));

        mvc.perform(multipart("/api/v1/customer/orders/{id}/attachments", orderId)
                        .file(new MockMultipartFile("file", "a.png", "image/png", Seeds.PNG))
                        .header("Authorization", customer.bearer()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mediaType").value("image/png"));
        offers(m).andExpect(jsonPath("$[0].attachments", hasSize(1)));

        // begona foydalanuvchilar ko'rmaydi
        Session other = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        mvc.perform(get("/api/v1/customer/orders/{id}", orderId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        Session otherMaster = api.loginAs(ApiClient.randomPhone(), "MASTER");
        mvc.perform(get("/api/v1/master/orders/{id}", orderId).header("Authorization", otherMaster.bearer()))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/customer/orders/{id}/cancel", orderId).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Rejam o'zgardi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Rejam o'zgardi"));
        accept(m, orderId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFER_NOT_AVAILABLE"));
        offers(m).andExpect(jsonPath("$[*].orderId", not(hasItem(orderId))));

        mvc.perform(get("/api/v1/customer/orders").header("Authorization", customer.bearer())
                        .param("status", "CANCELLED"))
                .andExpect(jsonPath("$.items[*].id", hasItem(orderId)));

        // validatsiya
        mvc.perform(post("/api/v1/customer/orders").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"" + Seeds.FAUCET_INSTALL + "\",\"description\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ADDRESS_REQUIRED"));
        mvc.perform(post("/api/v1/customer/orders").header("Authorization", m.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(fx.orderJson(Seeds.FAUCET_INSTALL, home, null)))
                .andExpect(status().isForbidden()); // MASTER roli mijoz yo'liga kira olmaydi
    }

    @Test
    void disputeIsResolvedByAdmin() throws Exception {
        Point home = Fixtures.randomArea();
        Session m = fx.approvedMaster("Nizoli usta", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        String orderId = idOf(createOrder(home, null));
        accept(m, orderId).andExpect(status().isOk());
        masterAction(m, orderId, "start", null).andExpect(status().isOk());

        mvc.perform(post("/api/v1/customer/orders/{id}/dispute", orderId).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ish sifatsiz\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISPUTED"));
        masterAction(m, orderId, "complete", "{\"price\":100000}").andExpect(status().isConflict());

        Session admin = fx.admin();
        mvc.perform(get("/api/v1/admin/orders").header("Authorization", admin.bearer()).param("status", "DISPUTED")
                        .param("size", "100"))
                .andExpect(jsonPath("$.items[*].id", hasItem(orderId)));
        // narx hali kiritilmagan — COMPLETED uchun majburiy
        resolve(admin, orderId, "{\"resolution\":\"COMPLETED\",\"note\":\"Qisman bajarilgan\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRICE_REQUIRED"));
        resolve(admin, orderId, "{\"resolution\":\"COMPLETED\",\"price\":50000,\"note\":\"Qisman bajarilgan\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("COMMISSION"))
                .andExpect(jsonPath("$.commissionAmount").value(5000))
                .andExpect(jsonPath("$.offers[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.customer.phone").value(customer.phone()));
        resolve(admin, orderId, "{\"resolution\":\"CANCELLED\",\"note\":\"x\"}").andExpect(status().isConflict());
        review(orderId, 3).andExpect(status().isCreated()); // nizo hal bo'lgach baholash mumkin

        relay.relayPending();
        mvc.perform(get("/api/v1/notifications").header("Authorization", m.bearer()))
                .andExpect(jsonPath("$.items[*].type", containsInAnyOrder(
                        "NEW_OFFER", "ORDER_DISPUTED", "DISPUTE_RESOLVED", "NEW_REVIEW")));
    }

    @Test
    void pushTokenRegistration() throws Exception {
        mvc.perform(post("/api/v1/push-tokens").header("Authorization", customer.bearer())
                        .header("Accept-Language", "ru").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"fcm-" + UUID.randomUUID() + "\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.sql("select locale from push_tokens where user_id = cast(:id as uuid)")
                .param("id", customer.userId()).query(String.class).single()).isEqualTo("ru");
    }

    // ---------------------------------------------------------------- yordamchilar

    private String completedOrder(Point home, Session master) throws Exception {
        String id = idOf(createOrder(home, UUID.fromString(master.userId())));
        accept(master, id).andExpect(status().isOk());
        masterAction(master, id, "start", null).andExpect(status().isOk());
        masterAction(master, id, "complete", "{\"price\":100000}").andExpect(status().isOk());
        return id;
    }

    private ResultActions createOrder(Point at, UUID preferredMasterId) throws Exception {
        return mvc.perform(post("/api/v1/customer/orders").header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(fx.orderJson(Seeds.FAUCET_INSTALL, at, preferredMasterId)));
    }

    private String idOf(ResultActions created) throws Exception {
        return json.readTree(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asString();
    }

    private ResultActions accept(Session master, String orderId) throws Exception {
        return mvc.perform(post("/api/v1/master/offers/{id}/accept", orderId).header("Authorization", master.bearer()));
    }

    private ResultActions offers(Session master) throws Exception {
        return mvc.perform(get("/api/v1/master/offers").header("Authorization", master.bearer()));
    }

    private ResultActions masterAction(Session master, String orderId, String action, String body) throws Exception {
        var req = post("/api/v1/master/orders/{id}/" + action, orderId).header("Authorization", master.bearer());
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private ResultActions review(String orderId, int rating) throws Exception {
        return mvc.perform(post("/api/v1/customer/orders/{id}/review", orderId).header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":" + rating + ",\"comment\":\"Zo'r\"}"));
    }

    private ResultActions resolve(Session admin, String orderId, String body) throws Exception {
        return mvc.perform(post("/api/v1/admin/orders/{id}/resolve", orderId).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int countNotifications(Session user) {
        return jdbc.sql("select count(*) from notifications where user_id = cast(:id as uuid)")
                .param("id", user.userId()).query(Integer.class).single();
    }
}
