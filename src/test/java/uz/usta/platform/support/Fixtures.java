package uz.usta.platform.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import uz.usta.platform.shared.outbox.OutboxRelay;
import uz.usta.platform.support.ApiClient.Session;

/** Integratsion testlar uchun ma'lumot tayyorlash — faqat ochiq API orqali (haqiqiy oqim). */
public final class Fixtures {

    public static final String ADMIN_PHONE = "+998990000001";

    private final MockMvc mvc;
    private final ApiClient api;
    private final OutboxRelay relay;
    private Session admin;

    public Fixtures(MockMvc mvc, ApiClient api, OutboxRelay relay) {
        this.mvc = mvc;
        this.api = api;
        this.relay = relay;
    }

    /** Nuqta (lat, lng). */
    public record Point(double lat, double lng) {
        /** Shimolga {@code km} kilometr siljitilgan nuqta (~111 km = 1°). */
        public Point north(double km) {
            return new Point(lat + km / 111.0, lng);
        }
    }

    /**
     * Har test uchun boshqa testlardan uzoq tasodifiy hudud (O'rta Osiyo atrofida, ~650×1300 km maydon) —
     * umumiy DB'da testlar bir-birining ustalarini ko'rmasligi uchun.
     */
    public static Point randomArea() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new Point(38.0 + r.nextDouble() * 6.0, 56.5 + r.nextDouble() * 15.0);
    }

    public Session admin() throws Exception {
        if (admin == null) {
            admin = api.login(ADMIN_PHONE);
        }
        return admin;
    }

    /**
     * Tasdiqlangan usta: profil (ish hududi), hujjatlar, admin tasdig'i, xizmat narxlari; outbox relay —
     * qidiruv indeksi yangilanadi.
     */
    public Session approvedMaster(String name, Point at, int radiusKm, Map<UUID, Integer> prices) throws Exception {
        Session m = api.loginAs(ApiClient.randomPhone(), "MASTER");
        mvc.perform(put("/api/v1/master/profile").header("Authorization", m.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(Locale.ROOT, """
                                {"fullName":"%s","experienceYears":5,"workRadiusKm":%d,
                                 "workLocation":{"latitude":%.6f,"longitude":%.6f}}""", name, radiusKm, at.lat(), at.lng())))
                .andExpect(status().isOk());
        for (String type : new String[] {"PASSPORT", "SELFIE"}) {
            mvc.perform(multipart("/api/v1/master/verification/documents")
                            .file(new MockMultipartFile("file", "d.png", "image/png", Seeds.PNG))
                            .param("type", type).header("Authorization", m.bearer()))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/master/verification/submit").header("Authorization", m.bearer()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/masters/{id}/approve", m.userId()).header("Authorization", admin().bearer()))
                .andExpect(status().isOk());
        for (var e : prices.entrySet()) {
            setPrice(m, e.getKey(), e.getValue());
        }
        relay.relayPending();
        return m;
    }

    public void setPrice(Session master, UUID serviceId, int price) throws Exception {
        mvc.perform(put("/api/v1/master/services/{id}", serviceId).header("Authorization", master.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"priceFrom\":" + price + "}"))
                .andExpect(status().isOk());
    }

    public String orderJson(UUID serviceId, Point at, UUID preferredMasterId) {
        return String.format(Locale.ROOT, """
                {"serviceId":"%s","description":"Jo'mrak oqyapti",
                 "address":{"address":"Test ko'chasi 1","details":"2-podyezd, 15-xonadon",
                            "location":{"latitude":%.6f,"longitude":%.6f}}%s}""",
                serviceId, at.lat(), at.lng(),
                preferredMasterId == null ? "" : ",\"preferredMasterId\":\"" + preferredMasterId + "\"");
    }

    // ---------------------------------------------------------------- buyurtmalar

    /** Buyurtma yaratadi (tanlangan ustaga) va id qaytaradi. */
    public String order(Session customer, Point at, Session master) throws Exception {
        String body = mvc.perform(post("/api/v1/customer/orders").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(Seeds.FAUCET_INSTALL, at, UUID.fromString(master.userId()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return api.read(body).get("id").asString();
    }

    public String acceptedOrder(Session customer, Point at, Session master) throws Exception {
        String id = order(customer, at, master);
        mvc.perform(post("/api/v1/master/offers/{id}/accept", id).header("Authorization", master.bearer()))
                .andExpect(status().isOk());
        return id;
    }

    public String startedOrder(Session customer, Point at, Session master) throws Exception {
        String id = acceptedOrder(customer, at, master);
        mvc.perform(post("/api/v1/master/orders/{id}/start", id).header("Authorization", master.bearer()))
                .andExpect(status().isOk());
        return id;
    }

    /** IN_PROGRESS -> COMPLETED -> COMMISSION (komissiya hisoblanadi). */
    public String completedOrder(Session customer, Point at, Session master, int price) throws Exception {
        String id = startedOrder(customer, at, master);
        mvc.perform(post("/api/v1/master/orders/{id}/complete", id).header("Authorization", master.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":" + price + "}"))
                .andExpect(status().isOk());
        return id;
    }
}
