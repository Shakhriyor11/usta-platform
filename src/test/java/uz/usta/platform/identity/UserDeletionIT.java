package uz.usta.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

/** Foydalanuvchini o'chirish: to'siqlar, shaxsiy ma'lumotlarning o'chishi va tarixning saqlanishi. */
@IntegrationTest
class UserDeletionIT {

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

    ApiClient api;
    Fixtures fx;
    Session admin;
    Point home;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, sms, json);
        fx = new Fixtures(mvc, api, relay);
        admin = fx.admin();
        home = Fixtures.randomArea();
    }

    @Test
    void customerIsAnonymizedAndThePhoneCanRegisterAgain() throws Exception {
        Session master = fx.approvedMaster("Qoladigan usta", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        Session customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        send(HttpMethod.PUT, customer, "/api/v1/customer/profile", "{\"fullName\":\"Maxfiy Mijoz\"}")
                .andExpect(status().isOk());
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/customer/profile/avatar")
                .file(new MockMultipartFile("file", "a.png", "image/png", Seeds.PNG))
                .header("Authorization", customer.bearer())).andExpect(status().isOk());
        send(HttpMethod.POST, customer, "/api/v1/customer/addresses", "{\"title\":\"Uy\",\"address\":\"Maxfiy 1\","
                + "\"location\":{\"latitude\":41.311081,\"longitude\":69.240562}}").andExpect(status().isCreated());
        send(HttpMethod.POST, customer, "/api/v1/push-tokens",
                "{\"token\":\"del-" + UUID.randomUUID() + "\",\"platform\":\"ANDROID\"}")
                .andExpect(status().isNoContent());
        String searching = fx.order(customer, home, master); // hali qabul qilinmagan
        mvc.perform(multipart("/api/v1/customer/orders/{id}/attachments", searching)
                .file(new MockMultipartFile("file", "a.png", "image/png", Seeds.PNG))
                .header("Authorization", customer.bearer())).andExpect(status().isCreated());
        relay.relayPending();

        remove(customer).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").header("Authorization", customer.bearer())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + customer.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
        relay.relayPending();
        relay.relayPending();

        assertThat(jdbc.sql("select status || ':' || coalesce(phone, '-') from users where id = cast(:id as uuid)")
                .param("id", customer.userId()).query(String.class).single()).isEqualTo("DELETED:-");
        for (String table : new String[] {"user_roles", "refresh_tokens", "customer_profiles", "addresses",
                "notifications", "push_tokens"}) {
            assertThat(count("select count(*) from " + table + " where user_id = cast(:id as uuid)", customer.userId()))
                    .as(table).isZero();
        }
        assertThat(count("select count(*) from order_attachments where order_id = cast(:id as uuid)", searching))
                .isZero();
        assertThat(count("select count(*) from orders where id = cast(:id as uuid) and address_text = '-' "
                + "and description = '-' and address_details is null and latitude = round(latitude::numeric, 2)",
                searching)).isOne();
        mvc.perform(get("/api/v1/admin/orders/{id}", searching).header("Authorization", admin.bearer())
                        .header("Accept-Language", "ru"))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"))
                .andExpect(jsonPath("$.order.cancelReason").value("Учётная запись клиента удалена"))
                .andExpect(jsonPath("$.order.history[*].reason", hasItem("Учётная запись клиента удалена")));
        mvc.perform(get("/api/v1/admin/users").param("phone", customer.phone()).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.items", hasSize(0)));
        remove(customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_DELETED"));

        redisFreeLogin(customer);
    }

    @Test
    void usersWithUnfinishedWorkOrDebtCannotBeDeleted() throws Exception {
        Session master = fx.approvedMaster("Qarzdor usta", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        Session customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        String order = fx.startedOrder(customer, home, master);
        for (Session busy : new Session[] {customer, master}) {
            remove(busy).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("USER_HAS_ACTIVE_ORDERS"));
        }
        send(HttpMethod.POST, master, "/api/v1/master/orders/" + order + "/complete", "{\"price\":100000}")
                .andExpect(status().isOk());
        remove(master).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_HAS_DEBT"))
                .andExpect(jsonPath("$.debt").value(10000.0));
        // rad etilgan urinish hech narsani o'zgartirmaydi
        mvc.perform(get("/api/v1/me").header("Authorization", master.bearer())).andExpect(status().isOk());
    }

    @Test
    void masterDisappearsFromSearchAndCatalog() throws Exception {
        Session master = fx.approvedMaster("Ketadigan usta", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        mvc.perform(multipart("/api/v1/master/portfolio")
                .file(new MockMultipartFile("file", "w.png", "image/png", Seeds.PNG))
                .header("Authorization", master.bearer())).andExpect(status().isCreated());

        remove(master).andExpect(status().isNoContent());
        relay.relayPending();
        relay.relayPending();

        for (String table : new String[] {"master_services", "master_portfolio",
                "master_verification_documents",
                "search_master_services"}) {
            assertThat(count("select count(*) from " + table + " where master_id = cast(:id as uuid)",
                    master.userId())).as(table).isZero();
        }
        assertThat(count("select count(*) from master_profiles where user_id = cast(:id as uuid) and full_name = '' "
                + "and work_latitude is null and avatar_key is null and not is_active", master.userId())).isOne();
        mvc.perform(get("/api/v1/public/masters/{id}", master.userId())).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- yordamchilar

    /** Shu raqam yangi, bo'sh hisob ochadi. OTP qayta yuborish tanaffusi testda kutilmaydi. */
    private void redisFreeLogin(Session old) throws Exception {
        redis.delete("otp:cd:" + old.phone());
        Session again = api.login(old.phone());
        assertThat(again.userId()).isNotEqualTo(old.userId());
    }

    @Autowired
    org.springframework.data.redis.core.StringRedisTemplate redis;

    private long count(String sql, String id) {
        return jdbc.sql(sql).param("id", id).query(Long.class).single();
    }

    private ResultActions remove(Session user) throws Exception {
        return mvc.perform(delete("/api/v1/admin/users/{id}", user.userId()).header("Authorization", admin.bearer()));
    }

    private ResultActions send(HttpMethod method, Session user, String url, String body) throws Exception {
        var request = method == HttpMethod.PUT ? put(url) : post(url);
        return mvc.perform(request.header("Authorization", user.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
