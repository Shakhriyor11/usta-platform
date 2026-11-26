package uz.usta.platform.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ClassUtils;
import uz.usta.platform.shared.error.ErrorCode;
import uz.usta.platform.shared.security.AdminRoutes;
import uz.usta.platform.shared.web.ApiPaths;

/**
 * Frontend dasturchisi uchun hujjatni to'ldiradi: bosh sahifada o'zgarishlar va xato kodlari, har endpoint'da til
 * sarlavhasi, kerakli admin huquqi, yangi/o'zgargan belgisi va xato javobi. Hammasi koddan yig'iladi (huquqlar —
 * {@link AdminRoutes}, kodlar — {@link ErrorCode} enum'lari). Takror chaqirilsa natija o'zgarmaydi.
 */
@Component
class ApiDocsCustomizer implements GlobalOpenApiCustomizer {

    /** {@link DeletePermissionCustomizer} qo'yadigan belgi. */
    static final String NEEDS_DELETE = "x-requires-delete-permission";

    private static final String LANGUAGE_HEADER = "Accept-Language";
    private static final String PROBLEM = "Problem";
    private static final String MULTIPART = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

    private final List<ErrorCode> errorCodes;
    private final String description;
    private final AntPathMatcher paths = new AntPathMatcher();

    ApiDocsCustomizer(MessageSource messages) {
        this.errorCodes = scanErrorCodes();
        this.description = describe(messages);
    }

    @Override
    public void customise(OpenAPI api) {
        api.getInfo().setDescription(description);
        if (api.getComponents() == null) {
            api.setComponents(new Components());
        }
        api.getComponents().addSchemas(PROBLEM, problemSchema());
        if (api.getPaths() != null) {
            api.getPaths().forEach((path, item) -> item.readOperationsMap()
                    .forEach((method, operation) -> customise(HttpMethod.valueOf(method.name()), path, operation)));
        }
    }

    private void customise(HttpMethod method, String path, Operation operation) {
        languageHeader(operation);
        if (path.startsWith(ApiPaths.PUBLIC + "/") || path.startsWith(ApiPaths.AUTH + "/")) {
            operation.setSecurity(List.of()); // token kerak emas
        }
        if (path.startsWith(ApiPaths.ADMIN + "/")) {
            prependOnce(operation, permissionNote(method, path, operation));
        }
        ApiChanges.ALL.stream().filter(c -> c.method().equals(method) && c.path().equals(path)).findFirst()
                .ifPresent(change -> {
                    String summary = Optional.ofNullable(operation.getSummary()).orElse(operation.getOperationId());
                    if (!summary.startsWith(change.kind().marker)) {
                        operation.setSummary(change.kind().marker + " " + summary);
                    }
                    prependOnce(operation, "**" + change.kind().marker + " " + change.kind().label + " ("
                            + ApiChanges.RELEASE + "):** " + change.note());
                });
        multipartFields(operation);
        errorResponse(operation);
    }

    private static void prependOnce(Operation operation, String note) {
        String current = operation.getDescription();
        if (current == null) {
            operation.setDescription(note);
        } else if (!current.contains(note)) {
            operation.setDescription(note + "\n\n" + current);
        }
    }

    private static void languageHeader(Operation operation) {
        boolean present = operation.getParameters() != null
                && operation.getParameters().stream().anyMatch(p -> LANGUAGE_HEADER.equals(p.getName()));
        if (!present) {
            Schema<Object> schema = type("string");
            schema.setEnum(List.of("uz", "uz-Cyrl", "ru", "en"));
            schema.setDefault("uz");
            operation.addParametersItem(new Parameter().in("header").name(LANGUAGE_HEADER).required(false)
                    .description("Javob matnlari tili").schema(schema));
        }
    }

    private String permissionNote(HttpMethod method, String path, Operation operation) {
        AdminRoutes.Rule rule = AdminRoutes.RULES.stream()
                .filter(r -> r.method() == null || r.method().equals(method))
                .filter(r -> r.patterns().stream().anyMatch(pattern -> paths.match(pattern, path)))
                .findFirst().orElseThrow();
        String needed = rule.anyOf().isEmpty() ? "faqat `SUPER_ADMIN`"
                : rule.anyOf().stream().map(p -> "`" + p + "`").collect(Collectors.joining(" yoki "));
        boolean delete = operation.getExtensions() != null && operation.getExtensions().containsKey(NEEDS_DELETE);
        return "**Huquq:** " + needed + (delete ? " + `DELETE`" : "");
    }

    /**
     * Fayl yuklashdagi matn maydonlari (izoh, hujjat turi) sukut bo'yicha query parametr bo'lib ko'rinadi —
     * ularni forma maydonlariga o'tkazamiz (server ikkalasini ham qabul qiladi, lekin izoh URL va loglarga
     * tushmasligi kerak).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void multipartFields(Operation operation) {
        if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null
                || operation.getParameters() == null) {
            return;
        }
        MediaType media = operation.getRequestBody().getContent().get(MULTIPART);
        if (media == null || media.getSchema() == null) {
            return;
        }
        Schema form = media.getSchema();
        List<Parameter> remaining = new ArrayList<>();
        for (Parameter p : operation.getParameters()) {
            if (!"query".equals(p.getIn())) {
                remaining.add(p);
                continue;
            }
            Schema field = p.getSchema();
            if (p.getDescription() != null) {
                field.setDescription(p.getDescription());
            }
            form.addProperty(p.getName(), field);
            if (Boolean.TRUE.equals(p.getRequired())) {
                form.addRequiredItem(p.getName());
            }
        }
        operation.setParameters(remaining);
    }

    private static void errorResponse(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        operation.getResponses().computeIfAbsent("default", key -> new ApiResponse()
                .description("Xato (RFC 9457). Kodlar ro'yxati — sahifa boshida")
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM)))));
    }

    private Schema<Object> problemSchema() {
        Schema<Object> code = type("string");
        code.setEnum(errorCodes.stream().map(e -> (Object) e.code()).distinct().toList());
        code.setDescription("Barqaror xato kodi — ilova shunga tayanadi");
        code.setExample("VALIDATION_FAILED");
        Schema<Object> fieldError = type("object");
        fieldError.addProperty("field", type("string"));
        fieldError.addProperty("message", type("string"));
        Schema<Object> errors = type("array");
        errors.setItems(fieldError);
        errors.setDescription("Faqat VALIDATION_FAILED: maydon xatolari");

        Schema<Object> problem = type("object");
        problem.addProperty("type", type("string").example("urn:usta:error:validation_failed"));
        problem.addProperty("title", type("string"));
        problem.addProperty("status", type("integer").example(400));
        problem.addProperty("detail", type("string").description("Foydalanuvchiga ko'rsatiladigan matn (so'rov tilida)"));
        problem.addProperty("instance", type("string"));
        problem.addProperty("code", code);
        problem.addProperty("traceId", type("string").description("Qo'llab-quvvatlashga yuboriladi"));
        problem.addProperty("timestamp", type("string").format("date-time"));
        problem.addProperty("errors", errors);
        problem.addProperty("retryAfterSeconds", type("integer").description("429 da: necha soniyadan so'ng"));
        problem.setRequired(List.of("title", "status", "code"));
        return problem;
    }

    /** OpenAPI 3.0 ({@code type}) va 3.1 ({@code types}) serializatsiyasi uchun. */
    private static Schema<Object> type(String type) {
        Schema<Object> schema = new Schema<>();
        schema.setType(type);
        schema.setTypes(Set.of(type));
        return schema;
    }

    private String describe(MessageSource messages) {
        String endpoints = ApiChanges.ALL.stream()
                .map(c -> "| " + c.kind().marker + " | `" + c.method() + " " + c.path().substring(ApiPaths.V1.length())
                        + "` | " + c.note() + " |")
                .collect(Collectors.joining("\n"));
        Map<String, String> rows = new LinkedHashMap<>();
        errorCodes.stream()
                .sorted(Comparator.<ErrorCode>comparingInt(e -> e.status().value()).thenComparing(ErrorCode::code))
                .forEach(e -> rows.putIfAbsent(e.code(), "| `" + e.code() + "` | " + e.status().value() + " | "
                        + messages.getMessage(e.messageKey(), null, e.code(), Locale.of("uz"))
                                .replaceAll("\\{\\d+}", "…")
                        + " |"));
        return resource("intro.md")
                + section("🆕 Yangiliklar (" + ApiChanges.RELEASE + ") — frontend uchun",
                        resource("changelog.md") + "\n### 5. Yangi va o'zgargan endpoint'lar\n\n"
                                + "| | Endpoint (`/api/v1` dan keyin) | Nima o'zgardi |\n|---|---|---|\n" + endpoints)
                + section("Xato kodlari", "| Kod | HTTP | Ma'nosi |\n|---|---|---|\n"
                        + String.join("\n", rows.values()));
    }

    /** Yig'iladigan bo'lim (Swagger UI HTML'ni ko'rsatadi; markdown atrofidagi bo'sh qatorlar shart). */
    private static String section(String title, String body) {
        return "\n<details>\n<summary><b>" + title + "</b></summary>\n\n" + body + "\n\n</details>\n";
    }

    private static String resource(String name) {
        try (InputStream in = new ClassPathResource("openapi/" + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Barcha modullardagi xato kodlari (modullarga kompilyatsiya bog'liqligisiz). */
    private static List<ErrorCode> scanErrorCodes() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));
        List<ErrorCode> codes = new ArrayList<>();
        scanner.findCandidateComponents("uz.usta.platform").forEach(bean -> {
            Class<?> type = ClassUtils.resolveClassName(bean.getBeanClassName(), ApiDocsCustomizer.class.getClassLoader());
            if (type.isEnum()) {
                for (Object constant : type.getEnumConstants()) {
                    codes.add((ErrorCode) constant);
                }
            }
        });
        return List.copyOf(codes);
    }
}
