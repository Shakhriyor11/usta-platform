package uz.usta.platform.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.imageio.ImageIO;
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
import uz.usta.platform.support.Fixtures;
import uz.usta.platform.support.Fixtures.Point;
import uz.usta.platform.support.IntegrationTest;
import uz.usta.platform.support.RecordingSmsSender;
import uz.usta.platform.support.Seeds;

/** Chatda rasm: ruxsat, fayl tekshiruvi, metama'lumot o'chishi va hisob o'chirilganda rasmning yo'qolishi. */
@IntegrationTest
class ChatImageIT {

    private static final String SECRET = "GPS-41.311081,69.240562";

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
    String orderId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, sms, json);
        fx = new Fixtures(mvc, api, relay);
        customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        Point home = Fixtures.randomArea();
        master = fx.approvedMaster("Rasm ustasi", home.north(1), 10, Map.of(Seeds.FAUCET_INSTALL, 100_000));
        orderId = fx.acceptedOrder(customer, home, master);
    }

    @Test
    void participantsExchangeImagesWithoutLocationMetadata() throws Exception {
        String body = upload(customer, jpegWithLocation(), "Mana shu yer")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Mana shu yer"))
                .andExpect(jsonPath("$.imageUrl", startsWith("http")))
                .andReturn().getResponse().getContentAsString();
        upload(master, Seeds.PNG, null).andExpect(status().isCreated()).andExpect(jsonPath("$.content").value(""));

        mvc.perform(get("/api/v1/orders/{id}/chat/messages", orderId).header("Authorization", master.bearer()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].imageUrl", startsWith("http")))
                .andExpect(jsonPath("$[1].imageUrl", startsWith("http")));

        String url = api.read(body).get("imageUrl").asString();
        HttpResponse<byte[]> stored = download(url);
        assertThat(stored.statusCode()).isEqualTo(200);
        assertThat(stored.headers().firstValue("content-type")).hasValue("image/jpeg");
        assertThat(new String(stored.body(), StandardCharsets.ISO_8859_1)).doesNotContain("GPS-");
        relay.relayPending();
    }

    @Test
    void outsidersWrongFilesAndClosedChatsAreRejected() throws Exception {
        Session stranger = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        upload(stranger, Seeds.PNG, null).andExpect(status().isNotFound());
        upload(customer, "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII), null)
                .andExpect(status().isUnsupportedMediaType());
        upload(customer, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3}, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_INVALID"));
        upload(customer, Seeds.PNG, "yashirin‮matn").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders/{id}/chat/messages", orderId).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"a\\u0000b\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/customer/orders/{id}/cancel", orderId).header("Authorization", customer.bearer()))
                .andExpect(status().isOk());
        upload(customer, Seeds.PNG, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_CLOSED"));
    }

    @Test
    void imagesOfADeletedUserAreRemoved() throws Exception {
        String url = api.read(upload(customer, Seeds.PNG, null).andReturn().getResponse().getContentAsString())
                .get("imageUrl").asString();
        upload(customer, Seeds.PNG, "izoh qoladi").andExpect(status().isCreated());
        mvc.perform(post("/api/v1/customer/orders/{id}/cancel", orderId).header("Authorization", customer.bearer()))
                .andExpect(status().isOk());
        assertThat(download(url).statusCode()).isEqualTo(200);

        Session admin = fx.admin();
        mvc.perform(delete("/api/v1/admin/users/{id}", customer.userId()).header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());
        relay.relayPending();

        assertThat(download(url).statusCode()).isEqualTo(404);
        mvc.perform(get("/api/v1/admin/orders/{id}/chat/messages", orderId).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("izoh qoladi"))
                .andExpect(jsonPath("$[0].imageUrl").isEmpty());
    }

    // ---------------------------------------------------------------- yordamchilar

    private ResultActions upload(Session sender, byte[] data, String caption) throws Exception {
        var request = multipart("/api/v1/orders/{id}/chat/images", orderId)
                .file(new MockMultipartFile("file", "p.bin", "image/png", data));
        if (caption != null) {
            request.param("caption", caption);
        }
        return mvc.perform(request.header("Authorization", sender.bearer()));
    }

    private static HttpResponse<byte[]> download(String url) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    /** Boshida GPS matnli EXIF segmenti bor haqiqiy JPEG. */
    private static byte[] jpegWithLocation() throws IOException {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB), "jpg", plain);
        byte[] exif = ("Exif\0\0MM\0*" + SECRET).getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain.toByteArray(), 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write(0);
        out.write(exif.length + 2);
        out.writeBytes(exif);
        out.write(plain.toByteArray(), 2, plain.size() - 2);
        return out.toByteArray();
    }
}
