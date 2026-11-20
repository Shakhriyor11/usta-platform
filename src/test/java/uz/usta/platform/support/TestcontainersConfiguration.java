package uz.usta.platform.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Haqiqiy PostgreSQL+PostGIS, Redis va MinIO konteynerlari (prod bilan bir xil versiyalar). */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String POSTGIS_IMAGE = "postgis/postgis:17-3.6";
    public static final String REDIS_IMAGE = "redis:8.6-alpine";
    public static final String MINIO_IMAGE = "cgr.dev/chainguard/minio:latest";
    private static final String MINIO_USER = "test-minio";
    private static final String MINIO_PASSWORD = "test-minio-secret";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGIS_IMAGE).asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(6379);
    }

    @Bean
    GenericContainer<?> minio() {
        return new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
                .withCommand("server", "/data")
                .withEnv("MINIO_ROOT_USER", MINIO_USER)
                .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));
    }

    @Bean
    DynamicPropertyRegistrar minioProperties(GenericContainer<?> minio) {
        return registry -> {
            String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
            registry.add("app.storage.endpoint", () -> endpoint);
            registry.add("app.storage.public-endpoint", () -> endpoint);
            registry.add("app.storage.access-key", () -> MINIO_USER);
            registry.add("app.storage.secret-key", () -> MINIO_PASSWORD);
        };
    }
}
