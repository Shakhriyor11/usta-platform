package uz.usta.platform.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.ThreadLocalRandom;
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
class CatalogIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingSmsSender sms;
    @Autowired
    JsonMapper json;

    ApiClient api;
    Session admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, sms, json);
        admin = api.login("+998990000001");
    }

    @Test
    void publicTreeIsLocalizedAndNested() throws Exception {
        mvc.perform(get("/api/v1/public/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'plumbing')].name").value(hasItem("Santexnika")))
                .andExpect(jsonPath("$[?(@.slug == 'repair')].children[*].slug").value(hasItem("repair-tiling")));
        mvc.perform(get("/api/v1/public/catalog/categories").header("Accept-Language", "ru"))
                .andExpect(jsonPath("$[?(@.slug == 'plumbing')].name").value(hasItem("Сантехника")));
    }

    @Test
    void servicesByCategoryAndSearch() throws Exception {
        String plumbing = categoryId("plumbing");
        mvc.perform(get("/api/v1/public/catalog/categories/{id}/services", plumbing).header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Faucet installation")));

        mvc.perform(get("/api/v1/public/catalog/services").param("q", "СМЕСИТ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("faucet-install")));
        // LIKE maxsus belgilari ekranlanadi
        mvc.perform(get("/api/v1/public/catalog/services").param("q", "%%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/public/catalog/services").param("q", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void adminManagesCategoryTree() throws Exception {
        String root = slug("root");
        String rootId = createCategory(null, root).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name.uz").value("Test " + root))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        rootId = json.readTree(rootId).get("id").asString();

        String child = slug("child");
        String childId = json.readTree(createCategory(rootId, child).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asString();
        String leaf = slug("leaf");
        String leafId = json.readTree(createCategory(childId, leaf).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asString();

        // chuqurlik 3 dan oshmaydi
        createCategory(leafId, slug("deep")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_DEPTH_EXCEEDED"));
        // sikl: ildizni o'z nevarasi ostiga ko'chirib bo'lmaydi
        updateCategory(rootId, leafId, root, "ACTIVE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));
        // slug band / formati
        createCategory(null, root).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLUG_TAKEN"));
        createCategory(null, "Bad Slug").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("slug"));

        // xizmat qo'shish va ommaviy ro'yxatda ko'rinishi
        mvc.perform(post("/api/v1/admin/catalog/services").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":"%s","slug":"%s","name":{"uz":"Test xizmat","ru":"Тест"},
                                 "description":{"uz":"Tavsif"}}""".formatted(leafId, slug("svc"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description.uz").value("Tavsif"));
        mvc.perform(get("/api/v1/public/catalog/categories/{id}/services", leafId))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Tavsif"));

        // ota kategoriya nofaol bo'lsa — butun shox yashiriladi
        updateCategory(rootId, null, root, "INACTIVE").andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/catalog/categories"))
                .andExpect(jsonPath("$[*].slug", not(hasItem(root))));
        mvc.perform(get("/api/v1/admin/catalog/categories").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$[*].slug", hasItem(root)));
    }

    @Test
    void catalogAdminRequiresAdminRole() throws Exception {
        Session master = api.loginAs(ApiClient.randomPhone(), "MASTER");
        mvc.perform(post("/api/v1/admin/catalog/categories").header("Authorization", master.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"x\",\"name\":{\"uz\":\"X\"}}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions createCategory(String parentId, String slug) throws Exception {
        return mvc.perform(post("/api/v1/admin/catalog/categories").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(categoryJson(parentId, slug, "ACTIVE")));
    }

    private ResultActions updateCategory(String id, String parentId, String slug, String status) throws Exception {
        return mvc.perform(put("/api/v1/admin/catalog/categories/{id}", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(categoryJson(parentId, slug, status)));
    }

    private static String categoryJson(String parentId, String slug, String status) {
        return """
                {"parentId":%s,"slug":"%s","name":{"uz":"Test %s"},"status":"%s"}"""
                .formatted(parentId == null ? "null" : "\"" + parentId + "\"", slug, slug, status);
    }

    private String categoryId(String slug) throws Exception {
        String body = mvc.perform(get("/api/v1/public/catalog/categories")).andReturn().getResponse()
                .getContentAsString();
        return json.readTree(body).valueStream().filter(n -> n.get("slug").asString().equals(slug))
                .findFirst().orElseThrow().get("id").asString();
    }

    private static String slug(String prefix) {
        return "t-" + prefix + "-" + ThreadLocalRandom.current().nextInt(1_000_000);
    }
}
