package kz.nurgissa.kasestockexchangeparser.scheduler;

import jakarta.annotation.PostConstruct;
import kz.nurgissa.kasestockexchangeparser.service.SecurityInstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityInstrumentScheduler {

    private static final Duration DELAY_BETWEEN_RUNS = Duration.ofMinutes(1);

    private final SecurityInstrumentService service;
    private final java.util.concurrent.atomic.AtomicBoolean isRunning = new java.util.concurrent.atomic.AtomicBoolean(false);

    private Disposable schedulingSubscription;

    @PostConstruct
    public void start() {
        schedulingSubscription = Flux.interval(Duration.ZERO, DELAY_BETWEEN_RUNS)
                .flatMap(tick -> {
                    if (!isRunning.compareAndSet(false, true)) {
                        log.warn("Previous KASE fetchAndSaveAll run is still in progress, skipping tick.");
                        return Mono.empty();
                    }
                    long start = System.nanoTime();
                    return service.fetchAndSaveAll()
                            .doOnSuccess(v -> {
                                long elapsed = (System.nanoTime() - start) / 1_000_000;
                                log.info("Full fetchAndSaveAll run completed in {} ms", elapsed);
                            })
                            .doOnError(e -> {
                                long elapsed = (System.nanoTime() - start) / 1_000_000;
                                log.error("Full fetchAndSaveAll run failed after {} ms", elapsed, e);
                            })
                            .onErrorResume(e -> Mono.empty())
                            .doFinally(sig -> isRunning.set(false));
                })
                .subscribe();
    }

    // при желании можно отменить подписку
    public void stop() {
        if (schedulingSubscription != null && !schedulingSubscription.isDisposed()) {
            schedulingSubscription.dispose();
        }
    }
}
