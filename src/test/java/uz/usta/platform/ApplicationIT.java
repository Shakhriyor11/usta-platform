package uz.usta.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import uz.usta.platform.support.IntegrationTest;

@IntegrationTest
class ApplicationIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void flywayAppliedAndPostgisAvailable() {
        String version = jdbc.sql("SELECT postgis_lib_version()").query(String.class).single();
        assertThat(version).startsWith("3.");
        Integer applied = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success").query(Integer.class).single();
        assertThat(applied).isGreaterThanOrEqualTo(1);
        // geo so'rov ishlashini tekshirish: Toshkent markazidan Chilonzorgacha ~5-6 km
        Double meters = jdbc.sql("""
                SELECT ST_Distance(ST_MakePoint(69.2797, 41.3111)::geography, ST_MakePoint(69.2045, 41.2856)::geography)
                """).query(Double.class).single();
        assertThat(meters).isBetween(5_000d, 8_000d);
    }

    @Test
    void redisIsReachable() {
        redis.opsForValue().set("it:ping", "pong");
        assertThat(redis.opsForValue().get("it:ping")).isEqualTo("pong");
    }

    @Test
    void publicMetaEndpointReturnsLocalizedInfoAndTraceId() throws Exception {
        mvc.perform(get("/api/v1/public/meta").header("X-Request-Id", "test-trace-12345"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "test-trace-12345"))
                .andExpect(jsonPath("$.service").value("usta-platform"))
                .andExpect(jsonPath("$.locale").value("uz"))
                .andExpect(jsonPath("$.message").value("Usta platformasiga xush kelibsiz"));
    }

    @Test
    void openApiDocumentIsServed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Usta Platform API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
}
