package kz.nurgissa.kasestockexchangeparser.model.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("market_benchmark")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketBenchmarkEntity {

    @Id
    private Long id;

    @Column("benchmark_key")
    private String benchmarkKey;

    @Column("benchmark_name")
    private String benchmarkName;

    @Column("numeric_value")
    private BigDecimal numericValue;

    @Column("unit")
    private String unit; // PERCENT, KZT, POINTS

    @Column("source_name")
    private String sourceName;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
