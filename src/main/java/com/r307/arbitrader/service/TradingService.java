package com.r307.arbitrader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.r307.arbitrader.DecimalConstants;
import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.exception.OrderNotFoundException;
import com.r307.arbitrader.config.ExchangeConfiguration;
import com.r307.arbitrader.config.TradingConfiguration;
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
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.CurrencyPairsParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import si.mazi.rescu.AwareException;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;
import static com.r307.arbitrader.DecimalConstants.USD_SCALE;

@Component
public class TradingService {
    public static final String METADATA_KEY = "arbitrader-metadata";

    private static final Logger LOGGER = LoggerFactory.getLogger(TradingService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String STATE_FILE = ".arbitrader/arbitrader-state.json";

    private TradingConfiguration tradingConfiguration;
    private NotificationConfiguration notificationConfiguration;
    private List<Exchange> exchanges = new ArrayList<>();
    private Map<String, Ticker> allTickers = new HashMap<>();
    private Map<String, BigDecimal> minSpread = new HashMap<>();
    private Map<String, BigDecimal> maxSpread = new HashMap<>();
    private boolean bailOut = false;
    private ActivePosition activePosition = null;
    private String lastMissedWarning = null;

    public TradingService(
        TradingConfiguration tradingConfiguration,
        NotificationConfiguration notificationConfiguration) {

        this.tradingConfiguration = tradingConfiguration;
        this.notificationConfiguration = notificationConfiguration;
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
                specification.setPort( exchangeMetadata.getPort());
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
                        getExchangeHomeCurrency(exchange));
                LOGGER.info("{} balance: {}{}",
                        exchange.getExchangeSpecification().getExchangeName(),
                        getExchangeHomeCurrency(exchange).getSymbol(),
                        getAccountBalance(exchange));
            } catch (IOException e) {
                LOGGER.error("Unable to fetch account balance: ", e);
            }

            try {
                CurrencyPairsParam param = () -> getExchangeMetadata(exchange).getTradingPairs();
                exchange.getMarketDataService().getTickers(param);
            } catch (NotYetImplementedForExchangeException e) {
                LOGGER.warn("{} does not implement MarketDataService.getTickers() and will fetch tickers " +
                                "individually instead. This may result in API rate limiting.",
                        exchange.getExchangeSpecification().getExchangeName());
            } catch (IOException e) {
                LOGGER.debug("IOException fetching tickers for: ", exchange.getExchangeSpecification().getExchangeName(), e);
            }

            BigDecimal tradingFee = getExchangeFee(exchange, convertExchangePair(exchange, CurrencyPair.BTC_USD), false);

            LOGGER.info("{} {} trading fee: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                convertExchangePair(exchange, CurrencyPair.BTC_USD),
                tradingFee);
        });

        LOGGER.info("Trading the following exchanges and pairs:");

        allTickers.clear();
        exchanges.forEach(exchange -> getTickers(exchange, getExchangeMetadata(exchange).getTradingPairs())
                .forEach(ticker -> allTickers.put(tickerKey(exchange, ticker.getCurrencyPair()), ticker)));

        exchanges.forEach(longExchange -> exchanges.forEach(shortExchange -> {
            // get the pairs common to both exchanges
            Collection<CurrencyPair> currencyPairs = CollectionUtils.intersection(
                    getExchangeMetadata(longExchange).getTradingPairs(),
                    getExchangeMetadata(shortExchange).getTradingPairs());

            currencyPairs.forEach(currencyPair -> {
                if (isInvalidExchangePair(longExchange, shortExchange, currencyPair)) {
                    return;
                }

                Ticker longTicker = allTickers.get(tickerKey(longExchange, currencyPair));
                Ticker shortTicker = allTickers.get(tickerKey(shortExchange, currencyPair));

                if (isInvalidTicker(longTicker) || isInvalidTicker(shortTicker)) {
                    return;
                }

                LOGGER.info("{}/{} {}",
                        longExchange.getExchangeSpecification().getExchangeName(),
                        shortExchange.getExchangeSpecification().getExchangeName(),
                        currencyPair);
            });
        }));

        if (tradingConfiguration.getFixedExposure() != null) {
            LOGGER.info("Using fixed exposure of ${} as configured", tradingConfiguration.getFixedExposure());
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
                } catch (IOException e) {
                    LOGGER.error("Unable to parse state file {}: ", stateFile.getAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * Display a summary once every 6 hours with the current spreads.
     */
    @Scheduled(cron = "0 0 0/6 * * *")
    public void summary() {
        LOGGER.info("Summary: [Long/Short Exchanges] [Pair] [Current Spread] -> [{} Spread Target]", (activePosition != null ? "Exit" : "Entry"));

        exchanges.forEach(longExchange -> exchanges.forEach(shortExchange -> {
            // get the pairs common to both exchanges
            List<CurrencyPair> currencyPairs = new ArrayList<>(CollectionUtils.intersection(
                getExchangeMetadata(longExchange).getTradingPairs(),
                getExchangeMetadata(shortExchange).getTradingPairs()));

            currencyPairs.forEach(currencyPair -> {
                if (isInvalidExchangePair(longExchange, shortExchange, currencyPair)) {
                    return;
                }

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
        }));
    }

    @Scheduled(initialDelay = 5000, fixedRate = 3000)
    public void tick() {
        if (bailOut) {
            LOGGER.error("Exiting immediately to avoid erroneous trades.");
            System.exit(1);
        }

        // fetch all the configured tickers for each exchange
        allTickers.clear();

        exchanges
            .parallelStream()
            .forEach(exchange -> {
                try {
                    getTickers(exchange, getExchangeMetadata(exchange).getTradingPairs())
                        .forEach(ticker -> allTickers.put(tickerKey(exchange, ticker.getCurrencyPair()), ticker));
                } catch (ExchangeException e) {
                    LOGGER.warn("Failed to fetch ticker for {}", exchange.getExchangeSpecification().getExchangeName());
                }
            });

        long exchangePollStartTime = System.currentTimeMillis();

        // If everything is always evaluated in the same order, earlier exchange/pair combos have a higher chance of
        // executing trades than ones at the end of the list.
        Collections.shuffle(exchanges);

        exchanges.forEach(longExchange -> exchanges.forEach(shortExchange -> {
            // get the pairs common to both exchanges
            List<CurrencyPair> currencyPairs = new ArrayList<>(CollectionUtils.intersection(
                    getExchangeMetadata(longExchange).getTradingPairs(),
                    getExchangeMetadata(shortExchange).getTradingPairs()));

            Collections.shuffle(currencyPairs);

            currencyPairs.forEach(currencyPair -> {
                if (isInvalidExchangePair(longExchange, shortExchange, currencyPair)) {
                    return;
                }

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

                if (spreadIn.compareTo(tradingConfiguration.getEntrySpread()) > 0) {
                    BigDecimal longFees = getExchangeFee(longExchange, currencyPair, true);
                    BigDecimal shortFees = getExchangeFee(shortExchange, currencyPair, true);

                    BigDecimal fees = (longFees.add(shortFees))
                            .multiply(new BigDecimal(2.0));

                    BigDecimal exitTarget = spreadIn
                            .subtract(tradingConfiguration.getExitTarget())
                            .subtract(fees);

                    BigDecimal maxExposure = getMaximumExposure(longExchange, shortExchange);

                    if (maxExposure != null) {
                        BigDecimal longVolume = maxExposure.divide(longTicker.getAsk(), BTC_SCALE, RoundingMode.HALF_EVEN);
                        BigDecimal shortVolume = maxExposure.divide(shortTicker.getBid(), BTC_SCALE, RoundingMode.HALF_EVEN);
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
                        } else if (activePosition != null) {
                            String tradeCombination = tradeCombination(longExchange, shortExchange, currencyPair);

                            if (!tradeCombination.equals(lastMissedWarning)
                                    && !longExchange.getExchangeSpecification().getExchangeName().equals(activePosition.getLongTrade().getExchange())
                                    && !shortExchange.getExchangeSpecification().getExchangeName().equals(activePosition.getShortTrade().getExchange())) {

                                LOGGER.info("***** MISSED ENTRY *****");
                                LOGGER.info("Detected an entry opportunity but there are already positions open.");
                                LOGGER.info("{}/{} {} @ {}",
                                        longExchange.getExchangeSpecification().getExchangeName(),
                                        shortExchange.getExchangeSpecification().getExchangeName(),
                                        currencyPair,
                                        spreadIn);

                                lastMissedWarning = tradeCombination;
                            }
                        } else {
                            LOGGER.info("***** ENTRY *****");

                            logCurrentExchangeBalances(longExchange, shortExchange);

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
                                activePosition.setCurrencyPair(currencyPair);
                                activePosition.setExitTarget(exitTarget);
                                activePosition.getLongTrade().setExchange(longExchange);
                                activePosition.getLongTrade().setVolume(longVolume);
                                activePosition.getLongTrade().setEntry(longLimitPrice);
                                activePosition.getShortTrade().setExchange(shortExchange);
                                activePosition.getShortTrade().setVolume(shortVolume);
                                activePosition.getShortTrade().setEntry(shortLimitPrice);
                                lastMissedWarning = null;

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
                    } else {
                        LOGGER.warn("Will not trade: exposure could not be computed");
                    }
                } else if (activePosition != null
                        && currencyPair.equals(activePosition.getCurrencyPair())
                        && longExchange.getExchangeSpecification().getExchangeName().equals(activePosition.getLongTrade().getExchange())
                        && shortExchange.getExchangeSpecification().getExchangeName().equals(activePosition.getShortTrade().getExchange())
                        && spreadOut.compareTo(activePosition.getExitTarget()) < 0) {

                    BigDecimal longVolume = getVolumeForOrder(
                        longExchange,
                        activePosition.getLongTrade().getOrderId(),
                        activePosition.getLongTrade().getVolume());
                    BigDecimal shortVolume = getVolumeForOrder(
                        shortExchange,
                        activePosition.getShortTrade().getOrderId(),
                        activePosition.getShortTrade().getVolume());

                    BigDecimal longLimitPrice = getLimitPrice(longExchange, currencyPair, longVolume, Order.OrderType.BID);
                    BigDecimal shortLimitPrice = getLimitPrice(shortExchange, currencyPair, shortVolume, Order.OrderType.ASK);

                    BigDecimal spreadVerification = computeSpread(longLimitPrice, shortLimitPrice);

                    if (longVolume.compareTo(BigDecimal.ZERO) <= 0 || shortVolume.compareTo(BigDecimal.ZERO) <= 0) {
                        LOGGER.error("Computed trade volume for exiting position was zero!");
                    }

                    if (spreadVerification.compareTo(activePosition.getExitTarget()) > 0) {
                        LOGGER.debug("Not enough liquidity to execute both trades profitably");
                    } else {
                        LOGGER.info("***** EXIT *****");

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

                            BigDecimal longProfit = longVolume.multiply(longLimitPrice)
                                    .subtract(longVolume.multiply(activePosition.getLongTrade().getEntry()))
                                    .setScale(USD_SCALE, RoundingMode.HALF_EVEN);
                            BigDecimal shortProfit = shortVolume.multiply(activePosition.getShortTrade().getEntry())
                                    .subtract(shortVolume.multiply(shortLimitPrice))
                                    .setScale(USD_SCALE, RoundingMode.HALF_EVEN);

                            LOGGER.info("Estimated profit: (long) {}{} + (short) {}{} = {}{}",
                                    Currency.USD.getSymbol(),
                                    longProfit,
                                    Currency.USD.getSymbol(),
                                    shortProfit,
                                    Currency.USD.getSymbol(),
                                    longProfit.add(shortProfit));

                            executeOrderPair(
                                    longExchange, shortExchange,
                                    currencyPair,
                                    longLimitPrice, shortLimitPrice,
                                    longVolume, shortVolume,
                                    false);
                        } catch (IOException e) {
                            LOGGER.error("IOE executing limit orders: ", e);
                        }

                        logCurrentExchangeBalances(longExchange, shortExchange);

                        activePosition = null;

                        FileUtils.deleteQuietly(new File(STATE_FILE));
                    }
                }

                String spreadKey = spreadKey(longExchange, shortExchange, currencyPair);

                minSpread.put(spreadKey, spreadIn.min(minSpread.getOrDefault(spreadKey, BigDecimal.valueOf(1))));
                maxSpread.put(spreadKey, spreadIn.max(maxSpread.getOrDefault(spreadKey, BigDecimal.valueOf(-1))));
                minSpread.put(spreadKey, spreadOut.min(minSpread.getOrDefault(spreadKey, BigDecimal.valueOf(1))));
                maxSpread.put(spreadKey, spreadOut.max(maxSpread.getOrDefault(spreadKey, BigDecimal.valueOf(-1))));
            });
        }));

        long exchangePollDuration = System.currentTimeMillis() - exchangePollStartTime;

        if (exchangePollDuration > 3000) {
            LOGGER.warn("Polling exchanges took {} ms", exchangePollDuration);
        }
    }

    private static ExchangeConfiguration getExchangeMetadata(Exchange exchange) {
        return (ExchangeConfiguration) exchange.getExchangeSpecification().getExchangeSpecificParametersItem(METADATA_KEY);
    }

    private static String tickerKey(Exchange exchange, CurrencyPair currencyPair) {
        return String.format("%s:%s",
                exchange.getExchangeSpecification().getExchangeName(),
                convertExchangePair(exchange, currencyPair));
    }

    private static String spreadKey(Exchange longExchange, Exchange shortExchange, CurrencyPair currencyPair) {
        return String.format("%s:%s:%s",
                longExchange.getExchangeSpecification().getExchangeName(),
                shortExchange.getExchangeSpecification().getExchangeName(),
                currencyPair);
    }

    private static BigDecimal getExchangeFee(Exchange exchange, CurrencyPair currencyPair, boolean isQuiet) {
        try {
            Map<CurrencyPair, Fee> fees = exchange.getAccountService().getDynamicTradingFees();

            if (fees.containsKey(currencyPair)) {
                return fees.get(currencyPair).getMakerFee();
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

        CurrencyPairMetaData currencyPairMetaData = exchange.getExchangeMetaData().getCurrencyPairs().get(convertExchangePair(exchange, currencyPair));

        if (currencyPairMetaData == null || currencyPairMetaData.getTradingFee() == null) {
            BigDecimal configuredFee = getExchangeMetadata(exchange).getFee();

            if (configuredFee == null) {
                if (!isQuiet) {
                    LOGGER.error("{} has no fees configured. Setting default of 0.0030. Please configure the correct value!",
                            exchange.getExchangeSpecification().getExchangeName());
                }

                return new BigDecimal(0.0030);
            }

            if (!isQuiet) {
                LOGGER.warn("{} fees unavailable via API. Will use configured value.",
                        exchange.getExchangeSpecification().getExchangeName());
            }

            return configuredFee;
        }

        return currencyPairMetaData.getTradingFee();
    }

    private static Currency getExchangeHomeCurrency(Exchange exchange) {
        return getExchangeMetadata(exchange).getHomeCurrency();
    }

    private static CurrencyPair convertExchangePair(Exchange exchange, CurrencyPair currencyPair) {
        if (Currency.USD == currencyPair.base) {
            return new CurrencyPair(getExchangeHomeCurrency(exchange), currencyPair.counter);
        } else if (Currency.USD == currencyPair.counter) {
            return new CurrencyPair(currencyPair.base, getExchangeHomeCurrency(exchange));
        }

        return currencyPair;
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
        if (!getExchangeMetadata(shortExchange).getMargin()) {
            return true;
        }

        // the "short" exchange doesn't support margin on this currency pair
        if (getExchangeMetadata(shortExchange).getMarginExclude().contains(currencyPair)) {
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

        LimitOrder longLimitOrder = new LimitOrder.Builder(isPositionOpen ? Order.OrderType.BID : Order.OrderType.ASK, convertExchangePair(longExchange, currencyPair))
                .limitPrice(longLimitPrice)
                .originalAmount(longVolume)
                .build();
        LimitOrder shortLimitOrder = new LimitOrder.Builder(isPositionOpen ? Order.OrderType.ASK : Order.OrderType.BID, convertExchangePair(shortExchange, currencyPair))
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

        OpenOrders longOpenOrders = longExchange.getTradeService().getOpenOrders();
        OpenOrders shortOpenOrders = shortExchange.getTradeService().getOpenOrders();

        LOGGER.info("Waiting for limit orders to complete...");

        while (!longOpenOrders.getOpenOrders().isEmpty() && !shortOpenOrders.getOpenOrders().isEmpty()) {
            longOpenOrders = longExchange.getTradeService().getOpenOrders();
            shortOpenOrders = shortExchange.getTradeService().getOpenOrders();

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                LOGGER.trace("Sleep interrupted!", e);
            }
        }

        LOGGER.info("Trades executed successfully!");
    }

    private BigDecimal computeSpread(BigDecimal longPrice, BigDecimal shortPrice) {
        return (shortPrice.subtract(longPrice)).divide(longPrice, RoundingMode.HALF_EVEN);
    }

    private List<Ticker> getTickers(Exchange exchange, List<CurrencyPair> currencyPairs) {
        MarketDataService marketDataService = exchange.getMarketDataService();

        long start = System.currentTimeMillis();

        try {
            try {
                CurrencyPairsParam param = () -> currencyPairs.stream()
                        .map(currencyPair -> convertExchangePair(exchange, currencyPair))
                        .collect(Collectors.toList());
                List<Ticker> tickers = marketDataService.getTickers(param);

                tickers.forEach(ticker ->
                        LOGGER.debug("{}: {} {}/{}",
                                ticker.getCurrencyPair(),
                                exchange.getExchangeSpecification().getExchangeName(),
                                ticker.getBid(), ticker.getAsk()));

                long completion = System.currentTimeMillis() - start;

                if (completion > notificationConfiguration.getLogs().getSlowTickerWarning()) {
                    LOGGER.warn("Slow Tickers! Fetched {} tickers via getTickers() for {} in {} ms",
                        tickers.size(),
                        exchange.getExchangeSpecification().getExchangeName(),
                        System.currentTimeMillis() - start);
                }

                return tickers;
            } catch (UndeclaredThrowableException ute) {
                // Method proxying in rescu can enclose a real exception in this UTE, so we need to unwrap and re-throw it.
                throw ute.getCause();
            }
        } catch (NotYetImplementedForExchangeException e) {
            LOGGER.debug("{} does not implement MarketDataService.getTickers()", exchange.getExchangeSpecification().getExchangeName());

            List<Ticker> tickers = currencyPairs.parallelStream()
                    .map(currencyPair -> {
                        try {
                            try {
                                return marketDataService.getTicker(convertExchangePair(exchange, currencyPair));
                            } catch (UndeclaredThrowableException ute) {
                                // Method proxying in rescu can enclose a real exception in this UTE, so we need to unwrap and re-throw it.
                                throw ute.getCause();
                            }
                        } catch (IOException | NullPointerException | ExchangeException ex) {
                            LOGGER.debug("Unable to fetch ticker for {} {}",
                                    exchange.getExchangeSpecification().getExchangeName(),
                                    currencyPair);
                        } catch (Throwable t) {
                            // TODO remove this general catch when we stop seeing oddball exceptions

                            LOGGER.warn("Uncaught Throwable class was: {}", t.getClass().getName());

                            if (t instanceof RuntimeException) {
                                throw (RuntimeException) t;
                            } else {
                                LOGGER.error("Not re-throwing checked Exception!");
                            }
                        }

                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            long completion = System.currentTimeMillis() - start;

            if (completion > notificationConfiguration.getLogs().getSlowTickerWarning()) {
                LOGGER.warn("Slow Tickers! Fetched {} tickers via parallelStream for {} getTicker(): {} ms",
                    tickers.size(),
                    exchange.getExchangeSpecification().getExchangeName(),
                    System.currentTimeMillis() - start);
            }

            return tickers;
        } catch (AwareException | ExchangeException | IOException e) {
            LOGGER.debug("Unable to get ticker for {}: {}", exchange.getExchangeSpecification().getExchangeName(), e.getMessage());
        } catch (Throwable t) {
            // TODO remove this general catch when we stop seeing oddball exceptions
            // I hate seeing general catches like this but the method proxying in rescu is throwing some weird ones
            // to us that I'd like to capture and handle appropriately. It's impossible to tell what they actually are
            // without laying a trap like this to catch, inspect and log them at runtime.

            LOGGER.warn("Uncaught Throwable's actual class was: {}", t.getClass().getName());

            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                LOGGER.error("Not re-throwing checked Exception!");
            }
        }

        long completion = System.currentTimeMillis() - start;

        if (completion > notificationConfiguration.getLogs().getSlowTickerWarning()) {
            LOGGER.warn("Slow Tickers! Fetched empty ticker list for {} in {} ms",
                exchange.getExchangeSpecification().getExchangeName(),
                System.currentTimeMillis() - start);
        }

        return Collections.emptyList();
    }

    BigDecimal getVolumeForOrder(Exchange exchange, String orderId, BigDecimal defaultVolume) {
        try {
            BigDecimal volume = exchange.getTradeService().getOrder(orderId)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new OrderNotFoundException(orderId))
                    .getOriginalAmount();

            if (volume.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Volume must be more than zero.");
            }

            LOGGER.info("{} using order ID {} volume of: {}",
                exchange.getExchangeSpecification().getExchangeName(),
                orderId,
                volume);

            return volume;
        } catch (NotAvailableFromExchangeException e) {
            LOGGER.debug("Exchange does not support fetching orders by ID");
        } catch (IOException e) {
            LOGGER.warn("Unable to fetch order {} from exchange", orderId);
        } catch (IllegalStateException e) {
            LOGGER.debug(e.getMessage());
        }

        LOGGER.debug("{} using default volume: {}",
            exchange.getExchangeSpecification().getExchangeName(),
            defaultVolume);
        return defaultVolume;
    }

    BigDecimal getLimitPrice(Exchange exchange, CurrencyPair rawCurrencyPair, BigDecimal allowedVolume, Order.OrderType orderType) {
        CurrencyPair currencyPair = convertExchangePair(exchange, rawCurrencyPair);

        try {
            OrderBook orderBook = exchange.getMarketDataService().getOrderBook(currencyPair);
            List<LimitOrder> orders = orderType.equals(Order.OrderType.ASK) ? orderBook.getAsks() : orderBook.getBids();
            BigDecimal price;
            BigDecimal volume = BigDecimal.ZERO;

            for (LimitOrder order : orders) {
                price = order.getLimitPrice();
                volume = volume.add(order.getRemainingAmount());

                LOGGER.debug("Order: {} @ {}",
                        order.getRemainingAmount().setScale(BTC_SCALE, RoundingMode.HALF_EVEN),
                        order.getLimitPrice());

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
                    .multiply(new BigDecimal(0.9))
                    .setScale(DecimalConstants.USD_SCALE, RoundingMode.HALF_EVEN);

            LOGGER.debug("Maximum exposure for {}: {}", exchanges, exposure);

            return exposure;
        }
    }

    private void logCurrentExchangeBalances(Exchange longExchange, Exchange shortExchange) {
        try {
            BigDecimal longBalance = getAccountBalance(longExchange);
            BigDecimal shortBalance = getAccountBalance(shortExchange);

            LOGGER.info("Updated account balances: {} ${} + {} ${} = ${}",
                    longExchange.getExchangeSpecification().getExchangeName(),
                    longBalance,
                    shortExchange.getExchangeSpecification().getExchangeName(),
                    shortBalance,
                    longBalance.add(shortBalance));
        } catch (IOException e) {
            LOGGER.error("IOE fetching account balances: ", e);
        }
    }

    BigDecimal getAccountBalance(Exchange exchange, Currency currency, int scale) throws IOException {
        AccountService accountService = exchange.getAccountService();

        for (Wallet wallet : accountService.getAccountInfo().getWallets().values()) {
            if (wallet.getBalances().containsKey(currency)) {
                return wallet.getBalance(currency).getAvailable()
                        .setScale(scale, RoundingMode.HALF_EVEN);
            }
        }

        LOGGER.error("Unable to fetch {} balance for {}.",
                currency.getDisplayName(),
                exchange.getExchangeSpecification().getExchangeName());

        return BigDecimal.ZERO;
    }

    private BigDecimal getAccountBalance(Exchange exchange, Currency currency) throws IOException {
        return getAccountBalance(exchange, currency, USD_SCALE);
    }

    private BigDecimal getAccountBalance(Exchange exchange) throws IOException {
        Currency currency = getExchangeHomeCurrency(exchange);

        return getAccountBalance(exchange, currency);
    }
}
