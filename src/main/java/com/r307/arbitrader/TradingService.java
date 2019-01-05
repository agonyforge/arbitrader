package com.r307.arbitrader;

import com.r307.arbitrader.config.ExchangeConfiguration;
import com.r307.arbitrader.config.TradingConfiguration;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class TradingService {
    static final String METADATA_KEY = "arbitrader-metadata";

    private static final Logger LOGGER = LoggerFactory.getLogger(TradingService.class);

    private TradingConfiguration tradingConfiguration;
    private List<CurrencyPair> currencyPairs;
    private List<Exchange> exchanges = new ArrayList<>();
    private Map<String, Ticker> allTickers = new HashMap<>();
    private Map<String, BigDecimal> minSpread = new HashMap<>();
    private Map<String, BigDecimal> maxSpread = new HashMap<>();

    // active trade information
    private boolean inMarket = false;
    private CurrencyPair activeCurrencyPair = null;
    private Exchange activeLongExchange = null;
    private Exchange activeShortExchange = null;
    private BigDecimal activeLongVolume = null;
    private BigDecimal activeShortVolume = null;
    private BigDecimal activeLongEntry = null;
    private BigDecimal activeShortEntry = null;
    private BigDecimal activeExitTarget = null;

    public TradingService(TradingConfiguration tradingConfiguration) {
        this.tradingConfiguration = tradingConfiguration;

        currencyPairs = tradingConfiguration.getTradingPairs();

        LOGGER.info("Currency pairs: {}", currencyPairs);
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
                CurrencyPairsParam param = () -> currencyPairs;
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
        exchanges.forEach(exchange -> getTickers(exchange, currencyPairs)
                .forEach(ticker -> allTickers.put(tickerKey(exchange, ticker.getCurrencyPair()), ticker)));

        exchanges.forEach(longExchange -> exchanges.forEach(shortExchange -> currencyPairs.forEach(currencyPair -> {
            if (isInvalidExchangePair(longExchange, shortExchange, currencyPair)) {
                return;
            }

            Ticker longTicker = allTickers.get(tickerKey(longExchange, currencyPair));
            Ticker shortTicker = allTickers.get(tickerKey(shortExchange, currencyPair));

            if (longTicker == null || shortTicker == null) {
                return;
            }

            LOGGER.info("{}/{} {}",
                    longExchange.getExchangeSpecification().getExchangeName(),
                    shortExchange.getExchangeSpecification().getExchangeName(),
                    currencyPair);
        })));

        if (tradingConfiguration.getFixedExposure() != null) {
            LOGGER.info("Using fixed exposure of ${} as configured", tradingConfiguration.getFixedExposure());
        }
    }

    @Scheduled(initialDelay = 5000, fixedRate = 3000)
    public void tick() {
        LOGGER.debug("Fetching all tickers...");
        allTickers.clear();
        exchanges.forEach(exchange -> getTickers(exchange, currencyPairs)
                .forEach(ticker -> allTickers.put(tickerKey(exchange, ticker.getCurrencyPair()), ticker)));

        // If everything is always evaluated in the same order, earlier exchange/pair combos have a higher chance of
        // executing trades than ones at the end of the list.
        Collections.shuffle(exchanges);
        Collections.shuffle(currencyPairs);

        currencyPairs.forEach(currencyPair -> {
            LOGGER.debug("Computing trade opportunities...");
            exchanges.forEach(longExchange -> exchanges.forEach(shortExchange -> {
                if (isInvalidExchangePair(longExchange, shortExchange, currencyPair)) {
                    return;
                }

                LOGGER.debug("=> Long/Short: {}/{} {} <=",
                        longExchange.getExchangeSpecification().getExchangeName(),
                        shortExchange.getExchangeSpecification().getExchangeName(),
                        currencyPair);

                Ticker longTicker = allTickers.get(tickerKey(longExchange, currencyPair));
                Ticker shortTicker = allTickers.get(tickerKey(shortExchange, currencyPair));

                // if we couldn't get a ticker for either exchange, bail out
                if (longTicker == null || shortTicker == null) {
                    LOGGER.debug("Ticker was null! long: {}, short: {}", longTicker, shortTicker);
                    return;
                }

                BigDecimal spreadIn = computeSpread(longTicker.getAsk(), shortTicker.getBid());
                BigDecimal spreadOut = computeSpread(longTicker.getBid(), shortTicker.getAsk());

                LOGGER.debug("{}/{} {} {} {}",
                        longExchange.getExchangeSpecification().getExchangeName(),
                        shortExchange.getExchangeSpecification().getExchangeName(),
                        currencyPair,
                        spreadIn,
                        spreadOut);

                if (!inMarket && spreadIn.compareTo(tradingConfiguration.getEntrySpread()) > 0) {
                    BigDecimal longFees = getExchangeFee(longExchange, currencyPair, true);
                    BigDecimal shortFees = getExchangeFee(shortExchange, currencyPair, true);

                    BigDecimal fees = (longFees.add(shortFees))
                            .multiply(new BigDecimal(2.0));

                    BigDecimal exitTarget = spreadIn
                            .subtract(tradingConfiguration.getExitTarget())
                            .subtract(fees);

                    BigDecimal maxExposure = getMaximumExposure(currencyPair);

                    if (maxExposure != null) {
                        BigDecimal longVolume = maxExposure.divide(longTicker.getAsk(), RoundingMode.HALF_EVEN);
                        BigDecimal shortVolume = maxExposure.divide(shortTicker.getBid(), RoundingMode.HALF_EVEN);
                        BigDecimal longLimitPrice = getLimitPrice(longExchange, currencyPair, longVolume, Order.OrderType.ASK);
                        BigDecimal shortLimitPrice = getLimitPrice(shortExchange, currencyPair, shortVolume, Order.OrderType.BID);

                        BigDecimal spreadVerification = computeSpread(longLimitPrice, shortLimitPrice);

                        if (spreadVerification.compareTo(tradingConfiguration.getEntrySpread()) < 0) {
                            LOGGER.debug("Not enough liquidity to execute both trades profitably");
                        } else {
                            LOGGER.info("***** ENTRY *****");
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
                                executeOrderPair(
                                        longExchange, shortExchange,
                                        currencyPair,
                                        longLimitPrice, shortLimitPrice,
                                        longVolume, shortVolume,
                                        true);
                            } catch (IOException e) {
                                LOGGER.error("IOE executing limit orders: ", e);
                            }

                            inMarket = true;
                            activeCurrencyPair = currencyPair;
                            activeExitTarget = exitTarget;
                            activeLongExchange = longExchange;
                            activeShortExchange = shortExchange;
                            activeLongVolume = longVolume;
                            activeShortVolume = shortVolume;
                            activeLongEntry = longLimitPrice;
                            activeShortEntry = shortLimitPrice;
                        }
                    } else {
                        LOGGER.warn("Will not trade: exposure could not be computed");
                    }
                } else if (inMarket
                        && currencyPair.equals(activeCurrencyPair)
                        && longExchange.equals(activeLongExchange)
                        && shortExchange.equals(activeShortExchange)
                        && spreadOut.compareTo(activeExitTarget) < 0) {

                    BigDecimal longLimitPrice = getLimitPrice(longExchange, currencyPair, activeLongVolume, Order.OrderType.BID);
                    BigDecimal shortLimitPrice = getLimitPrice(shortExchange, currencyPair, activeShortVolume, Order.OrderType.ASK);

                    BigDecimal spreadVerification = computeSpread(longLimitPrice, shortLimitPrice);

                    if (spreadVerification.compareTo(activeExitTarget) > 0) {
                        LOGGER.debug("Not enough liquidity to execute both trades profitably");
                    } else {
                        LOGGER.info("***** EXIT *****");

                        try {
                            LOGGER.info("Long close: {} {} {} @ {} ({} slip) = {}{}",
                                    longExchange.getExchangeSpecification().getExchangeName(),
                                    currencyPair,
                                    activeLongVolume,
                                    longLimitPrice,
                                    longLimitPrice.subtract(longTicker.getBid()),
                                    Currency.USD.getSymbol(),
                                    activeLongVolume.multiply(longTicker.getBid()));
                            LOGGER.info("Short close: {} {} {} @ {} ({} slip) = {}{}",
                                    shortExchange.getExchangeSpecification().getExchangeName(),
                                    currencyPair,
                                    activeShortVolume,
                                    shortLimitPrice,
                                    shortTicker.getAsk().subtract(shortLimitPrice),
                                    Currency.USD.getSymbol(),
                                    activeShortVolume.multiply(shortTicker.getAsk()));

                            BigDecimal longProfit = activeLongVolume.multiply(longLimitPrice)
                                    .subtract(activeLongVolume.multiply(activeLongEntry))
                                    .setScale(2, RoundingMode.HALF_EVEN);
                            BigDecimal shortProfit = activeShortVolume.multiply(activeShortEntry)
                                    .subtract(activeShortVolume.multiply(shortLimitPrice))
                                    .setScale(2, RoundingMode.HALF_EVEN);

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
                                    activeLongVolume, activeShortVolume,
                                    false);
                        } catch (IOException e) {
                            LOGGER.error("IOE executing limit orders: ", e);
                        }

                        try {
                            BigDecimal longBalance = getAccountBalance(longExchange);
                            BigDecimal shortBalance = getAccountBalance(shortExchange);

                            LOGGER.info("Updated account balances: {} ${} / {} ${}",
                                    longExchange.getExchangeSpecification().getExchangeName(),
                                    longBalance,
                                    shortExchange.getExchangeSpecification().getExchangeName(),
                                    shortBalance);
                        } catch (IOException e) {
                            LOGGER.error("IOE fetching account balances: ", e);
                        }

                        inMarket = false;
                        activeCurrencyPair = null;
                        activeExitTarget = null;
                        activeLongExchange = null;
                        activeShortExchange = null;
                        activeLongVolume = null;
                        activeShortVolume = null;
                        activeLongEntry = null;
                        activeShortEntry = null;
                    }
                }

                String spreadKey = spreadKey(longExchange, shortExchange, currencyPair);

                minSpread.put(spreadKey, spreadIn.min(minSpread.getOrDefault(spreadKey, BigDecimal.valueOf(1))));
                maxSpread.put(spreadKey, spreadIn.max(maxSpread.getOrDefault(spreadKey, BigDecimal.valueOf(-1))));
                minSpread.put(spreadKey, spreadOut.min(minSpread.getOrDefault(spreadKey, BigDecimal.valueOf(1))));
                maxSpread.put(spreadKey, spreadOut.max(maxSpread.getOrDefault(spreadKey, BigDecimal.valueOf(-1))));
            }));
        });
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

    private static boolean isInvalidExchangePair(Exchange longExchange, Exchange shortExchange, CurrencyPair currencyPair) {
        // both exchanges are the same
        if (longExchange == shortExchange) {
            return true;
        }

        // the "short" exchange doesn't support margin
        if (!getExchangeMetadata(shortExchange).getMargin()) {
            return true;
        }

        // the "short" exchange doesn't support margin on this currency pair
        //noinspection RedundantIfStatement
        if (getExchangeMetadata(shortExchange).getMarginExclude().contains(currencyPair)) {
            return true;
        }

        return false;
    }

    private void executeOrderPair(Exchange longExchange, Exchange shortExchange,
                                  CurrencyPair currencyPair,
                                  BigDecimal longLimitPrice, BigDecimal shortLimitPrice,
                                  BigDecimal longVolume, BigDecimal shortVolume,
                                  boolean isPositionOpen) throws IOException {
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

        String longResult = longExchange.getTradeService().placeLimitOrder(longLimitOrder);
        String shortResult = shortExchange.getTradeService().placeLimitOrder(shortLimitOrder);

        LOGGER.info("{} order ID: {}",
                longExchange.getExchangeSpecification().getExchangeName(),
                longResult);
        LOGGER.info("{} order ID: {}",
                shortExchange.getExchangeSpecification().getExchangeName(),
                shortResult);

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

            return tickers;
        } catch (NotYetImplementedForExchangeException e) {
            LOGGER.debug("{} does not implement MarketDataService.getTickers()", exchange.getExchangeSpecification().getExchangeName());

            return currencyPairs.stream()
                    .map(currencyPair -> {
                        try {
                            return marketDataService.getTicker(convertExchangePair(exchange, currencyPair));
                        } catch (IOException | NullPointerException | ExchangeException ex) {
                            LOGGER.debug("Unable to fetch ticker for {} {}",
                                    exchange.getExchangeSpecification().getExchangeName(),
                                    currencyPair);
                        }

                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (AwareException | ExchangeException | IOException e) {
            LOGGER.debug("Unable to get ticker for {}: {}", exchange.getExchangeSpecification().getExchangeName(), e.getMessage());
        }

        return Collections.emptyList();
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
                        order.getRemainingAmount().setScale(6, RoundingMode.HALF_EVEN),
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

    private BigDecimal getMaximumExposure(CurrencyPair rawCurrencyPair) {
        if (tradingConfiguration.getFixedExposure() != null) {
            return tradingConfiguration.getFixedExposure();
        } else {
            BigDecimal smallestBalance = null;

            for (Exchange exchange : exchanges) {
                CurrencyPair currencyPair = convertExchangePair(exchange, rawCurrencyPair);

                try {
                    BigDecimal balance = getAccountBalance(exchange);

                    if (smallestBalance == null) {
                        smallestBalance = balance;
                    } else {
                        smallestBalance = smallestBalance.min(balance);
                    }
                } catch (IOException e) {
                    LOGGER.error("Unable to fetch account balance: {} {}",
                            exchange.getExchangeSpecification().getExchangeName(),
                            currencyPair.counter.getDisplayName(),
                            e);
                }
            }

            return smallestBalance == null ? null : smallestBalance
                    .multiply(new BigDecimal(0.9))
                    .setScale(DecimalConstants.USD_SCALE, RoundingMode.HALF_EVEN);
        }
    }

    private BigDecimal getAccountBalance(Exchange exchange, Currency currency) throws IOException {
        AccountService accountService = exchange.getAccountService();

        for (Wallet wallet : accountService.getAccountInfo().getWallets().values()) {
            if (wallet.getBalances().containsKey(currency)) {
                return wallet.getBalance(currency).getAvailable()
                        .setScale(DecimalConstants.USD_SCALE, RoundingMode.HALF_EVEN);
            }
        }

        LOGGER.error("Unable to fetch {} balance for {}.",
                currency.getDisplayName(),
                exchange.getExchangeSpecification().getExchangeName());

        return BigDecimal.ZERO;
    }

    private BigDecimal getAccountBalance(Exchange exchange) throws IOException {
        Currency currency = getExchangeHomeCurrency(exchange);

        return getAccountBalance(exchange, currency);
    }
}
