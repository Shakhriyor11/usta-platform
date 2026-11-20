package uz.usta.platform.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
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

/** Naqd to'lov modeli: komissiya -> ledger (qarz) -> haftalik hisob-faktura -> to'lov; nizo va solishtirish. */
@IntegrationTest
class FinanceIT {

    static final LocalDate THIS_WEEK = LocalDate.now(ZoneId.of("Asia/Tashkent"))
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
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
    Point home;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, sms, json);
        fx = new Fixtures(mvc, api, relay);
        customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        home = Fixtures.randomArea();
        master = fx.approvedMaster("Moliya ustasi", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
    }

    @Test
    void commissionIsInvoicedWeeklyAndSettled() throws Exception {
        String o1 = fx.completedOrder(customer, home, master, 200_000);
        String o2 = fx.completedOrder(customer, home, master, 180_000);

        summary().andExpect(jsonPath("$.balance").value(-38000))
                .andExpect(jsonPath("$.accrued").value(38000))
                .andExpect(jsonPath("$.invoicedDue").value(0))
                .andExpect(jsonPath("$.currency").value("UZS"));
        mvc.perform(get("/api/v1/master/finance/commissions").header("Authorization", master.bearer()))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].orderId", hasItem(o1)))
                .andExpect(jsonPath("$.items[0].status").value("ACCRUED"))
                .andExpect(jsonPath("$.items[0].period").value(THIS_WEEK.toString()))
                .andExpect(jsonPath("$.items[0].ratePercent").value(10));
        mvc.perform(get("/api/v1/master/finance/transactions").header("Authorization", master.bearer()))
                .andExpect(jsonPath("$.items[*].type", hasItem("COMMISSION_CHARGE")))
                .andExpect(jsonPath("$.items[*].amount", hasItem(-20000.0)));
        assertThat(jdbc.sql("select status || ':' || amount from payments where order_id = cast(:id as uuid)")
                .param("id", o2).query(String.class).single()).isEqualTo("PAID:180000.00");

        // joriy hafta hali yopilmaydi
        closePeriods();
        summary().andExpect(jsonPath("$.invoicedDue").value(0));

        // hafta tugadi (vaqtni surish o'rniga — qatorlarni o'tgan haftaga ko'chiramiz)
        jdbc.sql("update commission_reports set period = :p where master_id = cast(:m as uuid)")
                .param("p", LAST_WEEK).param("m", master.userId()).update();
        closePeriods();
        closePeriods(); // idempotent
        summary().andExpect(jsonPath("$.invoicedDue").value(38000)).andExpect(jsonPath("$.accrued").value(0));
        mvc.perform(get("/api/v1/admin/finance/invoices").header("Authorization", fx.admin().bearer())
                        .param("masterId", master.userId()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].invoiced").value(38000))
                .andExpect(jsonPath("$[0].orders").value(2))
                .andExpect(jsonPath("$[0].dueDate").value(LAST_WEEK.plusWeeks(1).plusDays(7).toString()));

        relay.relayPending();
        mvc.perform(get("/api/v1/notifications").header("Authorization", master.bearer()))
                .andExpect(jsonPath("$.items[?(@.type == 'COMMISSION_INVOICED')].body",
                        hasItem("Oʻtgan hafta 2 ta buyurtma uchun komissiya: 38000.00 UZS. Toʻlov muddati: "
                                + LAST_WEEK.plusWeeks(1).plusDays(7))));

        // to'lov: faqat to'liq summa
        settle(LAST_WEEK, "30000").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"))
                .andExpect(jsonPath("$.outstanding").value(38000));
        settle(LAST_WEEK.plusDays(1), "38000").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        settle(LAST_WEEK, "38000").andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").value(2))
                .andExpect(jsonPath("$.balance").value(0));
        settle(LAST_WEEK, "38000").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTHING_TO_SETTLE"));
        summary().andExpect(jsonPath("$.balance").value(0)).andExpect(jsonPath("$.invoicedDue").value(0));

        // ledger o'zgarmas (DB trigger)
        assertThatThrownBy(() -> jdbc.sql("update transactions set amount = 1 where master_id = cast(:m as uuid)")
                .param("m", master.userId()).update())
                .hasMessageContaining("append-only");
        // usta boshqa ustaning moliyasini, mijoz esa umuman ko'ra olmaydi
        mvc.perform(get("/api/v1/master/finance/summary").header("Authorization", customer.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void disputeRecalculatesOrReversesCommissionAndReconciles() throws Exception {
        Session admin = fx.admin();
        String recalculated = fx.completedOrder(customer, home, master, 200_000);
        String cancelled = fx.completedOrder(customer, home, master, 100_000);
        summary().andExpect(jsonPath("$.balance").value(-30000));

        // 1) nizo -> admin narxni 150 000 ga tushirdi: eski komissiya bekor, yangisi yozildi
        dispute(recalculated);
        resolve(admin, recalculated, "{\"resolution\":\"COMPLETED\",\"price\":150000,\"note\":\"Kelishildi\"}")
                .andExpect(jsonPath("$.commissionAmount").value(15000));
        // 2) nizo -> bekor: komissiya qaytarildi, to'lov REFUNDED
        dispute(cancelled);
        resolve(admin, cancelled, "{\"resolution\":\"CANCELLED\",\"note\":\"Ish bajarilmagan\"}")
                .andExpect(jsonPath("$.order.status").value("CANCELLED"))
                .andExpect(jsonPath("$.commissionAmount").doesNotExist());

        summary().andExpect(jsonPath("$.balance").value(-15000)).andExpect(jsonPath("$.accrued").value(15000));
        mvc.perform(get("/api/v1/admin/finance/commissions").header("Authorization", admin.bearer())
                        .param("masterId", master.userId()).param("status", "CANCELLED"))
                .andExpect(jsonPath("$.items", hasSize(2)));
        assertThat(jdbc.sql("select status from payments where order_id = cast(:id as uuid)")
                .param("id", cancelled).query(String.class).single()).isEqualTo("REFUNDED");

        // solishtirish: uch manba bir xil
        mvc.perform(get("/api/v1/admin/finance/reconciliation").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mismatches[*].orderId", not(hasItem(recalculated))))
                .andExpect(jsonPath("$.mismatches[*].orderId", not(hasItem(cancelled))));

        // nomuvofiqlikni aniqlaydi (masalan, hisobot qo'lda o'zgartirilgan)
        jdbc.sql("update commission_reports set amount = 1 where order_id = cast(:id as uuid) and status <> 'CANCELLED'")
                .param("id", recalculated).update();
        mvc.perform(get("/api/v1/admin/finance/reconciliation").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.mismatches[?(@.orderId == '" + recalculated + "')].ledgerNet", hasItem(15000.0)));
    }

    private ResultActions summary() throws Exception {
        return mvc.perform(get("/api/v1/master/finance/summary").header("Authorization", master.bearer()));
    }

    private void closePeriods() throws Exception {
        mvc.perform(post("/api/v1/admin/finance/invoices/close").header("Authorization", fx.admin().bearer()))
                .andExpect(status().isOk());
    }

    private ResultActions settle(LocalDate period, String amount) throws Exception {
        return mvc.perform(post("/api/v1/admin/finance/settlements").header("Authorization", fx.admin().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"masterId\":\"" + master.userId() + "\",\"period\":\"" + period + "\",\"amount\":" + amount
                        + ",\"reference\":\"Kassa #1\"}"));
    }

    private void dispute(String orderId) throws Exception {
        mvc.perform(post("/api/v1/customer/orders/{id}/dispute", orderId).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Narx kelishilmagan\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions resolve(Session admin, String orderId, String body) throws Exception {
        return mvc.perform(post("/api/v1/admin/orders/{id}/resolve", orderId).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }
}
