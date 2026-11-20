package uz.usta.platform.identity.internal.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.usta.platform.identity.internal.persistence.RefreshTokenRepository;

/** Muddati o'tgan refresh tokenlarni (7 kun "kuzatuv" muddatidan keyin) tozalaydi. */
@Component
class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository tokens;
    private final Clock clock;

    RefreshTokenCleanupJob(RefreshTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.identity.cleanup-cron:0 45 3 * * *}")
    @SchedulerLock(name = "refresh-token-cleanup", lockAtMostFor = "PT10M")
    @Transactional
    public void cleanup() {
        int removed = tokens.deleteExpiredBefore(Instant.now(clock).minus(Duration.ofDays(7)));
        if (removed > 0) {
            log.info("Removed {} expired refresh tokens", removed);
        }
    }
}
