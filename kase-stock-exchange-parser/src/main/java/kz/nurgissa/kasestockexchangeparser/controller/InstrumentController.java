package kz.nurgissa.kasestockexchangeparser.controller;

import kz.nurgissa.kasestockexchangeparser.model.dtos.InstrumentDetailDto;
import kz.nurgissa.kasestockexchangeparser.service.BondAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/instruments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InstrumentController {

    private final BondAnalyticsService bondAnalyticsService;

    @GetMapping("/{id}")
    public Mono<InstrumentDetailDto> getInstrument(@PathVariable Long id) {
        return bondAnalyticsService.getInstrumentDetail(id);
    }

    @GetMapping("/by-code/{code}")
    public Mono<InstrumentDetailDto> getInstrumentByCode(@PathVariable String code) {
        return bondAnalyticsService.getInstrumentDetailByCode(code);
    }
}
