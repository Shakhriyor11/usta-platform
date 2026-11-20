package uz.usta.platform.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Integratsion testlar uchun yordamchi: OTP orqali kirish, rol tanlash. */
public final class ApiClient {

    private final MockMvc mvc;
    private final RecordingSmsSender sms;
    private final JsonMapper json;

    public ApiClient(MockMvc mvc, RecordingSmsSender sms, JsonMapper json) {
        this.mvc = mvc;
        this.sms = sms;
        this.json = json;
    }

    /** Har test uchun yangi, takrorlanmaydigan raqam (Redis cooldown/limitlar to'qnashmasligi uchun). */
    public static String randomPhone() {
        return "+99890" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
    }

    public record Session(String phone, String accessToken, String refreshToken, String userId) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    public void requestOtp(String phone) throws Exception {
        mvc.perform(post("/api/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());
    }

    public Session login(String phone) throws Exception {
        requestOtp(phone);
        String body = mvc.perform(post("/api/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"" + sms.lastCode(phone) + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(body);
        return new Session(phone, n.get("accessToken").asString(), n.get("refreshToken").asString(),
                n.get("user").get("id").asString());
    }

    /** Kiradi va rol tanlaydi; qaytgan session'dagi access token yangi rolni o'z ichiga oladi. */
    public Session loginAs(String phone, String role) throws Exception {
        Session s = login(phone);
        String body = mvc.perform(post("/api/v1/me/roles").header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Session(phone, json.readTree(body).get("accessToken").asString(), s.refreshToken(), s.userId());
    }

    public JsonNode read(String body) {
        return json.readTree(body);
    }
}
