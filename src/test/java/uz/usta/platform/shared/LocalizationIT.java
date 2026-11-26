package uz.usta.platform.shared;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.shared.outbox.OutboxRelay;
import uz.usta.platform.support.ApiClient;
import uz.usta.platform.support.ApiClient.Session;
import uz.usta.platform.support.Fixtures;
import uz.usta.platform.support.Fixtures.Point;
import uz.usta.platform.support.IntegrationTest;
import uz.usta.platform.support.RecordingSmsSender;
import uz.usta.platform.support.Seeds;

/** Xato matnlari, resurs va holat nomlari, tizim sabablari va bildirishnomalar so'rov tilida. */
@IntegrationTest
class LocalizationIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingSmsSender sms;
    @Autowired
    JsonMapper json;
    @Autowired
    OutboxRelay relay;

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
    void errorsAreTranslatedIncludingResourceNames() throws Exception {
        UUID missing = UUID.randomUUID();
        for (String[] expected : new String[][] {
                {"uz", "Xizmat topilmadi (id: " + missing + ")"},
                {"uz-Cyrl", "Хизмат топилмади (ID: " + missing + ")"},
                {"ru", "Не найдено: Услуга (id: " + missing + ")"},
                {"en", "Service not found (id: " + missing + ")"}}) {
            mvc.perform(get("/api/v1/public/catalog/services/{id}", missing).header("Accept-Language", expected[0]))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value(expected[1]))
                    .andExpect(jsonPath("$.title").value(expected[1])); // sarlavhada {0} qolmaydi
        }
        mvc.perform(get("/api/v1/me").header("Accept-Language", "uz-Cyrl"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Авторизациядан ўтилмаган ёки токен яроқсиз"));
    }

    @Test
    void statusNamesReasonsAndNotificationsFollowTheRequestLanguage() throws Exception {
        Point home = Fixtures.randomArea();
        Session master = fx.approvedMaster("Til ustasi", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        String order = fx.startedOrder(customer, home, master);

        mvc.perform(post("/api/v1/customer/orders/{id}/cancel", order).header("Authorization", customer.bearer())
                        .header("Accept-Language", "ru"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("выполняется")));

        mvc.perform(post("/api/v1/complaints").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + order + "\",\"reason\":\"QUALITY\",\"description\":\"Yomon\","
                                + "\"dispute\":true}"))
                .andExpect(status().isCreated());
        relay.relayPending();

        mvc.perform(get("/api/v1/customer/orders/{id}", order).header("Authorization", customer.bearer())
                        .header("Accept-Language", "uz-Cyrl"))
                .andExpect(jsonPath("$.history[*].reason", hasItem("Шикоят: иш сифати ёмон")))
                .andExpect(jsonPath("$.serviceName").value("Жўмрак ўрнатиш"));
        mvc.perform(get("/api/v1/notifications").header("Authorization", master.bearer())
                        .header("Accept-Language", "uz-Cyrl"))
                .andExpect(jsonPath("$.items[*].title", hasItem("Низо очилди")));
    }

    @Test
    void usersCannotImitateSystemReasons() throws Exception {
        Point home = Fixtures.randomArea();
        Session master = fx.approvedMaster("Soxta ustasi", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        String order = fx.order(customer, home, master);
        mvc.perform(post("/api/v1/customer/orders/{id}/cancel", order).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"system:master-blocked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelReason").value("system:master-blocked"));
    }
}
