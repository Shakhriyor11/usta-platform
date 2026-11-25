package uz.usta.platform.notification.internal.push;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class FcmPushSenderTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @TempDir
    Path dir;

    @Test
    void readsServiceAccountFile() throws Exception {
        Path file = writeCredentials(Map.of(
                "project_id", "usta-test",
                "client_email", "push@usta-test.iam.gserviceaccount.com",
                "private_key", pem()));

        assertThatCode(() -> new FcmPushSender(file.toString(), json)).doesNotThrowAnyException();
    }

    @Test
    void requiresCredentialsFile() {
        assertThatThrownBy(() -> new FcmPushSender("", json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIREBASE_CREDENTIALS_FILE");
        assertThatThrownBy(() -> new FcmPushSender(dir.resolve("missing.json").toString(), json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot read");
    }

    @Test
    void rejectsIncompleteOrBrokenKey() throws Exception {
        Path noKey = writeCredentials(Map.of("project_id", "p", "client_email", "e@x"));
        assertThatThrownBy(() -> new FcmPushSender(noKey.toString(), json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("private_key");

        Path badKey = writeCredentials(Map.of("project_id", "p", "client_email", "e@x", "private_key", "not-a-key"));
        assertThatThrownBy(() -> new FcmPushSender(badKey.toString(), json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid private_key");
    }

    private Path writeCredentials(Map<String, String> fields) throws Exception {
        Path file = Files.createTempFile(dir, "sa", ".json");
        Files.writeString(file, json.writeValueAsString(fields));
        return file;
    }

    private static String pem() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(gen.generateKeyPair().getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
    }
}
