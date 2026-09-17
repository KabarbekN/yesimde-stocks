package kz.nurgissa.kasestockexchangeparser.scheduler;

import kz.nurgissa.kasestockexchangeparser.service.AixService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AixScheduler {

    private final AixService aixService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Triggering initial AIX instruments synchronization...");
        aixService.fetchAndSaveAll()
                .subscribe();
    }

    /**
     * Periodic sync every 2 minutes
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 120_000)
    public void scheduleAixSync() {
        long start = System.currentTimeMillis();
        aixService.fetchAndSaveAll()
                .doOnSuccess(v -> log.info("Periodic AIX sync completed in {} ms", (System.currentTimeMillis() - start)))
                .doOnError(e -> log.error("Periodic AIX sync failed: {}", e.getMessage()))
                .subscribe();
    }
}
