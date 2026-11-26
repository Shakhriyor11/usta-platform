package uz.usta.platform.shared.ops;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.stereotype.Component;
import uz.usta.platform.shared.security.AccessTokenIssuer;
import uz.usta.platform.shared.security.Role;
import uz.usta.platform.shared.web.ApiPaths;

/**
 * JVM "isitish": ilova trafik qabul qilishdan oldin (readiness hali REFUSING — ApplicationRunner'lar
 * ApplicationReadyEvent'dan oldin ishlaydi) asosiy yo'llarga lokal so'rovlar yuboradi, shunda JIT, Hibernate va
 * Jackson keshlari qiziydi. Yuklama testida (docs/performance.md) sovuq ishga tushishdan keyingi birinchi
 * daqiqada p99 ~0.5 s, maksimum ~2.5 s edi; isitilgan JVM'da p99 ~20 ms.
 */
@Component
@ConditionalOnProperty(name = "app.warmup.enabled", havingValue = "true", matchIfMissing = true)
class WarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WarmupRunner.class);
    private static final int ROUNDS = 200;
    private static final Duration BUDGET = Duration.ofSeconds(25);

    private final WebServerApplicationContext context;
    private final AccessTokenIssuer tokens;

    WarmupRunner(WebServerApplicationContext context, AccessTokenIssuer tokens) {
        this.context = context;
        this.tokens = tokens;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        long started = System.nanoTime();
        String base = "http://127.0.0.1:" + context.getWebServer().getPort();
        // mavjud bo'lmagan "foydalanuvchi" — faqat o'qish yo'llari va 404/403 yo'llari isitiladi, DB'ga hech narsa yozilmaydi
        String bearer = "Bearer " + tokens.issue(UUID.randomUUID(), "+998000000000", Set.of(Role.CUSTOMER), Set.of(),
                0).value();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        String categoryId = firstCategoryId(client, base);
        List<String> paths = List.of(
                ApiPaths.PUBLIC + "/catalog/categories",
                ApiPaths.PUBLIC + "/meta",
                ApiPaths.V1 + "/search/masters?lat=41.31&lng=69.27&categoryId=" + categoryId,
                ApiPaths.V1 + "/search/masters?lat=41.29&lng=69.21&sort=DISTANCE&categoryId=" + categoryId,
                ApiPaths.V1 + "/masters/" + UUID.randomUUID(),
                ApiPaths.V1 + "/notifications/unread-count",
                ApiPaths.CUSTOMER + "/orders?size=10");
        AtomicInteger ok = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < ROUNDS; i++) {
                for (String p : paths) {
                    pool.submit(() -> send(client, base + p, bearer, ok));
                }
            }
            pool.shutdown();
            if (!pool.awaitTermination(BUDGET.toSeconds(), TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
        log.info("Warm-up finished: {} requests in {} ms", ok.get(),
                Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    private static void send(HttpClient client, String url, String bearer, AtomicInteger ok) {
        try {
            client.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", bearer)
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.discarding());
            ok.incrementAndGet();
        } catch (Exception e) {
            // isitish — xatolar muhim emas (masalan, katalog bo'sh)
        }
    }

    private static String firstCategoryId(HttpClient client, String base) {
        try {
            String body = client.send(HttpRequest.newBuilder(URI.create(base + ApiPaths.PUBLIC + "/catalog/categories"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString()).body();
            int i = body.indexOf("\"id\":\"");
            return i < 0 ? UUID.randomUUID().toString() : body.substring(i + 6, i + 42);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
