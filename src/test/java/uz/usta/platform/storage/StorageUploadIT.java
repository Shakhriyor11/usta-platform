package uz.usta.platform.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import uz.usta.platform.support.IntegrationTest;

/** Oqim bilan yuklanadigan (rasm bo'lmagan) katta fayllar omborga baytma-bayt yetib boradi. */
@IntegrationTest
class StorageUploadIT {

    @Autowired
    FileStorage storage;

    @Test
    void documentsAndVideosArriveUnchanged() throws Exception {
        roundTrip(FilePurpose.VERIFICATION_DOCUMENT, "%PDF-1.7\n", 300_000, "application/pdf");
        roundTrip(FilePurpose.ORDER_ATTACHMENT, "\0\0\0\u0018ftypmp42", 6_000_000, "video/mp4");
    }

    private void roundTrip(FilePurpose purpose, String magic, int size, String type) throws Exception {
        byte[] data = new byte[size];
        new Random(size).nextBytes(data);
        byte[] header = magic.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(header, 0, data, 0, header.length);

        StoredFile stored = storage.storeAndSave(purpose, UUID.randomUUID(),
                new MockMultipartFile("file", "f", type, data), saved -> saved);

        assertThat(stored.contentType()).isEqualTo(type);
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<byte[]> response = http.send(
                    HttpRequest.newBuilder(URI.create(storage.url(stored.key()))).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(data);
        }
    }
}
