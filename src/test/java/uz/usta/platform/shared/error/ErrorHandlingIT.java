package uz.usta.platform.shared.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.usta.platform.support.IntegrationTest;

@IntegrationTest
class ErrorHandlingIT {

    private static final String BASE = "/api/v1/public/test-errors";

    @Autowired
    MockMvc mvc;

    @Test
    void bodyValidationReturnsFieldErrorsInUzbekByDefault() throws Exception {
        mvc.perform(post(BASE + "/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"\",\"rating\":7}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.type").value("urn:usta:error:validation_failed"))
                .andExpect(jsonPath("$.title").value("Soʻrov maʼlumotlari notoʻgʻri"))
                .andExpect(jsonPath("$.traceId").value(notNullValue()))
                .andExpect(jsonPath("$.instance").value(BASE + "/validate"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("phone")))
                .andExpect(jsonPath("$.errors[?(@.field == 'rating')].message").value(hasItem("5 dan katta boʻlmasligi kerak")));
    }

    @Test
    void messagesFollowAcceptLanguage() throws Exception {
        mvc.perform(post(BASE + "/validate").header("Accept-Language", "ru")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"+998901234567\",\"rating\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Некорректные данные запроса"))
                .andExpect(jsonPath("$.errors[0].message").value("должно быть не меньше 1"));
    }

    @Test
    void unsupportedLanguageFallsBackToUzbek() throws Exception {
        mvc.perform(get(BASE + "/business").header("Accept-Language", "fr-FR"))
                .andExpect(jsonPath("$.detail").value("Buyurtma topilmadi (id: 42)"));
    }

    @Test
    void methodParameterValidation() throws Exception {
        mvc.perform(get(BASE + "/param").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void malformedJsonAndMissingParam() throws Exception {
        mvc.perform(post(BASE + "/validate").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(get(BASE + "/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void businessExceptionUsesItsCodeAndArgs() throws Exception {
        mvc.perform(get(BASE + "/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Buyurtma topilmadi (id: 42)"));
    }

    @Test
    void dataConflictsMapTo409() throws Exception {
        mvc.perform(get(BASE + "/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(get(BASE + "/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void unexpectedErrorDoesNotLeakDetails() throws Exception {
        mvc.perform(get(BASE + "/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret internal detail"))));
    }

    @Test
    void unknownPublicPathIs404AndWrongMethodIs405() throws Exception {
        mvc.perform(get("/api/v1/public/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(put(BASE + "/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void protectedPathWithoutTokenIs401InSameFormat() throws Exception {
        mvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").value(notNullValue()));
    }

    /** Security filtrlarida yuz beradigan xatolar ham tizim tilida emas, so'rov tilida qaytishi kerak. */
    @Test
    void securityErrorsAreLocalized() throws Exception {
        mvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Avtorizatsiyadan oʻtilmagan yoki token yaroqsiz"));
        mvc.perform(get("/api/v1/orders").header("Accept-Language", "ru"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Требуется авторизация или токен недействителен"));
    }
}
