package kz.nurgissa.kasestockexchangeparser.controller;

import kz.nurgissa.kasestockexchangeparser.model.dtos.BondItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.YieldCurveResponseDto;
import kz.nurgissa.kasestockexchangeparser.service.BondAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bonds")
@RequiredArgsConstructor
public class BondController {

    private final BondAnalyticsService bondAnalyticsService;

    @GetMapping
    public Mono<List<BondItemDto>> getBonds(
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Double minYtm,
            @RequestParam(required = false) Double maxYtm,
            @RequestParam(required = false) Integer minDtm,
            @RequestParam(required = false) Integer maxDtm,
            @RequestParam(required = false) Double maxSpread,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "ytm") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset
    ) {
        return bondAnalyticsService.getBondScreener(
                currency, minYtm, maxYtm, minDtm, maxDtm, maxSpread, preset, search, sortBy, sortDir, limit, offset
        );
    }

    @GetMapping("/yield-curve")
    public Mono<YieldCurveResponseDto> getYieldCurve(
            @RequestParam(required = false, defaultValue = "KZT") String currency
    ) {
        return bondAnalyticsService.getYieldCurve(currency);
    }
}
