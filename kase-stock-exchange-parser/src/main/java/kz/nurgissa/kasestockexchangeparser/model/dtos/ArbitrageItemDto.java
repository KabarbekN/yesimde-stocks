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
public class ArbitrageItemDto {
    private String isin;
    private String companyName;
    private String kaseCode;
    private BigDecimal kasePrice;
    private String aixCode;
    private BigDecimal aixPrice;
    private String currency;
    private BigDecimal spreadAbs;
    private BigDecimal spreadPercent;
    private String cheaperExchange;
    private String recommendation;
}
