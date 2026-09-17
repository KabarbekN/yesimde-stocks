package kz.nurgissa.kasestockexchangeparser.model.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AixMarketDepthDto {
    private String symbol;
    private String isin;
    private List<OrderRowDto> bidRows;
    private List<OrderRowDto> offerRows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderRowDto {
        private Integer orderNumber;
        private Long volume;
        private BigDecimal price;
        private String ordVerb;
        private String insertedAt;
    }
}
