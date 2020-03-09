package com.r307.arbitrader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r307.arbitrader.DecimalConstants;
import com.r307.arbitrader.exception.OrderNotFoundException;
import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.service.model.ActivePosition;
import com.r307.arbitrader.service.model.TradeCombination;
import com.r307.arbitrader.service.ticker.TickerStrategy;
import org.apache.commons.collections4.CollectionUtils;
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
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String STATE_FILE = ".arbitrader/arbitrader-state.json";

    private static final BigDecimal TRADE_PORTION = new BigDecimal("0.9");
    private static final BigDecimal TRADE_REMAINDER = BigDecimal.ONE.subtract(TRADE_PORTION);

    private static final CurrencyPairMetaData NULL_CURRENCY_PAIR_METADATA = new CurrencyPairMetaData(
        null, null, null, null, null);
    private static final CurrencyPairMetaData DEFAULT_CURRENCY_PAIR_METADATA = new CurrencyPairMetaData(
        new BigDecimal("0.0030"), null, null, BTC_SCALE, null);

    private TradingConfiguration tradingConfiguration;
    private ExchangeFeeCache feeCache;
    private ConditionService conditionService;
    private ExchangeService exchangeService;
    private ErrorCollectorService errorCollectorService;
    private TickerService tickerService;
    private Map<String, TickerStrategy> tickerStrategies;
    private List<Exchange> exchanges = new ArrayList<>();
    private List<TradeCombination> tradeCombinations = new ArrayList<>();
    private Map<String, Ticker> allTickers = new HashMap<>();
    private Map<String, BigDecimal> minSpread = new HashMap<>();
    private Map<String, BigDecimal> maxSpread = new HashMap<>();
    private Map<TradeCombination, BigDecimal> missedTrades = new HashMap<>();
    private boolean bailOut = false;
    private ActivePosition activePosition = null;

    public TradingService(
        TradingConfiguration tradingConfiguration,
        ExchangeFeeCache feeCache,
        ConditionService conditionService,
        ExchangeService exchangeService,
        ErrorCollectorService errorCollectorService,
        TickerService tickerService,
        Map<String, TickerStrategy> tickerStrategies) {

        this.tradingConfiguration = tradingConfiguration;
        this.feeCache = feeCache;
        this.conditionService = conditionService;
        this.exchangeService = exchangeService;
        this.errorCollectorService = errorCollectorService;
        this.tickerService = tickerService;
        this.tickerStrategies = tickerStrategies;
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

            exchanges.add(ExchangeFactory.INSTANCE.createExchange(specification));
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

            BigDecimal tradingFee = getExchangeFee(exchange, exchangeService.convertExchangePair(exchange, CurrencyPair.BTC_USD), false);

            LOGGER.info("{} {} trading fee: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                exchangeService.convertExchangePair(exchange, CurrencyPair.BTC_USD),
                tradingFee);
        });

        LOGGER.info("Fetching all tickers for all exchanges...");

        allTickers.clear();
        exchanges.forEach(exchange -> tickerService.getTickers(exchange, exchangeService.getExchangeMetadata(exchange).getTradingPairs())
                .forEach(ticker -> allTickers.put(tickerKey(exchange, ticker.getCurrencyPair()), ticker)));

        LOGGER.info("Trading the following exchanges and pairs:");

        exchanges.forEach(longExchange -> exchanges.forEach(shortExchange -> {
            // get the pairs common to both exchanges
            Collection<CurrencyPair> currencyPairs = CollectionUtils.intersection(
                exchangeService.getExchangeMetadata(longExchange).getTradingPairs(),
                exchangeService.getExchangeMetadata(shortExchange).getTradingPairs());

            currencyPairs.forEach(currencyPair -> {
                if (isInvalidExchangePair(longExchange, shortExchange, currencyPair)) {
                    return;
                }

                Ticker longTicker = allTickers.get(tickerKey(longExchange, currencyPair));
                Ticker shortTicker = allTickers.get(tickerKey(shortExchange, currencyPair));

                if (isInvalidTicker(longTicker) || isInvalidTicker(shortTicker)) {
                    return;
                }

                TradeCombination combination = new TradeCombination(longExchange, shortExchange, currencyPair);

                tradeCombinations.add(combination);

                LOGGER.info("{}", combination);
            });
        }));

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
                    activePosition = OBJECT_MAPPER.readValue(stateFile, ActivePosition.class);

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
    @Scheduled(cron = "0 0 0/6 * * *")
    public void summary() {
        LOGGER.info("Summary: [Long/Short Exchanges] [Pair] [Current Spread] -> [{} Spread Target]", (activePosition != null ? "Exit" : "Entry"));

        tradeCombinations.forEach(tradeCombination -> {
            Exchange longExchange = tradeCombination.getLongExchange();
            Exchange shortExchange = tradeCombination.getShortExchange();
            CurrencyPair currencyPair = tradeCombination.getCurrencyPair();

            Ticker longTicker = allTickers.get(tickerKey(longExchange, currencyPair));
            Ticker shortTicker = allTickers.get(tickerKey(shortExchange, currencyPair));

            if (isInvalidTicker(longTicker) || isInvalidTicker(shortTicker)) {
                return;
            }

            BigDecimal spreadIn = computeSpread(longTicker.getAsk(), shortTicker.getBid());
            BigDecimal spreadOut = computeSpread(longTicker.getBid(), shortTicker.getAsk());

            if (activePosition == null && BigDecimal.ZERO.compareTo(spreadIn) < 0) {
                LOGGER.info("{}/{} {} {} -> {}",
                    longExchange.getExchangeSpecification().getExchangeName(),
                    shortExchange.getExchangeSpecification().getExchangeName(),
                    currencyPair,
                    spreadIn,
                    tradingConfiguration.getEntrySpread());
            } else if (activePosition != null
                && activePosition.getCurrencyPair().equals(currencyPair)
                && activePosition.getLongTrade().getExchange().equals(longExchange.getExchangeSpecification().getExchangeName())
                && activePosition.getShortTrade().getExchange().equals(shortExchange.getExchangeSpecification().getExchangeName())) {

                LOGGER.info("{}/{} {} {} -> {}",
                    longExchange.getExchangeSpecification().getExchangeName(),
                    shortExchange.getExchangeSpecification().getExchangeName(),
                    currencyPair,
                    spreadOut,
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

        // fetch all the configured tickers for each exchange
        allTickers.clear();

        // find the currency pairs for each exchange that are both configured and actually in use for trading
        // only fetch those tickers that are being used, to try to avoid API rate limiting
        exchanges
            .parallelStream()
            .forEach(exchange -> {
                List<CurrencyPair> activePairs = tradeCombinations
                    .parallelStream()
                    .filter(tradeCombination -> tradeCombination.getLongExchange().equals(exchange) || tradeCombination.getShortExchange().equals(exchange))
                    .map(TradeCombination::getCurrencyPair)
                    .distinct()
                    .collect(Collectors.toList());

                try {
                    LOGGER.trace("{} fetching tickers for: {}", exchange.getExchangeSpecification().getExchangeName(), activePairs);

                    tickerService.getTickers(exchange, activePairs)
                        .forEach(ticker -> allTickers.put(tickerKey(exchange, ticker.getCurrencyPair()), ticker));
                } catch (ExchangeException e) {
                    LOGGER.warn("Failed to fetch ticker for {}", exchange.getExchangeSpecification().getExchangeName());
                }
            });

        long exchangePollStartTime = System.currentTimeMillis();

        // If everything is always evaluated in the same order, earlier exchange/pair combos have a higher chance of
        // executing trades than ones at the end of the list.
        Collections.shuffle(tradeCombinations);

        tradeCombinations.forEach(tradeCombination -> {
            Exchange longExchange = tradeCombination.getLongExchange();
            Exchange shortExchange = tradeCombination.getShortExchange();
            CurrencyPair currencyPair = tradeCombination.getCurrencyPair();

            Ticker longTicker = allTickers.get(tickerKey(longExchange, currencyPair));
            Ticker shortTicker = allTickers.get(tickerKey(shortExchange, currencyPair));

            // if we couldn't get a ticker for either exchange, bail out
            if (isInvalidTicker(longTicker) || isInvalidTicker(shortTicker)) {
                return;
            }

            BigDecimal spreadIn = computeSpread(longTicker.getAsk(), shortTicker.getBid());
            BigDecimal spreadOut = computeSpread(longTicker.getBid(), shortTicker.getAsk());

            LOGGER.debug("Long/Short: {}/{} {} {}",
                    longExchange.getExchangeSpecification().getExchangeName(),
                    shortExchange.getExchangeSpecification().getExchangeName(),
                    currencyPair,
                    spreadIn);

            if (activePosition != null
                && spreadIn.compareTo(tradingConfiguration.getEntrySpread()) <= 0
                && missedTrades.containsKey(tradeCombination)) {

                LOGGER.info("{} has exited entry threshold: {}", tradeCombination, spreadIn);

                missedTrades.remove(tradeCombination);
            }

            if (!bailOut && !conditionService.isForceCloseCondition() && spreadIn.compareTo(tradingConfiguration.getEntrySpread()) > 0) {
                if (activePosition != null) {
                    if (!activePosition.getCurrencyPair().equals(currencyPair)
                        || !activePosition.getLongTrade().getExchange().equals(longExchange.getExchangeSpecification().getExchangeName())
                        || !activePosition.getShortTrade().getExchange().equals(shortExchange.getExchangeSpecification().getExchangeName())) {

                        if (!missedTrades.containsKey(tradeCombination)) {
                            LOGGER.info("{} has entered entry threshold: {}", tradeCombination, spreadIn);

                            missedTrades.put(tradeCombination, spreadIn);
                        }
                    }

                    return;
                }

                BigDecimal longFees = getExchangeFee(longExchange, currencyPair, true);
                BigDecimal shortFees = getExchangeFee(shortExchange, currencyPair, true);

                BigDecimal fees = (longFees.add(shortFees))
                        .multiply(new BigDecimal("2.0"));

                BigDecimal exitTarget = spreadIn
                        .subtract(tradingConfiguration.getExitTarget())
                        .subtract(fees);

                BigDecimal maxExposure = getMaximumExposure(longExchange, shortExchange);

                BigDecimal longMinAmount = longExchange.getExchangeMetaData().getCurrencyPairs().getOrDefault(exchangeService.convertExchangePair(longExchange, currencyPair), NULL_CURRENCY_PAIR_METADATA).getMinimumAmount();
                BigDecimal shortMinAmount = shortExchange.getExchangeMetaData().getCurrencyPairs().getOrDefault(exchangeService.convertExchangePair(shortExchange, currencyPair), NULL_CURRENCY_PAIR_METADATA).getMinimumAmount();

                if (longMinAmount == null) {
                    longMinAmount = new BigDecimal("0.001");
                }

                if (shortMinAmount == null) {
                    shortMinAmount = new BigDecimal("0.001");
                }

                if (maxExposure.compareTo(longMinAmount) <= 0) {
                    LOGGER.error("{} must have more than ${} to trade {}",
                        longExchange.getExchangeSpecification().getExchangeName(),
                        longMinAmount.add(longMinAmount.multiply(TRADE_REMAINDER)),
                        exchangeService.convertExchangePair(longExchange, currencyPair));
                    return;
                }

                if (maxExposure.compareTo(shortMinAmount) <= 0) {
                    LOGGER.error("{} must have more than ${} to trade {}",
                        shortExchange.getExchangeSpecification().getExchangeName(),
                        shortMinAmount.add(shortMinAmount.multiply(TRADE_REMAINDER)),
                        exchangeService.convertExchangePair(shortExchange, currencyPair));
                    return;
                }

                int longScale = longExchange.getExchangeMetaData().getCurrencyPairs().compute(exchangeService.convertExchangePair(longExchange, currencyPair), this::getScale).getPriceScale();
                int shortScale = longExchange.getExchangeMetaData().getCurrencyPairs().compute(exchangeService.convertExchangePair(shortExchange, currencyPair), this::getScale).getPriceScale();

                BigDecimal longVolume = maxExposure.divide(longTicker.getAsk(), longScale, RoundingMode.HALF_EVEN);
                BigDecimal shortVolume = maxExposure.divide(shortTicker.getBid(), shortScale, RoundingMode.HALF_EVEN);

                BigDecimal longStepSize = longExchange.getExchangeMetaData().getCurrencyPairs().getOrDefault(exchangeService.convertExchangePair(longExchange, currencyPair), NULL_CURRENCY_PAIR_METADATA).getAmountStepSize();
                BigDecimal shortStepSize = shortExchange.getExchangeMetaData().getCurrencyPairs().getOrDefault(exchangeService.convertExchangePair(shortExchange, currencyPair), NULL_CURRENCY_PAIR_METADATA).getAmountStepSize();

                if (longStepSize != null) {
                    longVolume = roundByStep(longVolume, longStepSize);
                }

                if (shortStepSize != null) {
                    shortVolume = roundByStep(shortVolume, shortStepSize);
                }

                BigDecimal longLimitPrice;
                BigDecimal shortLimitPrice;

                try {
                    longLimitPrice = getLimitPrice(longExchange, currencyPair, longVolume, Order.OrderType.ASK);
                    shortLimitPrice = getLimitPrice(shortExchange, currencyPair, shortVolume, Order.OrderType.BID);
                } catch (ExchangeException e) {
                    LOGGER.warn("Failed to fetch order books for {}/{} to compute entry prices: {}",
                        longExchange.getExchangeSpecification().getExchangeName(),
                        shortExchange.getDefaultExchangeSpecification().getExchangeName(),
                        e.getMessage());
                    return;
                }

                BigDecimal spreadVerification = computeSpread(longLimitPrice, shortLimitPrice);

                if (spreadVerification.compareTo(tradingConfiguration.getEntrySpread()) < 0) {
                    LOGGER.debug("Not enough liquidity to execute both trades profitably");
                } else if (conditionService.isBlackoutCondition(longExchange) || conditionService.isBlackoutCondition(shortExchange)) {
                    LOGGER.warn("Cannot open position on one or more exchanges due to user configured blackout");
                } else {
                    LOGGER.info("***** ENTRY *****");

                    BigDecimal totalBalance = logCurrentExchangeBalances(longExchange, shortExchange);

                    LOGGER.info("Entry spread: {}", spreadIn);
                    LOGGER.info("Exit spread target: {}", exitTarget);
                    LOGGER.info("Long entry: {} {} {} @ {} ({} slip) = {}{}",
                            longExchange.getExchangeSpecification().getExchangeName(),
                            currencyPair,
                            longVolume,
                            longLimitPrice,
                            longLimitPrice.subtract(longTicker.getAsk()),
                            Currency.USD.getSymbol(),
                            longVolume.multiply(longLimitPrice));
                    LOGGER.info("Short entry: {} {} {} @ {} ({} slip) = {}{}",
                            shortExchange.getExchangeSpecification().getExchangeName(),
                            currencyPair,
                            shortVolume,
                            shortLimitPrice,
                            shortTicker.getBid().subtract(shortLimitPrice),
                            Currency.USD.getSymbol(),
                            shortVolume.multiply(shortLimitPrice));

                    try {
                        activePosition = new ActivePosition();
                        activePosition.setEntryTime(OffsetDateTime.now());
                        activePosition.setCurrencyPair(currencyPair);
                        activePosition.setExitTarget(exitTarget);
                        activePosition.setEntryBalance(totalBalance);
                        activePosition.getLongTrade().setExchange(longExchange);
                        activePosition.getLongTrade().setVolume(longVolume);
                        activePosition.getLongTrade().setEntry(longLimitPrice);
                        activePosition.getShortTrade().setExchange(shortExchange);
                        activePosition.getShortTrade().setVolume(shortVolume);
                        activePosition.getShortTrade().setEntry(shortLimitPrice);

                        executeOrderPair(
                                longExchange, shortExchange,
                                currencyPair,
                                longLimitPrice, shortLimitPrice,
                                longVolume, shortVolume,
                                true);
                    } catch (IOException e) {
                        LOGGER.error("IOE executing limit orders: ", e);
                        activePosition = null;
                    }

                    try {
                        FileUtils.write(new File(STATE_FILE), OBJECT_MAPPER.writeValueAsString(activePosition), Charset.defaultCharset());
                    } catch (IOException e) {
                        LOGGER.error("Unable to write state file!", e);
                    }
                }
            } else if (activePosition != null
                    && currencyPair.equals(activePosition.getCurrencyPair())
                    && longExchange.getExchangeSpecification().getExchangeName().equals(activePosition.getLongTrade().getExchange())
                    && shortExchange.getExchangeSpecification().getExchangeName().equals(activePosition.getShortTrade().getExchange())
                    && (spreadOut.compareTo(activePosition.getExitTarget()) < 0 || conditionService.isForceCloseCondition() || isTradeExpired())) {

                BigDecimal longVolume = getVolumeForOrder(
                    longExchange,
                    currencyPair,
                    activePosition.getLongTrade().getOrderId(),
                    activePosition.getLongTrade().getVolume());
                BigDecimal shortVolume = getVolumeForOrder(
                    shortExchange,
                    currencyPair,
                    activePosition.getShortTrade().getOrderId(),
                    activePosition.getShortTrade().getVolume());

                LOGGER.debug("Volumes: {}/{}", longVolume, shortVolume);

                BigDecimal longLimitPrice = getLimitPrice(longExchange, currencyPair, longVolume, Order.OrderType.BID);
                BigDecimal shortLimitPrice = getLimitPrice(shortExchange, currencyPair, shortVolume, Order.OrderType.ASK);

                LOGGER.debug("Limit prices: {}/{}", longLimitPrice, shortLimitPrice);

                BigDecimal spreadVerification = computeSpread(longLimitPrice, shortLimitPrice);

                LOGGER.debug("Spread verification: {}", spreadVerification);

                if (longVolume.compareTo(BigDecimal.ZERO) <= 0 || shortVolume.compareTo(BigDecimal.ZERO) <= 0) {
                    LOGGER.error("Computed trade volume for exiting position was zero!");
                }

                if (conditionService.isBlackoutCondition(longExchange) || conditionService.isBlackoutCondition(shortExchange)) {
                    LOGGER.warn("Cannot exit position on one or more exchanges due to user configured blackout");
                } else if (!conditionService.isForceCloseCondition() && spreadVerification.compareTo(activePosition.getExitTarget()) > 0) {
                    LOGGER.debug("Not enough liquidity to execute both trades profitably!");
                } else {
                    if (isTradeExpired()) {
                        if (spreadVerification.compareTo(tradingConfiguration.getEntrySpread()) < 0) {
                            LOGGER.debug("Not exiting for timeout because it would immediately re-enter");
                            return;
                        }

                        LOGGER.warn("***** TIMEOUT EXIT *****");
                    } else if (conditionService.isForceCloseCondition()) {
                        LOGGER.warn("***** FORCED EXIT *****");
                    } else {
                        LOGGER.info("***** EXIT *****");
                    }

                    try {
                        LOGGER.info("Long close: {} {} {} @ {} ({} slip) = {}{}",
                                longExchange.getExchangeSpecification().getExchangeName(),
                                currencyPair,
                                longVolume,
                                longLimitPrice,
                                longLimitPrice.subtract(longTicker.getBid()),
                                Currency.USD.getSymbol(),
                                longVolume.multiply(longTicker.getBid()));
                        LOGGER.info("Short close: {} {} {} @ {} ({} slip) = {}{}",
                                shortExchange.getExchangeSpecification().getExchangeName(),
                                currencyPair,
                                shortVolume,
                                shortLimitPrice,
                                shortTicker.getAsk().subtract(shortLimitPrice),
                                Currency.USD.getSymbol(),
                                shortVolume.multiply(shortTicker.getAsk()));

                        executeOrderPair(
                                longExchange, shortExchange,
                                currencyPair,
                                longLimitPrice, shortLimitPrice,
                                longVolume, shortVolume,
                                false);
                    } catch (IOException e) {
                        LOGGER.error("IOE executing limit orders: ", e);
                    }

                    LOGGER.info("Combined account balances on entry: ${}", activePosition.getEntryBalance());
                    BigDecimal updatedBalance = logCurrentExchangeBalances(longExchange, shortExchange);
                    LOGGER.info("Profit calculation: ${} - ${} = ${}",
                        updatedBalance,
                        activePosition.getEntryBalance(),
                        updatedBalance.subtract(activePosition.getEntryBalance()));

                    activePosition = null;

                    FileUtils.deleteQuietly(new File(STATE_FILE));

                    if (conditionService.isForceCloseCondition()) {
                        conditionService.clearForceCloseCondition();
                    }
                }
            }

            String spreadKey = spreadKey(longExchange, shortExchange, currencyPair);

            minSpread.put(spreadKey, spreadIn.min(minSpread.getOrDefault(spreadKey, BigDecimal.valueOf(1))));
            maxSpread.put(spreadKey, spreadIn.max(maxSpread.getOrDefault(spreadKey, BigDecimal.valueOf(-1))));
            minSpread.put(spreadKey, spreadOut.min(minSpread.getOrDefault(spreadKey, BigDecimal.valueOf(1))));
            maxSpread.put(spreadKey, spreadOut.max(maxSpread.getOrDefault(spreadKey, BigDecimal.valueOf(-1))));
        });

        long exchangePollDuration = System.currentTimeMillis() - exchangePollStartTime;

        if (exchangePollDuration > 3000) {
            LOGGER.warn("Polling exchanges took {} ms", exchangePollDuration);
        }
    }

    private String tickerKey(Exchange exchange, CurrencyPair currencyPair) {
        return String.format("%s:%s",
                exchange.getExchangeSpecification().getExchangeName(),
            exchangeService.convertExchangePair(exchange, currencyPair));
    }

    private static String spreadKey(Exchange longExchange, Exchange shortExchange, CurrencyPair currencyPair) {
        return String.format("%s:%s:%s",
                longExchange.getExchangeSpecification().getExchangeName(),
                shortExchange.getExchangeSpecification().getExchangeName(),
                currencyPair);
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

    private static String tradeCombination(Exchange longExchange, Exchange shortExchange, CurrencyPair currencyPair) {
        return String.format("%s:%s:%s",
                longExchange.getExchangeSpecification().getExchangeName(),
                shortExchange.getExchangeSpecification().getExchangeName(),
                currencyPair);
    }

    private boolean isInvalidExchangePair(Exchange longExchange, Exchange shortExchange, CurrencyPair currencyPair) {
        // both exchanges are the same
        if (longExchange == shortExchange) {
            return true;
        }

        // the "short" exchange doesn't support margin
        if (!exchangeService.getExchangeMetadata(shortExchange).getMargin()) {
            return true;
        }

        // the "short" exchange doesn't support margin on this currency pair
        if (exchangeService.getExchangeMetadata(shortExchange).getMarginExclude().contains(currencyPair)) {
            return true;
        }

        // this specific combination of exchanges/currency has been blocked in the configuration
        //noinspection RedundantIfStatement
        if (tradingConfiguration.getTradeBlacklist().contains(tradeCombination(longExchange, shortExchange, currencyPair))) {
            return true;
        }

        return false;
    }

    private boolean isInvalidTicker(Ticker ticker) {
        return ticker == null || ticker.getBid() == null || ticker.getAsk() == null;
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
            longOpenOrders = longExchange.getTradeService().getOpenOrders();
            shortOpenOrders = shortExchange.getTradeService().getOpenOrders();

            if (count++ % 10 == 0) {
                if (!longOpenOrders.getOpenOrders().isEmpty()) {
                    LOGGER.warn("{} limit order has not yet filled", longExchange.getExchangeSpecification().getExchangeName());
                }

                if (!shortOpenOrders.getOpenOrders().isEmpty()) {
                    LOGGER.warn("{} limit order has not yet filled", shortExchange.getExchangeSpecification().getExchangeName());
                }
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                LOGGER.trace("Sleep interrupted!", e);
            }
        } while (!longOpenOrders.getOpenOrders().isEmpty() || !shortOpenOrders.getOpenOrders().isEmpty());

        LOGGER.info("Trades executed successfully!");
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
            LOGGER.error("IOE", e);
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

    private CurrencyPairMetaData getScale(@SuppressWarnings("unused") CurrencyPair currencyPair, CurrencyPairMetaData metaData) {
        // NOTE: Based on the data structures it appears that getBaseScale() is what we would want to use here
        // but based on inspecting the actual live metadata for several currencies on several different exchanges
        // it appears that getBaseScale() is always null and getPriceScale() gives the number of decimal places
        // for the **base** currency (e.g. XRP), not the counter (e.g. USD). So that's weird, but we'll try it.
        if (metaData != null && metaData.getPriceScale() != null) {
            return metaData;
        }

        return DEFAULT_CURRENCY_PAIR_METADATA; // defaults to BTC_SCALE
    }

    static BigDecimal roundByStep(BigDecimal input, BigDecimal step) {
        return input
            .divide(step, RoundingMode.HALF_EVEN)
            .round(MathContext.DECIMAL64)
            .multiply(step);
    }

    private boolean isTradeExpired() {
        if (tradingConfiguration.getTradeTimeout() == null || activePosition == null || activePosition.getEntryTime() == null) {
            return false;
        }

        return activePosition.getEntryTime().plusHours(tradingConfiguration.getTradeTimeout()).isAfter(OffsetDateTime.now());
    }
}
