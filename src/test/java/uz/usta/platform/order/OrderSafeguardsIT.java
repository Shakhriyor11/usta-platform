package uz.usta.platform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.shared.outbox.OutboxRelay;
import uz.usta.platform.support.ApiClient;
import uz.usta.platform.support.ApiClient.Session;
import uz.usta.platform.support.Fixtures;
import uz.usta.platform.support.Fixtures.Point;
import uz.usta.platform.support.IntegrationTest;
import uz.usta.platform.support.RecordingSmsSender;
import uz.usta.platform.support.Seeds;

/** Narx himoyasi, usta voz kechishi, taklif taqsimoti, rejalashtirilgan buyurtma, bloklangan foydalanuvchi. */
@IntegrationTest
class OrderSafeguardsIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingSmsSender sms;
    @Autowired
    JsonMapper json;
    @Autowired
    OutboxRelay relay;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    StringRedisTemplate redis;

    ApiClient api;
    Fixtures fx;
    Session customer;
    Point home;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, sms, json);
        fx = new Fixtures(mvc, api, relay);
        customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        home = Fixtures.randomArea();
    }

    @Test
    void customerSeesEstimateWhileSearchingAndFinalPriceOnCompletion() throws Exception {
        Session cheap = master("Arzon", 1, 90_000);
        master("Qimmat", 2, 140_000);
        String orderId = create(null, null);

        customerView(orderId).andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.estimatedPriceMin").value(90000))
                .andExpect(jsonPath("$.estimatedPriceMax").value(140000));
        accept(cheap, orderId).andExpect(status().isOk());
        customerView(orderId).andExpect(jsonPath("$.estimatedPriceMin").value(nullValue()))
                .andExpect(jsonPath("$.priceFrom").value(90000));

        action(cheap, orderId, "start", null).andExpect(status().isOk());
        action(cheap, orderId, "complete", "{\"price\":0}").andExpect(status().isBadRequest());
        action(cheap, orderId, "complete", "{\"price\":0.5}").andExpect(status().isBadRequest());
        action(cheap, orderId, "complete", "{\"price\":175000}").andExpect(status().isOk());

        relay.relayPending();
        mvc.perform(get("/api/v1/notifications").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.items[?(@.type == 'ORDER_COMPLETED')].body", hasItem(
                        "Usta ishni yakunladi. Yakuniy narx: 175000.00 UZS. Rozi boʻlmasangiz nizo oching, "
                                + "aks holda ustani baholang")));
    }

    @Test
    void masterCancellationReopensTheSearch() throws Exception {
        Session first = master("Birinchi", 1, 100_000);
        Session second = master("Ikkinchi", 2, 100_000);
        String orderId = create(null, null);
        accept(first, orderId).andExpect(status().isOk());

        action(first, orderId, "cancel", "{\"reason\":\"Ulgurmayman\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.customer").value(nullValue()))
                .andExpect(jsonPath("$.address.details").value(nullValue()));

        customerView(orderId).andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.master").value(nullValue()));
        // voz kechgan usta qayta ololmaydi va ko'rmaydi; yopilgan taklifi bo'lgan usta qayta oladi
        accept(first, orderId).andExpect(status().isConflict());
        masterView(first, orderId).andExpect(status().isNotFound());
        offers(second).andExpect(jsonPath("$[*].orderId", hasItem(orderId)));
        relay.relayPending();
        mvc.perform(get("/api/v1/notifications").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.items[*].type", hasItem("ORDER_REOPENED")));
        accept(second, orderId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void rejectedOrLostOfferDoesNotRevealTheOrder() throws Exception {
        Session rejecting = master("Rad etgan", 1, 100_000);
        Session winner = master("Olgan", 2, 100_000);
        Session slow = master("Kechikkan", 3, 100_000);
        String orderId = create(null, null);

        masterView(rejecting, orderId).andExpect(status().isOk());
        reject(rejecting, orderId).andExpect(status().isNoContent());
        masterView(rejecting, orderId).andExpect(status().isNotFound());
        accept(winner, orderId).andExpect(status().isOk());
        masterView(slow, orderId).andExpect(status().isNotFound());
        masterView(winner, orderId).andExpect(status().isOk());
    }

    @Test
    void onlineMastersAreOfferedFirst() throws Exception {
        List<Session> near = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            near.add(master("Offline " + i, i * 0.5, 100_000));
        }
        Session farOnline = master("Uzoq online", 8, 100_000);
        mvc.perform(post("/api/v1/master/presence/heartbeat").header("Authorization", farOnline.bearer()))
                .andExpect(status().isNoContent());

        String orderId = create(null, null);

        offers(farOnline).andExpect(jsonPath("$[*].orderId", hasItem(orderId)));
        // 5 ta o'rinning 4 tasi eng yaqin offline ustalarga, eng uzog'i chetda qoladi
        offers(near.get(3)).andExpect(jsonPath("$[*].orderId", hasItem(orderId)));
        offers(near.get(4)).andExpect(jsonPath("$[*].orderId", not(hasItem(orderId))));
    }

    @Test
    void busyMasterGetsNoImmediateOrdersButCanBeBookedLater() throws Exception {
        Session busy = master("Band", 1, 100_000);
        String current = create(null, null);
        accept(busy, current).andExpect(status().isOk());
        action(busy, current, "start", null).andExpect(status().isOk());

        String asap = create(null, null);
        offers(busy).andExpect(jsonPath("$[*].orderId", not(hasItem(asap))));
        String nextWeek = create(Instant.now().plus(Duration.ofDays(7)), null);
        offers(busy).andExpect(jsonPath("$[*].orderId", hasItem(nextWeek)));
    }

    @Test
    void scheduledOrderIsSearchedUntilOneHourBeforeTheVisit() throws Exception {
        Instant visit = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);
        String later = create(visit, null);
        assertThat(deadline(later)).isEqualTo(visit.minus(Duration.ofHours(1)));

        Instant soon = Instant.now().plus(Duration.ofMinutes(20));
        String shortly = create(soon, null);
        assertThat(deadline(shortly)).isBetween(Instant.now().plus(Duration.ofMinutes(14)),
                Instant.now().plus(Duration.ofMinutes(16)));
    }

    @Test
    void blockingUsersReleasesTheirOrders() throws Exception {
        Session blockedMaster = master("Bloklanadigan", 1, 100_000);
        Session spare = master("Zaxira", 2, 100_000);
        String accepted = create(null, UUID.fromString(blockedMaster.userId()));
        accept(blockedMaster, accepted).andExpect(status().isOk());
        String started = create(null, UUID.fromString(blockedMaster.userId()));
        accept(blockedMaster, started).andExpect(status().isOk());
        action(blockedMaster, started, "start", null).andExpect(status().isOk());

        block(blockedMaster);
        customerView(accepted).andExpect(jsonPath("$.status").value("SEARCHING"));
        customerView(started).andExpect(jsonPath("$.status").value("DISPUTED"));
        offers(spare).andExpect(jsonPath("$[*].orderId", hasItem(accepted)));

        block(customer);
        assertThat(jdbc.sql("select status from orders where id = cast(:id as uuid)").param("id", accepted)
                .query(String.class).single()).isEqualTo("CANCELLED");
    }

    @Test
    void orderCreationIsRateLimited() throws Exception {
        redis.opsForValue().set("rl:order-create:" + customer.userId(), "1000000", Duration.ofMinutes(5));
        mvc.perform(post("/api/v1/customer/orders").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fx.orderJson(Seeds.FAUCET_INSTALL, home, null)))
                .andExpect(status().isTooManyRequests());
    }

    // ---------------------------------------------------------------- yordamchilar

    private Session master(String name, double kmNorth, int price) throws Exception {
        return fx.approvedMaster(name, home.north(kmNorth), 15, Map.of(Seeds.FAUCET_INSTALL, price));
    }

    private void block(Session user) throws Exception {
        mvc.perform(post("/api/v1/admin/users/{id}/block", user.userId()).header("Authorization", fx.admin().bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"test\"}")).andExpect(status().isOk());
        relay.relayPending();
    }

    private String create(Instant scheduledAt, UUID preferred) throws Exception {
        String body = fx.orderJson(Seeds.FAUCET_INSTALL, home, preferred);
        if (scheduledAt != null) {
            body = body.substring(0, body.lastIndexOf('}')) + ",\"scheduledAt\":\"" + scheduledAt + "\"}";
        }
        String response = mvc.perform(post("/api/v1/customer/orders").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asString();
    }

    private Instant deadline(String orderId) {
        return jdbc.sql("select search_deadline from orders where id = cast(:id as uuid)").param("id", orderId)
                .query(java.sql.Timestamp.class).single().toInstant();
    }

    private ResultActions customerView(String orderId) throws Exception {
        return mvc.perform(get("/api/v1/customer/orders/{id}", orderId).header("Authorization", customer.bearer()));
    }

    private ResultActions masterView(Session master, String orderId) throws Exception {
        return mvc.perform(get("/api/v1/master/orders/{id}", orderId).header("Authorization", master.bearer()));
    }

    private ResultActions offers(Session master) throws Exception {
        return mvc.perform(get("/api/v1/master/offers").header("Authorization", master.bearer()));
    }

    private ResultActions accept(Session master, String orderId) throws Exception {
        return mvc.perform(post("/api/v1/master/offers/{id}/accept", orderId).header("Authorization", master.bearer()));
    }

    private ResultActions action(Session master, String orderId, String action, String body) throws Exception {
        var req = post("/api/v1/master/orders/{id}/" + action, orderId).header("Authorization", master.bearer());
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private ResultActions reject(Session master, String orderId) throws Exception {
        return mvc.perform(post("/api/v1/master/offers/{id}/reject", orderId).header("Authorization", master.bearer()));
    }
}
