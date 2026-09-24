package kz.nurgissa.kasestockexchangeparser.telegram;

import kz.nurgissa.kasestockexchangeparser.model.dtos.BondItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.StockItemDto;
import kz.nurgissa.kasestockexchangeparser.model.entities.AlertCooldownEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.PriceAlertTargetEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.SecurityInstrumentEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.TickerEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.AlertCooldownRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.PriceAlertTargetRepository;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class TelegramAlertDispatcherService {

    private final TelegramSubscriberRepository subscriberRepository;
    private final AlertCooldownRepository cooldownRepository;
    private final PriceAlertTargetRepository priceAlertTargetRepository;
    private final TelegramClient telegramClient;
    private final boolean enabled;

    public TelegramAlertDispatcherService(
            TelegramSubscriberRepository subscriberRepository,
            AlertCooldownRepository cooldownRepository,
            PriceAlertTargetRepository priceAlertTargetRepository,
            @Value("${telegram.bot.token:}") String botToken,
            @Value("${telegram.bot.enabled:false}") boolean enabled
    ) {
        this.subscriberRepository = subscriberRepository;
        this.cooldownRepository = cooldownRepository;
        this.priceAlertTargetRepository = priceAlertTargetRepository;
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
                            .concatMap(sub -> sendHtmlMessage(sub.getChatId(), message, keyboard))
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
                            InlineKeyboardMarkup keyboard = buildNewBondInlineKeyboard(bond, ticker);
                            return subscriberRepository.findAllBySubNewBondsTrue()
                                    .concatMap(sub -> sendHtmlMessage(sub.getChatId(), message, keyboard))
                                    .then(Mono.empty());
                        })
                )
                .then();
    }

    /**
     * Alert triggered when a previously pending bond gets its terms or coupon published by KASE.
     */
    public Mono<Void> broadcastBondTermsUpdatedAlert(SecurityInstrumentEntity bond, TickerEntity ticker) {
        if (!isEnabled() || bond == null || bond.getCode() == null || ticker == null) {
            return Mono.empty();
        }

        String code = bond.getCode();
        BigDecimal coupon = ticker.getCupon() != null ? ticker.getCupon() : ticker.getCupon2();
        if (coupon == null || coupon.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.empty();
        }

        return cooldownRepository.findByAlertTypeAndTicker("TERMS_UPDATED", code)
                .switchIfEmpty(
                        cooldownRepository.save(AlertCooldownEntity.builder()
                                .alertType("TERMS_UPDATED")
                                .ticker(code)
                                .lastSentAt(LocalDateTime.now())
                                .lastValue(coupon)
                                .build()
                        ).flatMap(cd -> {
                            String message = buildBondTermsUpdatedMessage(bond, ticker);
                            InlineKeyboardMarkup keyboard = buildBondTermsUpdatedKeyboard(code);
                            return subscriberRepository.findAllBySubNewBondsTrue()
                                    .concatMap(sub -> sendHtmlMessage(sub.getChatId(), message, keyboard))
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

        // Night quiet hours: only broadcast whale alerts during active market/day hours (08:30 - 22:00 Almaty time)
        LocalTime nowAlmaty = LocalTime.now(ZoneId.of("Asia/Almaty"));
        if (nowAlmaty.isBefore(LocalTime.of(8, 30)) || nowAlmaty.isAfter(LocalTime.of(22, 0))) {
            return Mono.empty();
        }

        return cooldownRepository.findByAlertTypeAndTicker("WHALE", ticker)
                .flatMap(cd -> {
                    BigDecimal lastVol = cd.getLastValue() != null ? cd.getLastValue() : BigDecimal.ZERO;
                    BigDecimal delta = volKzt.subtract(lastVol);
                    // Only alert if volume increased by at least 500 million KZT since last notification
                    if (delta.compareTo(BigDecimal.valueOf(500_000_000L)) < 0) {
                        return Mono.empty();
                    }
                    cd.setLastSentAt(LocalDateTime.now());
                    cd.setLastValue(volKzt);
                    return cooldownRepository.save(cd).then(Mono.just(true));
                })
                .switchIfEmpty(
                        // Initialize baseline on first scan so cumulative historical volume doesn't trigger false alerts
                        cooldownRepository.save(AlertCooldownEntity.builder()
                                .alertType("WHALE")
                                .ticker(ticker)
                                .lastSentAt(LocalDateTime.now())
                                .lastValue(volKzt)
                                .build()
                        ).then(Mono.empty())
                )
                .flatMap(shouldSend -> {
                    String message = buildWhaleMessage(ticker, name, volKzt, price);
                    InlineKeyboardMarkup keyboard = buildBondInlineKeyboard(ticker);
                    return subscriberRepository.findAllBySubWhalesTrue()
                            .concatMap(sub -> sendHtmlMessage(sub.getChatId(), message, keyboard))
                            .then();
                });
    }

    /**
     * Alert for major stock price movements (respecting each user's custom threshold, default >= 3%).
     */
    public Mono<Void> broadcastStockMoveAlert(StockItemDto stock) {
        if (!isEnabled() || stock == null || stock.getCode() == null || stock.getChangePercent() == null) {
            return Mono.empty();
        }

        String code = stock.getCode();
        BigDecimal change = stock.getChangePercent().abs();
        if (change.compareTo(BigDecimal.valueOf(1.0)) < 0) {
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
                            .flatMap(sub -> {
                                BigDecimal userThreshold = (sub.getPriceChangeThreshold() != null && sub.getPriceChangeThreshold().compareTo(BigDecimal.ZERO) > 0)
                                        ? sub.getPriceChangeThreshold()
                                        : BigDecimal.valueOf(3.0);
                                if (change.compareTo(userThreshold) < 0) {
                                    return Mono.empty();
                                }
                                // If subscriber has specific watchlist, check if subscribed
                                if (sub.getWatchlist() == null || sub.getWatchlist().isBlank()
                                        || sub.getWatchlist().toUpperCase().contains(code.toUpperCase())) {
                                    return sendHtmlMessage(sub.getChatId(), message, keyboard);
                                }
                                return Mono.empty();
                            })
                            .then();
                });
    }

    /**
     * Check and trigger price target limit alerts set by users.
     */
    public Mono<Void> checkAndDispatchPriceTargets(String ticker, BigDecimal currentPrice) {
        if (!isEnabled() || ticker == null || currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.empty();
        }

        return priceAlertTargetRepository.findAllActiveByTicker(ticker.trim())
                .flatMap(alert -> {
                    boolean triggered = false;
                    if ("ABOVE".equalsIgnoreCase(alert.getDirection()) && currentPrice.compareTo(alert.getTargetPrice()) >= 0) {
                        triggered = true;
                    } else if ("BELOW".equalsIgnoreCase(alert.getDirection()) && currentPrice.compareTo(alert.getTargetPrice()) <= 0) {
                        triggered = true;
                    }

                    if (triggered) {
                        alert.setIsTriggered(true);
                        alert.setTriggeredAt(LocalDateTime.now());
                        return priceAlertTargetRepository.save(alert)
                                .flatMap(saved -> {
                                    String msg = buildPriceAlertTriggeredMessage(saved, currentPrice);
                                    InlineKeyboardMarkup keyboard = buildTargetTriggeredKeyboard(saved.getTicker());
                                    return sendHtmlMessage(saved.getChatId(), msg, keyboard);
                                });
                    }
                    return Mono.empty();
                })
                .then();
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
                .concatMap(sub -> sendHtmlMessage(sub.getChatId(), message, null))
                .then();
    }

    private Mono<Void> sendHtmlMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        if (chatId == null || text == null || telegramClient == null) return Mono.empty();
        return Mono.fromRunnable(() -> {
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
                String errorMsg = ex.getMessage() != null ? ex.getMessage() : "";
                if (errorMsg.contains("403") || errorMsg.toLowerCase().contains("blocked")
                        || errorMsg.toLowerCase().contains("chat not found") || errorMsg.toLowerCase().contains("deactivated")) {
                    log.warn("Subscriber {} blocked the bot or chat is invalid. Deactivating alerts. Error: {}", chatId, errorMsg);
                    subscriberRepository.deactivateAllSubscriptions(chatId, LocalDateTime.now()).subscribe();
                } else {
                    log.error("Failed to send Telegram message to chatId {}: {}", chatId, errorMsg);
                }
            }
        })
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .then();
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

        String code = bond.getCode();
        boolean isPrivatePlacement = (code != null && code.toLowerCase().contains("pp"))
                || (bond.getBoardRu() != null && (bond.getBoardRu().toLowerCase().contains("частн") || bond.getBoardRu().toLowerCase().contains("private")));

        StringBuilder sb = new StringBuilder();
        sb.append("🆕 <b>Новый выпуск облигаций на KASE</b>\n\n");
        sb.append("<b>Эмитент:</b> ").append(escapeHtml(name != null ? name : code)).append("\n");
        sb.append("<b>Тикер:</b> <code>").append(code).append("</code>\n");

        if (isPrivatePlacement) {
            sb.append("<b>Формат:</b> 🔒 <b>Частное размещение (Private Placement)</b>\n");
        } else {
            sb.append("<b>Формат:</b> 🌐 Публичный биржевой выпуск\n");
        }

        if (coupon != null && coupon.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<b>Купонная ставка:</b> <b>").append(coupon.toPlainString()).append("% годовых</b>\n");
        } else {
            sb.append("<b>Купонная ставка:</b> ⏳ <b>Определяется на торгах (по проспекту)</b>\n");
            sb.append("• <i>Условия: плавающая ставка (Базовая ставка НБРК + маржа) либо по итогам аукциона заявок.</i>\n");
        }

        sb.append("<b>Дата погашения:</b> ").append(finish).append("\n");
        sb.append("<b>Валюта выпуска:</b> ").append(cur).append("\n");

        String board = (bond.getBoardRu() != null && !bond.getBoardRu().isBlank())
                ? bond.getBoardRu()
                : (isPrivatePlacement ? "Частное размещение" : "Основная площадка");
        sb.append("<b>Сектор:</b> ").append(board).append("\n\n");

        if (isPrivatePlacement) {
            sb.append("ℹ️ <i>Облигации частного размещения выпускаются для институциональных инвесторов по закрытой подписке.</i>\n");
        } else {
            sb.append("<i>Бумага добавлена в биржевой список KASE и скоро станет доступна для открытых торгов.</i>\n");
        }

        if (bond.getOrgCode() != null && !bond.getOrgCode().isBlank()) {
            sb.append("\n📄 <i>Проспект выпуска и решение эмитента: <a href=\"https://kase.kz/ru/issuers/").append(bond.getOrgCode()).append("/\">kase.kz/ru/issuers/").append(bond.getOrgCode()).append("/</a></i>");
        }

        return sb.toString();
    }

    private String buildBondTermsUpdatedMessage(SecurityInstrumentEntity bond, TickerEntity ticker) {
        String name = bond.getOrgShortNameRu() != null ? bond.getOrgShortNameRu() : bond.getOrgNameRu();
        BigDecimal coupon = ticker.getCupon() != null ? ticker.getCupon() : ticker.getCupon2();
        String finish = ticker.getFinishDate() != null ? ticker.getFinishDate().toString() : "По регламенту выпуска";
        String cur = ticker.getCurrency() != null ? ticker.getCurrency() : "KZT";

        return String.format(
                "🔔 <b>Утверждена ставка по выпуску на KASE!</b>\n\n" +
                "<b>Эмитент:</b> %s\n" +
                "<b>Тикер:</b> <code>%s</code>\n" +
                "<b>Ставка купона:</b> <b>%.2f%% годовых</b>\n" +
                "<b>Дата погашения:</b> %s\n" +
                "<b>Валюта:</b> %s\n\n" +
                "💡 <i>Биржа KASE внесла параметры выпуска в торговый реестр. Бумага готова к расчетам доходности.</i>",
                escapeHtml(name != null ? name : bond.getCode()),
                bond.getCode(),
                coupon != null ? coupon.doubleValue() : 0.0,
                finish,
                cur
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

    private InlineKeyboardMarkup buildNewBondInlineKeyboard(SecurityInstrumentEntity bond, TickerEntity ticker) {
        String code = bond.getCode();
        String orgCode = bond.getOrgCode();
        BigDecimal coupon = (ticker != null) ? (ticker.getCupon() != null ? ticker.getCupon() : ticker.getCupon2()) : null;
        boolean hasCoupon = coupon != null && coupon.compareTo(BigDecimal.ZERO) > 0;

        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (hasCoupon) {
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder().text("🧮 Посчитать доход").callbackData("CALC_" + code + "_500000").build(),
                    InlineKeyboardButton.builder().text("📊 Открыть на KASE").url("https://kase.kz/ru/bonds/show/" + code + "/").build()
            ));
        } else {
            List<InlineKeyboardButton> topRow = new ArrayList<>();
            topRow.add(InlineKeyboardButton.builder().text("❓ Где ставка?").callbackData("WHY_NO_COUPON_" + code).build());
            if (orgCode != null && !orgCode.isBlank()) {
                topRow.add(InlineKeyboardButton.builder().text("🏢 Проспект эмитента").url("https://kase.kz/ru/issuers/" + orgCode + "/").build());
            } else {
                topRow.add(InlineKeyboardButton.builder().text("📊 Страница KASE").url("https://kase.kz/ru/bonds/show/" + code + "/").build());
            }
            rows.add(new InlineKeyboardRow(topRow));
        }

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("⭐ В избранное").callbackData("TRACK_" + code).build(),
                InlineKeyboardButton.builder().text("📄 Паспорт бумаги").callbackData("BOND_" + code).build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup buildBondTermsUpdatedKeyboard(String ticker) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("🧮 Рассчитать доход на 500k ₸")
                                .callbackData("CALC_" + ticker + "_500000")
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
        if (dtm == null) return "По регламенту выпуска";
        if (dtm <= 0) return "Срок истек";
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

    private String buildPriceAlertTriggeredMessage(PriceAlertTargetEntity alert, BigDecimal currentPrice) {
        String directionText = "ABOVE".equalsIgnoreCase(alert.getDirection()) ? "выросла до / превысила" : "опустилась до / ниже";
        String icon = "ABOVE".equalsIgnoreCase(alert.getDirection()) ? "🚀" : "📉";

        BigDecimal initial = alert.getInitialPrice() != null ? alert.getInitialPrice() : alert.getTargetPrice();
        BigDecimal diffPct = BigDecimal.ZERO;
        if (initial != null && initial.compareTo(BigDecimal.ZERO) > 0) {
            diffPct = currentPrice.subtract(initial).multiply(BigDecimal.valueOf(100)).divide(initial, 2, RoundingMode.HALF_UP);
        }
        String sign = diffPct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";

        return String.format(
                "🎯 <b>Сработал ваш лимит цены!</b> %s\n\n" +
                "<b>Инструмент:</b> <code>%s</code>\n" +
                "<b>Текущая цена:</b> <b>%s ₸</b> (%s%s%% от момента установки)\n" +
                "<b>Целевой уровень:</b> <b>%s ₸</b> (цена %s цель)\n\n" +
                "🔔 <i>Лимит выполнен и перемещен в архив. Вы можете поставить новый алерт в любой момент.</i>",
                icon,
                alert.getTicker().toUpperCase(),
                formatMoney(currentPrice),
                sign, diffPct.toPlainString(),
                formatMoney(alert.getTargetPrice()),
                directionText
        );
    }

    private InlineKeyboardMarkup buildTargetTriggeredKeyboard(String ticker) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("📊 Анализ " + ticker)
                                .callbackData("TA_" + ticker)
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("🔔 Новый алерт")
                                .callbackData("SET_ALERT_" + ticker)
                                .build()
                ))
                .build();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

