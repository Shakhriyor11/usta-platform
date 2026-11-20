package uz.usta.platform.complaint;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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

@IntegrationTest
class ComplaintIT {

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
    Session master;
    Session admin;
    Point home;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, sms, json);
        fx = new Fixtures(mvc, api, relay);
        customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        home = Fixtures.randomArea();
        master = fx.approvedMaster("Shikoyat ustasi", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        admin = fx.admin();
    }

    @Test
    void complaintWithDisputeIsResolvedTogetherWithOrder() throws Exception {
        String searching = fx.order(customer, home, master);
        file(customer, searching, "NO_SHOW", true).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ALLOWED"));

        String orderId = fx.completedOrder(customer, home, master, 300_000);
        String complaintId = id(file(customer, orderId, "QUALITY", true).andExpect(status().isCreated())
                .andExpect(jsonPath("$.againstUserId").value(master.userId()))
                .andExpect(jsonPath("$.status").value("OPEN")));
        file(customer, orderId, "PRICE", false).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_OPEN"));
        Session stranger = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        file(stranger, orderId, "OTHER", false).andExpect(status().isNotFound());

        // nizo ochildi: baholash to'xtaydi
        mvc.perform(get("/api/v1/customer/orders/{id}", orderId).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.status").value("DISPUTED"));
        mvc.perform(post("/api/v1/customer/orders/{id}/review", orderId).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":1}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/admin/complaints").header("Authorization", admin.bearer())
                        .param("status", "OPEN").param("size", "100"))
                .andExpect(jsonPath("$.items[*].id", hasItem(complaintId)));
        mvc.perform(post("/api/v1/admin/complaints/{id}/take", complaintId).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.complaint.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.orderStatus").value("DISPUTED"))
                .andExpect(jsonPath("$.complaintsAgainstUser").value(1));

        close(complaintId, "{\"result\":\"RESOLVED\",\"note\":\"Qisman qayta qilinadi\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_RESOLUTION_REQUIRED"));
        close(complaintId, """
                {"result":"RESOLVED","note":"Narx kamaytirildi","orderResolution":"COMPLETED","price":200000}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complaint.status").value("RESOLVED"))
                .andExpect(jsonPath("$.orderStatus").value("COMMISSION"));
        close(complaintId, "{\"result\":\"REJECTED\",\"note\":\"x\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // komissiya yangi narxdan, baholash yana mumkin
        mvc.perform(get("/api/v1/master/finance/summary").header("Authorization", master.bearer()))
                .andExpect(jsonPath("$.balance").value(-20000));
        mvc.perform(post("/api/v1/customer/orders/{id}/review", orderId).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":3}"))
                .andExpect(status().isCreated());

        relay.relayPending();
        mvc.perform(get("/api/v1/notifications").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.items[*].type", hasItem("COMPLAINT_RESOLVED")));
        mvc.perform(get("/api/v1/complaints/{id}", complaintId).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.resolutionNote").value("Narx kamaytirildi"));
        mvc.perform(get("/api/v1/complaints/{id}", complaintId).header("Authorization", master.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void masterComplainsAboutCustomerWithoutDispute() throws Exception {
        String orderId = fx.startedOrder(customer, home, master);
        String complaintId = id(file(master, orderId, "BEHAVIOR", false).andExpect(status().isCreated())
                .andExpect(jsonPath("$.againstUserId").value(customer.userId())));
        mvc.perform(get("/api/v1/customer/orders/{id}", orderId).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        close(complaintId, "{\"result\":\"REJECTED\",\"note\":\"Dalil yetarli emas\"}")
                .andExpect(jsonPath("$.complaint.status").value("REJECTED"));
        relay.relayPending();
        mvc.perform(get("/api/v1/notifications").header("Authorization", master.bearer()))
                .andExpect(jsonPath("$.items[*].type", hasItem("COMPLAINT_REJECTED")));
        mvc.perform(get("/api/v1/complaints").header("Authorization", master.bearer()))
                .andExpect(jsonPath("$.items[*].id", hasItem(complaintId)));
        // oddiy foydalanuvchi admin yo'liga kira olmaydi
        mvc.perform(get("/api/v1/admin/complaints").header("Authorization", master.bearer()))
                .andExpect(status().isForbidden());
    }

    private ResultActions file(Session author, String orderId, String reason, boolean dispute) throws Exception {
        return mvc.perform(post("/api/v1/complaints").header("Authorization", author.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"" + orderId + "\",\"reason\":\"" + reason
                        + "\",\"description\":\"Test shikoyat\",\"dispute\":" + dispute + "}"));
    }

    private ResultActions close(String id, String body) throws Exception {
        return mvc.perform(post("/api/v1/admin/complaints/{id}/close", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String id(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asString();
    }
}
