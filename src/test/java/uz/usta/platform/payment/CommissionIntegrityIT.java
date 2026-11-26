package uz.usta.platform.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

/** Komissiya yaxlitligi: qayta talab qilinmaydi, ortiqcha to'lov chegiriladi, parallel to'lov, qarzdor cheklovi. */
@IntegrationTest
class CommissionIntegrityIT {

    static final LocalDate THIS_WEEK = LocalDate.now(ZoneId.of("Asia/Tashkent"))
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    static final LocalDate LAST_WEEK = THIS_WEEK.minusWeeks(1);

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
        master = fx.approvedMaster("Hisob ustasi", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        admin = fx.admin();
    }

    @Test
    void paidCommissionIsNotBilledAgainWhenDisputeKeepsThePrice() throws Exception {
        String order = fx.completedOrder(customer, home, master, 200_000);
        invoice(LAST_WEEK);
        settle(LAST_WEEK, "20000").andExpect(status().isOk());

        dispute(order);
        resolve(order, "{\"resolution\":\"COMPLETED\",\"note\":\"Narx o'zgarmadi\"}");
        closePeriods();

        summary().andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.invoicedDue").value(0))
                .andExpect(jsonPath("$.accrued").value(0));
    }

    @Test
    void overpaymentAfterRecalculationIsOffsetAgainstNextDebt() throws Exception {
        String order = fx.completedOrder(customer, home, master, 200_000);
        invoice(LAST_WEEK);
        settle(LAST_WEEK, "20000").andExpect(status().isOk());

        // to'langandan keyin narx tushirildi: 5 000 so'm ortiqcha
        dispute(order);
        resolve(order, "{\"resolution\":\"COMPLETED\",\"price\":150000,\"note\":\"Chegirma\"}");
        closePeriods();
        summary().andExpect(jsonPath("$.balance").value(5000)).andExpect(jsonPath("$.invoicedDue").value(0));

        fx.completedOrder(customer, home, master, 100_000); // 10 000 komissiya
        invoice(LAST_WEEK);
        summary().andExpect(jsonPath("$.invoicedDue").value(5000));
        settle(LAST_WEEK, "10000").andExpect(status().isConflict()).andExpect(jsonPath("$.outstanding").value(5000));
        settle(LAST_WEEK, "5000").andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0));
        summary().andExpect(jsonPath("$.invoicedDue").value(0));
    }

    @Test
    void higherPriceAfterPaymentChargesOnlyTheDifference() throws Exception {
        String order = fx.completedOrder(customer, home, master, 200_000);
        invoice(LAST_WEEK);
        settle(LAST_WEEK, "20000").andExpect(status().isOk());

        dispute(order);
        resolve(order, "{\"resolution\":\"COMPLETED\",\"price\":250000,\"note\":\"Qo'shimcha ish\"}");
        closePeriods(); // dastlabki davr saqlangan — darhol yopiladi

        summary().andExpect(jsonPath("$.balance").value(-5000)).andExpect(jsonPath("$.invoicedDue").value(5000));
        settle(LAST_WEEK, "5000").andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void disputedCommissionIsNotInvoicedUntilResolved() throws Exception {
        String order = fx.completedOrder(customer, home, master, 200_000);
        dispute(order);
        invoice(LAST_WEEK);
        summary().andExpect(jsonPath("$.invoicedDue").value(0)).andExpect(jsonPath("$.accrued").value(0));
        settle(LAST_WEEK, "20000").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTHING_TO_SETTLE"));

        resolve(order, "{\"resolution\":\"COMPLETED\",\"note\":\"Usta haq\"}");
        closePeriods();
        summary().andExpect(jsonPath("$.invoicedDue").value(20000));
    }

    @Test
    void parallelSettlementsRecordExactlyOnePayment() throws Exception {
        fx.completedOrder(customer, home, master, 200_000);
        invoice(LAST_WEEK);

        int n = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(n)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return settle(LAST_WEEK, "20000").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
        }

        assertThat(statuses).containsOnlyOnce(200).filteredOn(s -> s != 200).containsOnly(409);
        assertThat(jdbc.sql("select count(*) from transactions where master_id = cast(:m as uuid) "
                        + "and type = 'COMMISSION_PAYMENT'")
                .param("m", master.userId()).query(Long.class).single()).isEqualTo(1);
        summary().andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void overdueDebtorGetsNoOffersAndCannotAccept() throws Exception {
        fx.completedOrder(customer, home, master, 200_000);
        String offeredBefore = fx.order(customer, home, master); // qarz paydo bo'lishidan oldingi taklif
        LocalDate longAgo = THIS_WEEK.minusWeeks(5);
        invoice(longAgo);
        summary().andExpect(jsonPath("$.overdue").value(true));

        mvc.perform(post("/api/v1/master/offers/{id}/accept", offeredBefore).header("Authorization", master.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MASTER_HAS_OVERDUE_DEBT"));
        mvc.perform(post("/api/v1/customer/orders").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fx.orderJson(Seeds.FAUCET_INSTALL, home,
                                java.util.UUID.fromString(master.userId()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MASTER_NOT_AVAILABLE"));
        String broadcast = idOf(mvc.perform(post("/api/v1/customer/orders")
                .header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(fx.orderJson(Seeds.FAUCET_INSTALL, home, null))));
        offers().andExpect(jsonPath("$[*].orderId", not(hasItem(broadcast))));

        settle(longAgo, "20000").andExpect(status().isOk());
        summary().andExpect(jsonPath("$.overdue").value(false));
        mvc.perform(post("/api/v1/master/offers/{id}/accept", offeredBefore).header("Authorization", master.bearer()))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- yordamchilar

    /** Ochiq komissiyalarni berilgan o'tgan haftaga ko'chirib, davrni yopadi (vaqtni surish o'rniga). */
    private void invoice(LocalDate period) throws Exception {
        jdbc.sql("update commission_reports set period = :p where master_id = cast(:m as uuid) and status = 'ACCRUED'")
                .param("p", period).param("m", master.userId()).update();
        closePeriods();
    }

    private void closePeriods() throws Exception {
        mvc.perform(post("/api/v1/admin/finance/invoices/close").header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
    }

    private ResultActions summary() throws Exception {
        return mvc.perform(get("/api/v1/master/finance/summary").header("Authorization", master.bearer()));
    }

    private ResultActions offers() throws Exception {
        return mvc.perform(get("/api/v1/master/offers").header("Authorization", master.bearer()));
    }

    private ResultActions settle(LocalDate period, String amount) throws Exception {
        return mvc.perform(post("/api/v1/admin/finance/settlements").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"masterId\":\"" + master.userId() + "\",\"period\":\"" + period + "\",\"amount\":" + amount
                        + "}"));
    }

    private void dispute(String orderId) throws Exception {
        mvc.perform(post("/api/v1/customer/orders/{id}/dispute", orderId).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Tekshirib bering\"}"))
                .andExpect(status().isOk());
    }

    private void resolve(String orderId, String body) throws Exception {
        mvc.perform(post("/api/v1/admin/orders/{id}/resolve", orderId).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private String idOf(ResultActions created) throws Exception {
        return json.readTree(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asString();
    }
}
