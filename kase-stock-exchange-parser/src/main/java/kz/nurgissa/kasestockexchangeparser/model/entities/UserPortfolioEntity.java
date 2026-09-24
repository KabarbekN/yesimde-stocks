package kz.nurgissa.kasestockexchangeparser.model.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("user_portfolio")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPortfolioEntity {

    @Id
    private Long id;

    @Column("chat_id")
    private Long chatId;

    @Column("ticker")
    private String ticker;

    @Column("quantity")
    private BigDecimal quantity;

    @Column("buy_price")
    private BigDecimal buyPrice;

    @Column("currency")
    private String currency;

    @Column("notes")
    private String notes;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
