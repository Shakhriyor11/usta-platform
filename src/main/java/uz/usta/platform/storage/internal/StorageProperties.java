package uz.usta.platform.storage.internal;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param endpoint       ilova S3'ga ulanadigan manzil (compose ichida {@code http://minio:9000})
 * @param publicEndpoint presigned URL'lardagi manzil — mijoz qurilmasidan ochiladigan bo'lishi kerak
 */
@ConfigurationProperties("app.storage")
record StorageProperties(URI endpoint, URI publicEndpoint, String region, String bucket, String accessKey,
                         String secretKey, boolean pathStyle, boolean createBucket, Duration urlTtl) {

    StorageProperties {
        region = region == null ? "us-east-1" : region;
        publicEndpoint = publicEndpoint == null ? endpoint : publicEndpoint;
        urlTtl = urlTtl == null ? Duration.ofMinutes(15) : urlTtl;
    }
}
