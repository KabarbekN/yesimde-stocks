package kz.nurgissa.kasestockexchangeparser.model.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("instrument_market_maker")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InstrumentMarketMakerEntity {
    @Column("security_instrument_id")
    private Long securityInstrumentId;

    @Column("org_code")
    private String orgCode;



}

