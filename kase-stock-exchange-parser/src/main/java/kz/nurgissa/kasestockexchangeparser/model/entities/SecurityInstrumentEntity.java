package kz.nurgissa.kasestockexchangeparser.model.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("security_instrument")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityInstrumentEntity {
    @Id
    private Long id;
    private String code;
    private String secType;
    private java.math.BigDecimal price;
    private java.math.BigDecimal closePrice;
    private java.math.BigDecimal bestBid;
    private java.math.BigDecimal bestOffer;
    private java.math.BigDecimal volkzt;
    private java.math.BigDecimal volusd;
    private java.math.BigDecimal capit;
    private java.time.LocalDateTime date0;
    private java.math.BigDecimal trand;
    private java.math.BigDecimal trandPercent;
    private String orgCode;
    private String orgNameRu;
    private String orgNameEn;
    private String orgNameKz;
    private String orgShortNameRu;
    private String orgShortNameEn;
    private String orgShortNameKz;
    private Integer dealcnt;
    private String finSecRu;
    private String finSecEn;
    private String finSecKz;
    private String boardRu;
    private String boardEn;
    private String boardKz;
    private java.time.LocalDate repaymentStartDate;
    private java.math.BigDecimal dohod;
    private java.math.BigDecimal dohodTotal;
    private String monthlySparkLine;
    private java.math.BigDecimal monthTrand;
    private java.math.BigDecimal monthTrandPercent;
    private Integer cntMm;
    private String currencyType;
    private java.math.BigDecimal volumeNumber;
    private java.math.BigDecimal volumeReleaseNumber;
    private java.math.BigDecimal volume;
    private java.math.BigDecimal volumeRelease;
    private String mainMarketRu;
    private String mainMarketEn;
    private String mainMarketKz;
    private Integer dtm;
    private java.math.BigDecimal ytm;
    private String subcategoryNameRu;
    private String subcategoryNameEn;
    private String subcategoryNameKz;
    private String subcategoryShortnameRu;
    private String subcategoryShortnameEn;
    private String subcategoryShortnameKz;
    private String scheme;
    private Boolean tPlus;
    private Integer liquidClass;
}
