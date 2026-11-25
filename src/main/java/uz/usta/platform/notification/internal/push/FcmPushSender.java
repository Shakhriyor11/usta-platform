package uz.usta.platform.notification.internal.push;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Firebase Cloud Messaging (HTTP v1) adapteri. Qo'shimcha SDK'siz: service account kaliti bilan JWT imzolanadi,
 * OAuth2 access token olinadi (keshlanadi) va {@code messages:send} chaqiriladi.
 *
 * <p>Sozlash: {@code PUSH_PROVIDER=fcm} va {@code FIREBASE_CREDENTIALS_FILE=/path/service-account.json}
 * (Firebase konsoli → Project settings → Service accounts → Generate new private key).
 *
 * <p>Android kanal: {@code NEW_OFFER} — {@code offers} (yuqori ahamiyat), qolganlari — {@code orders}.
 * Mobil ilova shu kanallarni yaratadi.
 */
@Component
@ConditionalOnProperty(name = "app.push.provider", havingValue = "fcm")
class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final Duration TOKEN_SKEW = Duration.ofMinutes(2);

    private final RestClient http;
    private final JsonMapper json;
    private final String projectId;
    private final String clientEmail;
    private final String tokenUri;
    private final RSAPrivateKey privateKey;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile String accessToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;

    FcmPushSender(@Value("${app.push.fcm.credentials-file:}") String credentialsFile, JsonMapper json) {
        if (credentialsFile == null || credentialsFile.isBlank()) {
            throw new IllegalStateException("app.push.fcm.credentials-file (FIREBASE_CREDENTIALS_FILE) is required for PUSH_PROVIDER=fcm");
        }
        this.json = json;
        try {
            JsonNode sa = json.readTree(Files.readString(Path.of(credentialsFile)));
            this.projectId = required(sa, "project_id");
            this.clientEmail = required(sa, "client_email");
            this.tokenUri = sa.path("token_uri").asString("https://oauth2.googleapis.com/token");
            this.privateKey = parsePrivateKey(required(sa, "private_key"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Firebase credentials file: " + credentialsFile, e);
        }
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(5));
        rf.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(rf).build();
        log.info("FCM push enabled for Firebase project {}", projectId);
    }

    @Override
    public void send(String token, String title, String body, Map<String, String> data) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("token", token);
        message.put("notification", Map.of("title", title, "body", body));
        message.put("data", data);
        String channel = "NEW_OFFER".equals(data.get("type")) ? "offers" : "orders";
        message.put("android", Map.of(
                "priority", "HIGH",
                "notification", Map.of("channel_id", channel, "sound", "default")));
        message.put("apns", Map.of("payload", Map.of("aps", Map.of("sound", "default"))));
        try {
            post(message);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                accessToken = null; // token bekor qilingan — bir marta yangilab qayta urinamiz
                try {
                    post(message);
                    return;
                } catch (RestClientResponseException retry) {
                    throw classify(retry);
                }
            }
            throw classify(e);
        } catch (RestClientException e) {
            throw new IllegalStateException("FCM request failed: " + e.getMessage(), e);
        }
    }

    private void post(Map<String, Object> message) {
        http.post()
                .uri("https://fcm.googleapis.com/v1/projects/{project}/messages:send", projectId)
                .headers(h -> h.setBearerAuth(currentAccessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(Map.of("message", message)))
                .retrieve()
                .toBodilessEntity();
    }

    /** Eskirgan/noto'g'ri token → {@link InvalidTokenException} (chaqiruvchi uni bazadan o'chiradi). */
    private RuntimeException classify(RestClientResponseException e) {
        String errorBody = e.getResponseBodyAsString();
        int status = e.getStatusCode().value();
        boolean invalidToken = status == 404
                || errorBody.contains("UNREGISTERED")
                || (status == 400 && errorBody.contains("registration token"));
        if (invalidToken) {
            return new InvalidTokenException("FCM token is no longer valid");
        }
        return new IllegalStateException("FCM error " + status + ": " + errorBody);
    }

    private String currentAccessToken() {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiresAt.minus(TOKEN_SKEW))) {
            return accessToken;
        }
        lock.lock();
        try {
            if (accessToken == null || !Instant.now().isBefore(accessTokenExpiresAt.minus(TOKEN_SKEW))) {
                refreshAccessToken();
            }
            return accessToken;
        } finally {
            lock.unlock();
        }
    }

    private void refreshAccessToken() {
        Instant now = Instant.now();
        String assertion;
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(clientEmail)
                    .audience(tokenUri)
                    .claim("scope", SCOPE)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(privateKey));
            assertion = jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign FCM OAuth assertion", e);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", assertion);
        String res = http.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(String.class);
        JsonNode node = json.readTree(res == null ? "{}" : res);
        String token = node.path("access_token").asString(null);
        if (token == null) {
            throw new IllegalStateException("Google OAuth response has no access_token");
        }
        accessToken = token;
        accessTokenExpiresAt = now.plusSeconds(node.path("expires_in").asLong(3600));
    }

    private static String required(JsonNode node, String field) {
        String v = node.path(field).asString(null);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Firebase credentials file has no '" + field + "'");
        }
        return v;
    }

    private static RSAPrivateKey parsePrivateKey(String pem) {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid private_key in Firebase credentials file", e);
        }
    }
}
