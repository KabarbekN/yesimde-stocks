package kz.nurgissa.kasestockexchangeparser.client;

import kz.nurgissa.kasestockexchangeparser.model.dtos.SecurityInstrumentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class KaseClient {
    private final WebClient kaseWebClient;

    public Flux<SecurityInstrumentResponse> fetchAllSecurityInstruments() {
        return kaseWebClient.get()
                .uri("/instruments/securities/")
                .retrieve()
                .bodyToFlux(SecurityInstrumentResponse.class);
    }

}
