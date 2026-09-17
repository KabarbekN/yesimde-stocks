package kz.nurgissa.kasestockexchangeparser.model.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("telegram_subscriber")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramSubscriberEntity {

    @Id
    @Column("chat_id")
    private Long chatId;

    @Column("username")
    private String username;

    @Column("first_name")
    private String firstName;

    @Column("sub_new_bonds")
    private Boolean subNewBonds;

    @Column("sub_discounts")
    private Boolean subDiscounts;

    @Column("sub_whales")
    private Boolean subWhales;

    @Column("sub_coupons")
    private Boolean subCoupons;

    @Column("sub_stocks")
    private Boolean subStocks;

    @Column("watchlist")
    private String watchlist;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
