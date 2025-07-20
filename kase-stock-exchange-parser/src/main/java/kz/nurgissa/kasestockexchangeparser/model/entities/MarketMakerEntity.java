package kz.nurgissa.kasestockexchangeparser.model.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("market_maker")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketMakerEntity {
    @Id
    private String orgCode;
    private String orgNameRu;
    private String orgNameEn;
    private String orgNameKz;
    private String orgShortNameRu;
    private String orgShortNameEn;
    private String orgShortNameKz;
    private String orgShortestNameRu;
    private String orgShortestNameEn;
    private String orgShortestNameKz;
}
