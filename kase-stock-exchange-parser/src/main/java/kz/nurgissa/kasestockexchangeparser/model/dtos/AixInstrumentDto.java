package kz.nurgissa.kasestockexchangeparser.model.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AixInstrumentDto {
    private String secCode;
    private String isin;
    private String issuer;
    private String name;
    private String shortName;
    private String instrument;
    private String segment;
    private String assetClass;
    private String securityGroup;
    private String currency;
    private String state;
    private BigDecimal referencePrice;
    private BigDecimal bidPrice;
    private Long bidQty;
    private BigDecimal offerPrice;
    private Long offerQty;
    private BigDecimal lastTrade;
    private BigDecimal previousClose;
    private BigDecimal averageWeightedPrice;
    private BigDecimal percentChange;
    private BigDecimal priceChange;
    private BigDecimal volume;
    private BigDecimal value;
    private Integer numberOfTrades;
    private String nav;
    private String navCurrency;
    private String timestamp;
}
