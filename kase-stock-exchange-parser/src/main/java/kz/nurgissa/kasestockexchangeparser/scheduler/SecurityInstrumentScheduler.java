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

    private Disposable schedulingSubscription;

    @PostConstruct
    public void start() {

        schedulingSubscription = Flux.interval(Duration.ZERO, DELAY_BETWEEN_RUNS)
                .flatMap(tick -> {
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
                            .onErrorResume(e -> Mono.empty());
                })
                .subscribe();
//        schedulingSubscription = Mono.defer(() -> {
//                    long start = System.nanoTime();
//                    return service.fetchAndSaveAll()
//                            .doOnSuccess(v -> {
//                                long elapsed = (System.nanoTime() - start) / 1_000_000;
//                                log.info("Full fetchAndSaveAll run completed in {} ms", elapsed);
//                            })
//                            .doOnError(e -> {
//                                long elapsed = (System.nanoTime() - start) / 1_000_000;
//                                log.error("Full fetchAndSaveAll run failed after {} ms", elapsed, e);
//                            })
//                            // чтобы ошибки не прервали цепочку
//                            .onErrorResume(e -> Mono.empty());
//                })
//                // ждем после каждого выполнения
//                .repeatWhen(companion ->
//                        companion.delayElements(DELAY_BETWEEN_RUNS)
//                )
//                .subscribe();
    }

    // при желании можно отменить подписку
    public void stop() {
        if (schedulingSubscription != null && !schedulingSubscription.isDisposed()) {
            schedulingSubscription.dispose();
        }
    }
}
