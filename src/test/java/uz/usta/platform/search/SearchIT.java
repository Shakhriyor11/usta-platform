package uz.usta.platform.search;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.search.internal.application.SearchIndexer;
import uz.usta.platform.shared.outbox.OutboxRelay;
import uz.usta.platform.support.ApiClient;
import uz.usta.platform.support.ApiClient.Session;
import uz.usta.platform.support.Fixtures;
import uz.usta.platform.support.Fixtures.Point;
import uz.usta.platform.support.IntegrationTest;
import uz.usta.platform.support.RecordingSmsSender;
import uz.usta.platform.support.Seeds;

@IntegrationTest
class SearchIT {

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
    SearchIndexer indexer;

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
    void filtersAndSortsByRatingCountAndDistance() throws Exception {
        Point center = Fixtures.randomArea();
        Session near = fx.approvedMaster("Yaqin", center.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        Session top = fx.approvedMaster("Eng yaxshi", center.north(4), 10, Map.of(Seeds.FAUCET_INSTALL, 200_000));
        Session sameRatingMoreReviews = fx.approvedMaster("Ko'p baholangan", center.north(6), 10,
                Map.of(Seeds.FAUCET_INSTALL, 150_000));
        // uzoq: radius 20 km dan tashqarida
        fx.approvedMaster("Uzoq", center.north(30), 50, Map.of(Seeds.FAUCET_INSTALL, 50_000));
        // ish radiusi kichik: nuqta uning hududiga kirmaydi
        fx.approvedMaster("Kichik hudud", center.north(8), 3, Map.of(Seeds.FAUCET_INSTALL, 90_000));
        // boshqa xizmat
        fx.approvedMaster("Boshqa xizmat", center.north(2), 10, Map.of(Seeds.TOILET_INSTALL, 90_000));

        setRating(top, "4.80", 10);
        setRating(sameRatingMoreReviews, "4.50", 30);
        setRating(near, "4.50", 3);

        mvc.perform(search(center).param("serviceId", Seeds.FAUCET_INSTALL.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                // rating ↓ -> rating_count ↓ -> masofa ↑
                .andExpect(jsonPath("$.items[*].fullName", contains("Eng yaxshi", "Ko'p baholangan", "Yaqin")))
                .andExpect(jsonPath("$.items[2].distanceKm").value(1.0))
                .andExpect(jsonPath("$.items[2].priceFrom").value(100000))
                .andExpect(jsonPath("$.items[0].online").value(false));

        mvc.perform(search(center).param("serviceId", Seeds.FAUCET_INSTALL.toString()).param("sort", "DISTANCE"))
                .andExpect(jsonPath("$.items[*].fullName", contains("Yaqin", "Eng yaxshi", "Ko'p baholangan")));
        mvc.perform(search(center).param("serviceId", Seeds.FAUCET_INSTALL.toString()).param("sort", "PRICE"))
                .andExpect(jsonPath("$.items[*].fullName", contains("Yaqin", "Ko'p baholangan", "Eng yaxshi")));

        // filtrlar
        mvc.perform(search(center).param("serviceId", Seeds.FAUCET_INSTALL.toString()).param("minRating", "4.6"))
                .andExpect(jsonPath("$.items[*].fullName", contains("Eng yaxshi")));
        mvc.perform(search(center).param("serviceId", Seeds.FAUCET_INSTALL.toString())
                        .param("priceMin", "120000").param("priceMax", "180000"))
                .andExpect(jsonPath("$.items[*].fullName", contains("Ko'p baholangan")));
        mvc.perform(search(center).param("serviceId", Seeds.FAUCET_INSTALL.toString()).param("radiusKm", "5"))
                .andExpect(jsonPath("$.items[*].fullName", contains("Eng yaxshi", "Yaqin")));

        // kategoriya bo'yicha: santexnika -> ikkala xizmat; narx — mos xizmatlarning eng pasti
        mvc.perform(search(center).param("categoryId", Seeds.PLUMBING.toString()))
                .andExpect(jsonPath("$.totalElements").value(4));

        // pagination
        mvc.perform(search(center).param("serviceId", Seeds.FAUCET_INSTALL.toString()).param("size", "2")
                        .param("page", "1"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void onlineStatusAndCacheInvalidation() throws Exception {
        Point center = Fixtures.randomArea();
        Session a = fx.approvedMaster("A usta", center.north(1), 10, Map.of(Seeds.PIPE_LEAK, 80_000));
        fx.approvedMaster("B usta", center.north(2), 10, Map.of(Seeds.PIPE_LEAK, 90_000));

        mvc.perform(search(center).param("serviceId", Seeds.PIPE_LEAK.toString()).param("onlineOnly", "true"))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(post("/api/v1/master/presence/heartbeat").header("Authorization", a.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(search(center).param("serviceId", Seeds.PIPE_LEAK.toString()).param("onlineOnly", "true"))
                .andExpect(jsonPath("$.items[*].fullName", contains("A usta")))
                .andExpect(jsonPath("$.items[0].online").value(true));

        // keshlangan natija narx o'zgargach yangilanadi (event -> indeks -> kesh versiyasi)
        mvc.perform(search(center).param("serviceId", Seeds.PIPE_LEAK.toString()).param("sort", "DISTANCE"))
                .andExpect(jsonPath("$.items[0].priceFrom").value(80000));
        fx.setPrice(a, Seeds.PIPE_LEAK, 70_000);
        relay.relayPending();
        mvc.perform(search(center).param("serviceId", Seeds.PIPE_LEAK.toString()).param("sort", "DISTANCE"))
                .andExpect(jsonPath("$.items[0].priceFrom").value(70000));

        // offline va usta xizmatni o'chirsa — natijadan chiqadi
        mvc.perform(delete("/api/v1/master/presence").header("Authorization", a.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/master/services/{id}", Seeds.PIPE_LEAK).header("Authorization", a.bearer()))
                .andExpect(status().isNoContent());
        relay.relayPending();
        mvc.perform(search(center).param("serviceId", Seeds.PIPE_LEAK.toString()))
                .andExpect(jsonPath("$.items[*].fullName", contains("B usta")));
    }

    @Test
    void searchBySavedAddress() throws Exception {
        Point center = Fixtures.randomArea();
        fx.approvedMaster("Manzil ustasi", center.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        String body = mvc.perform(post("/api/v1/customer/addresses").header("Authorization", customer.bearer())
                        .contentType("application/json")
                        .content(String.format(Locale.ROOT, """
                                {"title":"Uy","address":"X","location":{"latitude":%.6f,"longitude":%.6f}}""",
                                center.lat(), center.lng())))
                .andReturn().getResponse().getContentAsString();
        String addressId = json.readTree(body).get("id").asString();
        mvc.perform(get("/api/v1/search/masters").header("Authorization", customer.bearer())
                        .param("serviceId", Seeds.FAUCET_INSTALL.toString()).param("addressId", addressId))
                .andExpect(jsonPath("$.items[*].fullName", contains("Manzil ustasi")));
    }

    @Test
    void validation() throws Exception {
        mvc.perform(get("/api/v1/search/masters").header("Authorization", customer.bearer())
                        .param("lat", "41.3").param("lng", "69.2"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TARGET_REQUIRED"));
        mvc.perform(get("/api/v1/search/masters").header("Authorization", customer.bearer())
                        .param("serviceId", Seeds.FAUCET_INSTALL.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
        mvc.perform(get("/api/v1/search/masters").header("Authorization", customer.bearer())
                        .param("serviceId", Seeds.FAUCET_INSTALL.toString()).param("lat", "41.3").param("lng", "69.2")
                        .param("radiusKm", "500"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/search/masters").param("serviceId", Seeds.FAUCET_INSTALL.toString())
                        .param("lat", "41.3").param("lng", "69.2"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder search(Point p) {
        return get("/api/v1/search/masters").header("Authorization", customer.bearer())
                .param("lat", String.valueOf(p.lat())).param("lng", String.valueOf(p.lng()));
    }

    /** Reytingni to'g'ridan-to'g'ri qo'yish (baholar oqimi OrderFlowIT da sinaladi) va qayta indekslash. */
    private void setRating(Session master, String avg, int count) {
        jdbc.sql("update master_profiles set rating_avg = cast(:avg as numeric), rating_count = :count where user_id = :id")
                .param("avg", avg).param("count", count).param("id", UUID.fromString(master.userId())).update();
        indexer.reindex(UUID.fromString(master.userId()));
    }
}
