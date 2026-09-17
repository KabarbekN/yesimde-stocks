package kz.nurgissa.kasestockexchangeparser.telegram;

import kz.nurgissa.kasestockexchangeparser.model.dtos.BondItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.InstrumentDetailDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.StockItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.SubscriberStatsDto;
import kz.nurgissa.kasestockexchangeparser.model.entities.TelegramSubscriberEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.TelegramSubscriberRepository;
import kz.nurgissa.kasestockexchangeparser.service.BondAnalyticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
@Slf4j
public class KaseTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final String botToken;
    private final TelegramClient telegramClient;
    private final TelegramSubscriberRepository subscriberRepository;
    private final BondAnalyticsService analyticsService;
    private final kz.nurgissa.kasestockexchangeparser.service.AixService aixService;
    private final DatabaseClient databaseClient;

    public KaseTelegramBot(
            @Value("${telegram.bot.token}") String botToken,
            TelegramSubscriberRepository subscriberRepository,
            BondAnalyticsService analyticsService,
            kz.nurgissa.kasestockexchangeparser.service.AixService aixService,
            DatabaseClient databaseClient
    ) {
        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.subscriberRepository = subscriberRepository;
        this.analyticsService = analyticsService;
        this.aixService = aixService;
        this.databaseClient = databaseClient;
        log.info("KASE & AIX Telegram Bot initialized successfully.");
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            log.error("Error processing Telegram update: {}", e.getMessage(), e);
        }
    }

    private void handleTextMessage(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText().trim();
        String username = message.getFrom() != null ? message.getFrom().getUserName() : "";
        String firstName = message.getFrom() != null ? message.getFrom().getFirstName() : "";

        // Register or refresh user info atomically without wiping customized preferences
        subscriberRepository.registerOrUpdate(
                chatId,
                username,
                firstName,
                true,
                true,
                true,
                true,
                true,
                "KSPI,HSBK,KZAP,AIRA,KMGZ",
                LocalDateTime.now(),
                LocalDateTime.now()
        ).subscribe(
                rows -> log.debug("Subscriber registered/refreshed: chatId={}", chatId),
                e -> log.error("Error registering subscriber chatId {}: {}", chatId, e.getMessage())
        );

        if (text.startsWith("/start") || text.equals("ℹ️ Меню")) {
            sendWelcome(chatId, firstName);
        } else if (text.equals("📉 Скидки (<95%)") || text.startsWith("/discounts")) {
            sendDiscounts(chatId);
        } else if (text.equals("🏆 Топ доходностей") || text.startsWith("/top")) {
            sendTopYields(chatId);
        } else if (text.equals("📈 Акции KASE") || text.startsWith("/stocks")) {
            sendStocks(chatId);
        } else if (text.equals("🏛️ Биржа AIX") || text.startsWith("/aix")) {
            sendAixSummary(chatId);
        } else if (text.equals("⚖️ Арбитраж KASE/AIX") || text.startsWith("/arbitrage") || text.startsWith("/compare")) {
            handleArbitrageCommand(chatId, text);
        } else if (text.startsWith("/depth")) {
            handleMarketDepthCommand(chatId, text);
        } else if (text.startsWith("⚙️") || text.startsWith("/settings") || text.equalsIgnoreCase("подписки")) {
            sendSubscriptionSettings(chatId);
        } else if (text.equals("📊 Статистика") || text.startsWith("/stats")) {
            sendGlobalStats(chatId);
        } else if (text.equals("🧮 Калькулятор") || text.startsWith("/calc")) {
            handleCalcCommand(chatId, text);
        } else if (text.startsWith("/bond")) {
            handleBondCommand(chatId, text);
        } else if (text.startsWith("/stock")) {
            handleStockCommand(chatId, text);
        } else if (text.startsWith("/track")) {
            handleTrackCommand(chatId, text, true);
        } else if (text.startsWith("/untrack")) {
            handleTrackCommand(chatId, text, false);
        } else if (text.equals("ℹ️ О боте") || text.startsWith("/help")) {
            sendHelp(chatId);
        } else {
            // Check if user just typed a ticker like "KZTKb3" or "KSPI" or "KAP"
            if (text.length() <= 10 && !text.contains(" ")) {
                handleQuickTickerLookup(chatId, text.toUpperCase());
            } else {
                sendMessage(chatId, "Команда не распознана. Используйте кнопки меню ниже или введите тикер бумаги (например: <code>KZTKb3</code>, <code>KSPI</code> или <code>KAP</code>).", buildMainMenuKeyboard());
            }
        }
    }

    private void handleCallbackQuery(CallbackQuery callback) {
        Long chatId = callback.getMessage().getChatId();
        Integer messageId = callback.getMessage().getMessageId();
        String data = callback.getData();

        if (data.startsWith("TOGGLE_")) {
            String category = data.substring("TOGGLE_".length());
            subscriberRepository.findById(chatId)
                    .flatMap(sub -> {
                        switch (category) {
                            case "NEW_BONDS" -> sub.setSubNewBonds(!Boolean.TRUE.equals(sub.getSubNewBonds()));
                            case "DISCOUNTS" -> sub.setSubDiscounts(!Boolean.TRUE.equals(sub.getSubDiscounts()));
                            case "WHALES" -> sub.setSubWhales(!Boolean.TRUE.equals(sub.getSubWhales()));
                            case "COUPONS" -> sub.setSubCoupons(!Boolean.TRUE.equals(sub.getSubCoupons()));
                            case "STOCKS" -> sub.setSubStocks(!Boolean.TRUE.equals(sub.getSubStocks()));
                        }
                        sub.setUpdatedAt(LocalDateTime.now());
                        return subscriberRepository.save(sub)
                                .flatMap(updatedSub -> getSubscriberStats().map(stats -> Map.entry(updatedSub, stats)));
                    })
                    .doOnSuccess(entry -> {
                        if (entry == null) return;
                        TelegramSubscriberEntity updatedSub = entry.getKey();
                        SubscriberStatsDto stats = entry.getValue();

                        int activeCount = calculateActiveCategories(updatedSub);
                        int watchlistCount = calculateWatchlistCount(updatedSub);

                        String text = buildSettingsText(activeCount, watchlistCount, updatedSub.getWatchlist(), stats.totalUsers());
                        EditMessageText edit = EditMessageText.builder()
                                .chatId(chatId.toString())
                                .messageId(messageId)
                                .text(text)
                                .parseMode("HTML")
                                .replyMarkup(buildSettingsInlineKeyboard(updatedSub, stats))
                                .build();
                        try {
                            telegramClient.execute(edit);
                        } catch (Exception e) {
                            log.error("Failed to update settings message: {}", e.getMessage());
                        }
                    })
                    .subscribe(
                            entry -> log.debug("Settings updated for chatId {}", chatId),
                            e -> log.error("Failed to update settings for chatId {}: {}", chatId, e.getMessage())
                    );
        } else if (data.startsWith("CALC_")) {
            String ticker = data.substring("CALC_".length());
            sendMessage(chatId, "Для расчета введите команду с суммой, например:\n<code>/calc " + ticker + " 500000</code>", null);
        } else if (data.startsWith("TRACK_")) {
            String ticker = data.substring("TRACK_".length());
            handleTrackCommand(chatId, "/track " + ticker, true);
        } else if (data.equals("VIEW_WATCHLIST")) {
            sendWatchlist(chatId);
        }
    }

    private void sendWelcome(Long chatId, String name) {
        subscriberRepository.findById(chatId)
                .map(this::calculateActiveCategories)
                .defaultIfEmpty(5)
                .doOnSuccess(activeCount -> {
                    String greeting = (name != null && !name.isBlank()) ? ", " + name : "";
                    String text = String.format(
                            "Здравствуйте%s!\n\n" +
                            "Добро пожаловать в <b>KASE & AIX Radar</b> — ваш монитор казахстанского финансового рынка ценных бумаг.\n\n" +
                            "<b>Что умеет бот:</b>\n" +
                            "• 📉 <b>Облигации со скидкой</b> — ловит бумаги ниже номинала (доходность выше рыночной).\n" +
                            "• 🆕 <b>Новые выпуски</b> — сообщает, когда на KASE появляются свежие облигации.\n" +
                            "• ⚖️ <b>Арбитраж KASE ⇄ AIX</b> — находит разницу цен на акции (Казатомпром, Kaspi, Halyk и др.).\n" +
                            "• 🏛️ <b>Биржа AIX</b> — стакан котировок (Level-2 Order Book), ETF и сукук.\n" +
                            "• 🐋 <b>Крупные сделки</b> — отслеживает заходы институциональных фондов (>500 млн ₸).\n" +
                            "• 📅 <b>Купонный дайджест</b> — напоминает по понедельникам, какие купоны выплатят на неделе.\n" +
                            "• 📈 <b>Акции KASE & AIX</b> — котировки и вотчлист избранных акций.\n" +
                            "• 🧮 <b>Калькулятор</b> — наглядно рассчитывает выплаты и прибыль на вложенную сумму.\n\n" +
                            "Выберите действие в меню ниже 👇",
                            greeting
                    );
                    sendMessage(chatId, text, buildMainMenuKeyboard(activeCount));
                })
                .subscribe(null, e -> log.error("Error sending welcome message: {}", e.getMessage()));
    }

    private void sendDiscounts(Long chatId) {
        analyticsService.getDiscountBonds(95.0)
                .doOnSuccess(bonds -> {
                    if (bonds == null || bonds.isEmpty()) {
                        sendMessage(chatId, "📉 В данный момент активных облигаций с ценой ≤ 95% от номинала не найдено.", null);
                        return;
                    }
                    StringBuilder sb = new StringBuilder("📉 <b>Облигации KASE, торгующиеся со скидкой (≤ 95%):</b>\n\n");
                    int count = Math.min(bonds.size(), 10);
                    for (int i = 0; i < count; i++) {
                        BondItemDto b = bonds.get(i);
                        double discount = 100.0 - (b.getPrice() != null ? b.getPrice().doubleValue() : 100.0);
                        String name = b.getOrgShortNameRu() != null ? b.getOrgShortNameRu() : b.getOrgNameRu();
                        BigDecimal yield = b.getDohod() != null ? b.getDohod() : b.getYtm();
                        sb.append(String.format(
                                "%d. <b>%s</b> (<code>%s</code>)\n" +
                                "   • Цена: <b>%.2f%%</b> (скидка <b>%.1f%%</b>)\n" +
                                "   • Купон: %.2f%% | YTM: <b>%.2f%%</b>\n" +
                                "   • Погашение через: %s\n" +
                                "   • Расчет: <code>/calc %s 500000</code>\n\n",
                                i + 1,
                                escapeHtml(name != null ? name : b.getCode()),
                                b.getCode(),
                                b.getPrice() != null ? b.getPrice().doubleValue() : 0.0,
                                discount,
                                b.getCupon() != null ? b.getCupon().doubleValue() : 0.0,
                                yield != null ? yield.doubleValue() : 0.0,
                                formatDuration(b.getDtm()),
                                b.getCode()
                        ));
                    }
                    sb.append("💡 <i>При покупке со скидкой вы платите меньше номинала, а при погашении эмитент возвращает полные 100%.</i>");
                    sendMessage(chatId, sb.toString(), null);
                })
                .subscribe();
    }

    private void sendTopYields(Long chatId) {
        analyticsService.getBondScreener("KZT", 10.0, 30.0, 30, null, null, null, null, "ytm", "desc", 8, 0)
                .doOnSuccess(bonds -> {
                    if (bonds == null || bonds.isEmpty()) {
                        sendMessage(chatId, "🏆 Данные по доходностям временно обновляются.", null);
                        return;
                    }
                    StringBuilder sb = new StringBuilder("🏆 <b>Топ облигаций KASE по доходности к погашению (в тенге):</b>\n\n");
                    for (int i = 0; i < bonds.size(); i++) {
                        BondItemDto b = bonds.get(i);
                        String name = b.getOrgShortNameRu() != null ? b.getOrgShortNameRu() : b.getOrgNameRu();
                        BigDecimal yield = b.getDohod() != null ? b.getDohod() : b.getYtm();
                        sb.append(String.format(
                                "%d. <b>%s</b> (<code>%s</code>)\n" +
                                "   • Доходность (YTM): <b>%.2f%%</b> годовых\n" +
                                "   • Купон: %.2f%% | Цена: %.2f%%\n" +
                                "   • Срок: %s | Инфо: <code>/bond %s</code>\n\n",
                                i + 1,
                                escapeHtml(name != null ? name : b.getCode()),
                                b.getCode(),
                                yield != null ? yield.doubleValue() : 0.0,
                                b.getCupon() != null ? b.getCupon().doubleValue() : 0.0,
                                b.getPrice() != null ? b.getPrice().doubleValue() : 100.0,
                                formatDuration(b.getDtm()),
                                b.getCode()
                        ));
                    }
                    sendMessage(chatId, sb.toString(), null);
                })
                .subscribe();
    }

    private void sendStocks(Long chatId) {
        List<String> blueChips = List.of("KSPI", "HSBK", "KZAP", "AIRA", "KMGZ", "KZTK", "CCBN", "KEGC", "KZTO");
        analyticsService.getTopStocks(blueChips)
                .doOnSuccess(stocks -> {
                    if (stocks == null || stocks.isEmpty()) {
                        sendMessage(chatId, "📈 Данные по акциям обновляются.", null);
                        return;
                    }
                    StringBuilder sb = new StringBuilder("📈 <b>Котировки ключевых акций KASE:</b>\n\n");
                    for (StockItemDto s : stocks) {
                        String sign = (s.getChangePercent() != null && s.getChangePercent().compareTo(BigDecimal.ZERO) >= 0) ? "+" : "";
                        String icon = (s.getChangePercent() != null && s.getChangePercent().compareTo(BigDecimal.ZERO) >= 0) ? "🟢" : "🔴";
                        sb.append(String.format(
                                "%s <b>%s</b> (<code>%s</code>)\n" +
                                "   • Цена: <b>%s %s</b> (%s%s%%)\n" +
                                "   • Объем торгов: %s ₸\n\n",
                                icon,
                                escapeHtml(s.getName() != null ? s.getName() : s.getCode()),
                                s.getCode(),
                                formatMoney(s.getPrice()), s.getCurrency() != null ? s.getCurrency() : "KZT",
                                sign, s.getChangePercent() != null ? s.getChangePercent().setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00",
                                formatMoney(s.getVolumeKzt())
                        ));
                    }
                    sb.append("💡 <i>Чтобы отслеживать конкретную акцию, отправьте:</i> <code>/track ТИКЕР</code>");
                    sendMessage(chatId, sb.toString(), null);
                })
                .subscribe();
    }

    private void sendSubscriptionSettings(Long chatId) {
        Mono.zip(
                subscriberRepository.findById(chatId).defaultIfEmpty(TelegramSubscriberEntity.builder()
                        .chatId(chatId)
                        .subNewBonds(true)
                        .subDiscounts(true)
                        .subWhales(true)
                        .subCoupons(true)
                        .subStocks(true)
                        .watchlist("KSPI,HSBK,KZAP,AIRA,KMGZ")
                        .build()),
                getSubscriberStats()
        ).doOnSuccess(tuple -> {
            TelegramSubscriberEntity sub = tuple.getT1();
            SubscriberStatsDto stats = tuple.getT2();

            int activeCount = calculateActiveCategories(sub);
            int watchlistCount = calculateWatchlistCount(sub);

            String text = buildSettingsText(activeCount, watchlistCount, sub.getWatchlist(), stats.totalUsers());
            sendMessage(chatId, text, buildSettingsInlineKeyboard(sub, stats));
        }).subscribe(null, e -> log.error("Error displaying settings for {}: {}", chatId, e.getMessage()));
    }

    private void sendGlobalStats(Long chatId) {
        getSubscriberStats().doOnSuccess(stats -> {
            String text = String.format("""
                    📊 <b>Статистика подписок биржевого радара</b>

                    👥 Всего инвесторов в боте: <b>%d</b>

                    <b>Активные подписки по направлениям:</b>
                    • 🆕 Новые облигации: <b>%d</b> подписчиков
                    • 📉 Скидки (&lt;95%%): <b>%d</b> подписчиков
                    • 🐋 Крупные сделки (>500M ₸): <b>%d</b> подписчиков
                    • 📅 Выплаты купонов: <b>%d</b> подписчиков
                    • 📈 Акции KASE/AIX: <b>%d</b> подписчиков
                    """,
                    stats.totalUsers(),
                    stats.newBondsCount(),
                    stats.discountsCount(),
                    stats.whalesCount(),
                    stats.couponsCount(),
                    stats.stocksCount()
            );
            sendMessage(chatId, text, null);
        }).subscribe(null, e -> log.error("Error sending global stats: {}", e.getMessage()));
    }

    private Mono<SubscriberStatsDto> getSubscriberStats() {
        String sql = """
            SELECT
                COUNT(CASE WHEN sub_new_bonds = true THEN 1 END) AS new_bonds_count,
                COUNT(CASE WHEN sub_discounts = true THEN 1 END) AS discounts_count,
                COUNT(CASE WHEN sub_whales = true THEN 1 END) AS whales_count,
                COUNT(CASE WHEN sub_coupons = true THEN 1 END) AS coupons_count,
                COUNT(CASE WHEN sub_stocks = true THEN 1 END) AS stocks_count,
                COUNT(*) AS total_users
            FROM telegram_subscriber
        """;
        return databaseClient.sql(sql)
                .map(row -> new SubscriberStatsDto(
                        row.get("new_bonds_count", Long.class) != null ? row.get("new_bonds_count", Long.class) : 0L,
                        row.get("discounts_count", Long.class) != null ? row.get("discounts_count", Long.class) : 0L,
                        row.get("whales_count", Long.class) != null ? row.get("whales_count", Long.class) : 0L,
                        row.get("coupons_count", Long.class) != null ? row.get("coupons_count", Long.class) : 0L,
                        row.get("stocks_count", Long.class) != null ? row.get("stocks_count", Long.class) : 0L,
                        row.get("total_users", Long.class) != null ? row.get("total_users", Long.class) : 0L
                ))
                .one()
                .defaultIfEmpty(SubscriberStatsDto.empty())
                .onErrorReturn(SubscriberStatsDto.empty());
    }

    private String buildSettingsText(int activeCount, int watchlistCount, String watchlist, long totalUsers) {
        return String.format("""
                ⚙️ <b>Управление вашими подписками</b>

                🎯 <b>Ваш статус:</b> %d из 5 категорий активно
                ⭐ <b>Вотчлист акций:</b> %d тикеров (<code>%s</code>)
                👥 <b>Всего инвесторов в боте:</b> %d

                <i>Нажимайте на кнопки ниже, чтобы включить (✅) или выключить (❌) категорию алертов. Возле каждой кнопки показано общее число активных подписчиков:</i>
                """,
                activeCount,
                watchlistCount,
                (watchlist != null && !watchlist.isBlank()) ? watchlist : "нет",
                totalUsers
        );
    }

    private int calculateActiveCategories(TelegramSubscriberEntity sub) {
        if (sub == null) return 0;
        int count = 0;
        if (Boolean.TRUE.equals(sub.getSubNewBonds())) count++;
        if (Boolean.TRUE.equals(sub.getSubDiscounts())) count++;
        if (Boolean.TRUE.equals(sub.getSubWhales())) count++;
        if (Boolean.TRUE.equals(sub.getSubCoupons())) count++;
        if (Boolean.TRUE.equals(sub.getSubStocks())) count++;
        return count;
    }

    private int calculateWatchlistCount(TelegramSubscriberEntity sub) {
        if (sub == null || sub.getWatchlist() == null || sub.getWatchlist().isBlank()) {
            return 0;
        }
        return (int) Arrays.stream(sub.getWatchlist().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .count();
    }

    private void handleBondCommand(Long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "Укажите тикер облигации, например:\n<code>/bond BIDBb5</code> или <code>/bond KZTKb3</code>", null);
            return;
        }
        handleQuickTickerLookup(chatId, parts[1].toUpperCase());
    }

    private void handleStockCommand(Long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "Укажите тикер акции, например:\n<code>/stock KSPI</code> или <code>/stock HSBK</code>", null);
            return;
        }
        String ticker = parts[1].toUpperCase();
        analyticsService.getTopStocks(List.of(ticker))
                .doOnSuccess(list -> {
                    if (list == null || list.isEmpty()) {
                        sendMessage(chatId, "Акция с тикером <code>" + ticker + "</code> не найдена.", null);
                        return;
                    }
                    StockItemDto s = list.get(0);
                    String sign = (s.getChangePercent() != null && s.getChangePercent().compareTo(BigDecimal.ZERO) >= 0) ? "+" : "";
                    String msg = String.format(
                            "📊 <b>Карточка акции: %s</b> (<code>%s</code>)\n\n" +
                            "• <b>Текущая цена:</b> <b>%s %s</b>\n" +
                            "• <b>Изменение за день:</b> %s%s%%\n" +
                            "• <b>Дневной объем торгов:</b> %s ₸\n" +
                            "• <b>Количество сделок:</b> %d\n\n" +
                            "<a href=\"https://kase.kz/ru/shares/show/%s/\">Открыть карточку на сайте KASE</a>",
                            escapeHtml(s.getName()), s.getCode(),
                            formatMoney(s.getPrice()), s.getCurrency(),
                            sign, s.getChangePercent() != null ? s.getChangePercent().setScale(2, RoundingMode.HALF_UP).toPlainString() : "0",
                            formatMoney(s.getVolumeKzt()),
                            s.getDealCount() != null ? s.getDealCount() : 0,
                            s.getCode()
                    );
                    sendMessage(chatId, msg, null);
                })
                .subscribe();
    }

    private void handleQuickTickerLookup(Long chatId, String ticker) {
        analyticsService.getInstrumentDetailByCode(ticker)
                .doOnSuccess(b -> {
                    if (b == null) {
                        // Check if it's a stock
                        analyticsService.getTopStocks(List.of(ticker))
                                .doOnSuccess(stocks -> {
                                    if (stocks != null && !stocks.isEmpty()) {
                                        StockItemDto s = stocks.get(0);
                                        sendMessage(chatId, String.format(
                                                "📊 <b>Акция %s</b> (<code>%s</code>)\n" +
                                                "• Цена: <b>%s %s</b>\n" +
                                                "• Дневной объем: %s ₸\n" +
                                                "<a href=\"https://kase.kz/ru/shares/show/%s/\">Смотреть на KASE</a>",
                                                escapeHtml(s.getName()), s.getCode(),
                                                formatMoney(s.getPrice()), s.getCurrency(),
                                                formatMoney(s.getVolumeKzt()),
                                                s.getCode()
                                        ), null);
                                    } else {
                                        aixService.getInstruments(null, null, ticker, 5)
                                                .doOnSuccess(aixList -> {
                                                    if (aixList != null && !aixList.isEmpty()) {
                                                        kz.nurgissa.kasestockexchangeparser.model.dtos.AixInstrumentDto inst = aixList.get(0);
                                                        BigDecimal pr = inst.getLastTrade() != null ? inst.getLastTrade() : inst.getReferencePrice();
                                                        String cur = inst.getCurrency() != null ? inst.getCurrency() : "";
                                                        sendMessage(chatId, String.format(
                                                                "🏛️ <b>Инструмент AIX: %s</b> (<code>%s</code>)\n" +
                                                                "• Класс: <b>%s</b>\n" +
                                                                "• Цена: <b>%s %s</b>\n" +
                                                                "• Изменение за день: %s\n" +
                                                                "• ISIN: <code>%s</code>\n\n" +
                                                                "📊 Стакан котировок: <code>/depth %s</code>\n" +
                                                                "⚖️ Сравнить с KASE: <code>/compare %s</code>",
                                                                escapeHtml(inst.getName() != null ? inst.getName() : inst.getSecCode()),
                                                                inst.getSecCode(),
                                                                inst.getAssetClass() != null ? inst.getAssetClass() : "-",
                                                                pr != null ? formatMoney(pr) : "—", cur,
                                                                inst.getPercentChange() != null ? String.format("%+.2f%%", inst.getPercentChange()) : "0%",
                                                                inst.getIsin() != null ? inst.getIsin() : "-",
                                                                inst.getSecCode(),
                                                                inst.getSecCode()
                                                        ), null);
                                                    } else {
                                                        sendMessage(chatId, "Инструмент с тикером <code>" + ticker + "</code> не найден ни на KASE, ни на AIX.", null);
                                                    }
                                                })
                                                .subscribe();
                                    }
                                })
                                .subscribe();
                        return;
                    }

                    BigDecimal faceVal = b.getFaceValue() != null ? b.getFaceValue() : BigDecimal.valueOf(1000);
                    BigDecimal price = (b.getInstrument() != null && b.getInstrument().getPrice() != null) ? b.getInstrument().getPrice() : BigDecimal.valueOf(100);
                    BigDecimal buyPrice = faceVal.multiply(price).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                    String name = (b.getInstrument() != null && b.getInstrument().getOrgShortNameRu() != null)
                            ? b.getInstrument().getOrgShortNameRu()
                            : (b.getInstrument() != null ? b.getInstrument().getOrgNameRu() : ticker);
                    String issuer = (b.getInstrument() != null && b.getInstrument().getOrgNameRu() != null) ? b.getInstrument().getOrgNameRu() : "-";
                    String cur = b.getResolvedCurrency() != null ? b.getResolvedCurrency() : "KZT";
                    BigDecimal couponRate = null;
                    if (b.getTicker() != null) {
                        couponRate = b.getTicker().getCupon() != null ? b.getTicker().getCupon() : b.getTicker().getCupon2();
                    }
                    BigDecimal ytm = b.getEffectiveYield();
                    String matDate = (b.getTicker() != null && b.getTicker().getFinishDate() != null)
                            ? b.getTicker().getFinishDate().toString()
                            : (b.getInstrument() != null && b.getInstrument().getRepaymentStartDate() != null ? b.getInstrument().getRepaymentStartDate().toString() : "По регламенту");
                    Integer dtm = b.getInstrument() != null ? b.getInstrument().getDtm() : 365;
                    BigDecimal volKzt = b.getInstrument() != null ? b.getInstrument().getVolkzt() : BigDecimal.ZERO;

                    String msg = String.format(
                            "📑 <b>Паспорт облигации: %s</b>\n\n" +
                            "<b>Эмитент:</b> %s\n" +
                            "<b>Тикер:</b> <code>%s</code>\n" +
                            "<b>Номинал:</b> %s %s\n" +
                            "<b>Текущая цена:</b> <b>%.2f%%</b> (~%s %s)\n" +
                            "<b>Купонная ставка:</b> %.2f%% годовых\n" +
                            "<b>Доходность к погашению (YTM):</b> <b>%.2f%%</b>\n" +
                            "<b>Дата погашения:</b> %s (осталось: %s)\n" +
                            "<b>Объем торгов сегодня:</b> %s ₸\n\n" +
                            "🧮 <i>Рассчитать свой доход:</i> <code>/calc %s 500000</code>\n" +
                            "<a href=\"https://kase.kz/ru/bonds/show/%s/\">Открыть страницу на KASE</a>",
                            escapeHtml(name),
                            escapeHtml(issuer),
                            ticker,
                            formatMoney(faceVal), cur,
                            price.doubleValue(), formatMoney(buyPrice), cur,
                            couponRate != null ? couponRate.doubleValue() : 0.0,
                            ytm != null ? ytm.doubleValue() : 0.0,
                            matDate,
                            formatDuration(dtm),
                            formatMoney(volKzt),
                            ticker,
                            ticker
                    );
                    sendMessage(chatId, msg, null);
                })
                .subscribe();
    }

    private void handleCalcCommand(Long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 3) {
            sendMessage(chatId, "🧮 <b>Калькулятор доходности</b>\n\n" +
                    "Формат команды:\n<code>/calc ТИКЕР СУММА</code>\n\n" +
                    "<b>Пример:</b>\n" +
                    "<code>/calc BIDBb5 500000</code> — расчет дохода от вложения 500 000 ₸ в облигацию BI Group.", null);
            return;
        }

        String rawTicker = parts[1].trim();
        double amount;
        try {
            amount = Double.parseDouble(parts[2].replace(",", ".").replace(" ", ""));
        } catch (Exception e) {
            sendMessage(chatId, "Сумма введена некорректно. Пример: <code>/calc " + rawTicker + " 500000</code>", null);
            return;
        }

        analyticsService.getInstrumentDetailByCode(rawTicker)
                .doOnSuccess(b -> {
                    if (b == null) {
                        sendMessage(chatId, "Облигация <code>" + rawTicker + "</code> не найдена.", null);
                        return;
                    }

                    BigDecimal faceVal = b.getFaceValue() != null && b.getFaceValue().compareTo(BigDecimal.ZERO) > 0 ? b.getFaceValue() : BigDecimal.valueOf(1000);
                    BigDecimal pricePct = (b.getInstrument() != null && b.getInstrument().getPrice() != null && b.getInstrument().getPrice().compareTo(BigDecimal.ZERO) > 0)
                            ? b.getInstrument().getPrice() : BigDecimal.valueOf(100);
                    BigDecimal pricePerBond = faceVal.multiply(pricePct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                    if (pricePerBond.compareTo(BigDecimal.ZERO) <= 0) {
                        sendMessage(chatId, "Невозможно рассчитать: нет рыночной цены по бумаге.", null);
                        return;
                    }

                    long count = (long) (amount / pricePerBond.doubleValue());
                    if (count <= 0) {
                        sendMessage(chatId, String.format("Суммы %.0f ₸ недостаточно для покупки хотя бы 1 облигации (стоимость 1 шт: %s ₸).",
                                amount, formatMoney(pricePerBond)), null);
                        return;
                    }

                    BigDecimal totalInvested = pricePerBond.multiply(BigDecimal.valueOf(count));
                    BigDecimal totalNominal = faceVal.multiply(BigDecimal.valueOf(count));
                    BigDecimal capitalGain = totalNominal.subtract(totalInvested);

                    BigDecimal couponRate = null;
                    if (b.getTicker() != null) {
                        couponRate = b.getTicker().getCupon() != null ? b.getTicker().getCupon() : b.getTicker().getCupon2();
                    }
                    double couponRateVal = couponRate != null ? couponRate.doubleValue() : 0.0;
                    double annualCouponIncome = totalNominal.doubleValue() * (couponRateVal / 100.0);
                    double quarterlyPayout = annualCouponIncome / 4.0;

                    int dtm = (b.getInstrument() != null && b.getInstrument().getDtm() != null) ? b.getInstrument().getDtm() : 365;
                    double years = Math.max(0.1, dtm / 365.25);
                    double totalCouponsAllTime = annualCouponIncome * years;
                    double grandTotalReturn = capitalGain.doubleValue() + totalCouponsAllTime;
                    double roiPercent = (grandTotalReturn / totalInvested.doubleValue()) * 100.0;

                    String name = (b.getInstrument() != null && b.getInstrument().getOrgShortNameRu() != null)
                            ? b.getInstrument().getOrgShortNameRu()
                            : (b.getInstrument() != null ? b.getInstrument().getOrgNameRu() : rawTicker);
                    String cur = b.getResolvedCurrency() != null ? b.getResolvedCurrency() : "KZT";

                    String res = String.format(
                            "🧮 <b>Расчет инвестиций в %s (<code>%s</code>):</b>\n\n" +
                            "• <b>Сумма инвестиций:</b> %s %s\n" +
                            "• <b>Будет куплено:</b> <b>%d шт.</b> (по цене ~%s %s за шт.)\n" +
                            "• <b>Реально затрачено:</b> %s %s\n\n" +
                            "💰 <b>Выплаты купонов:</b>\n" +
                            "• В квартал (каждые 3 мес): ~<b>%s %s</b>\n" +
                            "• В год: ~<b>%s %s</b>\n" +
                            "• Всего купонами за весь срок: ~<b>%s %s</b>\n\n" +
                            "🏦 <b>Возврат номинала при погашении:</b>\n" +
                            "• Эмитент вернет: <b>%s %s</b>\n" +
                            "• Прибыль на росте цены (скидка): <b>+%s %s</b>\n\n" +
                            "🎯 <b>ИТОГОВЫЙ ДОХОД:</b>\n" +
                            "• Чистая прибыль: <b>+%s %s</b> (<b>+%.1f%%</b> к вложениям)\n" +
                            "• Доходность годовых (YTM): <b>%.2f%%</b>\n" +
                            "• Срок до возврата капитала: %s",
                            escapeHtml(name), (b.getInstrument() != null && b.getInstrument().getCode() != null ? b.getInstrument().getCode() : rawTicker),
                            formatMoney(BigDecimal.valueOf(amount)), cur,
                            count, formatMoney(pricePerBond), cur,
                            formatMoney(totalInvested), cur,
                            formatMoney(BigDecimal.valueOf(quarterlyPayout)), cur,
                            formatMoney(BigDecimal.valueOf(annualCouponIncome)), cur,
                            formatMoney(BigDecimal.valueOf(totalCouponsAllTime)), cur,
                            formatMoney(totalNominal), cur,
                            formatMoney(capitalGain), cur,
                            formatMoney(BigDecimal.valueOf(grandTotalReturn)), cur,
                            roiPercent,
                            b.getEffectiveYield() != null ? b.getEffectiveYield().doubleValue() : couponRateVal,
                            formatDuration(dtm)
                    );
                    sendMessage(chatId, res, null);
                })
                .subscribe();
    }

    private void handleTrackCommand(Long chatId, String text, boolean add) {
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "Укажите тикер акции. Пример: <code>" + (add ? "/track KSPI" : "/untrack KSPI") + "</code>", null);
            return;
        }
        String ticker = parts[1].toUpperCase();

        subscriberRepository.findById(chatId)
                .flatMap(sub -> {
                    String cur = sub.getWatchlist() != null ? sub.getWatchlist() : "";
                    List<String> list = new ArrayList<>(Arrays.stream(cur.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList());

                    if (add) {
                        if (!list.contains(ticker)) list.add(ticker);
                    } else {
                        list.remove(ticker);
                    }

                    sub.setWatchlist(String.join(",", list));
                    sub.setUpdatedAt(LocalDateTime.now());
                    return subscriberRepository.save(sub);
                })
                .doOnSuccess(sub -> {
                    if (add) {
                        sendMessage(chatId, "⭐ Акция <code>" + ticker + "</code> добавлена в ваш вотчлист!\nТекущий список: " + sub.getWatchlist(), null);
                    } else {
                        sendMessage(chatId, "❌ Акция <code>" + ticker + "</code> удалена из вотчлиста.\nТекущий список: " + sub.getWatchlist(), null);
                    }
                })
                .subscribe(
                        sub -> log.debug("Watchlist updated for chatId {}: {}", chatId, sub != null ? sub.getWatchlist() : ""),
                        e -> log.error("Failed to update watchlist for chatId {}: {}", chatId, e.getMessage())
                );
    }

    private void sendWatchlist(Long chatId) {
        subscriberRepository.findById(chatId)
                .doOnSuccess(sub -> {
                    String wl = (sub != null && sub.getWatchlist() != null && !sub.getWatchlist().isBlank())
                            ? sub.getWatchlist()
                            : "KSPI,HSBK,KZAP,AIRA,KMGZ";
                    sendMessage(chatId, "📋 <b>Ваш список отслеживаемых акций:</b>\n<code>" + wl + "</code>\n\n" +
                            "• Добавить: <code>/track ТИКЕР</code>\n" +
                            "• Удалить: <code>/untrack ТИКЕР</code>", null);
                })
                .subscribe();
    }

    private void sendAixSummary(Long chatId) {
        aixService.getInstruments(null, null, null, 1000)
                .doOnSuccess(list -> {
                    if (list == null || list.isEmpty()) {
                        sendMessage(chatId, "Данные AIX временно недоступны или синхронизируются.", null);
                        return;
                    }
                    long total = list.size();
                    long equities = list.stream().filter(i -> "Equity".equalsIgnoreCase(i.getAssetClass()) || "Equities".equalsIgnoreCase(i.getAssetClass())).count();
                    long debt = list.stream().filter(i -> "Debt".equalsIgnoreCase(i.getAssetClass()) || "Bonds".equalsIgnoreCase(i.getAssetClass())).count();
                    long etfs = list.stream().filter(i -> i.getNav() != null && !i.getNav().isBlank()).count();

                    List<String> keySymbols = List.of("KAP", "KSPI", "HSBK", "AIRA", "KMGZ");
                    List<kz.nurgissa.kasestockexchangeparser.model.dtos.AixInstrumentDto> keyItems = list.stream()
                            .filter(i -> keySymbols.contains(i.getSecCode()))
                            .toList();

                    StringBuilder sb = new StringBuilder();
                    sb.append("🏛️ <b>Биржа AIX (Astana International Exchange)</b>\n\n");
                    sb.append(String.format("Всего инструментов в каталоге: <b>%d</b>\n", total));
                    sb.append(String.format("• 📈 Акции: <b>%d</b>\n", equities));
                    sb.append(String.format("• 📑 Облигации и сукук: <b>%d</b>\n", debt));
                    sb.append(String.format("• 🧺 ETF и ETN фонды: <b>%d</b>\n\n", etfs));
                    sb.append("<b>Ключевые бумаги AIX:</b>\n");

                    for (kz.nurgissa.kasestockexchangeparser.model.dtos.AixInstrumentDto item : keyItems) {
                        BigDecimal pr = item.getLastTrade() != null ? item.getLastTrade() : item.getReferencePrice();
                        String prStr = pr != null ? formatMoney(pr) + " " + (item.getCurrency() != null ? item.getCurrency() : "") : "—";
                        String chgStr = item.getPercentChange() != null ? String.format("%+.2f%%", item.getPercentChange()) : "0%";
                        sb.append(String.format("• <b>%s</b> (<code>%s</code>): <b>%s</b> (%s)\n",
                                escapeHtml(item.getName() != null ? item.getName() : item.getSecCode()),
                                item.getSecCode(), prStr, chgStr));
                    }

                    sb.append("\n<b>Полезные команды AIX:</b>\n");
                    sb.append("• <code>/depth KAP</code> — биржевой стакан (Level-2 Order Book)\n");
                    sb.append("• <code>/compare KZAP</code> — арбитраж с KASE (разница цен)\n");
                    sb.append("• <code>/arbitrage</code> — все возможности арбитража KASE ⇄ AIX\n");

                    sendMessage(chatId, sb.toString(), null);
                })
                .subscribe();
    }

    private void handleMarketDepthCommand(Long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sendMessage(chatId, "📊 <b>Биржевой стакан котировок AIX (Level-2)</b>\n\n" +
                    "Формат команды: <code>/depth СИМВОЛ</code>\n" +
                    "Примеры:\n" +
                    "• <code>/depth KAP</code> (Казатомпром)\n" +
                    "• <code>/depth KSPI</code> (Kaspi.kz)\n" +
                    "• <code>/depth AIRA</code> (Air Astana)", null);
            return;
        }
        String symbol = parts[1].toUpperCase();

        aixService.getMarketDepth(symbol)
                .doOnSuccess(depth -> {
                    if (depth == null || ((depth.getBidRows() == null || depth.getBidRows().isEmpty())
                            && (depth.getOfferRows() == null || depth.getOfferRows().isEmpty()))) {
                        sendMessage(chatId, "Стакан котировок для <code>" + symbol + "</code> на AIX в данный момент пуст или инструмент не найден.", null);
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("📊 <b>Стакан котировок AIX (Level-2): <code>").append(symbol).append("</code></b>\n");
                    if (depth.getIsin() != null) {
                        sb.append("ISIN: <code>").append(depth.getIsin()).append("</code>\n");
                    }
                    sb.append("━━━━━━━━━━━━━━━━━━━\n");

                    // Offers (Asks)
                    sb.append("🔴 <b>ПРОДАЖА (OFFERS/ASKS):</b>\n");
                    if (depth.getOfferRows() != null && !depth.getOfferRows().isEmpty()) {
                        int count = 0;
                        for (kz.nurgissa.kasestockexchangeparser.model.dtos.AixMarketDepthDto.OrderRowDto o : depth.getOfferRows()) {
                            if (++count > 5) break;
                            sb.append(String.format("  %s шт. по <b>%s</b>\n",
                                    formatMoney(BigDecimal.valueOf(o.getVolume())),
                                    o.getPrice() != null ? o.getPrice().toPlainString() : "—"));
                        }
                    } else {
                        sb.append("  (заявок нет)\n");
                    }

                    sb.append("───────────────────\n");

                    // Bids
                    sb.append("🟢 <b>ПОКУПКА (BIDS):</b>\n");
                    if (depth.getBidRows() != null && !depth.getBidRows().isEmpty()) {
                        int count = 0;
                        for (kz.nurgissa.kasestockexchangeparser.model.dtos.AixMarketDepthDto.OrderRowDto b : depth.getBidRows()) {
                            if (++count > 5) break;
                            sb.append(String.format("  %s шт. по <b>%s</b>\n",
                                    formatMoney(BigDecimal.valueOf(b.getVolume())),
                                    b.getPrice() != null ? b.getPrice().toPlainString() : "—"));
                        }
                    } else {
                        sb.append("  (заявок нет)\n");
                    }

                    sb.append("━━━━━━━━━━━━━━━━━━━\n");
                    sb.append("💡 <i>Обновляется в реальном времени с биржи AIX</i>\n");
                    sb.append("Для сравнения с KASE введите: <code>/compare ").append(symbol).append("</code>");

                    sendMessage(chatId, sb.toString(), null);
                })
                .subscribe();
    }

    private void handleArbitrageCommand(Long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length >= 2) {
            String ticker = parts[1].toUpperCase();
            aixService.getArbitrageByTicker(ticker)
                    .doOnSuccess(arb -> {
                        if (arb == null) {
                            sendMessage(chatId, "Бумага <code>" + ticker + "</code> не найдена в списке кросс-листинга KASE ⇄ AIX или по ней нет сопоставимых цен.", null);
                            return;
                        }
                        sendSingleArbitrage(chatId, arb);
                    })
                    .subscribe();
            return;
        }

        aixService.getArbitrageOpportunities()
                .doOnSuccess(list -> {
                    if (list == null || list.isEmpty()) {
                        sendMessage(chatId, "В данный момент нет доступных данных по кросс-листингу KASE / AIX.", null);
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("⚖️ <b>Арбитражный радар KASE ⇄ AIX</b>\n");
                    sb.append("Сравнение цен на одни и те же ценные бумаги (по ISIN):\n\n");

                    for (kz.nurgissa.kasestockexchangeparser.model.dtos.ArbitrageItemDto arb : list) {
                        String kasePr = arb.getKasePrice() != null ? formatMoney(arb.getKasePrice()) : "—";
                        String aixPr = arb.getAixPrice() != null ? formatMoney(arb.getAixPrice()) : "—";
                        String cur = arb.getCurrency() != null ? arb.getCurrency() : "₸";
                        String spread = arb.getSpreadPercent() != null ? arb.getSpreadPercent().setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";

                        sb.append(String.format("🔹 <b>%s</b>\n", escapeHtml(arb.getCompanyName())));
                        sb.append(String.format("• KASE (<code>%s</code>): <b>%s %s</b>\n", arb.getKaseCode(), kasePr, cur));
                        sb.append(String.format("• AIX (<code>%s</code>): <b>%s %s</b>\n", arb.getAixCode(), aixPr, cur));
                        sb.append(String.format("• Разница: <b>%s%%</b> (%s %s)\n", spread, arb.getSpreadAbs() != null ? arb.getSpreadAbs().setScale(2, RoundingMode.HALF_UP).toPlainString() : "0", cur));
                        if (arb.getRecommendation() != null && !arb.getRecommendation().isBlank()) {
                            sb.append(String.format("👉 <i>%s</i>\n", escapeHtml(arb.getRecommendation())));
                        }
                        sb.append("\n");
                    }

                    sb.append("ℹ️ <i>Для просмотра стакана котировок введите:</i> <code>/depth ТИКЕР</code>");
                    sendMessage(chatId, sb.toString(), null);
                })
                .subscribe();
    }

    private void sendSingleArbitrage(Long chatId, kz.nurgissa.kasestockexchangeparser.model.dtos.ArbitrageItemDto arb) {
        String kasePr = arb.getKasePrice() != null ? formatMoney(arb.getKasePrice()) : "—";
        String aixPr = arb.getAixPrice() != null ? formatMoney(arb.getAixPrice()) : "—";
        String cur = arb.getCurrency() != null ? arb.getCurrency() : "₸";
        String spread = arb.getSpreadPercent() != null ? arb.getSpreadPercent().setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";

        String msg = String.format(
                "⚖️ <b>Сравнение цен: %s</b>\n" +
                "ISIN: <code>%s</code>\n\n" +
                "🏛️ <b>KASE:</b>\n" +
                "• Код: <code>%s</code>\n" +
                "• Цена: <b>%s %s</b>\n\n" +
                "🏛️ <b>AIX:</b>\n" +
                "• Код: <code>%s</code>\n" +
                "• Цена: <b>%s %s</b>\n\n" +
                "📊 <b>Спред:</b> <b>%s%%</b> (%s %s)\n" +
                "💡 <b>Рекомендация:</b> %s\n\n" +
                "Посмотреть стакан AIX: <code>/depth %s</code>",
                escapeHtml(arb.getCompanyName()),
                arb.getIsin(),
                arb.getKaseCode(), kasePr, cur,
                arb.getAixCode(), aixPr, cur,
                spread, arb.getSpreadAbs() != null ? arb.getSpreadAbs().setScale(2, RoundingMode.HALF_UP).toPlainString() : "0", cur,
                escapeHtml(arb.getRecommendation() != null ? arb.getRecommendation() : "Цены на обеих площадках практически равны"),
                arb.getAixCode()
        );
        sendMessage(chatId, msg, null);
    }

    private void sendHelp(Long chatId) {
        String help = """
                ℹ️ <b>Справка по командам KASE & AIX Radar:</b>
                
                • <code>/discounts</code> — облигации с дисконтом (≤ 95% от номинала)
                • <code>/top</code> — самые доходные корпоративные облигации в тенге
                • <code>/stocks</code> — текущие котировки главных акций KASE
                • <code>/aix</code> — обзор инструментов, акций и ETF на бирже AIX
                • <code>/depth &lt;ТИКЕР&gt;</code> — биржевой стакан AIX (например: <code>/depth KAP</code>)
                • <code>/arbitrage</code> — межбиржевой арбитраж цен KASE ⇄ AIX
                • <code>/compare &lt;ТИКЕР&gt;</code> — сравнение цен акции на KASE и AIX (например: <code>/compare KZAP</code>)
                • <code>/bond &lt;ТИКЕР&gt;</code> — подробная карточка облигации (например: <code>/bond BIDBb5</code>)
                • <code>/stock &lt;ТИКЕР&gt;</code> — карточка акции (например: <code>/stock KSPI</code>)
                • <code>/calc &lt;ТИКЕР&gt; &lt;СУММА&gt;</code> — калькулятор купонов и прибыли (например: <code>/calc BIDBb5 500000</code>)
                • <code>/track &lt;ТИКЕР&gt;</code> — добавить акцию в персональный вотчлист
                • <code>/untrack &lt;ТИКЕР&gt;</code> — удалить акцию из вотчлиста
                • <code>/settings</code> — управление категориями подписок
                """;
        sendMessage(chatId, help, buildMainMenuKeyboard());
    }

    private void sendMessage(Long chatId, String text, org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard keyboard) {
        try {
            SendMessage.SendMessageBuilder smb = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("HTML")
                    .disableWebPagePreview(true);
            if (keyboard != null) {
                smb.replyMarkup(keyboard);
            }
            telegramClient.execute(smb.build());
        } catch (Exception ex) {
            log.error("Failed to send Telegram message to chatId {}: {}", chatId, ex.getMessage());
        }
    }

    private ReplyKeyboardMarkup buildMainMenuKeyboard(Integer activeCount) {
        String settingsLabel = (activeCount != null)
                ? String.format("⚙️ Подписки (%d/5)", activeCount)
                : "⚙️ Мои подписки";

        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow("📉 Скидки (<95%)", "🏆 Топ доходностей"))
                .keyboardRow(new KeyboardRow("📈 Акции KASE", "🏛️ Биржа AIX"))
                .keyboardRow(new KeyboardRow("⚖️ Арбитраж KASE/AIX", "🧮 Калькулятор"))
                .keyboardRow(new KeyboardRow(settingsLabel, "ℹ️ О боте"))
                .resizeKeyboard(true)
                .isPersistent(true)
                .build();
    }

    private ReplyKeyboardMarkup buildMainMenuKeyboard() {
        return buildMainMenuKeyboard(null);
    }

    private InlineKeyboardMarkup buildSettingsInlineKeyboard(TelegramSubscriberEntity sub, SubscriberStatsDto stats) {
        String newBondsIcon = Boolean.TRUE.equals(sub.getSubNewBonds()) ? "✅" : "❌";
        String discountsIcon = Boolean.TRUE.equals(sub.getSubDiscounts()) ? "✅" : "❌";
        String whalesIcon = Boolean.TRUE.equals(sub.getSubWhales()) ? "✅" : "❌";
        String couponsIcon = Boolean.TRUE.equals(sub.getSubCoupons()) ? "✅" : "❌";
        String stocksIcon = Boolean.TRUE.equals(sub.getSubStocks()) ? "✅" : "❌";

        long nb = stats != null ? stats.newBondsCount() : 0;
        long disc = stats != null ? stats.discountsCount() : 0;
        long wh = stats != null ? stats.whalesCount() : 0;
        long coup = stats != null ? stats.couponsCount() : 0;
        long st = stats != null ? stats.stocksCount() : 0;
        int wlCount = calculateWatchlistCount(sub);

        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text(String.format("%s Новые облигации (%d)", newBondsIcon, nb)).callbackData("TOGGLE_NEW_BONDS").build(),
                        InlineKeyboardButton.builder().text(String.format("%s Скидки <95%% (%d)", discountsIcon, disc)).callbackData("TOGGLE_DISCOUNTS").build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text(String.format("%s Крупные сделки (%d)", whalesIcon, wh)).callbackData("TOGGLE_WHALES").build(),
                        InlineKeyboardButton.builder().text(String.format("%s Выплаты купонов (%d)", couponsIcon, coup)).callbackData("TOGGLE_COUPONS").build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text(String.format("%s Акции KASE (%d)", stocksIcon, st)).callbackData("TOGGLE_STOCKS").build(),
                        InlineKeyboardButton.builder().text(String.format("⭐ Вотчлист (%d)", wlCount)).callbackData("VIEW_WATCHLIST").build()
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
