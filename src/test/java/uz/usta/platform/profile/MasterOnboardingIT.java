package uz.usta.platform.profile;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.shared.outbox.OutboxRelay;
import uz.usta.platform.support.ApiClient;
import uz.usta.platform.support.ApiClient.Session;
import uz.usta.platform.support.IntegrationTest;
import uz.usta.platform.support.RecordingSmsSender;
import uz.usta.platform.support.Seeds;

/** Usta yo'li: profil -> hujjatlar -> verifikatsiya -> admin tasdig'i -> xizmatlar va portfolio -> ommaviy kartochka. */
@IntegrationTest
class MasterOnboardingIT {


    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingSmsSender sms;
    @Autowired
    JsonMapper json;
    @Autowired
    OutboxRelay relay;

    ApiClient api;
    Session admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, sms, json);
        admin = api.login("+998990000001");
    }

    @Test
    void masterGoesThroughVerificationAndBecomesPublic() throws Exception {
        Session master = api.loginAs(ApiClient.randomPhone(), "MASTER");
        String faucet = seedId("svc:faucet-install");

        // profilsiz xizmat qo'shib bo'lmaydi
        putService(master, faucet, "150000").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MASTER_PROFILE_REQUIRED"));

        mvc.perform(put("/api/v1/master/profile").header("Authorization", master.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Botir Usmonov","bio":"10 yillik tajriba","experienceYears":10,
                                 "workRadiusKm":15}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("NOT_SUBMITTED"))
                .andExpect(jsonPath("$.verified").value(false));

        // hujjat turi baytlar bo'yicha tekshiriladi
        mvc.perform(multipart("/api/v1/master/verification/documents").file(
                                new MockMultipartFile("file", "passport.png", "image/png",
                                        "not an image".getBytes(StandardCharsets.UTF_8)))
                        .param("type", "PASSPORT").header("Authorization", master.bearer()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));

        uploadDocument(master, "PASSPORT").andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.url", startsWith("http")));
        submit(master).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_MISSING_DOCUMENTS"))
                .andExpect(jsonPath("$.missingDocuments", hasItem("SELFIE")));
        uploadDocument(master, "SELFIE").andExpect(status().isCreated());

        // ish hududisiz yuborib bo'lmaydi
        submit(master).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_WORK_AREA_REQUIRED"));
        mvc.perform(put("/api/v1/master/profile").header("Authorization", master.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Botir Usmonov","experienceYears":10,"workRadiusKm":15,
                                 "workLocation":{"latitude":41.2995,"longitude":69.2401}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workLocation.latitude").value(41.2995));

        submit(master).andExpect(status().isOk()).andExpect(jsonPath("$.verificationStatus").value("PENDING"));
        // yuborilgandan keyin hujjatlar qulflanadi
        uploadDocument(master, "CERTIFICATE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENTS_LOCKED"));

        // hali tasdiqlanmagan usta mijozlarga ko'rinmaydi
        Session customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        mvc.perform(get("/api/v1/masters/{id}", master.userId()).header("Authorization", customer.bearer()))
                .andExpect(status().isNotFound());

        // admin navbati
        mvc.perform(get("/api/v1/admin/masters").param("status", "PENDING").param("size", "100")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].profile.userId", hasItem(master.userId())))
                .andExpect(jsonPath("$.items[*].phone", hasItem(master.phone())));
        mvc.perform(get("/api/v1/admin/masters/{id}", master.userId()).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(2)));
        mvc.perform(post("/api/v1/admin/masters/{id}/approve", master.userId()).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.verified").value(true));
        // qayta tasdiqlash — noto'g'ri holat
        mvc.perform(post("/api/v1/admin/masters/{id}/approve", master.userId()).header("Authorization", admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFICATION_INVALID_STATE"));

        // xizmatlar va portfolio
        putService(master, faucet, "150000").andExpect(status().isOk())
                .andExpect(jsonPath("$.priceFrom").value(150000))
                .andExpect(jsonPath("$.name").value("Joʻmrak oʻrnatish"));
        putService(master, faucet, "120000.50").andExpect(status().isOk())
                .andExpect(jsonPath("$.priceFrom").value(120000.50));
        putService(master, faucet, "-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("priceFrom"));
        putService(master, seedId("svc:pipe-leak"), "80000").andExpect(status().isOk());
        mvc.perform(put("/api/v1/master/services/{id}", seedId("svc:toilet-install"))
                        .header("Authorization", master.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceFrom\":200000,\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/master/services").header("Authorization", master.bearer()))
                .andExpect(jsonPath("$", hasSize(3)));

        mvc.perform(multipart("/api/v1/master/portfolio")
                        .file(new MockMultipartFile("file", "work.png", "image/png", Seeds.PNG))
                        .param("description", "Hammom ta'miri").header("Authorization", master.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", startsWith("http")));

        // ommaviy kartochka: faqat faol xizmatlar, arzonidan
        mvc.perform(get("/api/v1/masters/{id}", master.userId()).header("Authorization", customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Botir Usmonov"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.workLocation").doesNotExist());
        mvc.perform(get("/api/v1/masters/{id}/services", master.userId())
                        .header("Authorization", customer.bearer()).header("Accept-Language", "ru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Устранение протечки"))
                .andExpect(jsonPath("$[0].priceFrom").value(80000));
        mvc.perform(get("/api/v1/masters/{id}/portfolio", master.userId()).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$", hasSize(1)));

        // xizmatni olib tashlash
        mvc.perform(delete("/api/v1/master/services/{id}", faucet).header("Authorization", master.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/masters/{id}/services", master.userId()).header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$", hasSize(1)));

        // usta bloklansa (event orqali) — kartochka yashiriladi
        mvc.perform(post("/api/v1/admin/users/{id}/block", master.userId()).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"shikoyatlar\"}"))
                .andExpect(status().isOk());
        relay.relayPending();
        mvc.perform(get("/api/v1/masters/{id}", master.userId()).header("Authorization", customer.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectedMasterCanFixDocumentsAndResubmit() throws Exception {
        Session master = api.loginAs(ApiClient.randomPhone(), "MASTER");
        mvc.perform(put("/api/v1/master/profile").header("Authorization", master.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Anvar","experienceYears":2,"workRadiusKm":5,
                                 "workLocation":{"latitude":41.31,"longitude":69.28}}"""))
                .andExpect(status().isOk());
        uploadDocument(master, "PASSPORT").andExpect(status().isCreated());
        uploadDocument(master, "SELFIE").andExpect(status().isCreated());
        submit(master).andExpect(status().isOk());

        mvc.perform(post("/api/v1/admin/masters/{id}/reject", master.userId()).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Selfi xira\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Selfi xira"));

        // REJECTED holatda hujjatlar yana tahrirlanadi
        String docs = mvc.perform(get("/api/v1/master/verification/documents").header("Authorization", master.bearer()))
                .andReturn().getResponse().getContentAsString();
        String selfieId = json.readTree(docs).valueStream()
                .filter(d -> d.get("type").asString().equals("SELFIE")).findFirst().orElseThrow().get("id").asString();
        mvc.perform(delete("/api/v1/master/verification/documents/{id}", selfieId)
                        .header("Authorization", master.bearer()))
                .andExpect(status().isNoContent());
        uploadDocument(master, "SELFIE").andExpect(status().isCreated());
        submit(master).andExpect(status().isOk()).andExpect(jsonPath("$.verificationStatus").value("PENDING"));
    }

    @Test
    void customerCannotUseMasterEndpoints() throws Exception {
        Session customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        mvc.perform(get("/api/v1/master/profile").header("Authorization", customer.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/masters/{id}/approve", customer.userId())
                        .header("Authorization", customer.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerManagesProfileAndAddresses() throws Exception {
        Session customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        mvc.perform(put("/api/v1/customer/profile").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Aziz Karimov\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Aziz Karimov"));

        String first = mvc.perform(post("/api/v1/customer/addresses").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Uy","address":"Toshkent, Chilonzor 9","location":{"latitude":41.28,"longitude":69.20}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true))   // birinchi manzil — asosiy
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/customer/addresses").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ish","address":"Toshkent, Yunusobod","isDefault":true,
                                 "location":{"latitude":41.36,"longitude":69.29}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true));
        mvc.perform(get("/api/v1/customer/addresses").header("Authorization", customer.bearer()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.isDefault == true)]", hasSize(1)));

        // koordinata validatsiyasi
        mvc.perform(post("/api/v1/customer/addresses").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"X","address":"Y","location":{"latitude":95,"longitude":69.29}}"""))
                .andExpect(status().isBadRequest());

        // boshqa mijoz birovning manzilini ko'rmaydi
        String id = json.readTree(first).get("id").asString();
        Session other = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        mvc.perform(get("/api/v1/customer/addresses/{id}", id).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/customer/addresses").header("Authorization", other.bearer()))
                .andExpect(jsonPath("$[*].id", not(hasItem(id))));
    }

    private ResultActions putService(Session master, String serviceId, String price) throws Exception {
        return mvc.perform(put("/api/v1/master/services/{id}", serviceId).header("Authorization", master.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"priceFrom\":" + price + "}"));
    }

    private ResultActions uploadDocument(Session master, String type) throws Exception {
        return mvc.perform(multipart("/api/v1/master/verification/documents")
                .file(new MockMultipartFile("file", type.toLowerCase() + ".png", "image/png", Seeds.PNG))
                .param("type", type).header("Authorization", master.bearer()));
    }

    private ResultActions submit(Session master) throws Exception {
        return mvc.perform(post("/api/v1/master/verification/submit").header("Authorization", master.bearer()));
    }

    /** V6 seed'dagi ID: PostgreSQL {@code md5(key)::uuid} bilan bir xil. */
    static String seedId(String key) throws Exception {
        String hex = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(key.getBytes(StandardCharsets.UTF_8)));
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20);
    }
}
