package kz.nurgissa.kasestockexchangeparser.model.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("alert_cooldown")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertCooldownEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("alert_type")
    private String alertType;

    @Column("ticker")
    private String ticker;

    @Column("last_sent_at")
    private LocalDateTime lastSentAt;

    @Column("last_value")
    private BigDecimal lastValue;
}
