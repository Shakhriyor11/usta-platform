package uz.usta.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.shared.outbox.OutboxRelay;
import uz.usta.platform.shared.security.AdminPermission;
import uz.usta.platform.support.ApiClient;
import uz.usta.platform.support.ApiClient.Session;
import uz.usta.platform.support.Fixtures;
import uz.usta.platform.support.IntegrationTest;
import uz.usta.platform.support.RecordingSmsSender;

/** Super admin, admin huquqlari va huquqni oshirib olishga urinishlar. */
@IntegrationTest
class AdminAccessIT {

    private static final String ADMIN = "/api/v1/admin";

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingSmsSender sms;
    @Autowired
    JsonMapper json;
    @Autowired
    OutboxRelay relay;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    ApiClient api;
    Session superAdmin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, sms, json);
        superAdmin = new Fixtures(mvc, api, relay).admin();
    }

    private record Endpoint(HttpMethod method, String path) {
    }

    /** Barcha admin endpoint'lari ilovaning o'zidan olinadi — yangisi qo'shilsa, shu testda tasniflanishi shart. */
    private List<Endpoint> adminEndpoints() {
        List<Endpoint> result = new ArrayList<>();
        mappings.getHandlerMethods().keySet().forEach(info -> info.getPathPatternsCondition().getPatternValues()
                .stream().filter(p -> p.startsWith(ADMIN + "/"))
                .forEach(pattern -> {
                    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                    assertThat(methods).as("metodsiz mapping: %s", pattern).isNotEmpty();
                    methods.forEach(m -> result.add(new Endpoint(m.asHttpMethod(),
                            pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString()))));
                }));
        return result;
    }

    /** Endpoint'ga kirish uchun yetarli huquqlardan biri; bo'sh — faqat super admin. */
    private static Set<AdminPermission> required(Endpoint e) {
        String path = e.path().substring(ADMIN.length() + 1);
        String area = path.split("/")[0];
        if (area.equals("orders") && path.endsWith("/chat/messages")) {
            return EnumSet.of(AdminPermission.ORDERS, AdminPermission.COMPLAINTS);
        }
        return switch (area) {
            case "admins" -> EnumSet.noneOf(AdminPermission.class);
            case "users" -> EnumSet.of(AdminPermission.USERS);
            case "masters" -> EnumSet.of(AdminPermission.MASTERS);
            case "catalog" -> EnumSet.of(AdminPermission.CATALOG);
            case "orders" -> EnumSet.of(AdminPermission.ORDERS);
            case "finance" -> EnumSet.of(AdminPermission.FINANCE);
            case "complaints" -> EnumSet.of(AdminPermission.COMPLAINTS);
            case "search", "outbox" -> EnumSet.of(AdminPermission.SYSTEM);
            default -> throw new AssertionError("Tasniflanmagan admin endpoint: " + e);
        };
    }

    /** O'chirish (katalog rasmi/ikonkasini olib tashlash bundan mustasno) qo'shimcha DELETE huquqini talab qiladi. */
    private static boolean needsDeletePermission(Endpoint e) {
        return e.method() == HttpMethod.DELETE && !e.path().endsWith("/image") && !e.path().endsWith("/icon")
                && !e.path().startsWith(ADMIN + "/admins/");
    }

    @Test
    void everyAdminEndpointIsClosedToOrdinaryUsers() throws Exception {
        Session customer = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        Session master = api.loginAs(customer.phone(), "MASTER");
        List<Endpoint> endpoints = adminEndpoints();
        assertThat(endpoints).hasSizeGreaterThan(30);
        for (Endpoint e : endpoints) {
            assertThat(call(e, master)).as("%s", e).isEqualTo(403);
            assertThat(mvc.perform(request(e.method(), e.path())).andReturn().getResponse().getStatus())
                    .as("anonim %s", e).isEqualTo(401);
        }
    }

    @Test
    void eachPermissionOpensOnlyItsOwnArea() throws Exception {
        List<Endpoint> endpoints = adminEndpoints();
        for (AdminPermission permission : AdminPermission.values()) {
            Session admin = appoint(permission);
            for (Endpoint e : endpoints) {
                boolean allowed = required(e).contains(permission) && !needsDeletePermission(e);
                int status = call(e, admin);
                if (allowed) {
                    assertThat(status).as("%s: %s", permission, e).isNotIn(401, 403).isLessThan(500);
                } else {
                    assertThat(status).as("%s: %s", permission, e).isEqualTo(403);
                }
            }
        }
    }

    @Test
    void deletionNeedsBothAreaAndDeletePermission() throws Exception {
        Session admin = appoint(AdminPermission.CATALOG, AdminPermission.USERS, AdminPermission.DELETE);
        for (Endpoint e : adminEndpoints().stream().filter(AdminAccessIT::needsDeletePermission).toList()) {
            assertThat(call(e, admin)).as("%s", e).isNotIn(401, 403).isLessThan(500);
        }
    }

    @Test
    void permissionChangeInvalidatesTheOldTokenAndRemovalTakesAccessAway() throws Exception {
        Session admin = appoint(AdminPermission.USERS);
        mvc.perform(get(ADMIN + "/users").header("Authorization", admin.bearer())).andExpect(status().isOk());

        mvc.perform(put(ADMIN + "/admins/{id}", admin.userId()).header("Authorization", superAdmin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[\"CATALOG\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("CATALOG")));
        mvc.perform(get(ADMIN + "/users").header("Authorization", admin.bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_OUTDATED"));

        Session refreshed = refresh(admin);
        mvc.perform(get(ADMIN + "/users").header("Authorization", refreshed.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get(ADMIN + "/catalog/categories").header("Authorization", refreshed.bearer()))
                .andExpect(status().isOk());

        mvc.perform(delete(ADMIN + "/admins/{id}", admin.userId()).header("Authorization", superAdmin.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get(ADMIN + "/catalog/categories").header("Authorization", refreshed.bearer()))
                .andExpect(status().isUnauthorized());
        Session ordinary = refresh(refreshed); // refresh token har safar almashadi
        mvc.perform(get(ADMIN + "/catalog/categories").header("Authorization", ordinary.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me").header("Authorization", ordinary.bearer()))
                .andExpect(jsonPath("$.roles", not(hasItem("ADMIN"))))
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    void adminsCannotTouchEachOtherOrTheSuperAdmin() throws Exception {
        Session first = appoint(AdminPermission.USERS, AdminPermission.DELETE);
        Session second = appoint(AdminPermission.USERS);
        for (ResultActions attempt : List.of(
                block(first, second.userId()),
                mvc.perform(delete(ADMIN + "/users/{id}", second.userId()).header("Authorization", first.bearer())))) {
            attempt.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ADMIN_REQUIRES_SUPER_ADMIN"));
        }
        block(first, superAdmin.userId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_BLOCK_ADMIN"));
        mvc.perform(delete(ADMIN + "/users/{id}", superAdmin.userId()).header("Authorization", first.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPER_ADMIN_IMMUTABLE"));
        mvc.perform(delete(ADMIN + "/users/{id}", first.userId()).header("Authorization", first.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_DELETE_SELF"));
        mvc.perform(delete(ADMIN + "/admins/{id}", superAdmin.userId()).header("Authorization", superAdmin.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPER_ADMIN_IMMUTABLE"));
        // bloklangan admin'ni super admin boshqaradi
        block(superAdmin, second.userId()).andExpect(status().isOk());
    }

    @Test
    void privilegedRolesCannotBeSelfAssigned() throws Exception {
        Session user = api.loginAs(ApiClient.randomPhone(), "CUSTOMER");
        for (String role : new String[] {"ADMIN", "SUPER_ADMIN"}) {
            mvc.perform(post("/api/v1/me/roles").header("Authorization", user.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ROLE_NOT_SELECTABLE"));
        }
    }

    @Test
    void permissionNamesAreLocalized() throws Exception {
        mvc.perform(get(ADMIN + "/admins/permissions").header("Authorization", superAdmin.bearer())
                        .header("Accept-Language", "ru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='CATALOG')].name", hasItem("Каталог")))
                .andExpect(jsonPath("$[?(@.code=='DELETE')].name", hasItem("Удаление")));
    }

    // ---------------------------------------------------------------- yordamchilar

    private Session appoint(AdminPermission... permissions) throws Exception {
        String phone = ApiClient.randomPhone();
        String list = Arrays.stream(permissions).map(p -> "\"" + p + "\"").collect(Collectors.joining(","));
        mvc.perform(post(ADMIN + "/admins").header("Authorization", superAdmin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"permissions\":[" + list + "]}"))
                .andExpect(status().isCreated());
        return api.login(phone);
    }

    private Session refresh(Session s) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + s.refreshToken() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var node = api.read(body);
        String next = node.has("refreshToken") ? node.get("refreshToken").asString() : s.refreshToken();
        return new Session(s.phone(), node.get("accessToken").asString(), next, s.userId());
    }

    private ResultActions block(Session actor, String userId) throws Exception {
        return mvc.perform(post(ADMIN + "/users/{id}/block", userId).header("Authorization", actor.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"test\"}"));
    }

    /** Noto'g'ri tana bilan chaqiradi: ruxsat tekshiriladi, ma'lumot o'zgarmaydi. */
    private int call(Endpoint e, Session user) throws Exception {
        return mvc.perform(request(e.method(), e.path()).header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andReturn().getResponse().getStatus();
    }
}
