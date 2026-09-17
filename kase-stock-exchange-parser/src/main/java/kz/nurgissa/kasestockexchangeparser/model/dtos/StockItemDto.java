package kz.nurgissa.kasestockexchangeparser.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockItemDto {
    private String code;
    private String name;
    private BigDecimal price;
    private BigDecimal closePrice;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal volumeKzt;
    private Integer dealCount;
    private String currency;
}
