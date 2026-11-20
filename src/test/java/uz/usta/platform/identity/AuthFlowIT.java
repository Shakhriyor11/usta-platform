package uz.usta.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.support.ApiClient;
import uz.usta.platform.support.ApiClient.Session;
import uz.usta.platform.support.IntegrationTest;
import uz.usta.platform.support.RecordingSmsSender;

@IntegrationTest
class AuthFlowIT {

    static final String ADMIN_PHONE = "+998990000001";

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingSmsSender sms;
    @Autowired
    JsonMapper json;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mvc, sms, json);
    }

    @Test
    void newUserLogsInWithOtpAndSelectsRole() throws Exception {
        String phone = ApiClient.randomPhone();
        // turli formatlar normalizatsiya qilinadi
        mvc.perform(post("/api/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone.substring(4, 6) + " " + phone.substring(6) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeLength").value(5))
                .andExpect(jsonPath("$.expiresInSeconds").value(120));
        assertThat(sms.lastMessage(phone)).contains("tasdiqlash kodi").contains("2 daqiqa");

        String body = verify(phone, sms.lastCode(phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.user.phone").value(phone))
                .andExpect(jsonPath("$.user.roles", empty()))
                .andReturn().getResponse().getContentAsString();
        String access = api.read(body).get("accessToken").asString();

        // rolsiz foydalanuvchi mijoz yo'llariga kira olmaydi
        mvc.perform(get("/api/v1/customer/profile").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());

        String roleBody = mvc.perform(post("/api/v1/me/roles").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles", contains("CUSTOMER")))
                .andReturn().getResponse().getContentAsString();
        String customerAccess = api.read(roleBody).get("accessToken").asString();

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + customerAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(phone));
        // profil hali yaratilmagan — 404 (403 emas): rol token ichida
        mvc.perform(get("/api/v1/customer/profile").header("Authorization", "Bearer " + customerAccess))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));

        // qayta kirish — newUser=false
        api.requestOtp(phone);
        verify(phone, sms.lastCode(phone)).andExpect(jsonPath("$.newUser").value(false))
                .andExpect(jsonPath("$.user.roles", contains("CUSTOMER")));
    }

    @Test
    void wrongCodeReportsRemainingAttemptsAndCodeIsSingleUse() throws Exception {
        String phone = ApiClient.randomPhone();
        api.requestOtp(phone);
        String code = sms.lastCode(phone);
        String wrong = code.equals("00000") ? "11111" : "00000";

        verify(phone, wrong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"))
                .andExpect(jsonPath("$.remainingAttempts").value(4))
                .andExpect(jsonPath("$.detail").value("Tasdiqlash kodi notoʻgʻri. Qolgan urinishlar: 4"));

        verify(phone, code).andExpect(status().isOk());
        verify(phone, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
    }

    @Test
    void codeIsBurnedAfterMaxAttempts() throws Exception {
        String phone = ApiClient.randomPhone();
        api.requestOtp(phone);
        String code = sms.lastCode(phone);
        String wrong = code.equals("00000") ? "11111" : "00000";
        for (int i = 0; i < 4; i++) {
            verify(phone, wrong).andExpect(jsonPath("$.code").value("OTP_INVALID"));
        }
        verify(phone, wrong).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
        // to'g'ri kod ham endi ishlamaydi
        verify(phone, code).andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
    }

    @Test
    void resendIsThrottled() throws Exception {
        String phone = ApiClient.randomPhone();
        api.requestOtp(phone);
        mvc.perform(post("/api/v1/auth/otp/request").header("Accept-Language", "ru")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("OTP_RESEND_TOO_EARLY"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    @Test
    void invalidPhoneIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+7 999 123 45 67\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHONE_NUMBER"));
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesWholeFamily() throws Exception {
        Session s = api.login(ApiClient.randomPhone());

        String rotated = refresh(s.refreshToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String r2 = api.read(rotated).get("refreshToken").asString();
        assertThat(r2).isNotEqualTo(s.refreshToken());

        // eski token qayta ishlatildi -> butun family bekor
        refresh(s.refreshToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        refresh(r2).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Session s = api.login(ApiClient.randomPhone());
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + s.refreshToken() + "\"}"))
                .andExpect(status().isNoContent());
        refresh(s.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoleCannotBeSelfAssigned() throws Exception {
        Session s = api.login(ApiClient.randomPhone());
        mvc.perform(post("/api/v1/me/roles").header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_SELECTABLE"));
    }

    @Test
    void adminBlocksAndUnblocksUser() throws Exception {
        Session admin = api.login(ADMIN_PHONE);
        Session user = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");

        // oddiy foydalanuvchi admin API'ga kira olmaydi
        mvc.perform(get("/api/v1/admin/users").header("Authorization", user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/admin/users").header("Authorization", admin.bearer())
                        .param("phone", user.phone().substring(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(user.userId())));

        mvc.perform(post("/api/v1/admin/users/{id}/block", user.userId()).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"spam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        refresh(user.refreshToken()).andExpect(status().isUnauthorized());
        // hali amal qilayotgan access token ham darhol rad etiladi (har qanday endpoint'da)
        mvc.perform(get("/api/v1/customer/orders").header("Authorization", user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
        mvc.perform(get("/api/v1/me").header("Authorization", user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
        api.requestOtp(user.phone());
        verify(user.phone(), sms.lastCode(user.phone())).andExpect(jsonPath("$.code").value("USER_BLOCKED"));

        // admin'ni bloklab bo'lmaydi
        mvc.perform(post("/api/v1/admin/users/{id}/block", admin.userId()).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_BLOCK_ADMIN"));

        mvc.perform(post("/api/v1/admin/users/{id}/unblock", user.userId()).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/me").header("Authorization", user.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/customer/orders").header("Authorization", user.bearer())).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/public/catalog/categories")).andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));
    }

    private ResultActions verify(String phone, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"));
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}"));
    }
}
