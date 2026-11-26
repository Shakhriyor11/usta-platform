package uz.usta.platform.shared.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uz.usta.platform.support.IntegrationTest;

/** Frontend uchun API hujjati: o'zgarishlar belgilangan, huquq, til, xato va fayl maydonlari ko'rinadi. */
@IntegrationTest
class OpenApiDocsIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;

    @Test
    void everyListedChangeExistsAndIsMarked() throws Exception {
        fetch("/v3/api-docs", "ru"); // takror yig'ilganda belgilar ikkilanmasligi kerak
        JsonNode spec = fetch("/v3/api-docs", "uz");
        Files.writeString(Path.of("build/openapi.json"), spec.toPrettyString());

        for (ApiChanges.Change change : ApiChanges.ALL) {
            JsonNode op = operation(spec, change.method().name(), change.path());
            assertThat(op).as("%s %s hujjatda yo'q", change.method(), change.path()).isNotNull();
            String summary = op.path("summary").asString();
            assertThat(summary).as(change.path()).startsWith(change.kind().marker);
            assertThat(summary.indexOf(change.kind().marker, 1)).as("ikkilangan belgi: %s", summary).isEqualTo(-1);
            assertThat(count(op.path("description").asString(), change.note())).as(change.path()).isOne();
        }
        String description = spec.path("info").path("description").asString();
        assertThat(description).contains("Token olish", "TOKEN_OUTDATED", "CATEGORY_NOT_EMPTY", "IMAGE_NOT_SQUARE",
                "/orders/{orderId}/chat/images");
        assertThat(count(description, "<summary><b>Xato kodlari</b></summary>")).isOne();
    }

    @Test
    void operationsDescribePermissionsLanguageErrorsAndSecurity() throws Exception {
        JsonNode spec = fetch("/v3/api-docs", "uz");

        assertThat(text(spec, "DELETE", "/api/v1/admin/users/{id}")).contains("**Huquq:** `USERS` + `DELETE`");
        assertThat(text(spec, "DELETE", "/api/v1/admin/catalog/services/{id}")).contains("`CATALOG` + `DELETE`");
        assertThat(text(spec, "DELETE", "/api/v1/admin/catalog/services/{id}/image"))
                .contains("**Huquq:** `CATALOG`").doesNotContain("+ `DELETE`");
        assertThat(text(spec, "GET", "/api/v1/admin/orders/{orderId}/chat/messages"))
                .contains("`ORDERS` yoki `COMPLAINTS`");
        assertThat(text(spec, "POST", "/api/v1/admin/admins")).contains("faqat `SUPER_ADMIN`");

        spec.path("paths").properties().forEach(path -> path.getValue().properties().forEach(entry -> {
            JsonNode op = entry.getValue();
            String name = entry.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey();
            assertThat(op.path("parameters").valueStream()
                    .filter(p -> p.path("name").asString().equals("Accept-Language")).count()).as(name).isOne();
            assertThat(op.path("responses").has("default")).as(name).isTrue();
            if (path.getKey().startsWith("/api/v1/admin/")) {
                assertThat(op.path("description").asString()).as(name).contains("**Huquq:**");
            }
            boolean open = path.getKey().startsWith("/api/v1/public/") || path.getKey().startsWith("/api/v1/auth/");
            assertThat(op.has("security") && op.path("security").isEmpty()).as(name).isEqualTo(open);
        }));

        JsonNode language = operation(spec, "GET", "/api/v1/me").path("parameters").get(0).path("schema");
        assertThat(language.path("type").asString()).isEqualTo("string");
        assertThat(language.path("enum").toString()).contains("uz-Cyrl");
    }

    @Test
    void uploadsShowAllFieldsInTheFormAndSchemasAreComplete() throws Exception {
        JsonNode spec = fetch("/v3/api-docs", "uz");

        for (String[] upload : new String[][] {
                {"POST", "/api/v1/orders/{orderId}/chat/images", "caption"},
                {"POST", "/api/v1/master/portfolio", "description"},
                {"POST", "/api/v1/master/verification/documents", "type"}}) {
            JsonNode op = operation(spec, upload[0], upload[1]);
            JsonNode form = op.path("requestBody").path("content").path("multipart/form-data").path("schema");
            assertThat(form.path("properties").has("file")).as(upload[1]).isTrue();
            assertThat(form.path("properties").has(upload[2])).as(upload[1]).isTrue();
            assertThat(op.path("parameters").valueStream().map(p -> p.path("in").asString()))
                    .as(upload[1]).doesNotContain("query");
        }
        JsonNode schemas = spec.path("components").path("schemas");
        assertThat(schemas.path("ChatEvent").path("properties").has("message")).isTrue();
        assertThat(schemas.path("MessageView").path("properties").has("imageUrl")).isTrue();
        assertThat(schemas.path("Problem").path("properties").path("code").path("enum").toString())
                .contains("USER_HAS_DEBT", "TOKEN_OUTDATED", "CHAT_CLOSED");
    }

    @Test
    void roleGroupsCarryTheSameAdditions() throws Exception {
        JsonNode admin = fetch("/v3/api-docs/admin", "uz");
        assertThat(text(admin, "PUT", "/api/v1/admin/catalog/categories/{id}/icon"))
                .contains("**Huquq:** `CATALOG`", "Yangi");
        assertThat(admin.path("info").path("description").asString()).contains("Yangiliklar");
        assertThat(fetch("/v3/api-docs/master", "uz").path("components").path("schemas").has("Problem")).isTrue();
    }

    private JsonNode fetch(String url, String language) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Accept-Language", language)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static JsonNode operation(JsonNode spec, String method, String path) {
        JsonNode op = spec.path("paths").path(path).path(method.toLowerCase(Locale.ROOT));
        return op.isMissingNode() ? null : op;
    }

    private static String text(JsonNode spec, String method, String path) {
        return operation(spec, method, path).path("description").asString();
    }

    private static int count(String text, String part) {
        return (text.length() - text.replace(part, "").length()) / part.length();
    }
}
