package uz.usta.platform.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
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

/** Auth, katalog, qidiruv, profil, so'rov parametrlari va outbox bo'yicha himoya qoidalari. */
@IntegrationTest
class HardeningIT {

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

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, sms, json);
        fx = new Fixtures(mvc, api, relay);
        customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
    }

    @Test
    void unknownSortFieldIsAClientError() throws Exception {
        Session admin = fx.admin();
        Session master = fx.approvedMaster("Saralash", Fixtures.randomArea(), 10,
                Map.of(Seeds.FAUCET_INSTALL, 100_000));
        Map<String, Session> endpoints = Map.of(
                "/api/v1/customer/orders", customer,
                "/api/v1/complaints", customer,
                "/api/v1/masters/" + master.userId() + "/reviews", customer,
                "/api/v1/master/orders", master,
                "/api/v1/master/finance/commissions", master,
                "/api/v1/master/finance/transactions", master,
                "/api/v1/admin/orders", admin,
                "/api/v1/admin/users", admin,
                "/api/v1/admin/masters", admin,
                "/api/v1/admin/complaints", admin);
        for (var e : endpoints.entrySet()) {
            int code = mvc.perform(get(e.getKey()).param("sort", "nosuchfield")
                    .header("Authorization", e.getValue().bearer())).andReturn().getResponse().getStatus();
            assertThat(code).as(e.getKey()).isEqualTo(400);
        }
        mvc.perform(get("/api/v1/admin/finance/commissions").param("sort", "nosuchfield")
                .header("Authorization", admin.bearer())).andExpect(status().isBadRequest());
    }

    @Test
    void otpGuessingIsCappedPerDay() throws Exception {
        String phone = ApiClient.randomPhone();
        for (int round = 0; round < 3; round++) { // 3 kod × 5 urinish = 15
            redis.delete("otp:cd:" + phone);
            api.requestOtp(phone);
            String wrong = sms.lastCode(phone).equals("00000") ? "11111" : "00000";
            for (int i = 0; i < 5; i++) {
                verify(phone, wrong);
            }
        }
        redis.delete("otp:cd:" + phone);
        mvc.perform(post("/api/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\"}")).andExpect(status().isTooManyRequests());
        verify(phone, "00000").andExpect(status().isTooManyRequests());
    }

    @Test
    void superAdminRoleFollowsTheConfiguredPhoneList() throws Exception {
        jdbc.sql("insert into user_roles (user_id, role) values (cast(:id as uuid), 'ADMIN'), "
                        + "(cast(:id as uuid), 'SUPER_ADMIN')")
                .param("id", customer.userId()).update(); // ro'yxatdan olib tashlangan sobiq super admin
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + customer.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles", not(hasItem("SUPER_ADMIN"))))
                .andExpect(jsonPath("$.user.roles", not(hasItem("ADMIN"))));
    }

    @Test
    void logoutRemovesThePushTokenOfThatDevice() throws Exception {
        String token = "fcm-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/push-tokens").header("Authorization", customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"platform\":\"ANDROID\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + customer.refreshToken() + "\",\"pushToken\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.sql("select count(*) from push_tokens where token = :t").param("t", token)
                .query(Long.class).single()).isZero();
    }

    @Test
    void identicalSearchesOfDifferentCustomersShareTheCache() throws Exception {
        Point p = Fixtures.randomArea();
        String url = String.format(Locale.ROOT, "/api/v1/search/masters?serviceId=%s&lat=%.6f&lng=%.6f",
                Seeds.FAUCET_INSTALL, p.lat(), p.lng());
        Session other = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        Set<String> before = redis.keys("search:v*");
        mvc.perform(get(url).header("Authorization", customer.bearer())).andExpect(status().isOk());
        mvc.perform(get(url).header("Authorization", other.bearer())).andExpect(status().isOk());
        Set<String> created = redis.keys("search:v*");
        created.removeAll(before);
        assertThat(created).hasSize(1);
    }

    @Test
    void servicesOfAnInactiveCategoryAreUnavailableEverywhere() throws Exception {
        Session admin = fx.admin();
        Point home = Fixtures.randomArea();
        Session master = fx.approvedMaster("Toifa ustasi", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        String slug = "audit-" + UUID.randomUUID().toString().substring(0, 8);
        String parent = created(admin, "/api/v1/admin/catalog/categories",
                "{\"slug\":\"" + slug + "\"," + names("Ota", "Отец", "Parent") + "}");
        String child = created(admin, "/api/v1/admin/catalog/categories",
                "{\"slug\":\"" + slug + "-c\",\"parentId\":\"" + parent + "\","
                        + names("Bola", "Дочь", "Child") + "}");
        UUID service = UUID.fromString(created(admin, "/api/v1/admin/catalog/services",
                "{\"categoryId\":\"" + child + "\",\"slug\":\"" + slug + "-s\","
                        + names("Xizmat", "Услуга", "Service") + "}"));
        fx.setPrice(master, service, 1000);
        relay.relayPending();

        send(HttpMethod.PUT, admin, "/api/v1/admin/catalog/categories/" + parent,
                "{\"slug\":\"" + slug + "\"," + names("Ota", "Отец", "Parent") + ",\"status\":\"INACTIVE\"}")
                .andExpect(status().isOk());
        relay.relayPending();

        send(HttpMethod.POST, customer, "/api/v1/customer/orders", fx.orderJson(service, home, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERVICE_NOT_AVAILABLE"));
        mvc.perform(get("/api/v1/public/catalog/services/{id}", service)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/catalog/categories/{id}/services", child)).andExpect(status().isNotFound());
        assertThat(jdbc.sql("select count(*) from search_master_services where service_id = :s").param("s", service)
                .query(Long.class).single()).isZero();
    }

    @Test
    void verifiedMasterCannotChangeNameOrPhoto() throws Exception {
        Session master = fx.approvedMaster("Tasdiqlangan Ism", Fixtures.randomArea(), 10,
                Map.of(Seeds.FAUCET_INSTALL, 100_000));
        String sameName = "{\"fullName\":\"Tasdiqlangan Ism\",\"bio\":\"yangi\",\"experienceYears\":6,"
                + "\"workRadiusKm\":12}";
        send(HttpMethod.PUT, master, "/api/v1/master/profile", sameName).andExpect(status().isOk());
        send(HttpMethod.PUT, master, "/api/v1/master/profile", sameName.replace("Tasdiqlangan Ism", "Boshqa Odam"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDENTITY_LOCKED"));
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/master/profile/avatar")
                        .file(new MockMultipartFile("file", "a.png", "image/png", Seeds.PNG))
                        .header("Authorization", master.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDENTITY_LOCKED"));
    }

    @Test
    void uploadsAreRateLimited() throws Exception {
        redis.opsForValue().set("rl:upload:" + customer.userId(), "1000000", Duration.ofMinutes(5));
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/customer/profile/avatar")
                        .file(new MockMultipartFile("file", "a.png", "image/png", Seeds.PNG))
                        .header("Authorization", customer.bearer()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void deadOutboxEventsCanBeRequeued() throws Exception {
        Session admin = fx.admin();
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into outbox_events (id, aggregate_type, event_type, payload, attempts, last_error) "
                        + "values (:id, 'audit', 'audit.dead', '{}'::jsonb, 10, 'boom')")
                .param("id", id).update();
        mvc.perform(get("/api/v1/admin/outbox/dead").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(id.toString())));
        mvc.perform(post("/api/v1/admin/outbox/dead/retry").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requeued").isNumber());
        relay.relayPending();
        assertThat(jdbc.sql("select published_at is not null from outbox_events where id = :id").param("id", id)
                .query(Boolean.class).single()).isTrue();
        mvc.perform(get("/api/v1/admin/outbox/dead").header("Authorization", customer.bearer()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- yordamchilar

    private static String names(String uz, String ru, String en) {
        return "\"name\":{\"uz\":\"" + uz + "\",\"ru\":\"" + ru + "\",\"en\":\"" + en + "\"}";
    }

    private ResultActions verify(String phone, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"));
    }

    private ResultActions send(HttpMethod method, Session user, String url, String body) throws Exception {
        var request = method == HttpMethod.PUT ? put(url) : post(url);
        return mvc.perform(request.header("Authorization", user.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String created(Session user, String url, String body) throws Exception {
        String response = send(HttpMethod.POST, user, url, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asString();
    }
}
