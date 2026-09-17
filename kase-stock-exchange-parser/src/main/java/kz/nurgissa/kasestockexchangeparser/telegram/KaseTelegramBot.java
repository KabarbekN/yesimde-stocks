package kz.nurgissa.kasestockexchangeparser.telegram;

import kz.nurgissa.kasestockexchangeparser.model.dtos.AixInstrumentDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.BondItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.InstrumentDetailDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.StockItemDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.SubscriberStatsDto;
import kz.nurgissa.kasestockexchangeparser.model.dtos.TechnicalAnalysisDto;
import kz.nurgissa.kasestockexchangeparser.model.entities.PriceAlertTargetEntity;
import kz.nurgissa.kasestockexchangeparser.model.entities.TelegramSubscriberEntity;
import kz.nurgissa.kasestockexchangeparser.repositories.PriceAlertTargetRepository;
import kz.nurgissa.kasestockexchangeparser.repositories.TelegramSubscriberRepository;
import kz.nurgissa.kasestockexchangeparser.service.BondAnalyticsService;
import kz.nurgissa.kasestockexchangeparser.service.TechnicalAnalysisService;
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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
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
    private final TechnicalAnalysisService technicalAnalysisService;
    private final PriceAlertTargetRepository priceAlertTargetRepository;
    private final DatabaseClient databaseClient;

    public KaseTelegramBot(
            @Value("${telegram.bot.token}") String botToken,
            TelegramSubscriberRepository subscriberRepository,
            BondAnalyticsService analyticsService,
            kz.nurgissa.kasestockexchangeparser.service.AixService aixService,
            TechnicalAnalysisService technicalAnalysisService,
            PriceAlertTargetRepository priceAlertTargetRepository,
            DatabaseClient databaseClient
    ) {
        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.subscriberRepository = subscriberRepository;
        this.analyticsService = analyticsService;
        this.aixService = aixService;
        this.technicalAnalysisService = technicalAnalysisService;
        this.priceAlertTargetRepository = priceAlertTargetRepository;
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

    @EventListener(ApplicationReadyEvent.class)
    public void registerBotCommands() {
        try {
            List<BotCommand> commands = List.of(
                    BotCommand.builder().command("pulse").description("⚡ Пульс рынка KASE и AIX").build(),
                    BotCommand.builder().command("ta").description("📊 Теханализ и RSI (напр. /ta KSPI)").build(),
                    BotCommand.builder().command("alert").description("🔔 Поставить лимит-алерт цены").build(),
                    BotCommand.builder().command("my_alerts").description("📋 Мои активные алерты").build(),
                    BotCommand.builder().command("threshold").description("⚙️ Настроить порог алертов").build(),
                    BotCommand.builder().command("stocks").description("📈 Акции KASE и котировки").build(),
                    BotCommand.builder().command("discounts").description("📉 Облигации со скидкой (<95%)").build(),
                    BotCommand.builder().command("top").description("🏆 Топ доходностей облигаций").build(),
                    BotCommand.builder().command("arbitrage").description("⚖️ Арбитраж цен KASE ⇄ AIX").build(),
                    BotCommand.builder().command("aix").description("🏛️ Биржа AIX (акции, стакан, ETF)").build(),
                    BotCommand.builder().command("depth").description("📖 Биржевой стакан (напр. /depth KAP)").build(),
                    BotCommand.builder().command("calc").description("🧮 Калькулятор дохода облигации").build(),
                    BotCommand.builder().command("bond").description("📄 Карточка облигации").build(),
                    BotCommand.builder().command("stock").description("📊 Карточка акции").build(),
                    BotCommand.builder().command("settings").description("⚙️ Управление подписками").build(),
                    BotCommand.builder().command("help").description("ℹ️ Справка по возможностям").build()
            );
            telegramClient.execute(SetMyCommands.builder().commands(commands).build());
            log.info("Successfully registered {} Telegram bot commands.", commands.size());
        } catch (Exception e) {
            log.warn("Failed to register bot commands via Telegram API: {}", e.getMessage());
        }
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            } else if (update.hasInlineQuery()) {
                handleInlineQuery(update.getInlineQuery());
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
        } else if (text.equals("⚡ Пульс рынка") || text.startsWith("/pulse") || text.startsWith("/market")) {
            sendMarketPulse(chatId);
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
        } else if (text.startsWith("/ta") || text.startsWith("/rsi")) {
            handleTechnicalAnalysisCommand(chatId, text);
        } else if (text.startsWith("/alert")) {
            handleAlertCommand(chatId, text);
        } else if (text.equals("🔔 Мои алерты") || text.startsWith("/my_alerts") || text.equalsIgnoreCase("мои алерты") || text.equalsIgnoreCase("алерты")) {
            sendMyAlerts(chatId);
        } else if (text.startsWith("/threshold") || text.startsWith("/move")) {
            handleThresholdCommand(chatId, text);
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
            // Natural language intent detection or quick ticker lookup
            if (isNaturalLanguageTaQuery(text)) {
                handleNaturalLanguageTaQuery(chatId, text);
            } else if (text.toLowerCase().contains("пульс")) {
                sendMarketPulse(chatId);
            } else if (text.length() <= 10 && !text.contains(" ")) {
                handleQuickTickerLookup(chatId, text.toUpperCase());
            } else {
                sendMessage(chatId, "Команда не распознана. Используйте кнопки меню ниже или введите тикер бумаги (например: <code>KZTKb3</code>, <code>KSPI</code>, <code>/ta AIRA</code> или <code>/pulse</code>).", buildMainMenuKeyboard());
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
            String rest = data.substring("CALC_".length());
            String ticker;
            String amount = "500000";
            if (rest.contains("_")) {
                int idx = rest.lastIndexOf('_');
                ticker = rest.substring(0, idx);
                amount = rest.substring(idx + 1);
            } else {
                ticker = rest;
            }
            handleCalcCommand(chatId, "/calc " + ticker + " " + amount);
        } else if (data.startsWith("BOND_")) {
            String ticker = data.substring("BOND_".length());
            handleBondCommand(chatId, "/bond " + ticker);
        } else if (data.startsWith("STOCK_")) {
            String ticker = data.substring("STOCK_".length());
            handleStockCommand(chatId, "/stock " + ticker);
        } else if (data.startsWith("TRACK_")) {
            String ticker = data.substring("TRACK_".length());
            handleTrackCommand(chatId, "/track " + ticker, true);
        } else if (data.equals("VIEW_WATCHLIST")) {
            sendWatchlist(chatId);
        } else if (data.startsWith("TA_")) {
            String ticker = data.substring("TA_".length());
            sendTechnicalAnalysis(chatId, ticker);
        } else if (data.startsWith("COMPARE_")) {
            String ticker = data.substring("COMPARE_".length());
            handleArbitrageCommand(chatId, "/compare " + ticker);
        } else if (data.startsWith("SET_ALERT_FIXED_")) {
            String rest = data.substring("SET_ALERT_FIXED_".length());
            int idx = rest.lastIndexOf('_');
            if (idx > 0) {
                String ticker = rest.substring(0, idx);
                String price = rest.substring(idx + 1);
                handleAlertCommand(chatId, "/alert " + ticker + " " + price);
            }
        } else if (data.startsWith("SET_ALERT_")) {
            String ticker = data.substring("SET_ALERT_".length());
            promptSetAlert(chatId, ticker);
        } else if (data.startsWith("SET_THRESH_")) {
            String val = data.substring("SET_THRESH_".length());
            handleThresholdCommand(chatId, "/threshold " + val);
        } else if (data.startsWith("DEL_ALERT_")) {
            try {
                Long alertId = Long.parseLong(data.substring("DEL_ALERT_".length()));
                priceAlertTargetRepository.deleteByIdAndChatId(alertId, chatId)
                        .doOnSuccess(v -> {
                            sendMessage(chatId, "✅ Лимит-уведомление успешно удалено.", null);
                            sendMyAlerts(chatId);
                        })
                        .subscribe(null, e -> log.error("Failed to delete alert: {}", e.getMessage()));
            } catch (Exception e) {
                log.error("Failed to parse alert ID: {}", e.getMessage());
            }
        } else if (data.equals("PULSE")) {
            sendMarketPulse(chatId);
        } else if (data.equals("MY_ALERTS")) {
            sendMyAlerts(chatId);
        } else if (data.equals("ARBITRAGE")) {
            handleArbitrageCommand(chatId, "/arbitrage");
        } else if (data.startsWith("DEPTH_")) {
            String ticker = data.substring("DEPTH_".length());
            handleMarketDepthCommand(chatId, "/depth " + ticker);
        }
    }

    private void promptSetAlert(Long chatId, String ticker) {
        technicalAnalysisService.analyzeInstrument(ticker)
                .doOnSuccess(ta -> {
                    BigDecimal curPrice = (ta != null && ta.getCurrentPrice() != null) ? ta.getCurrentPrice() : null;
                    String curStr = (ta != null && ta.getCurrency() != null) ? ta.getCurrency() : "₸";
                    String name = (ta != null && ta.getName() != null) ? ta.getName() : ticker;

                    if (curPrice == null || curPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder().text("✏️ Задать цену алерта").switchInlineQueryCurrentChat("/alert " + ticker + " ").build()
                                ))
                                .build();
                        sendMessage(chatId, "🔔 <b>Установка лимита для " + escapeHtml(name) + " (<code>" + ticker + "</code>)</b>\n\n" +
                                "Для установки лимита отправьте команду с целевой ценой:\n" +
                                "<pre>/alert " + ticker + " ЦЕНА</pre>", kb);
                        return;
                    }

                    BigDecimal pPlus2 = curPrice.multiply(BigDecimal.valueOf(1.02)).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal pPlus5 = curPrice.multiply(BigDecimal.valueOf(1.05)).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal pMinus2 = curPrice.multiply(BigDecimal.valueOf(0.98)).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal pMinus5 = curPrice.multiply(BigDecimal.valueOf(0.95)).setScale(2, RoundingMode.HALF_UP);

                    InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("📈 +2% (" + formatMoney(pPlus2) + ")").callbackData("SET_ALERT_FIXED_" + ticker + "_" + pPlus2.toPlainString()).build(),
                                    InlineKeyboardButton.builder().text("📈 +5% (" + formatMoney(pPlus5) + ")").callbackData("SET_ALERT_FIXED_" + ticker + "_" + pPlus5.toPlainString()).build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("📉 -2% (" + formatMoney(pMinus2) + ")").callbackData("SET_ALERT_FIXED_" + ticker + "_" + pMinus2.toPlainString()).build(),
                                    InlineKeyboardButton.builder().text("📉 -5% (" + formatMoney(pMinus5) + ")").callbackData("SET_ALERT_FIXED_" + ticker + "_" + pMinus5.toPlainString()).build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("✏️ Ввести свою цену вручную").switchInlineQueryCurrentChat("/alert " + ticker + " ").build()
                            ))
                            .build();

                    String msg = String.format(
                            "🔔 <b>Установка лимит-уведомления: %s</b> (<code>%s</code>)\n\n" +
                            "💵 Текущая цена: <b>%s %s</b>\n\n" +
                            "Выберите быстрый уровень (1 нажатие) или скопируйте команду:\n" +
                            "<pre>/alert %s %s</pre>",
                            escapeHtml(name), ticker,
                            formatMoney(curPrice), curStr,
                            ticker, pPlus5.toPlainString()
                    );
                    sendMessage(chatId, msg, kb);
                })
                .subscribe(null, e -> {
                    log.error("Failed to prompt alert for ticker {}: {}", ticker, e.getMessage());
                    sendMessage(chatId, "Для установки лимита отправьте команду:\n<pre>/alert " + ticker + " ЦЕНА</pre>", null);
                });
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
                            "<b>Что умеет бот (100%% бесплатно):</b>\n" +
                            "• ⚡ <b>Пульс рынка</b> — оперативные котировки, дневные диапазоны (High/Low) и индикаторы.\n" +
                            "• 📊 <b>Технический анализ (AI)</b> — расчет RSI(14), SMA(20), зон перекупленности и поддержки.\n" +
                            "• 🔔 <b>Лимит-уведомления</b> — ставьте цели по ценам (<code>/alert KSPI 55000</code>), бот мгновенно уведомит при достижении.\n" +
                            "• 📉 <b>Облигации со скидкой</b> — ловит бумаги ниже номинала (доходность выше рыночной).\n" +
                            "• 🆕 <b>Новые выпуски</b> — сообщает, когда на KASE появляются свежие облигации.\n" +
                            "• ⚖️ <b>Арбитраж KASE ⇄ AIX</b> — находит разницу цен на акции (Казатомпром, Kaspi, Halyk и др.).\n" +
                            "• 🏛️ <b>Биржа AIX</b> — стакан котировок (Level-2 Order Book), ETF и сукук.\n" +
                            "• 🐋 <b>Крупные сделки</b> — отслеживает заходы институциональных фондов (>500 млн ₸).\n" +
                            "• 📅 <b>Купонный дайджест</b> — напоминает по понедельникам, какие купоны выплатят на неделе.\n" +
                            "• 🧮 <b>Калькулятор</b> — наглядно рассчитывает выплаты и прибыль на вложенную сумму.\n\n" +
                            "🔍 <b>Помощь в наборе и поиске:</b>\n" +
                            "• Нажмите <code>/</code> — Telegram покажет подсказки по всем командам.\n" +
                            "• Наберите <code>@KaseRadarBot тикер</code> — живой поиск бумаг KASE & AIX прямо в чате!\n\n" +
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
                    sb.append("💡 <i>При покупке со скидкой эмитент возвращает 100%. Нажмите кнопку ниже для моментального расчета или просмотра:</i>");

                    InlineKeyboardMarkup.InlineKeyboardMarkupBuilder kbBuilder = InlineKeyboardMarkup.builder();
                    int buttonLimit = Math.min(bonds.size(), 4);
                    for (int i = 0; i < buttonLimit; i++) {
                        String code = bonds.get(i).getCode();
                        kbBuilder.keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton.builder().text("🧮 " + code + " (500k ₸)").callbackData("CALC_" + code + "_500000").build(),
                                InlineKeyboardButton.builder().text("📄 " + code).callbackData("BOND_" + code).build()
                        ));
                    }
                    kbBuilder.keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("🔍 Выбрать любую облигацию").switchInlineQueryCurrentChat("").build()
                    ));

                    sendMessage(chatId, sb.toString(), kbBuilder.build());
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
                    sb.append("💡 <i>Нажмите кнопку ниже, чтобы открыть паспорт бумаги или рассчитать доход в 1 нажатие:</i>");

                    InlineKeyboardMarkup.InlineKeyboardMarkupBuilder kbBuilder = InlineKeyboardMarkup.builder();
                    int buttonLimit = Math.min(bonds.size(), 4);
                    for (int i = 0; i < buttonLimit; i++) {
                        String code = bonds.get(i).getCode();
                        kbBuilder.keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton.builder().text("📄 " + code).callbackData("BOND_" + code).build(),
                                InlineKeyboardButton.builder().text("🧮 Расчет " + code).callbackData("CALC_" + code + "_500000").build()
                        ));
                    }
                    kbBuilder.keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("🔍 Поиск облигаций").switchInlineQueryCurrentChat("").build()
                    ));

                    sendMessage(chatId, sb.toString(), kbBuilder.build());
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
                    sb.append("💡 <i>Нажмите на акцию ниже для подробностей, алертов и теханализа:</i>");

                    InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("📊 KSPI").callbackData("STOCK_KSPI").build(),
                                    InlineKeyboardButton.builder().text("📊 HSBK").callbackData("STOCK_HSBK").build(),
                                    InlineKeyboardButton.builder().text("📊 KZAP").callbackData("STOCK_KZAP").build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("📊 AIRA").callbackData("STOCK_AIRA").build(),
                                    InlineKeyboardButton.builder().text("📊 KMGZ").callbackData("STOCK_KMGZ").build(),
                                    InlineKeyboardButton.builder().text("📊 CCBN").callbackData("STOCK_CCBN").build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("🔍 Поиск любой акции").switchInlineQueryCurrentChat("").build()
                            ))
                            .build();

                    sendMessage(chatId, sb.toString(), kb);
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
            InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("📄 BIDBb5").callbackData("BOND_BIDBb5").build(),
                            InlineKeyboardButton.builder().text("📄 KZTKb3").callbackData("BOND_KZTKb3").build(),
                            InlineKeyboardButton.builder().text("📄 TEBNb10").callbackData("BOND_TEBNb10").build()
                    ))
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("🔍 Выбрать облигацию из поиска").switchInlineQueryCurrentChat("").build()
                    ))
                    .build();
            sendMessage(chatId, "📑 <b>Карточка и паспорт облигации</b>\n\n" +
                    "Выберите облигацию ниже (1 нажатие) или скопируйте команду:\n" +
                    "<pre>/bond BIDBb5</pre>", kb);
            return;
        }
        handleQuickTickerLookup(chatId, parts[1].toUpperCase());
    }

    private void handleStockCommand(Long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("📊 KSPI").callbackData("STOCK_KSPI").build(),
                            InlineKeyboardButton.builder().text("📊 HSBK").callbackData("STOCK_HSBK").build(),
                            InlineKeyboardButton.builder().text("📊 KZAP").callbackData("STOCK_KZAP").build()
                    ))
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("📊 AIRA").callbackData("STOCK_AIRA").build(),
                            InlineKeyboardButton.builder().text("📊 KMGZ").callbackData("STOCK_KMGZ").build(),
                            InlineKeyboardButton.builder().text("📊 CCBN").callbackData("STOCK_CCBN").build()
                    ))
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("🔍 Выбрать акцию из поиска").switchInlineQueryCurrentChat("").build()
                    ))
                    .build();
            sendMessage(chatId, "📊 <b>Карточка акции</b>\n\n" +
                    "Выберите акцию ниже (1 нажатие) или скопируйте команду:\n" +
                    "<pre>/stock KSPI</pre>", kb);
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
                    InlineKeyboardMarkup stockKb = InlineKeyboardMarkup.builder()
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("📈 Теханализ RSI/SMA").callbackData("TA_" + s.getCode()).build(),
                                    InlineKeyboardButton.builder().text("🔔 Поставить алерт").callbackData("SET_ALERT_" + s.getCode()).build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("⚖️ KASE vs AIX").callbackData("COMPARE_" + s.getCode()).build(),
                                    InlineKeyboardButton.builder().text("⭐ В вотчлист").callbackData("TRACK_" + s.getCode()).build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("🌐 Открыть на KASE").url("https://kase.kz/ru/shares/show/" + s.getCode() + "/").build()
                            ))
                            .build();
                    sendMessage(chatId, msg, stockKb);
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
                                        InlineKeyboardMarkup stockKb = InlineKeyboardMarkup.builder()
                                                .keyboardRow(new InlineKeyboardRow(
                                                        InlineKeyboardButton.builder().text("📈 Теханализ RSI/SMA").callbackData("TA_" + s.getCode()).build(),
                                                        InlineKeyboardButton.builder().text("🔔 Поставить алерт").callbackData("SET_ALERT_" + s.getCode()).build()
                                                ))
                                                .keyboardRow(new InlineKeyboardRow(
                                                        InlineKeyboardButton.builder().text("⚖️ Сравнить KASE/AIX").callbackData("COMPARE_" + s.getCode()).build(),
                                                        InlineKeyboardButton.builder().text("⭐ В вотчлист").callbackData("TRACK_" + s.getCode()).build()
                                                ))
                                                .keyboardRow(new InlineKeyboardRow(
                                                        InlineKeyboardButton.builder().text("🌐 Смотреть на KASE").url("https://kase.kz/ru/shares/show/" + s.getCode() + "/").build()
                                                ))
                                                .build();
                                        sendMessage(chatId, String.format(
                                                "📊 <b>Акция %s</b> (<code>%s</code>)\n" +
                                                "• Цена: <b>%s %s</b>\n" +
                                                "• Дневной объем: %s ₸\n" +
                                                "<a href=\"https://kase.kz/ru/shares/show/%s/\">Смотреть на KASE</a>",
                                                escapeHtml(s.getName()), s.getCode(),
                                                formatMoney(s.getPrice()), s.getCurrency(),
                                                formatMoney(s.getVolumeKzt()),
                                                s.getCode()
                                        ), stockKb);
                                    } else {
                                        aixService.getInstruments(null, null, ticker, 5)
                                                .doOnSuccess(aixList -> {
                                                    if (aixList != null && !aixList.isEmpty()) {
                                                        kz.nurgissa.kasestockexchangeparser.model.dtos.AixInstrumentDto inst = aixList.get(0);
                                                        BigDecimal pr = inst.getLastTrade() != null ? inst.getLastTrade() : inst.getReferencePrice();
                                                        String cur = inst.getCurrency() != null ? inst.getCurrency() : "";
                                                        InlineKeyboardMarkup aixKb = InlineKeyboardMarkup.builder()
                                                                .keyboardRow(new InlineKeyboardRow(
                                                                        InlineKeyboardButton.builder().text("📊 Стакан котировок AIX").callbackData("DEPTH_" + inst.getSecCode()).build(),
                                                                        InlineKeyboardButton.builder().text("⚖️ Сравнить с KASE").callbackData("COMPARE_" + inst.getSecCode()).build()
                                                                ))
                                                                .keyboardRow(new InlineKeyboardRow(
                                                                        InlineKeyboardButton.builder().text("📈 Теханализ").callbackData("TA_" + inst.getSecCode()).build(),
                                                                        InlineKeyboardButton.builder().text("⭐ В вотчлист").callbackData("TRACK_" + inst.getSecCode()).build()
                                                                ))
                                                                .build();
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
                                                        ), aixKb);
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
                            "🧮 <i>Быстрый расчет дохода:</i> нажмите кнопку ниже или скопируйте:\n" +
                            "<pre>/calc %s 500000</pre>\n" +
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
                    InlineKeyboardMarkup bondKb = InlineKeyboardMarkup.builder()
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("🧮 Рассчитать доход на 500 000 ₸").callbackData("CALC_" + ticker + "_500000").build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("💰 100k ₸").callbackData("CALC_" + ticker + "_100000").build(),
                                    InlineKeyboardButton.builder().text("💰 1 млн ₸").callbackData("CALC_" + ticker + "_1000000").build(),
                                    InlineKeyboardButton.builder().text("💰 5 млн ₸").callbackData("CALC_" + ticker + "_5000000").build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("⭐ В избранное").callbackData("TRACK_" + ticker).build(),
                                    InlineKeyboardButton.builder().text("🌐 Страница KASE").url("https://kase.kz/ru/bonds/show/" + ticker + "/").build()
                            ))
                            .build();
                    sendMessage(chatId, msg, bondKb);
                })
                .subscribe();
    }

    private void handleCalcCommand(Long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 3) {
            InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("🧮 BIDBb5 (500k ₸)").callbackData("CALC_BIDBb5_500000").build(),
                            InlineKeyboardButton.builder().text("🧮 TEBNb10 (500k ₸)").callbackData("CALC_TEBNb10_500000").build()
                    ))
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("🔍 Выбрать облигацию из поиска").switchInlineQueryCurrentChat("").build()
                    ))
                    .build();
            sendMessage(chatId, "🧮 <b>Калькулятор доходности облигаций</b>\n\n" +
                    "Формат команды:\n<pre>/calc ТИКЕР СУММА</pre>\n\n" +
                    "<b>Пример (нажмите кнопку ниже или скопируйте):</b>\n" +
                    "<pre>/calc BIDBb5 500000</pre>\n\n" +
                    "<i>Или нажмите на одну из бумаг ниже для мгновенного расчета:</i>", kb);
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
                    InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("💰 100 000 ₸").callbackData("CALC_" + rawTicker + "_100000").build(),
                                    InlineKeyboardButton.builder().text("💰 500 000 ₸").callbackData("CALC_" + rawTicker + "_500000").build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("💰 1 000 000 ₸").callbackData("CALC_" + rawTicker + "_1000000").build(),
                                    InlineKeyboardButton.builder().text("💰 5 000 000 ₸").callbackData("CALC_" + rawTicker + "_5000000").build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("📄 Паспорт облигации").callbackData("BOND_" + rawTicker).build(),
                                    InlineKeyboardButton.builder().text("🔍 Другая бумага").switchInlineQueryCurrentChat("").build()
                            ))
                            .build();
                    sendMessage(chatId, res, kb);
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
                    InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("🔍 Добавить акцию из поиска").switchInlineQueryCurrentChat("").build()
                            ))
                            .build();
                    sendMessage(chatId, "📋 <b>Ваш список отслеживаемых акций:</b>\n<code>" + wl + "</code>\n\n" +
                            "• Добавить: <code>/track ТИКЕР</code>\n" +
                            "• Удалить: <code>/untrack ТИКЕР</code>", kb);
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
                    long etfs = list.stream().filter(i ->
                            (i.getNav() != null && !i.getNav().isBlank())
                            || "ETF".equalsIgnoreCase(i.getSecurityGroup())
                            || "ETF".equalsIgnoreCase(i.getAssetClass())
                            || "ETN".equalsIgnoreCase(i.getAssetClass())
                            || (i.getInstrument() != null && (i.getInstrument().toUpperCase().contains("ETF") || i.getInstrument().toUpperCase().contains("ETN")))
                    ).count();

                    long equities = list.stream().filter(i ->
                            !((i.getNav() != null && !i.getNav().isBlank()) || "ETF".equalsIgnoreCase(i.getSecurityGroup()) || "ETF".equalsIgnoreCase(i.getAssetClass()))
                            && ("EQTY".equalsIgnoreCase(i.getAssetClass())
                            || "Equity".equalsIgnoreCase(i.getAssetClass())
                            || "Equities".equalsIgnoreCase(i.getAssetClass())
                            || "share".equalsIgnoreCase(i.getAssetClass())
                            || (i.getInstrument() != null && (i.getInstrument().toLowerCase().contains("share")
                                    || i.getInstrument().toLowerCase().contains("gdr")
                                    || i.getInstrument().toLowerCase().contains("ads"))))
                    ).count();

                    long debt = list.stream().filter(i ->
                            !((i.getNav() != null && !i.getNav().isBlank()) || "ETF".equalsIgnoreCase(i.getSecurityGroup()) || "ETF".equalsIgnoreCase(i.getAssetClass()))
                            && ("DEBT".equalsIgnoreCase(i.getAssetClass())
                            || "Debt".equalsIgnoreCase(i.getAssetClass())
                            || "Bonds".equalsIgnoreCase(i.getAssetClass())
                            || (i.getInstrument() != null && (i.getInstrument().toLowerCase().contains("bond")
                                    || i.getInstrument().toLowerCase().contains("sukuk")
                                    || i.getInstrument().toLowerCase().contains("paper"))))
                    ).count();

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
                    sb.append("• <code>/arbitrage</code> — все возможности арбитража KASE ⇄ AIX\n\n");
                    sb.append("💡 <i>Нажмите кнопку ниже для быстрого перехода:</i>");

                    InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("⚖️ Арбитраж KASE ⇄ AIX").callbackData("ARBITRAGE").build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("📊 Стакан KAP").callbackData("DEPTH_KAP").build(),
                                    InlineKeyboardButton.builder().text("📊 Стакан KSPI").callbackData("DEPTH_KSPI").build(),
                                    InlineKeyboardButton.builder().text("📊 Стакан AIRA").callbackData("DEPTH_AIRA").build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("🔍 Поиск по AIX").switchInlineQueryCurrentChat("").build()
                            ))
                            .build();

                    sendMessage(chatId, sb.toString(), kb);
                })
                .subscribe();
    }

    private void handleMarketDepthCommand(Long chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("📊 Стакан KAP").callbackData("DEPTH_KAP").build(),
                            InlineKeyboardButton.builder().text("📊 Стакан KSPI").callbackData("DEPTH_KSPI").build(),
                            InlineKeyboardButton.builder().text("📊 Стакан AIRA").callbackData("DEPTH_AIRA").build()
                    ))
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("🔍 Выбрать другую бумагу AIX").switchInlineQueryCurrentChat("").build()
                    ))
                    .build();
            sendMessage(chatId, "📊 <b>Биржевой стакан котировок AIX (Level-2)</b>\n\n" +
                    "Формат команды:\n<pre>/depth СИМВОЛ</pre>\n\n" +
                    "Примеры (нажмите кнопку ниже или скопируйте):\n" +
                    "<pre>/depth KAP</pre>\n\n" +
                    "<i>Или выберите инструмент в 1 нажатие ниже:</i>", kb);
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

                    sb.append("💡 <i>Нажмите кнопку ниже для просмотра биржевого стакана AIX:</i>");

                    InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("📊 Стакан KAP").callbackData("DEPTH_KAP").build(),
                                    InlineKeyboardButton.builder().text("📊 Стакан KSPI").callbackData("DEPTH_KSPI").build(),
                                    InlineKeyboardButton.builder().text("📊 Стакан AIRA").callbackData("DEPTH_AIRA").build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("🔍 Выбрать бумагу из поиска").switchInlineQueryCurrentChat("").build()
                            ))
                            .build();
                    sendMessage(chatId, sb.toString(), kb);
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
        InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("📊 Стакан AIX (" + arb.getAixCode() + ")").callbackData("DEPTH_" + arb.getAixCode()).build(),
                        InlineKeyboardButton.builder().text("📈 Теханализ KASE").callbackData("TA_" + arb.getKaseCode()).build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("⚖️ Все пары арбитража").callbackData("ARBITRAGE").build()
                ))
                .build();
        sendMessage(chatId, msg, kb);
    }

    private void sendHelp(Long chatId) {
        String help = """
                ℹ️ <b>Справка по командам KASE & AIX Radar:</b>

                ⚡ <b>Пульс и Теханализ (100% Бесплатно):</b>
                • <code>/pulse</code> или <code>⚡ Пульс рынка</code> — оперативный срез цен и дневных диапазонов
                • <code>/ta &lt;ТИКЕР&gt;</code> или <code>/rsi &lt;ТИКЕР&gt;</code> — теханализ (RSI 14, SMA 20, уровни поддержки)
                • Или спросите текстом: <i>«Покажи RSI для HSBK»</i>, <i>«Перекуплен ли AIRA?»</i>, <i>«Анализ KSPI»</i>

                🔔 <b>Лимит-уведомления (Price Alerts):</b>
                • <code>/alert &lt;ТИКЕР&gt; &lt;ЦЕНА&gt;</code> — поставить алерт на цену (напр.: <code>/alert KSPI 55000</code>)
                • <code>/my_alerts</code> — список ваших активных алертов с кнопками отмены
                • <code>/threshold &lt;ПРОЦЕНТ&gt;</code> — настроить порог уведомлений об изменении цен акций (по умолч. ±3.0%)

                📊 <b>Облигации и Рынки:</b>
                • <code>/discounts</code> — облигации с дисконтом (≤ 95% от номинала)
                • <code>/top</code> — самые доходные облигации в тенге
                • <code>/stocks</code> — текущие котировки главных акций KASE
                • <code>/aix</code> — обзор инструментов, акций и ETF на бирже AIX
                • <code>/depth &lt;ТИКЕР&gt;</code> — биржевой стакан AIX (например: <code>/depth KAP</code>)
                • <code>/arbitrage</code> — межбиржевой арбитраж цен KASE ⇄ AIX
                • <code>/compare &lt;ТИКЕР&gt;</code> — сравнение цен акции на KASE и AIX (например: <code>/compare KZAP</code>)
                • <code>/bond &lt;ТИКЕР&gt;</code> — подробная карточка облигации (например: <code>/bond BIDBb5</code>)
                • <code>/stock &lt;ТИКЕР&gt;</code> — карточка акции (например: <code>/stock KSPI</code>)
                • <code>/calc &lt;ТИКЕР&gt; &lt;СУММА&gt;</code> — калькулятор дохода (например: <code>/calc BIDBb5 500000</code>)
                • <code>/track &lt;ТИКЕР&gt;</code> — добавить в персональный вотчлист
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
                .keyboardRow(new KeyboardRow("⚡ Пульс рынка", "📈 Акции KASE"))
                .keyboardRow(new KeyboardRow("📉 Скидки (<95%)", "🏆 Топ доходностей"))
                .keyboardRow(new KeyboardRow("⚖️ Арбитраж KASE/AIX", "🏛️ Биржа AIX"))
                .keyboardRow(new KeyboardRow("🧮 Калькулятор", settingsLabel))
                .keyboardRow(new KeyboardRow("🔔 Мои алерты", "ℹ️ О боте"))
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

    private void sendMarketPulse(Long chatId) {
        technicalAnalysisService.getMarketPulse(null)
                .doOnSuccess(pulseList -> {
                    if (pulseList == null || pulseList.isEmpty()) {
                        sendMessage(chatId, "⚡ Данные пульса рынка в данный момент обновляются...", null);
                        return;
                    }

                    StringBuilder sb = new StringBuilder("⚡ <b>Пульс рынка: KASE & AIX</b>\n");
                    sb.append("<i>Оперативный срез цен и дневной динамики:</i>\n\n");

                    for (TechnicalAnalysisDto item : pulseList) {
                        BigDecimal cur = item.getCurrentPrice();
                        BigDecimal chg = item.getChangePercent();
                        String sign = (chg != null && chg.compareTo(BigDecimal.ZERO) >= 0) ? "+" : "";
                        String icon = (chg != null && chg.compareTo(BigDecimal.ZERO) >= 0) ? "🟢" : "🔴";
                        String chgStr = chg != null ? chg.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
                        String curStr = item.getCurrency() != null ? item.getCurrency() : "₸";

                        String lowStr = item.getDayLow() != null ? formatMoney(item.getDayLow()) : "—";
                        String highStr = item.getDayHigh() != null ? formatMoney(item.getDayHigh()) : "—";

                        String rsiInfo = "";
                        if (item.getRsi14() != null) {
                            String rsiIcon = "OVERBOUGHT".equals(item.getRsiStatus()) ? "🔴 Перекуплен"
                                    : ("OVERSOLD".equals(item.getRsiStatus()) ? "🟢 Перепродан" : "⚖️ Нейтрально");
                            rsiInfo = String.format("\nRSI (14): <b>%.1f</b> (%s)", item.getRsi14().doubleValue(), rsiIcon);
                        }

                        sb.append(String.format(
                                "<b>%s (%s)</b> [%s]\n" +
                                "Текущая цена: <b>%s %s</b> %s %s%s%%\n" +
                                "Диапазон дня: %s — %s %s%s\n" +
                                "────────────────────\n",
                                item.getTicker(),
                                escapeHtml(item.getName()),
                                item.getExchange(),
                                cur != null ? formatMoney(cur) : "—",
                                curStr,
                                icon, sign, chgStr,
                                lowStr, highStr, curStr,
                                rsiInfo
                        ));
                    }

                    sb.append("💡 <i>Детальный теханализ:</i> <code>/ta ТИКЕР</code>\n");
                    sb.append("🔔 <i>Лимит цены:</i> <code>/alert ТИКЕР ЦЕНА</code>");

                    InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("📊 Анализ KSPI").callbackData("TA_KSPI").build(),
                                    InlineKeyboardButton.builder().text("📊 Анализ AIRA").callbackData("TA_AIRA").build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("📊 Анализ HSBK").callbackData("TA_HSBK").build(),
                                    InlineKeyboardButton.builder().text("📊 Анализ KZAP").callbackData("TA_KZAP").build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("🔔 Мои алерты").callbackData("MY_ALERTS").build(),
                                    InlineKeyboardButton.builder().text("⚖️ Арбитраж KASE/AIX").callbackData("ARBITRAGE").build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("🔍 Быстрый поиск любого тикера").switchInlineQueryCurrentChat("").build()
                            ))
                            .build();

                    sendMessage(chatId, sb.toString(), keyboard);
                })
                .subscribe(null, e -> log.error("Error sending market pulse: {}", e.getMessage()));
    }

    private void handleTechnicalAnalysisCommand(Long chatId, String text) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2) {
            InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("📈 KSPI").callbackData("TA_KSPI").build(),
                            InlineKeyboardButton.builder().text("📈 AIRA").callbackData("TA_AIRA").build(),
                            InlineKeyboardButton.builder().text("📈 HSBK").callbackData("TA_HSBK").build()
                    ))
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("📈 KAP (AIX)").callbackData("TA_KAP").build(),
                            InlineKeyboardButton.builder().text("📈 KZAP").callbackData("TA_KZAP").build(),
                            InlineKeyboardButton.builder().text("📈 KMGZ").callbackData("TA_KMGZ").build()
                    ))
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("🔍 Выбрать акцию из поиска").switchInlineQueryCurrentChat("").build()
                    ))
                    .build();
            sendMessage(chatId, "💡 <b>Технический анализ (RSI 14, SMA 20, уровни):</b>\n\n" +
                    "Выберите акцию ниже (1 нажатие) или скопируйте команду:\n" +
                    "<pre>/ta KSPI</pre>\n\n" +
                    "<i>Или напишите в чат: «Анализ AIRA» или «RSI для HSBK»</i>", kb);
            return;
        }
        sendTechnicalAnalysis(chatId, parts[1].trim().toUpperCase());
    }

    private void sendTechnicalAnalysis(Long chatId, String ticker) {
        technicalAnalysisService.analyzeInstrument(ticker)
                .doOnSuccess(ta -> {
                    if (ta == null || ta.getCurrentPrice() == null) {
                        sendMessage(chatId, "Инструмент <code>" + escapeHtml(ticker) + "</code> не найден среди акций KASE или AIX.", null);
                        return;
                    }

                    String sign = (ta.getChangePercent() != null && ta.getChangePercent().compareTo(BigDecimal.ZERO) >= 0) ? "+" : "";
                    String icon = (ta.getChangePercent() != null && ta.getChangePercent().compareTo(BigDecimal.ZERO) >= 0) ? "🟢" : "🔴";
                    String curStr = ta.getCurrency() != null ? ta.getCurrency() : "₸";

                    String rsiVal = ta.getRsi14() != null ? ta.getRsi14().toPlainString() : "Недостаточно свечей";
                    String rsiZone;
                    if ("OVERBOUGHT".equals(ta.getRsiStatus())) {
                        rsiZone = "🔴 <b>Перекупленность (&gt;70)</b> — риск фиксации прибыли";
                    } else if ("OVERSOLD".equals(ta.getRsiStatus())) {
                        rsiZone = "🟢 <b>Перепроданность (&lt;30)</b> — зона потенциального отскока вверх";
                    } else {
                        rsiZone = "⚖️ <b>Нейтральная зона (30–70)</b>";
                    }

                    String smaVal = ta.getSma20() != null ? formatMoney(ta.getSma20()) + " " + curStr : "—";
                    String trendStr;
                    if ("BULLISH".equals(ta.getTrendSignal())) {
                        trendStr = "Бычий 🐂 (цена выше средней)";
                    } else if ("BEARISH".equals(ta.getTrendSignal())) {
                        trendStr = "Медвежий 🐻 (цена ниже средней)";
                    } else {
                        trendStr = "Боковик / Нейтральный ⚖️";
                    }

                    String msg = String.format(
                            "📊 <b>Технический анализ: %s (<code>%s</code>)</b>\n" +
                            "Биржа: <b>%s</b> | Валюта: <b>%s</b>\n\n" +
                            "💰 <b>Котировки:</b>\n" +
                            "• Текущая цена: <b>%s %s</b> (%s %s%s%%)\n" +
                            "• Дневной минимум: %s %s\n" +
                            "• Дневной максимум: %s %s\n\n" +
                            "📐 <b>Индикаторы:</b>\n" +
                            "• <b>RSI (14):</b> <b>%s</b>\n" +
                            "  %s\n" +
                            "• <b>SMA (20 дней):</b> %s\n" +
                            "• <b>Линия тренда:</b> %s\n" +
                            "• <b>Уровень поддержки:</b> %s %s\n" +
                            "• <b>Уровень сопротивления:</b> %s %s\n\n" +
                            "💡 <b>Оценка AI-ассистента:</b>\n%s\n\n" +
                            "🔔 <i>Быстрый лимит цены:</i> нажмите кнопку ниже или скопируйте:\n" +
                            "<pre>/alert %s %s</pre>",
                            escapeHtml(ta.getName()),
                            ta.getTicker(),
                            ta.getExchange(),
                            curStr,
                            formatMoney(ta.getCurrentPrice()), curStr,
                            icon, sign, ta.getChangePercent() != null ? ta.getChangePercent().setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00",
                            formatMoney(ta.getDayLow()), curStr,
                            formatMoney(ta.getDayHigh()), curStr,
                            rsiVal,
                            rsiZone,
                            smaVal,
                            trendStr,
                            formatMoney(ta.getSupportLevel()), curStr,
                            formatMoney(ta.getResistanceLevel()), curStr,
                            ta.getRecommendation() != null ? ta.getRecommendation() : "—",
                            ta.getTicker(),
                            ta.getCurrentPrice().setScale(0, RoundingMode.HALF_UP).toPlainString()
                    );

                    InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("🔔 Поставить лимит").callbackData("SET_ALERT_" + ta.getTicker()).build(),
                                    InlineKeyboardButton.builder().text("⚖️ Сравнить KASE/AIX").callbackData("COMPARE_" + ta.getTicker()).build()
                            ))
                            .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder().text("⭐ В мой вотчлист").callbackData("TRACK_" + ta.getTicker()).build(),
                                    InlineKeyboardButton.builder().text("⚡ Пульс рынка").callbackData("PULSE").build()
                            ))
                            .build();

                    sendMessage(chatId, msg, keyboard);
                })
                .subscribe(null, e -> log.error("Error analyzing instrument {}: {}", ticker, e.getMessage()));
    }

    private boolean isNaturalLanguageTaQuery(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("rsi") || lower.contains("сма") || lower.contains("sma")
                || lower.contains("анализ") || lower.contains("теханализ")
                || lower.contains("перекуплен") || lower.contains("перепродан")
                || lower.contains("сигнал") || lower.contains("тренд")
                || lower.contains("техобзор") || lower.contains("индикатор");
    }

    private void handleNaturalLanguageTaQuery(Long chatId, String text) {
        String ticker = extractTickerFromQuery(text);
        if (ticker != null) {
            sendTechnicalAnalysis(chatId, ticker);
        } else {
            InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("📈 KSPI").callbackData("TA_KSPI").build(),
                            InlineKeyboardButton.builder().text("📈 AIRA").callbackData("TA_AIRA").build(),
                            InlineKeyboardButton.builder().text("📈 HSBK").callbackData("TA_HSBK").build()
                    ))
                    .build();
            sendMessage(chatId, "💡 Чтобы получить технический анализ, выберите акцию ниже или скопируйте команду:\n<pre>/ta KSPI</pre>", kb);
        }
    }

    private String extractTickerFromQuery(String query) {
        List<String> known = List.of(
                "KSPI", "HSBK", "KZAP", "AIRA", "CCBN", "KMGZ", "KZTK", "KEGC", "BCKP", "KAP", "KZTO", "ASBN", "FRHC_KZ"
        );
        String upper = query.toUpperCase();
        for (String k : known) {
            if (upper.contains(k)) {
                return k;
            }
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b([A-Z]{3,8})\\b").matcher(upper);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!candidate.equals("RSI") && !candidate.equals("SMA") && !candidate.equals("KASE") && !candidate.equals("AIX")) {
                return candidate;
            }
        }
        return null;
    }

    private void handleAlertCommand(Long chatId, String text) {
        String clean = text.trim();
        String[] parts = clean.split("\\s+");
        if (parts.length < 3) {
            InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("🔔 Лимит KSPI").callbackData("SET_ALERT_KSPI").build(),
                            InlineKeyboardButton.builder().text("🔔 Лимит HSBK").callbackData("SET_ALERT_HSBK").build(),
                            InlineKeyboardButton.builder().text("🔔 Лимит AIRA").callbackData("SET_ALERT_AIRA").build()
                    ))
                    .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("📋 Мои алерты").callbackData("MY_ALERTS").build(),
                            InlineKeyboardButton.builder().text("🔍 Поиск тикера").switchInlineQueryCurrentChat("").build()
                    ))
                    .build();
            sendMessage(chatId, """
                    🔔 <b>Установка лимит-уведомлений (Price Alerts):</b>

                    Бот пришлет мгновенное push-уведомление, когда цена акции на бирже достигнет вашей цели. <b>Функция 100% бесплатна!</b>

                    Формат:
                    <pre>/alert ТИКЕР ЦЕНА</pre>

                    Пример (нажмите кнопку ниже или скопируйте):
                    <pre>/alert KSPI 55000</pre>

                    <i>Или выберите акцию ниже для установки лимита в 1 нажатие:</i>
                    """, kb);
            return;
        }

        String ticker = parts[1].trim().toUpperCase();
        String priceStr = parts[2].replace(",", ".").replace(">", "").replace("<", "").trim();
        BigDecimal targetPrice;
        try {
            targetPrice = new BigDecimal(priceStr);
            if (targetPrice.compareTo(BigDecimal.ZERO) <= 0) {
                sendMessage(chatId, "Цена должна быть положительным числом.", null);
                return;
            }
        } catch (Exception e) {
            sendMessage(chatId, "Неверный формат цены. Пример: <code>/alert KSPI 55000</code>", null);
            return;
        }

        technicalAnalysisService.analyzeInstrument(ticker)
                .flatMap(ta -> {
                    BigDecimal curPrice = ta.getCurrentPrice();
                    if (curPrice == null) {
                        curPrice = targetPrice;
                    }

                    String direction = targetPrice.compareTo(curPrice) >= 0 ? "ABOVE" : "BELOW";
                    if (clean.contains("<")) {
                        direction = "BELOW";
                    } else if (clean.contains(">")) {
                        direction = "ABOVE";
                    }

                    PriceAlertTargetEntity alert = PriceAlertTargetEntity.builder()
                            .chatId(chatId)
                            .ticker(ticker)
                            .targetPrice(targetPrice)
                            .direction(direction)
                            .initialPrice(curPrice)
                            .isTriggered(false)
                            .createdAt(LocalDateTime.now())
                            .build();

                    final BigDecimal finalCurPrice = curPrice;
                    final String finalDirection = direction;
                    return priceAlertTargetRepository.save(alert)
                            .doOnSuccess(saved -> {
                                String dirText = "ABOVE".equals(finalDirection) ? "росте до / выше" : "падении до / ниже";
                                String icon = "ABOVE".equals(finalDirection) ? "📈" : "📉";
                                BigDecimal diffPct = BigDecimal.ZERO;
                                if (finalCurPrice.compareTo(BigDecimal.ZERO) > 0) {
                                    diffPct = targetPrice.subtract(finalCurPrice).multiply(BigDecimal.valueOf(100)).divide(finalCurPrice, 2, RoundingMode.HALF_UP);
                                }
                                String sign = diffPct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";

                                String msg = String.format(
                                        "🔔 <b>Лимит-уведомление установлено!</b>\n\n" +
                                        "📌 <b>Инструмент:</b> %s (<code>%s</code>)\n" +
                                        "💵 <b>Текущая цена:</b> %s %s\n" +
                                        "🎯 <b>Целевой уровень:</b> <b>%s %s</b> (%s%s%%)\n" +
                                        "📡 <b>Условие:</b> Сработает при <b>%s %s %s</b> %s\n\n" +
                                        "<i>Как только цена на бирже пробьет этот уровень, бот сразу пришлет вам сообщение. Функция абсолютно бесплатна!</i>\n\n" +
                                        "Посмотреть все алерты: <code>/my_alerts</code>",
                                        escapeHtml(ta.getName()),
                                        ticker,
                                        formatMoney(finalCurPrice), ta.getCurrency() != null ? ta.getCurrency() : "₸",
                                        formatMoney(targetPrice), ta.getCurrency() != null ? ta.getCurrency() : "₸",
                                        sign, diffPct.toPlainString(),
                                        dirText, formatMoney(targetPrice), ta.getCurrency() != null ? ta.getCurrency() : "₸", icon
                                );

                                InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                                        .keyboardRow(new InlineKeyboardRow(
                                                InlineKeyboardButton.builder().text("🔔 Мои алерты").callbackData("MY_ALERTS").build(),
                                                InlineKeyboardButton.builder().text("📊 Анализ " + ticker).callbackData("TA_" + ticker).build()
                                        ))
                                        .build();

                                sendMessage(chatId, msg, keyboard);
                            });
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        sendMessage(chatId, "Инструмент <code>" + escapeHtml(ticker) + "</code> не найден.", null)
                ))
                .subscribe(null, e -> log.error("Error setting alert: {}", e.getMessage()));
    }

    private void sendMyAlerts(Long chatId) {
        priceAlertTargetRepository.findAllActiveByChatId(chatId)
                .collectList()
                .doOnSuccess(alerts -> {
                    if (alerts == null || alerts.isEmpty()) {
                        InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder().text("🔔 Лимит KSPI").callbackData("SET_ALERT_KSPI").build(),
                                        InlineKeyboardButton.builder().text("🔔 Лимит HSBK").callbackData("SET_ALERT_HSBK").build(),
                                        InlineKeyboardButton.builder().text("🔔 Лимит AIRA").callbackData("SET_ALERT_AIRA").build()
                                ))
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder().text("🔍 Поиск тикера").switchInlineQueryCurrentChat("").build()
                                ))
                                .build();
                        sendMessage(chatId, """
                                🔔 <b>У вас нет активных лимит-уведомлений.</b>

                                Чтобы установить алерт, выберите акцию ниже или скопируйте команду:
                                <pre>/alert KSPI 55000</pre>
                                """, kb);
                        return;
                    }

                    StringBuilder sb = new StringBuilder("🔔 <b>Ваши активные лимит-уведомления:</b>\n\n");
                    List<InlineKeyboardRow> rows = new ArrayList<>();

                    for (int i = 0; i < alerts.size(); i++) {
                        PriceAlertTargetEntity a = alerts.get(i);
                        String cond = "ABOVE".equalsIgnoreCase(a.getDirection()) ? "при росте ≥" : "при падении ≤";
                        sb.append(String.format(
                                "%d. <code>%s</code> — цель: <b>%s ₸</b> (%s)\n" +
                                "   Создан: %s\n\n",
                                i + 1,
                                a.getTicker(),
                                formatMoney(a.getTargetPrice()),
                                cond,
                                a.getCreatedAt() != null ? a.getCreatedAt().toLocalDate().toString() : "сегодня"
                        ));

                        rows.add(new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text(String.format("❌ Удалить %s (%s)", a.getTicker(), formatMoney(a.getTargetPrice())))
                                        .callbackData("DEL_ALERT_" + a.getId())
                                        .build()
                        ));
                    }

                    sb.append("<i>Нажмите на кнопку ниже, чтобы отменить алерт:</i>");
                    InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder().keyboard(rows).build();
                    sendMessage(chatId, sb.toString(), keyboard);
                })
                .subscribe(null, e -> log.error("Error fetching my alerts: {}", e.getMessage()));
    }

    private void handleThresholdCommand(Long chatId, String text) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2) {
            subscriberRepository.findById(chatId)
                    .doOnSuccess(sub -> {
                        BigDecimal curTh = (sub != null && sub.getPriceChangeThreshold() != null)
                                ? sub.getPriceChangeThreshold()
                                : BigDecimal.valueOf(3.0);

                        InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder().text("±1.5% (активный)").callbackData("SET_THRESH_1.5").build(),
                                        InlineKeyboardButton.builder().text("±2.5%").callbackData("SET_THRESH_2.5").build()
                                ))
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder().text("±3.0% (стандарт)").callbackData("SET_THRESH_3.0").build(),
                                        InlineKeyboardButton.builder().text("±5.0% (сильный)").callbackData("SET_THRESH_5.0").build()
                                ))
                                .build();

                        sendMessage(chatId, String.format(
                                "⚙️ <b>Настройка порога движения цен акций:</b>\n\n" +
                                "Текущий порог: <b>±%.1f%%</b>\n\n" +
                                "Бот присылает уведомления обо всех акциях KASE, цена которых за день изменилась сильнее этого значения.\n\n" +
                                "Выберите порог в 1 нажатие ниже или скопируйте команду:\n" +
                                "<pre>/threshold 2.5</pre>\n\n" +
                                "💡 <i>У других ботов эта функция требует платной подписки (399 ₸/мес), а у нас — 100%% бесплатно!</i>",
                                curTh.doubleValue()
                        ), kb);
                    })
                    .subscribe();
            return;
        }

        try {
            double val = Double.parseDouble(parts[1].replace(",", ".").trim());
            if (val <= 0 || val > 50) {
                sendMessage(chatId, "Пожалуйста, укажите порог в процентах от 0.5% до 50%. Например: <code>/threshold 2.5</code>", null);
                return;
            }
            BigDecimal threshold = BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP);
            subscriberRepository.findById(chatId)
                    .flatMap(sub -> {
                        sub.setPriceChangeThreshold(threshold);
                        sub.setUpdatedAt(LocalDateTime.now());
                        return subscriberRepository.save(sub);
                    })
                    .doOnSuccess(sub -> {
                        sendMessage(chatId, String.format(
                                "✅ <b>Порог движения цен успешно установлен: ±%.1f%%!</b>\n\n" +
                                "Теперь вы будете оперативно получать алерты об акциях KASE с дневным движением от %.1f%%.\n\n" +
                                "Напоминаем: в нашем боте все подобные функции всегда 100% бесплатны!",
                                val, val
                        ), null);
                    })
                    .subscribe();
        } catch (Exception e) {
            sendMessage(chatId, "Неверный формат. Пример: <code>/threshold 2.5</code>", null);
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void handleInlineQuery(InlineQuery inlineQuery) {
        String rawQuery = inlineQuery.getQuery() != null ? inlineQuery.getQuery().trim() : "";
        String q = rawQuery.toUpperCase();
        String queryId = inlineQuery.getId();

        Mono<List<StockItemDto>> kaseStocksMono = analyticsService.getTopStocks(null)
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());

        Mono<List<AixInstrumentDto>> aixSecuritiesMono = aixService.getInstruments(null, null, rawQuery.isBlank() ? null : rawQuery, 15)
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());

        Mono<List<BondItemDto>> bondsMono = (q.isBlank()
                ? analyticsService.getDiscountBonds(95.0)
                : analyticsService.getBondScreener(null, null, null, null, null, null, null, rawQuery, null, null, 15, 0))
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());

        Mono.zip(kaseStocksMono, aixSecuritiesMono, bondsMono)
                .doOnSuccess(tuple -> {
                    List<StockItemDto> kaseStocks = tuple.getT1();
                    List<AixInstrumentDto> aixSecurities = tuple.getT2();
                    List<BondItemDto> bonds = tuple.getT3();

                    List<InlineQueryResult> results = new ArrayList<>();

                    // 1. KASE Stocks
                    for (StockItemDto s : kaseStocks) {
                        if (results.size() >= 20) break;
                        if (s.getCode() == null) continue;
                        if (!matchesTickerQuery(s.getCode(), s.getName(), q)) continue;

                        BigDecimal price = s.getPrice() != null ? s.getPrice() : s.getClosePrice();
                        BigDecimal chg = s.getChangePercent();
                        String sign = (chg != null && chg.compareTo(BigDecimal.ZERO) >= 0) ? "+" : "";
                        String icon = (chg != null && chg.compareTo(BigDecimal.ZERO) >= 0) ? "🟢" : "🔴";
                        String chgStr = chg != null ? chg.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
                        String cur = s.getCurrency() != null ? s.getCurrency() : "₸";

                        String volStr = s.getVolumeKzt() != null && s.getVolumeKzt().compareTo(BigDecimal.ZERO) > 0
                                ? String.format(" • Объем: %,.0f ₸", s.getVolumeKzt().doubleValue()).replace(',', ' ')
                                : "";

                        String title = String.format("📈 %s — %s", s.getCode(), s.getName() != null ? s.getName() : "Акция KASE");
                        String desc = String.format("%s %s (%s%s%%)%s • KASE",
                                price != null ? formatMoney(price) : "—", cur, sign, chgStr, volStr);

                        String messageText = String.format(
                                "📈 <b>%s — %s</b>\n" +
                                "Биржа: <b>KASE</b> | Валюта: <b>%s</b>\n\n" +
                                "💵 Текущая цена: <b>%s %s</b> (%s %s%s%%)\n" +
                                "📊 Объем торгов: <b>%s ₸</b> | Сделок: <b>%d</b>\n\n" +
                                "💡 <i>Выберите действие для этого инструмента:</i>",
                                s.getCode(), escapeHtml(s.getName()), cur,
                                price != null ? formatMoney(price) : "—", cur,
                                icon, sign, chgStr,
                                s.getVolumeKzt() != null ? formatMoney(s.getVolumeKzt()) : "0",
                                s.getDealCount() != null ? s.getDealCount() : 0
                        );

                        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder().text("📊 Теханализ / RSI").callbackData("TA_" + s.getCode()).build(),
                                        InlineKeyboardButton.builder().text("🔔 Алерт цены").callbackData("SET_ALERT_" + s.getCode()).build()
                                ))
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder().text("⚖️ KASE ⇄ AIX").callbackData("COMPARE_" + s.getCode()).build(),
                                        InlineKeyboardButton.builder().text("⭐ В вотчлист").callbackData("TRACK_" + s.getCode()).build()
                                ))
                                .build();

                        results.add(InlineQueryResultArticle.builder()
                                .id("kase_" + s.getCode())
                                .title(title)
                                .description(desc)
                                .inputMessageContent(InputTextMessageContent.builder()
                                        .messageText(messageText)
                                        .parseMode("HTML")
                                        .disableWebPagePreview(true)
                                        .build())
                                .replyMarkup(keyboard)
                                .build());
                    }

                    // 2. AIX Securities
                    for (AixInstrumentDto aix : aixSecurities) {
                        if (results.size() >= 35) break;
                        if (aix.getSecCode() == null) continue;
                        String name = aix.getShortName() != null ? aix.getShortName()
                                : (aix.getName() != null ? aix.getName() : aix.getIssuer());
                        if (!matchesTickerQuery(aix.getSecCode(), name, q)) continue;

                        BigDecimal price = aix.getLastTrade() != null ? aix.getLastTrade()
                                : (aix.getPreviousClose() != null ? aix.getPreviousClose() : aix.getReferencePrice());
                        BigDecimal chg = aix.getPercentChange();
                        String sign = (chg != null && chg.compareTo(BigDecimal.ZERO) >= 0) ? "+" : "";
                        String chgStr = chg != null ? chg.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
                        String cur = aix.getCurrency() != null ? aix.getCurrency() : "KZT";

                        String title = String.format("🏛️ %s — %s (AIX)", aix.getSecCode(), name != null ? name : "AIX");
                        String desc = String.format("%s %s (%s%s%%) • Биржа AIX",
                                price != null ? formatMoney(price) : "—", cur, sign, chgStr);

                        String messageText = String.format(
                                "🏛️ <b>%s — %s</b>\n" +
                                "Биржа: <b>AIX</b> | Валюта: <b>%s</b>\n\n" +
                                "💵 Текущая цена: <b>%s %s</b> (%s%s%%)\n" +
                                "📖 Доступен стакан заявок Level-2 (Order Book).\n\n" +
                                "💡 <i>Выберите действие для этого инструмента:</i>",
                                aix.getSecCode(), escapeHtml(name != null ? name : aix.getSecCode()), cur,
                                price != null ? formatMoney(price) : "—", cur,
                                sign, chgStr
                        );

                        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder().text("📖 Стакан котировок").callbackData("DEPTH_" + aix.getSecCode()).build(),
                                        InlineKeyboardButton.builder().text("📊 Теханализ").callbackData("TA_" + aix.getSecCode()).build()
                                ))
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder().text("⚖️ Сравнить с KASE").callbackData("COMPARE_" + aix.getSecCode()).build(),
                                        InlineKeyboardButton.builder().text("🔔 Лимит цены").callbackData("SET_ALERT_" + aix.getSecCode()).build()
                                ))
                                .build();

                        results.add(InlineQueryResultArticle.builder()
                                .id("aix_" + aix.getSecCode())
                                .title(title)
                                .description(desc)
                                .inputMessageContent(InputTextMessageContent.builder()
                                        .messageText(messageText)
                                        .parseMode("HTML")
                                        .disableWebPagePreview(true)
                                        .build())
                                .replyMarkup(keyboard)
                                .build());
                    }

                    // 3. Bonds
                    for (BondItemDto b : bonds) {
                        if (results.size() >= 48) break;
                        if (b.getCode() == null) continue;
                        String name = b.getOrgShortNameRu() != null ? b.getOrgShortNameRu() : b.getOrgNameRu();
                        if (!matchesTickerQuery(b.getCode(), name, q)) continue;

                        BigDecimal ytm = b.getDohod() != null ? b.getDohod() : b.getYtm();
                        double priceVal = b.getPrice() != null ? b.getPrice().doubleValue() : 100.0;
                        double discount = 100.0 - priceVal;

                        String title = String.format("📉 %s — %s (Облигация)", b.getCode(), name != null ? name : "KASE");
                        String desc = String.format("Цена: %.1f%% %s• YTM: %.2f%% • Купон: %.2f%%",
                                priceVal,
                                discount > 0 ? String.format("(скидка %.1f%%) ", discount) : "",
                                ytm != null ? ytm.doubleValue() : 0.0,
                                b.getCupon() != null ? b.getCupon().doubleValue() : 0.0);

                        String messageText = String.format(
                                "📉 <b>%s — %s</b>\n" +
                                "Тип: <b>Облигация KASE</b>\n\n" +
                                "💵 Чистая цена: <b>%.2f%%</b>%s\n" +
                                "📈 Доходность (YTM): <b>%.2f%%</b>\n" +
                                "💰 Купонная ставка: <b>%.2f%%</b>\n" +
                                "⏳ Срок до погашения: <b>%s</b>\n\n" +
                                "💡 <i>Выберите действие для этой облигации:</i>",
                                b.getCode(), escapeHtml(name != null ? name : b.getCode()),
                                priceVal, discount > 0 ? String.format(" (Скидка <b>%.1f%%</b>)", discount) : "",
                                ytm != null ? ytm.doubleValue() : 0.0,
                                b.getCupon() != null ? b.getCupon().doubleValue() : 0.0,
                                formatDuration(b.getDtm() != null ? b.getDtm() : 365)
                        );

                        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                                .keyboardRow(new InlineKeyboardRow(
                                        InlineKeyboardButton.builder().text("🧮 Рассчитать доход").callbackData("CALC_" + b.getCode()).build(),
                                        InlineKeyboardButton.builder().text("⭐ В вотчлист").callbackData("TRACK_" + b.getCode()).build()
                                ))
                                .build();

                        results.add(InlineQueryResultArticle.builder()
                                .id("bond_" + b.getCode() + "_" + (b.getId() != null ? b.getId() : "0"))
                                .title(title)
                                .description(desc)
                                .inputMessageContent(InputTextMessageContent.builder()
                                        .messageText(messageText)
                                        .parseMode("HTML")
                                        .disableWebPagePreview(true)
                                        .build())
                                .replyMarkup(keyboard)
                                .build());
                    }

                    if (results.isEmpty()) {
                        results.add(InlineQueryResultArticle.builder()
                                .id("not_found")
                                .title("🔍 Ничего не найдено")
                                .description("По запросу «" + rawQuery + "» бумаги не найдены. Попробуйте KSPI, AIRA, KAP...")
                                .inputMessageContent(InputTextMessageContent.builder()
                                        .messageText("🔍 По запросу <code>" + escapeHtml(rawQuery) + "</code> ничего не найдено.\n\nПопробуйте найти:\n• <code>KSPI</code> (Kaspi.kz)\n• <code>AIRA</code> (Air Astana)\n• <code>HSBK</code> (Halyk Bank)\n• <code>KAP</code> (Казатомпром)\n• <code>/pulse</code> (Пульс рынка)")
                                        .parseMode("HTML")
                                        .build())
                                .build());
                    }

                    try {
                        AnswerInlineQuery answer = AnswerInlineQuery.builder()
                                .inlineQueryId(queryId)
                                .results(results)
                                .cacheTime(5)
                                .isPersonal(true)
                                .build();
                        telegramClient.execute(answer);
                    } catch (Exception ex) {
                        log.error("Failed to answer inline query {}: {}", queryId, ex.getMessage());
                    }
                })
                .subscribe(null, e -> log.error("Error processing inline query: {}", e.getMessage()));
    }

    private boolean matchesTickerQuery(String code, String name, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toUpperCase();
        if (code != null && code.toUpperCase().contains(q)) return true;
        if (name != null && name.toUpperCase().contains(q)) return true;

        if (q.contains("КАСПИ") || q.contains("KASPI")) {
            return "KSPI".equalsIgnoreCase(code) || "KSPI.Y".equalsIgnoreCase(code);
        }
        if (q.contains("ХАЛЫК") || q.contains("HALYK") || q.contains("НАРОДН")) {
            return "HSBK".equalsIgnoreCase(code);
        }
        if (q.contains("АСТАНА") || q.contains("ASTANA") || q.contains("AIRA")) {
            return "AIRA".equalsIgnoreCase(code);
        }
        if (q.contains("АТОМ") || q.contains("КАП") || q.contains("KAZATOM") || q.contains("УРАН")) {
            return "KZAP".equalsIgnoreCase(code) || "KAP".equalsIgnoreCase(code);
        }
        if (q.contains("ТЕЛЕКОМ") || q.contains("TELECOM")) {
            return "KZTK".equalsIgnoreCase(code) || "KZTKP".equalsIgnoreCase(code);
        }
        if (q.contains("МУНАЙ") || q.contains("НЕФТ") || q.contains("KMG")) {
            return "KMGZ".equalsIgnoreCase(code);
        }
        if (q.contains("ЦЕНТР") || q.contains("CCBN")) {
            return "CCBN".equalsIgnoreCase(code);
        }
        if (q.contains("КЕГОК") || q.contains("KEGC")) {
            return "KEGC".equalsIgnoreCase(code);
        }
        if (q.contains("ФОРТЕ") || q.contains("FORTE") || q.contains("ASBN")) {
            return "ASBN".equalsIgnoreCase(code);
        }
        return false;
    }
}

