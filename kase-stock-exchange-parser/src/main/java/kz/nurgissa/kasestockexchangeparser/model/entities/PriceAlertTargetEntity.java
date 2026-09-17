package kz.nurgissa.kasestockexchangeparser.model.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("price_alert_target")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceAlertTargetEntity {

    @Id
    private Long id;

    @Column("chat_id")
    private Long chatId;

    @Column("ticker")
    private String ticker;

    @Column("target_price")
    private BigDecimal targetPrice;

    @Column("direction")
    private String direction; // ABOVE, BELOW

    @Column("initial_price")
    private BigDecimal initialPrice;

    @Column("is_triggered")
    private Boolean isTriggered;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("triggered_at")
    private LocalDateTime triggeredAt;
}
