package kz.nurgissa.kasestockexchangeparser.telegram;

import kz.nurgissa.kasestockexchangeparser.model.dtos.BondItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.StockItemDto;
import kz.nurgissa.kasestockexchangeparser.model.entities.AlertCooldownEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.TickerEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.AlertCooldownRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.TelegramSubscriberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Slf4j
public class TelegramAlertDispatcherService {

    private final TelegramSubscriberRepository subscriberRepository;
    private final AlertCooldownRepository cooldownRepository;
    private final TelegramClient telegramClient;
    private final boolean enabled;

    public TelegramAlertDispatcherService(
            TelegramSubscriberRepository subscriberRepository,
            AlertCooldownRepository cooldownRepository,
            @Value("${telegram.bot.token:}") String botToken,
            @Value("${telegram.bot.enabled:false}") boolean enabled
    ) {
        this.subscriberRepository = subscriberRepository;
        this.cooldownRepository = cooldownRepository;
        this.enabled = enabled && botToken != null && !botToken.isBlank();
        this.telegramClient = this.enabled ? new OkHttpTelegramClient(botToken) : null;
    }

    public boolean isEnabled() {
        return enabled && telegramClient != null;
    }

    /**
     * Alert for discounted bonds (price <= 95% of nominal).
     * Cooldown: 24 hours, or if price drops by at least 1.0% further.
     */
    public Mono<Void> broadcastDiscountAlert(BondItemDto bond) {
        if (!isEnabled() || bond == null || bond.getCode() == null || bond.getPrice() == null) {
            return Mono.empty();
        }

        String ticker = bond.getCode();
        BigDecimal currentPrice = bond.getPrice();

        return cooldownRepository.findByAlertTypeAndTicker("DISCOUNT", ticker)
                .flatMap(cd -> {
                    long hoursAgo = ChronoUnit.HOURS.between(cd.getLastSentAt(), LocalDateTime.now());
                    BigDecimal lastPrice = cd.getLastValue() != null ? cd.getLastValue() : BigDecimal.valueOf(100);
                    BigDecimal drop = lastPrice.subtract(currentPrice);

                    // Skip if notified within 24h and price hasn't dropped by >= 1.0%
                    if (hoursAgo < 24 && drop.compareTo(BigDecimal.valueOf(1.0)) < 0) {
                        return Mono.empty();
                    }
                    cd.setLastSentAt(LocalDateTime.now());
                    cd.setLastValue(currentPrice);
                    return cooldownRepository.save(cd).then(Mono.just(true));
                })
                .switchIfEmpty(
                        cooldownRepository.save(AlertCooldownEntity.builder()
                                .alertType("DISCOUNT")
                                .ticker(ticker)
                                .lastSentAt(LocalDateTime.now())
                                .lastValue(currentPrice)
                                .build()
                        ).map(e -> true)
                )
                .flatMap(shouldSend -> {
                    String message = buildDiscountMessage(bond);
                    InlineKeyboardMarkup keyboard = buildBondInlineKeyboard(ticker);
                    return subscriberRepository.findAllBySubDiscountsTrue()
                            .doOnNext(sub -> sendHtmlMessage(sub.getChatId(), message, keyboard))
                            .then();
                });
    }

    /**
     * Alert for newly listed bonds.
     */
    public Mono<Void> broadcastNewBondAlert(SecurityInstrumentEntity bond, TickerEntity ticker) {
        if (!isEnabled() || bond == null || bond.getCode() == null) {
            return Mono.empty();
        }

        String code = bond.getCode();

        return cooldownRepository.findByAlertTypeAndTicker("NEW_BOND", code)
                .switchIfEmpty(
                        cooldownRepository.save(AlertCooldownEntity.builder()
                                .alertType("NEW_BOND")
                                .ticker(code)
                                .lastSentAt(LocalDateTime.now())
                                .lastValue(BigDecimal.ZERO)
                                .build()
                        ).flatMap(cd -> {
                            String message = buildNewBondMessage(bond, ticker);
                            InlineKeyboardMarkup keyboard = buildBondInlineKeyboard(code);
                            return subscriberRepository.findAllBySubNewBondsTrue()
                                    .doOnNext(sub -> sendHtmlMessage(sub.getChatId(), message, keyboard))
                                    .then(Mono.empty());
                        })
                )
                .then();
    }

    /**
     * Alert for institutional deals (whale alerts > 500 mln KZT).
     */
    public Mono<Void> broadcastWhaleAlert(String ticker, String name, BigDecimal volKzt, BigDecimal price) {
        if (!isEnabled() || ticker == null || volKzt == null) {
            return Mono.empty();
        }

        return cooldownRepository.findByAlertTypeAndTicker("WHALE", ticker)
                .flatMap(cd -> {
                    long hoursAgo = ChronoUnit.HOURS.between(cd.getLastSentAt(), LocalDateTime.now());
                    if (hoursAgo < 12) {
                        return Mono.empty();
                    }
                    cd.setLastSentAt(LocalDateTime.now());
                    cd.setLastValue(volKzt);
                    return cooldownRepository.save(cd).then(Mono.just(true));
                })
                .switchIfEmpty(
                        cooldownRepository.save(AlertCooldownEntity.builder()
                                .alertType("WHALE")
                                .ticker(ticker)
                                .lastSentAt(LocalDateTime.now())
                                .lastValue(volKzt)
                                .build()
                        ).map(e -> true)
                )
                .flatMap(shouldSend -> {
                    String message = buildWhaleMessage(ticker, name, volKzt, price);
                    InlineKeyboardMarkup keyboard = buildBondInlineKeyboard(ticker);
                    return subscriberRepository.findAllBySubWhalesTrue()
                            .doOnNext(sub -> sendHtmlMessage(sub.getChatId(), message, keyboard))
                            .then();
                });
    }

    /**
     * Alert for major stock price movements (>= 3% daily change).
     */
    public Mono<Void> broadcastStockMoveAlert(StockItemDto stock) {
        if (!isEnabled() || stock == null || stock.getCode() == null || stock.getChangePercent() == null) {
            return Mono.empty();
        }

        String code = stock.getCode();
        BigDecimal change = stock.getChangePercent().abs();
        if (change.compareTo(BigDecimal.valueOf(3.0)) < 0) {
            return Mono.empty();
        }

        return cooldownRepository.findByAlertTypeAndTicker("STOCK_MOVE", code)
                .flatMap(cd -> {
                    long hoursAgo = ChronoUnit.HOURS.between(cd.getLastSentAt(), LocalDateTime.now());
                    if (hoursAgo < 8) {
                        return Mono.empty();
                    }
                    cd.setLastSentAt(LocalDateTime.now());
                    cd.setLastValue(stock.getChangePercent());
                    return cooldownRepository.save(cd).then(Mono.just(true));
                })
                .switchIfEmpty(
                        cooldownRepository.save(AlertCooldownEntity.builder()
                                .alertType("STOCK_MOVE")
                                .ticker(code)
                                .lastSentAt(LocalDateTime.now())
                                .lastValue(stock.getChangePercent())
                                .build()
                        ).map(e -> true)
                )
                .flatMap(shouldSend -> {
                    String message = buildStockMoveMessage(stock);
                    InlineKeyboardMarkup keyboard = buildStockInlineKeyboard(code);
                    return subscriberRepository.findAllBySubStocksTrue()
                            .doOnNext(sub -> {
                                // If subscriber has specific watchlist, check if subscribed
                                if (sub.getWatchlist() == null || sub.getWatchlist().isBlank()
                                        || sub.getWatchlist().toUpperCase().contains(code.toUpperCase())) {
                                    sendHtmlMessage(sub.getChatId(), message, keyboard);
                                }
                            })
                            .then();
                });
    }

    /**
     * Weekly coupon payout digest.
     */
    public Mono<Void> broadcastWeeklyCouponCalendar(List<BondItemDto> upcomingBonds) {
        if (!isEnabled() || upcomingBonds == null || upcomingBonds.isEmpty()) {
            return Mono.empty();
        }

        String message = buildWeeklyCouponMessage(upcomingBonds);
        return subscriberRepository.findAllBySubCouponsTrue()
                .doOnNext(sub -> sendHtmlMessage(sub.getChatId(), message, null))
                .then();
    }

    private void sendHtmlMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        if (chatId == null || text == null || telegramClient == null) return;
        try {
            SendMessage sm = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("HTML")
                    .disableWebPagePreview(true)
                    .replyMarkup(keyboard)
                    .build();
            telegramClient.execute(sm);
        } catch (Exception ex) {
            log.error("Failed to send Telegram message to chatId {}: {}", chatId, ex.getMessage());
        }
    }

    private String buildDiscountMessage(BondItemDto bond) {
        BigDecimal discount = BigDecimal.valueOf(100).subtract(bond.getPrice());
        BigDecimal faceVal = bond.getFaceValue() != null ? bond.getFaceValue() : BigDecimal.valueOf(1000);
        BigDecimal buyPrice = faceVal.multiply(bond.getPrice()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal capitalGain = faceVal.subtract(buyPrice);

        String name = bond.getOrgShortNameRu() != null ? bond.getOrgShortNameRu() : bond.getOrgNameRu();
        BigDecimal yield = bond.getDohod() != null ? bond.getDohod() : bond.getYtm();

        return String.format(
                "📉 <b>Скидка на KASE: облигация ниже номинала</b>\n\n" +
                "<b>Эмитент:</b> %s\n" +
                "<b>Тикер:</b> <code>%s</code>\n" +
                "<b>Текущая цена:</b> <b>%.2f%%</b> (скидка <b>%.2f%%</b>)\n" +
                "<b>Купонная ставка:</b> %.2f%% годовых\n" +
                "<b>Доходность к погашению (YTM):</b> <b>%.2f%%</b>\n" +
                "<b>Срок до погашения:</b> %s\n\n" +
                "💡 <i>Выгода для инвестора:</i>\n" +
                "При номинале <b>%s %s</b> вы покупаете бумагу за <b>%s %s</b>.\n" +
                "При погашении вам вернется полный номинал (+<b>%s %s</b> чистой прибыли на росте цены) + все регулярные купоны.",
                escapeHtml(name != null ? name : bond.getCode()),
                bond.getCode(),
                bond.getPrice().doubleValue(),
                discount.doubleValue(),
                bond.getCupon() != null ? bond.getCupon().doubleValue() : 0.0,
                yield != null ? yield.doubleValue() : 0.0,
                formatDuration(bond.getDtm()),
                formatMoney(faceVal), bond.getCurrency(),
                formatMoney(buyPrice), bond.getCurrency(),
                formatMoney(capitalGain), bond.getCurrency()
        );
    }

    private String buildNewBondMessage(SecurityInstrumentEntity bond, TickerEntity ticker) {
        String name = bond.getOrgShortNameRu() != null ? bond.getOrgShortNameRu() : bond.getOrgNameRu();
        BigDecimal coupon = null;
        if (ticker != null) {
            coupon = ticker.getCupon() != null ? ticker.getCupon() : ticker.getCupon2();
        }
        String finish = ticker != null && ticker.getFinishDate() != null ? ticker.getFinishDate().toString() : "По регламенту выпуска";
        String cur = ticker != null && ticker.getCurrency() != null ? ticker.getCurrency() : "KZT";

        return String.format(
                "🆕 <b>Новый выпуск облигаций на KASE</b>\n\n" +
                "<b>Эмитент:</b> %s\n" +
                "<b>Тикер:</b> <code>%s</code>\n" +
                "<b>Купонная ставка:</b> %s\n" +
                "<b>Дата погашения:</b> %s\n" +
                "<b>Валюта выпуска:</b> %s\n" +
                "<b>Сектор:</b> %s\n\n" +
                "<i>Бумага добавлена в биржевой список KASE и скоро станет доступна для торгов.</i>",
                escapeHtml(name != null ? name : bond.getCode()),
                bond.getCode(),
                coupon != null ? coupon.toPlainString() + "% годовых" : "Плавающая / Уточняется",
                finish,
                cur,
                bond.getBoardRu() != null ? bond.getBoardRu() : "Основная площадка"
        );
    }

    private String buildWhaleMessage(String ticker, String name, BigDecimal volKzt, BigDecimal price) {
        return String.format(
                "🐋 <b>Крупная сделка на бирже («Кит»)</b>\n\n" +
                "<b>Инструмент:</b> %s (<code>%s</code>)\n" +
                "<b>Объем сделки:</b> <b>%s ₸</b>\n" +
                "<b>Цена сделки:</b> %s\n\n" +
                "<i>Сигнал повышенного институционального интереса (крупный фонд или банк сформировал позицию).</i>",
                escapeHtml(name != null ? name : ticker),
                ticker,
                formatMoney(volKzt),
                price != null ? price.toPlainString() + "%" : "Рыночная"
        );
    }

    private String buildStockMoveMessage(StockItemDto stock) {
        String sign = stock.getChangePercent().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        String icon = stock.getChangePercent().compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";

        return String.format(
                "%s <b>Движение акции на KASE: %s%s%%</b>\n\n" +
                "<b>Компания:</b> %s\n" +
                "<b>Тикер:</b> <code>%s</code>\n" +
                "<b>Текущая цена:</b> <b>%s %s</b> (%s%s ₸)\n" +
                "<b>Дневной объем:</b> %s ₸ (%d сделок)\n\n" +
                "<i>Значительное дневное отклонение цены.</i>",
                icon,
                sign, stock.getChangePercent().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                escapeHtml(stock.getName() != null ? stock.getName() : stock.getCode()),
                stock.getCode(),
                formatMoney(stock.getPrice()), stock.getCurrency() != null ? stock.getCurrency() : "KZT",
                sign, stock.getChange() != null ? formatMoney(stock.getChange()) : "0",
                stock.getVolumeKzt() != null ? formatMoney(stock.getVolumeKzt()) : "0",
                stock.getDealCount() != null ? stock.getDealCount() : 0
        );
    }

    private String buildWeeklyCouponMessage(List<BondItemDto> upcoming) {
        StringBuilder sb = new StringBuilder("📅 <b>Купонный календарь на неделю («Что капнет»)</b>\n\n" +
                "На этой неделе купонные выплаты и погашения ожидаются по бумагам:\n\n");
        for (BondItemDto b : upcoming) {
            String name = b.getOrgShortNameRu() != null ? b.getOrgShortNameRu() : b.getOrgNameRu();
            sb.append(String.format("• <b>%s</b> (<code>%s</code>) — купон %.2f%%, погашение через %s\n",
                    escapeHtml(name != null ? name : b.getCode()),
                    b.getCode(),
                    b.getCupon() != null ? b.getCupon().doubleValue() : 0.0,
                    formatDuration(b.getDtm())
            ));
        }
        sb.append("\n<i>Проверьте баланс на брокерском счете или настройте реинвестирование.</i>");
        return sb.toString();
    }

    private InlineKeyboardMarkup buildBondInlineKeyboard(String ticker) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("🧮 Посчитать доход")
                                .callbackData("CALC_" + ticker)
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("📊 Открыть на KASE")
                                .url("https://kase.kz/ru/bonds/show/" + ticker + "/")
                                .build()
                ))
                .build();
    }

    private InlineKeyboardMarkup buildStockInlineKeyboard(String ticker) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("⭐ В мой вотчлист")
                                .callbackData("TRACK_" + ticker)
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("📊 График на KASE")
                                .url("https://kase.kz/ru/shares/show/" + ticker + "/")
                                .build()
                ))
                .build();
    }

    private String formatDuration(Integer dtm) {
        if (dtm == null || dtm <= 0) return "Срок истек";
        if (dtm < 30) return dtm + " дн.";
        int months = dtm / 30;
        if (months < 12) return months + " мес.";
        int years = dtm / 365;
        int remMonths = (dtm % 365) / 30;
        if (remMonths == 0) return years + " г.";
        return years + " г. " + remMonths + " мес.";
    }

    private String formatMoney(BigDecimal val) {
        if (val == null) return "0";
        return String.format("%,.0f", val.doubleValue()).replace(',', ' ');
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
