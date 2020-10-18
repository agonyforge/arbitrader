package com.r307.arbitrader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r307.arbitrader.DecimalConstants;
import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.exception.OrderNotFoundException;
import com.r307.arbitrader.service.model.ActivePosition;
import com.r307.arbitrader.service.model.ArbitrageLog;
import com.r307.arbitrader.service.model.Spread;
import com.r307.arbitrader.service.model.TradeCombination;
import com.r307.arbitrader.service.ticker.TickerStrategy;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import org.apache.commons.io.FileUtils;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Fee;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.params.CurrencyPairsParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;
import static com.r307.arbitrader.DecimalConstants.USD_SCALE;

@Component
public class TradingService {
    public static final String METADATA_KEY = "arbitrader-metadata";
    public static final String TICKER_STRATEGY_KEY = "tickerStrategy";

    private static final Logger LOGGER = LoggerFactory.getLogger(TradingService.class);
    private static final String STATE_FILE = ".arbitrader/arbitrader-state.json";
    protected static final String TRADE_HISTORY_FILE = ".arbitrader/arbitrader-arbitrage-history.csv";

    private static final BigDecimal TRADE_PORTION = new BigDecimal("0.9");
    private static final BigDecimal TRADE_REMAINDER = BigDecimal.ONE.subtract(TRADE_PORTION);

    private static final CurrencyPairMetaData NULL_CURRENCY_PAIR_METADATA = new CurrencyPairMetaData(
        null, null, null, null, null);

    private final ObjectMapper objectMapper;
    private final TradingConfiguration tradingConfiguration;
    private final ExchangeFeeCache feeCache;
    private final ConditionService conditionService;
    private final ExchangeService exchangeService;
    private final ErrorCollectorService errorCollectorService;
    private final SpreadService spreadService;
    private final TickerService tickerService;
    private final NotificationService notificationService;
    private final Map<String, TickerStrategy> tickerStrategies;
    private final List<Exchange> exchanges = new ArrayList<>();
    private final Map<TradeCombination, BigDecimal> missedTrades = new HashMap<>();
    private boolean bailOut = false;
    private boolean timeoutExitWarning = false;
    private ActivePosition activePosition = null;

    public TradingService(
        ObjectMapper objectMapper,
        TradingConfiguration tradingConfiguration,
        ExchangeFeeCache feeCache,
        ConditionService conditionService,
        ExchangeService exchangeService,
        ErrorCollectorService errorCollectorService,
        SpreadService spreadService,
        TickerService tickerService,
        NotificationService notificationService,
        Map<String, TickerStrategy> tickerStrategies) {

        this.objectMapper = objectMapper;
        this.tradingConfiguration = tradingConfiguration;
        this.feeCache = feeCache;
        this.conditionService = conditionService;
        this.exchangeService = exchangeService;
        this.errorCollectorService = errorCollectorService;
        this.spreadService = spreadService;
        this.tickerService = tickerService;
        this.tickerStrategies = tickerStrategies;
        this.notificationService = notificationService;
    }

    @PostConstruct
    public void connectExchanges() {
        tradingConfiguration.getExchanges().forEach(exchangeMetadata -> {
            ExchangeSpecification specification = new ExchangeSpecification(exchangeMetadata.getExchangeClass());

            specification.setUserName(exchangeMetadata.getUserName());
            specification.setApiKey(exchangeMetadata.getApiKey());
            specification.setSecretKey(exchangeMetadata.getSecretKey());

            if (exchangeMetadata.getSslUri() != null) {
                specification.setSslUri(exchangeMetadata.getSslUri());
            }

            if (exchangeMetadata.getHost() != null) {
                specification.setHost(exchangeMetadata.getHost());
            }

            if (exchangeMetadata.getPort() != null) {
                specification.setPort(exchangeMetadata.getPort());
            }

            if (!exchangeMetadata.getCustom().isEmpty()) {
                exchangeMetadata.getCustom().forEach((key, value) -> {
                    if ("true".equals(value) || "false".equals(value)) {
                        specification.setExchangeSpecificParametersItem(key, Boolean.valueOf(value));
                    } else {
                        specification.setExchangeSpecificParametersItem(key, value);
                    }
                });
            }

            specification.setExchangeSpecificParametersItem(METADATA_KEY, exchangeMetadata);

            if (specification.getExchangeClassName().contains("Streaming")) {
                exchanges.add(StreamingExchangeFactory.INSTANCE.createExchange(specification));
            } else {
                exchanges.add(ExchangeFactory.INSTANCE.createExchange(specification));
            }
        });

        exchanges.forEach(exchange -> {
            try {
                LOGGER.debug("{} SSL URI: {}",
                        exchange.getExchangeSpecification().getExchangeName(),
                        exchange.getExchangeSpecification().getSslUri());
                LOGGER.debug("{} SSL host: {}",
                        exchange.getExchangeSpecification().getExchangeName(),
                        exchange.getExchangeSpecification().getHost());
                LOGGER.debug("{} SSL port: {}",
                        exchange.getExchangeSpecification().getExchangeName(),
                        exchange.getExchangeSpecification().getPort());
                LOGGER.debug("{} home currency: {}",
                        exchange.getExchangeSpecification().getExchangeName(),
                        exchangeService.getExchangeHomeCurrency(exchange));
                LOGGER.info("{} balance: {}{}",
                        exchange.getExchangeSpecification().getExchangeName(),
                    exchangeService.getExchangeHomeCurrency(exchange).getSymbol(),
                        getAccountBalance(exchange));
            } catch (IOException e) {
                LOGGER.error("Unable to fetch account balance: ", e);
            }

            if (exchange.getExchangeSpecification().getExchangeClassName().contains("Streaming")) {
                exchange.getExchangeSpecification().setExchangeSpecificParametersItem(TICKER_STRATEGY_KEY, tickerStrategies.get("streamingTickerStrategy"));
            } else {
                try {
                    CurrencyPairsParam param = () -> exchangeService.getExchangeMetadata(exchange).getTradingPairs().subList(0, 1);
                    exchange.getMarketDataService().getTickers(param);

                    exchange.getExchangeSpecification().setExchangeSpecificParametersItem(TICKER_STRATEGY_KEY, tickerStrategies.get("singleCallTickerStrategy"));
                } catch (NotYetImplementedForExchangeException e) {
                    LOGGER.warn("{} does not support fetching multiple tickers at a time and will fetch tickers " +
                            "individually instead. This may result in API rate limiting.",
                        exchange.getExchangeSpecification().getExchangeName());

                    exchange.getExchangeSpecification().setExchangeSpecificParametersItem(TICKER_STRATEGY_KEY, tickerStrategies.get("parallelTickerStrategy"));
                } catch (IOException e) {
                    LOGGER.debug("IOException fetching tickers for {}: ", exchange.getExchangeSpecification().getExchangeName(), e);
                }
            }

            BigDecimal tradingFee = getExchangeFee(exchange, exchangeService.convertExchangePair(exchange, CurrencyPair.BTC_USD), false);

            LOGGER.info("{} ticker strategy: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                exchange.getExchangeSpecification().getExchangeSpecificParametersItem(TICKER_STRATEGY_KEY));

            LOGGER.info("{} {} trading fee: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                exchangeService.convertExchangePair(exchange, CurrencyPair.BTC_USD),
                tradingFee);
        });

        tickerService.initializeTickers(exchanges);

        if (tradingConfiguration.getFixedExposure() != null) {
            LOGGER.info("Using fixed exposure of ${} as configured", tradingConfiguration.getFixedExposure());
        }

        if (tradingConfiguration.getTradeTimeout() != null) {
            LOGGER.info("Using trade timeout of {} hours", tradingConfiguration.getTradeTimeout());
        }

        // load active trades from file, if there is one
        File stateFile = new File(STATE_FILE);

        if (stateFile.exists()) {
            if (!stateFile.canRead()) {
                LOGGER.error("Cannot read state file: {}", stateFile.getAbsolutePath());
            } else {
                try {
                    activePosition = objectMapper.readValue(stateFile, ActivePosition.class);

                    LOGGER.info("Loaded active trades from file: {}", stateFile.getAbsolutePath());
                    LOGGER.info("Active trades: {}", activePosition);
                } catch (IOException e) {
                    LOGGER.error("Unable to parse state file {}: ", stateFile.getAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * As often as once per minute, display a summary of any non-critical error messages. Summarizing them greatly
     * reduces how noisy the logs are while still providing the same information.
     */
    @Scheduled(cron = "0 * * * * *")
    public void errorSummary() {
        if (!errorCollectorService.isEmpty()) {
            errorCollectorService.report().forEach(LOGGER::info);
            errorCollectorService.clear();
        }
    }

    /**
     * Display a summary once every 6 hours with the current spreads.
     */
    @Scheduled(cron = "0 0 0/6 * * *") // every 6 hours
    public void summary() {
        LOGGER.info("Summary: [Long/Short Exchanges] [Pair] [Current Spread] -> [{} Spread Target]", (activePosition != null ? "Exit" : "Entry"));

        List<TradeCombination> tradeCombinations = tickerService.getTradeCombinations();

        tradeCombinations.forEach(tradeCombination -> {
            Spread spread = computeSpread(tradeCombination);

            if (spread == null) {
                return;
            }

            if (activePosition == null && BigDecimal.ZERO.compareTo(spread.getIn()) < 0) {
                LOGGER.info("{}/{} {} {} -> {}",
                    spread.getLongExchange().getExchangeSpecification().getExchangeName(),
                    spread.getShortExchange().getExchangeSpecification().getExchangeName(),
                    spread.getCurrencyPair(),
                    spread.getIn(),
                    tradingConfiguration.getEntrySpread());
            } else if (activePosition != null
                && activePosition.getCurrencyPair().equals(spread.getCurrencyPair())
                && activePosition.getLongTrade().getExchange().equals(spread.getLongExchange().getExchangeSpecification().getExchangeName())
                && activePosition.getShortTrade().getExchange().equals(spread.getShortExchange().getExchangeSpecification().getExchangeName())) {

                LOGGER.info("{}/{} {} {} -> {}",
                    spread.getLongExchange().getExchangeSpecification().getExchangeName(),
                    spread.getShortExchange().getExchangeSpecification().getExchangeName(),
                    spread.getCurrencyPair(),
                    spread.getOut(),
                    activePosition.getExitTarget());
            }
        });
    }

    @Scheduled(initialDelay = 5000, fixedRate = 3000)
    public void tick() {
        LOGGER.debug("Tick");

        if (bailOut) {
            LOGGER.error("Exiting immediately to avoid erroneous trades.");
            System.exit(1);
        }

        if (activePosition == null && conditionService.isExitWhenIdleCondition()) {
            LOGGER.info("Exiting at user request");
            conditionService.clearExitWhenIdleCondition();
            System.exit(0);
        }

        tickerService.refreshTickers();

        long exchangePollStartTime = System.currentTimeMillis();

        List<TradeCombination> tradeCombinations = tickerService.getTradeCombinations();

        tradeCombinations.forEach(tradeCombination -> {
            Spread spread = computeSpread(tradeCombination);

            if (spread == null) {
                return;
            }

            final String shortExchangeName = spread.getShortExchange().getExchangeSpecification().getExchangeName();
            final String longExchangeName = spread.getLongExchange().getExchangeSpecification().getExchangeName();

            LOGGER.debug("Long/Short: {}/{} {} {}",
                longExchangeName,
                shortExchangeName,
                spread.getCurrencyPair(),
                spread.getIn());

            if (activePosition != null
                && spread.getIn().compareTo(tradingConfiguration.getEntrySpread()) <= 0
                && missedTrades.containsKey(tradeCombination)) {

                LOGGER.debug("{} has exited entry threshold: {}", tradeCombination, spread.getIn());

                missedTrades.remove(tradeCombination);
            }

            if (!bailOut && !conditionService.isForceCloseCondition() && spread.getIn().compareTo(tradingConfiguration.getEntrySpread()) > 0) {
                if (activePosition != null) {
                    if (!activePosition.getCurrencyPair().equals(spread.getCurrencyPair())
                        || !activePosition.getLongTrade().getExchange().equals(longExchangeName)
                        || !activePosition.getShortTrade().getExchange().equals(shortExchangeName)) {

                        if (!missedTrades.containsKey(tradeCombination)) {
                            LOGGER.debug("{} has entered entry threshold: {}", tradeCombination, spread.getIn());

                            missedTrades.put(tradeCombination, spread.getIn());
                        }
                    }

                    return;
                }

                BigDecimal longFees = getExchangeFee(spread.getLongExchange(), spread.getCurrencyPair(), true);
                BigDecimal shortFees = getExchangeFee(spread.getShortExchange(), spread.getCurrencyPair(), true);

                BigDecimal fees = (longFees.add(shortFees))
                        .multiply(new BigDecimal("2.0"));

                BigDecimal exitTarget = spread.getIn()
                        .subtract(tradingConfiguration.getExitTarget())
                        .subtract(fees);

                BigDecimal maxExposure = getMaximumExposure(spread.getLongExchange(), spread.getShortExchange());

                BigDecimal longMinAmount = spread.getLongExchange().getExchangeMetaData().getCurrencyPairs()
                    .getOrDefault(exchangeService.convertExchangePair(spread.getLongExchange(), spread.getCurrencyPair()), NULL_CURRENCY_PAIR_METADATA)
                    .getMinimumAmount();
                BigDecimal shortMinAmount = spread.getShortExchange().getExchangeMetaData().getCurrencyPairs()
                    .getOrDefault(exchangeService.convertExchangePair(spread.getShortExchange(), spread.getCurrencyPair()), NULL_CURRENCY_PAIR_METADATA)
                    .getMinimumAmount();

                if (longMinAmount == null) {
                    longMinAmount = new BigDecimal("0.001");
                }

                if (shortMinAmount == null) {
                    shortMinAmount = new BigDecimal("0.001");
                }

                if (maxExposure.compareTo(longMinAmount) <= 0) {
                    LOGGER.error("{} must have more than ${} to trade {}",
                        longExchangeName,
                        longMinAmount.add(longMinAmount.multiply(TRADE_REMAINDER)),
                        exchangeService.convertExchangePair(spread.getLongExchange(), spread.getCurrencyPair()));
                    return;
                }

                if (maxExposure.compareTo(shortMinAmount) <= 0) {
                    LOGGER.error("{} must have more than ${} to trade {}",
                        shortExchangeName,
                        shortMinAmount.add(shortMinAmount.multiply(TRADE_REMAINDER)),
                        exchangeService.convertExchangePair(spread.getShortExchange(), spread.getCurrencyPair()));
                    return;
                }

                int longScale = BTC_SCALE;
                int shortScale = BTC_SCALE;

                LOGGER.debug("Max exposure: {}", maxExposure);
                LOGGER.debug("Long scale: {}", longScale);
                LOGGER.debug("Short scale: {}", shortScale);
                LOGGER.debug("Long ticker ASK: {}", spread.getLongTicker().getAsk());
                LOGGER.debug("Short ticker BID: {}", spread.getShortTicker().getBid());

                BigDecimal longVolume = maxExposure.divide(spread.getLongTicker().getAsk(), longScale, RoundingMode.HALF_EVEN);
                BigDecimal shortVolume = maxExposure.divide(spread.getShortTicker().getBid(), shortScale, RoundingMode.HALF_EVEN);

                BigDecimal longStepSize = spread.getLongExchange().getExchangeMetaData().getCurrencyPairs().getOrDefault(exchangeService.convertExchangePair(spread.getLongExchange(), spread.getCurrencyPair()), NULL_CURRENCY_PAIR_METADATA).getAmountStepSize();
                BigDecimal shortStepSize = spread.getShortExchange().getExchangeMetaData().getCurrencyPairs().getOrDefault(exchangeService.convertExchangePair(spread.getShortExchange(), spread.getCurrencyPair()), NULL_CURRENCY_PAIR_METADATA).getAmountStepSize();

                LOGGER.debug("Long step size: {}", longStepSize);
                LOGGER.debug("Short step size: {}", shortStepSize);

                LOGGER.debug("Long exchange volume before rounding: {}", longVolume);
                LOGGER.debug("Short exchange volume before rounding: {}", shortVolume);

                if (longStepSize != null) {
                    longVolume = roundByStep(longVolume, longStepSize);
                }

                if (shortStepSize != null) {
                    shortVolume = roundByStep(shortVolume, shortStepSize);
                }

                LOGGER.debug("Long exchange volume after rounding: {}", longVolume);
                LOGGER.debug("Short exchange volume after rounding: {}", shortVolume);

                BigDecimal longLimitPrice;
                BigDecimal shortLimitPrice;

                try {
                    longLimitPrice = getLimitPrice(spread.getLongExchange(), spread.getCurrencyPair(), longVolume, Order.OrderType.ASK);
                    shortLimitPrice = getLimitPrice(spread.getShortExchange(), spread.getCurrencyPair(), shortVolume, Order.OrderType.BID);
                } catch (ExchangeException e) {
                    LOGGER.warn("Failed to fetch order books for {}/{} and currency {}/{} to compute entry prices: {}",
                        longExchangeName,
                        spread.getShortExchange().getDefaultExchangeSpecification().getExchangeName(),
                        spread.getCurrencyPair().base,
                        spread.getCurrencyPair().counter,
                        e.getMessage());
                    return;
                }

                BigDecimal spreadVerification = computeSpread(longLimitPrice, shortLimitPrice);

                if (spreadVerification.compareTo(tradingConfiguration.getEntrySpread()) < 0) {
                    LOGGER.debug("Not enough liquidity to execute both trades profitably");
                } else if (conditionService.isBlackoutCondition(spread.getLongExchange()) || conditionService.isBlackoutCondition(spread.getShortExchange())) {
                    LOGGER.warn("Cannot open position on one or more exchanges due to user configured blackout");
                } else {
                    LOGGER.info("***** ENTRY *****");

                    BigDecimal totalBalance = logCurrentExchangeBalances(spread.getLongExchange(), spread.getShortExchange());

                    LOGGER.info("Entry spread: {}", spread.getIn());
                    LOGGER.info("Exit spread target: {}", exitTarget);
                    LOGGER.info("Long entry: {} {} {} @ {} ({} slip) = {}{}",
                            longExchangeName,
                            spread.getCurrencyPair(),
                            longVolume,
                            longLimitPrice,
                            longLimitPrice.subtract(spread.getLongTicker().getAsk()),
                            Currency.USD.getSymbol(),
                            longVolume.multiply(longLimitPrice));
                    LOGGER.info("Short entry: {} {} {} @ {} ({} slip) = {}{}",
                            shortExchangeName,
                            spread.getCurrencyPair(),
                            shortVolume,
                            shortLimitPrice,
                            spread.getShortTicker().getBid().subtract(shortLimitPrice),
                            Currency.USD.getSymbol(),
                            shortVolume.multiply(shortLimitPrice));

                    try {
                        activePosition = new ActivePosition();
                        activePosition.setEntryTime(OffsetDateTime.now());
                        activePosition.setCurrencyPair(spread.getCurrencyPair());
                        activePosition.setExitTarget(exitTarget);
                        activePosition.setEntryBalance(totalBalance);
                        activePosition.getLongTrade().setExchange(spread.getLongExchange());
                        activePosition.getLongTrade().setVolume(longVolume);
                        activePosition.getLongTrade().setEntry(longLimitPrice);
                        activePosition.getShortTrade().setExchange(spread.getShortExchange());
                        activePosition.getShortTrade().setVolume(shortVolume);
                        activePosition.getShortTrade().setEntry(shortLimitPrice);

                        executeOrderPair(
                                spread.getLongExchange(), spread.getShortExchange(),
                                spread.getCurrencyPair(),
                                longLimitPrice, shortLimitPrice,
                                longVolume, shortVolume,
                                true);

                        notificationService.sendEmailNotificationBodyForEntryTrade(spread, exitTarget, longVolume,
                            longLimitPrice, shortVolume, shortLimitPrice);

                    } catch (IOException e) {
                        LOGGER.error("IOE executing limit orders: ", e);
                        activePosition = null;
                    }

                    try {
                        FileUtils.write(new File(STATE_FILE), objectMapper.writeValueAsString(activePosition), Charset.defaultCharset());
                    } catch (IOException e) {
                        LOGGER.error("Unable to write state file!", e);
                    }
                }
            } else if (activePosition != null
                    && spread.getCurrencyPair().equals(activePosition.getCurrencyPair())
                    && longExchangeName.equals(activePosition.getLongTrade().getExchange())
                    && shortExchangeName.equals(activePosition.getShortTrade().getExchange())
                    && (spread.getOut().compareTo(activePosition.getExitTarget()) < 0 || conditionService.isForceCloseCondition() || isTradeExpired())) {

                BigDecimal longVolume = getVolumeForOrder(
                    spread.getLongExchange(),
                    spread.getCurrencyPair(),
                    activePosition.getLongTrade().getOrderId(),
                    activePosition.getLongTrade().getVolume());
                BigDecimal shortVolume = getVolumeForOrder(
                    spread.getShortExchange(),
                    spread.getCurrencyPair(),
                    activePosition.getShortTrade().getOrderId(),
                    activePosition.getShortTrade().getVolume());

                LOGGER.debug("Volumes: {}/{}", longVolume, shortVolume);

                BigDecimal longLimitPrice = null;
                BigDecimal shortLimitPrice = null;

                try {
                    longLimitPrice = getLimitPrice(spread.getLongExchange(), spread.getCurrencyPair(), longVolume, Order.OrderType.BID);
                    shortLimitPrice = getLimitPrice(spread.getShortExchange(), spread.getCurrencyPair(), shortVolume, Order.OrderType.ASK);
                } catch (ExchangeException e) {
                    LOGGER.warn("Failed to fetch order books (on active position) for {}/{} and currency {}/{} to compute entry prices: {}",
                        longExchangeName,
                        spread.getShortExchange().getDefaultExchangeSpecification().getExchangeName(),
                        spread.getCurrencyPair().base,
                        spread.getCurrencyPair().counter,
                        e.getMessage());
                }

                if (longLimitPrice != null && shortLimitPrice != null) {
                    LOGGER.debug("Limit prices: {}/{}", longLimitPrice, shortLimitPrice);

                    BigDecimal spreadVerification = computeSpread(longLimitPrice, shortLimitPrice);

                    LOGGER.debug("Spread verification: {}", spreadVerification);

                    if (longVolume.compareTo(BigDecimal.ZERO) <= 0 || shortVolume.compareTo(BigDecimal.ZERO) <= 0) {
                        LOGGER.error("Computed trade volume for exiting position was zero!");
                    }

                    if (conditionService.isBlackoutCondition(spread.getLongExchange()) || conditionService.isBlackoutCondition(spread.getShortExchange())) {
                        LOGGER.warn("Cannot exit position on one or more exchanges due to user configured blackout");
                    } else if (isTradeExpired() && spreadVerification.compareTo(tradingConfiguration.getEntrySpread()) < 0) {
                        if (!timeoutExitWarning) {
                            LOGGER.warn("Timeout exit triggered");
                            LOGGER.warn("Cannot exit now because spread would cause immediate reentry");
                            timeoutExitWarning = true;
                        }
                    } else if (!isTradeExpired() && !conditionService.isForceCloseCondition() && spreadVerification.compareTo(activePosition.getExitTarget()) > 0) {
                        LOGGER.debug("Not enough liquidity to execute both trades profitably!");
                    } else {
                        if (isTradeExpired()) {
                            LOGGER.warn("***** TIMEOUT EXIT *****");
                            timeoutExitWarning = false;
                        } else if (conditionService.isForceCloseCondition()) {
                            LOGGER.warn("***** FORCED EXIT *****");
                        } else {
                            LOGGER.info("***** EXIT *****");
                        }

                        try {
                            LOGGER.info("Long close: {} {} {} @ {} ({} slip) = {}{}",
                                longExchangeName,
                                spread.getCurrencyPair(),
                                longVolume,
                                longLimitPrice,
                                longLimitPrice.subtract(spread.getLongTicker().getBid()),
                                Currency.USD.getSymbol(),
                                longVolume.multiply(spread.getLongTicker().getBid()));
                            LOGGER.info("Short close: {} {} {} @ {} ({} slip) = {}{}",
                                shortExchangeName,
                                spread.getCurrencyPair(),
                                shortVolume,
                                shortLimitPrice,
                                spread.getShortTicker().getAsk().subtract(shortLimitPrice),
                                Currency.USD.getSymbol(),
                                shortVolume.multiply(spread.getShortTicker().getAsk()));

                            executeOrderPair(
                                spread.getLongExchange(), spread.getShortExchange(),
                                spread.getCurrencyPair(),
                                longLimitPrice, shortLimitPrice,
                                longVolume, shortVolume,
                                false);
                        } catch (IOException e) {
                            LOGGER.error("IOE executing limit orders: ", e);
                        }

                        LOGGER.info("Combined account balances on entry: ${}", activePosition.getEntryBalance());
                        BigDecimal updatedBalance = logCurrentExchangeBalances(spread.getLongExchange(), spread.getShortExchange());
                        final BigDecimal profit = updatedBalance.subtract(activePosition.getEntryBalance());

                        LOGGER.info("Profit calculation: ${} - ${} = ${}",
                            updatedBalance,
                            activePosition.getEntryBalance(),
                            profit);

                        final ArbitrageLog arbitrageLog = ArbitrageLog.ArbitrageLogBuilder.builder()
                            .withShortExchange(shortExchangeName)
                            .withShortCurrency(spread.getCurrencyPair().toString())
                            .withShortSpread(shortLimitPrice)
                            .withShortSlip(spread.getShortTicker().getAsk().subtract(shortLimitPrice))
                            .withShortAmount(shortVolume.multiply(spread.getShortTicker().getAsk()))
                            .withLongExchange(longExchangeName)
                            .withLongCurrency(spread.getCurrencyPair().toString())
                            .withLongSpread(longLimitPrice)
                            .withLongSlip(longLimitPrice.subtract(spread.getLongTicker().getBid()))
                            .withLongAmount(longVolume.multiply(spread.getLongTicker().getBid()))
                            .withProfit(profit)
                            .withTimestamp(OffsetDateTime.now())
                            .build();

                        persistArbitrageToCsvFile(arbitrageLog);

                        // Email notification must be sent before we set activePosition = null
                        notificationService.sendEmailNotificationBodyForExitTrade(spread, longVolume, longLimitPrice, shortVolume,
                            shortLimitPrice, activePosition.getEntryBalance(), updatedBalance);

                        activePosition = null;

                        FileUtils.deleteQuietly(new File(STATE_FILE));

                        if (conditionService.isForceCloseCondition()) {
                            conditionService.clearForceCloseCondition();
                        }
                    }
                }
            }
        });

        long exchangePollDuration = System.currentTimeMillis() - exchangePollStartTime;

        if (exchangePollDuration > 3000) {
            LOGGER.warn("Polling exchanges took {} ms", exchangePollDuration);
        }
    }

    private BigDecimal getExchangeFee(Exchange exchange, CurrencyPair currencyPair, boolean isQuiet) {
        BigDecimal cachedFee = feeCache.getCachedFee(exchange, currencyPair);

        if (cachedFee != null) {
            return cachedFee;
        }

        // if an explicit override is configured, default to that
        if (exchangeService.getExchangeMetadata(exchange).getFeeOverride() != null) {
            BigDecimal fee = exchangeService.getExchangeMetadata(exchange).getFeeOverride();
            feeCache.setCachedFee(exchange, currencyPair, fee);

            LOGGER.trace("Using explicitly configured fee override of {} for {}",
                fee,
                exchange.getExchangeSpecification().getExchangeName());

            return fee;
        }

        try {
            Map<CurrencyPair, Fee> fees = exchange.getAccountService().getDynamicTradingFees();

            if (fees.containsKey(currencyPair)) {
                BigDecimal fee = fees.get(currencyPair).getMakerFee();

                // We're going to cache this value. Fees don't change all that often and we don't want to use up
                // our allowance of API calls just checking the fees.
                feeCache.setCachedFee(exchange, currencyPair, fee);

                LOGGER.trace("Using dynamic maker fee for {}",
                    exchange.getExchangeSpecification().getExchangeName());

                return fee;
            }
        } catch (NotYetImplementedForExchangeException e) {
            LOGGER.trace("Dynamic fees not yet implemented for {}, will try other methods",
                    exchange.getExchangeSpecification().getExchangeName());
        } catch (IOException e) {
            LOGGER.trace("IOE fetching dynamic trading fees for {}",
                    exchange.getExchangeSpecification().getExchangeName());
        } catch (Exception e) {
            LOGGER.warn("Programming error in XChange! {} when calling getDynamicTradingFees() for exchange: {}",
                    e.getClass().getName(),
                    exchange.getExchangeSpecification().getExchangeName());
        }

        CurrencyPairMetaData currencyPairMetaData = exchange.getExchangeMetaData().getCurrencyPairs().get(exchangeService.convertExchangePair(exchange, currencyPair));

        if (currencyPairMetaData == null || currencyPairMetaData.getTradingFee() == null) {
            BigDecimal configuredFee = exchangeService.getExchangeMetadata(exchange).getFee();

            if (configuredFee == null) {
                if (!isQuiet) {
                    LOGGER.error("{} has no fees configured. Setting default of 0.0030. Please configure the correct value!",
                            exchange.getExchangeSpecification().getExchangeName());
                }

                return new BigDecimal("0.0030");
            }

            if (!isQuiet) {
                LOGGER.warn("{} fees unavailable via API. Will use configured value.",
                        exchange.getExchangeSpecification().getExchangeName());
            }

            return configuredFee;
        }

        return currencyPairMetaData.getTradingFee();
    }

    private void executeOrderPair(Exchange longExchange, Exchange shortExchange,
                                  CurrencyPair currencyPair,
                                  BigDecimal longLimitPrice, BigDecimal shortLimitPrice,
                                  BigDecimal longVolume, BigDecimal shortVolume,
                                  boolean isPositionOpen) throws IOException, ExchangeException {

        LimitOrder longLimitOrder = new LimitOrder.Builder(isPositionOpen ? Order.OrderType.BID : Order.OrderType.ASK, exchangeService.convertExchangePair(longExchange, currencyPair))
                .limitPrice(longLimitPrice)
                .originalAmount(longVolume)
                .build();
        LimitOrder shortLimitOrder = new LimitOrder.Builder(isPositionOpen ? Order.OrderType.ASK : Order.OrderType.BID, exchangeService.convertExchangePair(shortExchange, currencyPair))
                .limitPrice(shortLimitPrice)
                .originalAmount(shortVolume)
                .build();

        shortLimitOrder.setLeverage("2");

        LOGGER.debug("{}: {}",
                longExchange.getExchangeSpecification().getExchangeName(),
                longLimitOrder);
        LOGGER.debug("{}: {}",
                shortExchange.getExchangeSpecification().getExchangeName(),
                shortLimitOrder);

        try {
            String longOrderId = longExchange.getTradeService().placeLimitOrder(longLimitOrder);
            String shortOrderId = shortExchange.getTradeService().placeLimitOrder(shortLimitOrder);

            // TODO not happy with this coupling, need to refactor this
            if (isPositionOpen) {
                activePosition.getLongTrade().setOrderId(longOrderId);
                activePosition.getShortTrade().setOrderId(shortOrderId);
            } else {
                activePosition.getLongTrade().setOrderId(null);
                activePosition.getShortTrade().setOrderId(null);
            }

            LOGGER.info("{} limit order ID: {}",
                longExchange.getExchangeSpecification().getExchangeName(),
                longOrderId);
            LOGGER.info("{} limit order ID: {}",
                shortExchange.getExchangeSpecification().getExchangeName(),
                shortOrderId);
        } catch (ExchangeException e) {
            // At this point we may or may not have executed one of the trades
            // so we're in an unknown state.
            // For now, we'll just bail out and let the human figure out what to do to fix it.
            // TODO Look at both exchanges and attempt close any open positions.
            LOGGER.error("Exchange returned an error executing trade!", e);
            bailOut = true;
            return;
        }

        LOGGER.info("Waiting for limit orders to complete...");

        OpenOrders longOpenOrders;
        OpenOrders shortOpenOrders;
        int count = 0;

        do {
            longOpenOrders = fetchOpenOrders(longExchange).orElse(null);
            shortOpenOrders = fetchOpenOrders(shortExchange).orElse(null);

            if (longOpenOrders != null && !longOpenOrders.getOpenOrders().isEmpty() && count % 10 == 0) {
                LOGGER.warn(collectOpenOrders(longExchange, longOpenOrders));
            }

            if (shortOpenOrders != null && !shortOpenOrders.getOpenOrders().isEmpty() && count % 10 == 0) {
                LOGGER.warn(collectOpenOrders(shortExchange, shortOpenOrders));
            }

            count++;

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                LOGGER.trace("Sleep interrupted!", e);
            }
        } while (longOpenOrders == null || !longOpenOrders.getOpenOrders().isEmpty()
            || shortOpenOrders == null || !shortOpenOrders.getOpenOrders().isEmpty());

        LOGGER.info("Trades executed successfully!");
    }

    private String collectOpenOrders(Exchange exchange, OpenOrders openOrders) {
        String header = String.format("%s has the following open orders:\n", exchange.getExchangeSpecification().getExchangeName());

        return header + openOrders.getOpenOrders()
            .stream()
            .map(LimitOrder::toString)
            .collect(Collectors.joining("\n"));
    }

    private Optional<OpenOrders> fetchOpenOrders(Exchange exchange) {
        try {
            return Optional.of(exchange.getTradeService().getOpenOrders());
        } catch (IOException e) {
            LOGGER.error("{} threw IOException while fetching open orders: ",
                exchange.getExchangeSpecification().getExchangeName(), e);
        }

        return Optional.empty();
    }

    private BigDecimal computeSpread(BigDecimal longPrice, BigDecimal shortPrice) {
        BigDecimal scaledLongPrice = longPrice.setScale(BTC_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal scaledShortPrice = shortPrice.setScale(BTC_SCALE, RoundingMode.HALF_EVEN);

        return (scaledShortPrice.subtract(scaledLongPrice)).divide(scaledLongPrice, RoundingMode.HALF_EVEN);
    }

    BigDecimal getVolumeForOrder(Exchange exchange, CurrencyPair currencyPair, String orderId, BigDecimal defaultVolume) {
        try {
            LOGGER.debug("{}: Attempting to fetch volume from order by ID: {}", exchange.getExchangeSpecification().getExchangeName(), orderId);
            BigDecimal volume = Optional.ofNullable(exchange.getTradeService().getOrder(orderId))
                .orElseThrow(() -> new NotAvailableFromExchangeException(orderId))
                .stream()
                .findFirst()
                .orElseThrow(() -> new OrderNotFoundException(orderId))
                .getOriginalAmount();

            LOGGER.debug("{}: Order {} volume is: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                orderId,
                volume);

            if (volume == null || volume.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Volume must be more than zero.");
            }

            LOGGER.debug("{}: Using volume: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                orderId);

            return volume;
        } catch (NotAvailableFromExchangeException e) {
            LOGGER.debug("{}: Does not support fetching orders by ID", exchange.getExchangeSpecification().getExchangeName());
        } catch (IOException e) {
            LOGGER.warn("{}: Unable to fetch order {}", exchange.getExchangeSpecification().getExchangeName(), orderId, e);
        } catch (IllegalStateException e) {
            LOGGER.debug(e.getMessage());
        }

        try {
            BigDecimal balance = getAccountBalance(exchange, currencyPair.base);

            if (BigDecimal.ZERO.compareTo(balance) < 0) {
                LOGGER.debug("{}: Using {} balance: {}", exchange.getExchangeSpecification().getExchangeName(), currencyPair.base.toString(), balance);

                return balance;
            }
        } catch (IOException e) {
            LOGGER.warn("{}: Unable to fetch {} account balance", exchange.getExchangeSpecification().getExchangeName(), currencyPair.base.toString(), e);
        }

        LOGGER.debug("{}: Falling back to default volume: {}",
            exchange.getExchangeSpecification().getExchangeName(),
            defaultVolume);

        return defaultVolume;
    }

    BigDecimal getLimitPrice(Exchange exchange, CurrencyPair rawCurrencyPair, BigDecimal allowedVolume, Order.OrderType orderType) {
        CurrencyPair currencyPair = exchangeService.convertExchangePair(exchange, rawCurrencyPair);

        try {
            OrderBook orderBook = exchange.getMarketDataService().getOrderBook(currencyPair);
            List<LimitOrder> orders = orderType.equals(Order.OrderType.ASK) ? orderBook.getAsks() : orderBook.getBids();
            BigDecimal price;
            BigDecimal volume = BigDecimal.ZERO;

            for (LimitOrder order : orders) {
                price = order.getLimitPrice();
                volume = volume.add(order.getRemainingAmount());

                if (volume.compareTo(allowedVolume) > 0) {
                    return price;
                }
            }
        } catch (IOException e) {
            LOGGER.error("IOE fetching {} {} order volume", exchange.getExchangeSpecification().getExchangeName(), currencyPair, e);
        }

        throw new RuntimeException("Not enough liquidity on exchange to fulfill required volume!");
    }

    BigDecimal getMaximumExposure(Exchange ... exchanges) {
        if (tradingConfiguration.getFixedExposure() != null) {
            return tradingConfiguration.getFixedExposure();
        } else {
            BigDecimal smallestBalance = Arrays.stream(exchanges)
                .parallel()
                .map(exchange -> {
                    try {
                        return getAccountBalance(exchange);
                    } catch (IOException e) {
                        LOGGER.trace("IOException fetching {} account balance",
                                exchange.getExchangeSpecification().getExchangeName());
                    }

                    return BigDecimal.ZERO;
                })
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

            BigDecimal exposure = smallestBalance
                    .multiply(TRADE_PORTION)
                    .setScale(DecimalConstants.USD_SCALE, RoundingMode.HALF_EVEN);

            LOGGER.debug("Maximum exposure for {}: {}", exchanges, exposure);

            return exposure;
        }
    }

    private BigDecimal logCurrentExchangeBalances(Exchange longExchange, Exchange shortExchange) {
        try {
            BigDecimal longBalance = getAccountBalance(longExchange);
            BigDecimal shortBalance = getAccountBalance(shortExchange);
            BigDecimal totalBalance = longBalance.add(shortBalance);

            LOGGER.info("Updated account balances: {} ${} + {} ${} = ${}",
                    longExchange.getExchangeSpecification().getExchangeName(),
                    longBalance,
                    shortExchange.getExchangeSpecification().getExchangeName(),
                    shortBalance,
                    totalBalance);

            return totalBalance;
        } catch (IOException e) {
            LOGGER.error("IOE fetching account balances: ", e);
        }

        return BigDecimal.ZERO;
    }

    BigDecimal getAccountBalance(Exchange exchange, Currency currency, int scale) throws IOException {
        AccountService accountService = exchange.getAccountService();

        for (Wallet wallet : accountService.getAccountInfo().getWallets().values()) {
            if (wallet.getBalances().containsKey(currency)) {
                return wallet.getBalance(currency).getAvailable()
                        .setScale(scale, RoundingMode.HALF_EVEN);
            }
        }

        LOGGER.error("{}: Unable to fetch {} balance",
            exchange.getExchangeSpecification().getExchangeName(),
            currency.getCurrencyCode());

        return BigDecimal.ZERO;
    }

    private BigDecimal getAccountBalance(Exchange exchange, Currency currency) throws IOException {
        return getAccountBalance(exchange, currency, USD_SCALE);
    }

    private BigDecimal getAccountBalance(Exchange exchange) throws IOException {
        Currency currency = exchangeService.getExchangeHomeCurrency(exchange);

        return getAccountBalance(exchange, currency);
    }

    /*
     * The formula is: step * round(input / step)
     * All the BigDecimals make it really hard to read. We're using setScale() instead of round() because you can't
     * set the precision on round() to zero. You can do it with setScale() and it will implicitly do the rounding.
     */
    static BigDecimal roundByStep(BigDecimal input, BigDecimal step) {
        LOGGER.info("input = {} step = {}", input, step);

        BigDecimal result = input
            .divide(step, RoundingMode.HALF_EVEN)
            .setScale(0, RoundingMode.HALF_EVEN)
            .multiply(step)
            .setScale(step.scale(), RoundingMode.HALF_EVEN);

        LOGGER.info("result = {}", result);

        return result;
    }

    private boolean isTradeExpired() {
        if (tradingConfiguration.getTradeTimeout() == null || activePosition == null || activePosition.getEntryTime() == null) {
            return false;
        }

        return activePosition.getEntryTime().plusHours(tradingConfiguration.getTradeTimeout()).isBefore(OffsetDateTime.now());
    }

    private Spread computeSpread(TradeCombination tradeCombination) {
        Exchange longExchange = tradeCombination.getLongExchange();
        Exchange shortExchange = tradeCombination.getShortExchange();
        CurrencyPair currencyPair = tradeCombination.getCurrencyPair();

        Ticker longTicker = tickerService.getTicker(longExchange, currencyPair);
        Ticker shortTicker = tickerService.getTicker(shortExchange, currencyPair);

        if (tickerService.isInvalidTicker(longTicker) || tickerService.isInvalidTicker(shortTicker)) {
            return null;
        }

        BigDecimal spreadIn = computeSpread(longTicker.getAsk(), shortTicker.getBid());
        BigDecimal spreadOut = computeSpread(longTicker.getBid(), shortTicker.getAsk());

        Spread spread = new Spread(
            currencyPair,
            longExchange,
            shortExchange,
            longTicker,
            shortTicker,
            spreadIn,
            spreadOut);

        spreadService.publish(spread);

        return spread;
    }

    protected void persistArbitrageToCsvFile(ArbitrageLog arbitrageLog) {
        final File csvFile = new File(TRADE_HISTORY_FILE);

        try {
            if (!csvFile.exists()) {
                // Add headers first
                FileUtils.write(csvFile, arbitrageLog.csvHeaders(), StandardCharsets.UTF_8, csvFile.exists());
            }
            FileUtils.write(csvFile, arbitrageLog.toCsv(), StandardCharsets.UTF_8, csvFile.exists());
        }
        catch (IOException e) {
            LOGGER.error("Unable to log the trade into the csv file. Reason: {}", e.getMessage());
        }
    }
}
