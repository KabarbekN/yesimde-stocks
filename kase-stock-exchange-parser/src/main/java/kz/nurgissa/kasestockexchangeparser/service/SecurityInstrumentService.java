package kz.nurgissa.kasestockexchangeparser.service;

import kz.nurgissa.kasestockexchangeparser.model.dtos.SecurityInstrumentResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SecurityInstrumentService {
    Mono<Void> saveAll(Flux<SecurityInstrumentResponse> securityInstrumentResponseFlux);
    Mono<Void> fetchAndSaveAll();
}
