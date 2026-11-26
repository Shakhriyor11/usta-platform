package uz.usta.platform.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.shared.outbox.OutboxRelay;
import uz.usta.platform.support.ApiClient;
import uz.usta.platform.support.ApiClient.Session;
import uz.usta.platform.support.Fixtures;
import uz.usta.platform.support.Fixtures.Point;
import uz.usta.platform.support.IntegrationTest;
import uz.usta.platform.support.RecordingSmsSender;
import uz.usta.platform.support.Seeds;

/** Ikonkalar to'plami, kategoriya/xizmat rasmlari, o'chirish (arxivlash) va uch tilli nomlar. */
@IntegrationTest
class CatalogMediaIT {

    private static final String ADMIN = "/api/v1/admin/catalog";
    private static final String PUBLIC = "/api/v1/public/catalog";

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

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, sms, json);
        fx = new Fixtures(mvc, api, relay);
        admin = fx.admin();
    }

    @Test
    void presetIconsArePublicAndEverySeedCategoryUsesOne() throws Exception {
        String body = mvc.perform(get(PUBLIC + "/icons").header("Accept-Language", "ru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(50)))
                .andExpect(jsonPath("$[?(@.key=='plumbing')].name", hasItem("Сантехника")))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode icon : json.readTree(body)) {
            mvc.perform(get(icon.get("url").asString()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                    .andExpect(content().string(containsString("<svg")))
                    .andExpect(content().string(not(containsString("<script"))))
                    .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                    .andExpect(header().string("Cache-Control", containsString("max-age=86400")));
        }
        mvc.perform(get(PUBLIC + "/icons/nope.svg")).andExpect(status().isNotFound());
        mvc.perform(get(PUBLIC + "/categories"))
                .andExpect(jsonPath("$[?(@.slug=='plumbing')].iconUrl",
                        hasItem(PUBLIC + "/icons/plumbing.svg")));
    }

    @Test
    void adminPicksAPresetIconOrUploadsOwnOne() throws Exception {
        String slug = slug();
        send(HttpMethod.POST, ADMIN + "/categories", category(slug, null, "no-such-icon"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_ICON"));
        String id = created(ADMIN + "/categories", category(slug, null, "pipe"));
        String url = ADMIN + "/categories/" + id;

        upload(url + "/icon", "i.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType());
        upload(url + "/icon", "i.png", "image/png", image("png", 128, 64))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_NOT_SQUARE"));
        upload(url + "/icon", "i.png", "image/png", image("png", 32, 32))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_DIMENSIONS"));
        upload(url + "/icon", "i.jpg", "image/png", image("jpg", 128, 128)) // JPEG ikonka bo'lolmaydi
                .andExpect(status().isUnsupportedMediaType());
        upload(url + "/icon", "i.png", "image/png", image("png", 128, 128))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customIcon").value(true))
                .andExpect(jsonPath("$.icon").isEmpty())
                .andExpect(jsonPath("$.iconUrl", containsString("/icons/" + id)));

        // ikonka ko'rsatilmagan yangilash yuklanganini o'chirmaydi
        send(HttpMethod.PUT, url, category(slug, null, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customIcon").value(true));
        send(HttpMethod.PUT, url, category(slug, null, "tv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customIcon").value(false))
                .andExpect(jsonPath("$.iconUrl").value(PUBLIC + "/icons/tv.svg"));

        upload(url + "/image", "p.png", "image/png", image("png", 100, 100))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_DIMENSIONS"));
        upload(url + "/image", "p.jpg", "image/jpeg", image("jpg", 640, 360))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl", startsWith("http")));
        mvc.perform(get(PUBLIC + "/categories"))
                .andExpect(jsonPath("$[?(@.slug=='" + slug + "')].imageUrl", hasSize(1)));
        mvc.perform(delete(url + "/image").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").isEmpty());
    }

    @Test
    void deletedServiceLeavesHistoryButDisappearsEverywhere() throws Exception {
        Point home = Fixtures.randomArea();
        String slug = slug();
        String categoryId = created(ADMIN + "/categories", category(slug, null, "repair"));
        String serviceSlug = slug + "-s";
        UUID service = UUID.fromString(created(ADMIN + "/services", service(serviceSlug, categoryId)));
        Session master = fx.approvedMaster("O'chirish ustasi", home.north(1), 10,
                Map.of(service, 5000, Seeds.FAUCET_INSTALL, 100_000));
        Session customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        String order = api.read(mvc.perform(post("/api/v1/customer/orders").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(fx.orderJson(service, home, null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();

        mvc.perform(delete(ADMIN + "/services/{id}", service).header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());
        relay.relayPending();

        mvc.perform(get(PUBLIC + "/services/{id}", service)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/master/services").header("Authorization", master.bearer()))
                .andExpect(jsonPath("$[*].serviceId", not(hasItem(service.toString()))))
                .andExpect(jsonPath("$[*].serviceId", hasItem(Seeds.FAUCET_INSTALL.toString())));
        mvc.perform(get(ADMIN + "/services").param("categoryId", categoryId).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$", hasSize(0)));
        send(HttpMethod.PUT, ADMIN + "/services/" + service, service(serviceSlug, categoryId))
                .andExpect(status().isNotFound());
        assertThat(jdbc.sql("select count(*) from search_master_services where service_id = :s").param("s", service)
                .query(Long.class).single()).isZero();
        mvc.perform(get("/api/v1/customer/orders/{id}", order).header("Authorization", customer.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.serviceName").value("Xizmat " + serviceSlug));
        created(ADMIN + "/services", service(serviceSlug, categoryId)); // slug bo'shadi
    }

    @Test
    void categoryWithContentIsDeletedOnlyWithCascade() throws Exception {
        String slug = slug();
        String root = created(ADMIN + "/categories", category(slug, null, null));
        String child = created(ADMIN + "/categories", category(slug + "-c", root, null));
        String service = created(ADMIN + "/services", service(slug + "-s", child));

        mvc.perform(delete(ADMIN + "/categories/{id}", root).header("Authorization", admin.bearer())
                        .header("Accept-Language", "en"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_EMPTY"))
                .andExpect(jsonPath("$.categories").value(1))
                .andExpect(jsonPath("$.services").value(1));
        mvc.perform(delete(ADMIN + "/categories/{id}?cascade=true", root).header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get(ADMIN + "/categories").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$[*].id", not(hasItem(root))))
                .andExpect(jsonPath("$[*].id", not(hasItem(child))));
        mvc.perform(get(PUBLIC + "/services/{id}", service)).andExpect(status().isNotFound());
        send(HttpMethod.POST, ADMIN + "/categories", category(slug + "-x", root, null))
                .andExpect(status().isNotFound());
        created(ADMIN + "/categories", category(slug, null, null));
    }

    @Test
    void namesAreRequiredInThreeLanguagesAndSearchableInCyrillic() throws Exception {
        send(HttpMethod.POST, ADMIN + "/categories", "{\"slug\":\"" + slug() + "\",\"name\":{\"uz\":\"Faqat\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name.ru")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name.en")));
        send(HttpMethod.POST, ADMIN + "/categories", "{\"slug\":\"" + slug()
                + "\",\"name\":{\"uz\":\"Yashirin\\u202Ematn\",\"ru\":\"Тест\",\"en\":\"Test\"}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("name.uz"));

        mvc.perform(get(PUBLIC + "/services/{id}", Seeds.FAUCET_INSTALL).header("Accept-Language", "uz-Cyrl"))
                .andExpect(jsonPath("$.name").value("Жўмрак ўрнатиш"));
        for (String query : new String[] {"жўмрак", "jo‘mrak", "JO'MRAK", "смесител", "faucet"}) {
            mvc.perform(get(PUBLIC + "/services").param("q", query))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", hasItem(Seeds.FAUCET_INSTALL.toString())));
        }
    }

    // ---------------------------------------------------------------- yordamchilar

    private static String slug() {
        return "m-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String category(String slug, String parentId, String icon) {
        return "{\"slug\":\"" + slug + "\",\"parentId\":" + quote(parentId) + ",\"icon\":" + quote(icon)
                + ",\"name\":{\"uz\":\"Toifa\",\"ru\":\"Раздел\",\"en\":\"Section\"}}";
    }

    private static String service(String slug, String categoryId) {
        return "{\"categoryId\":\"" + categoryId + "\",\"slug\":\"" + slug + "\",\"name\":{\"uz\":\"Xizmat " + slug
                + "\",\"ru\":\"Услуга\",\"en\":\"Service\"}}";
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static byte[] image(String format, int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    private ResultActions upload(String url, String name, String type, byte[] data) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, url).file(new MockMultipartFile("file", name, type, data))
                .header("Authorization", admin.bearer()));
    }

    private ResultActions send(HttpMethod method, String url, String body) throws Exception {
        var request = method == HttpMethod.PUT ? put(url) : post(url);
        return mvc.perform(request.header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String created(String url, String body) throws Exception {
        String response = send(HttpMethod.POST, url, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asString();
    }
}
