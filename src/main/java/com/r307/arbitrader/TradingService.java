package com.r307.arbitrader;

import com.r307.arbitrader.config.TradingConfiguration;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.CurrencyPairsParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

            if (!exchangeMetadata.getCustom().isEmpty()) {
                exchangeMetadata.getCustom().forEach(specification::setExchangeSpecificParametersItem);
            }

            specification.setExchangeSpecificParametersItem("margin", exchangeMetadata.getMargin());
            specification.setExchangeSpecificParametersItem("makerFee", exchangeMetadata.getMakerFee());

            exchanges.add(ExchangeFactory.INSTANCE.createExchange(specification));
        });

        exchanges.forEach(exchange -> {
            try {
                LOGGER.info("{} balance: {}{}",
                        exchange.getExchangeSpecification().getExchangeName(),
                        Currency.USD.getSymbol(),
                        getAccountBalance(exchange, Currency.USD));
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

            BigDecimal tradingFee = getExchangeFee(exchange, CurrencyPair.BTC_USD);

            if (tradingFee == null) {
                LOGGER.warn("{} does not provide dynamic trading fees", exchange.getExchangeSpecification().getExchangeName());

                if (exchange.getExchangeSpecification().getExchangeSpecificParametersItem("makerFee") == null) {
                    LOGGER.error("{} must be configured with a makerFee", exchange.getExchangeSpecification().getExchangeName());
                }
            }
        });
    }

    @Scheduled(initialDelay = 5000, fixedRate = 3000)
    public void tick() {
        LOGGER.debug("Fetching all tickers...");
        allTickers.clear();
        exchanges.forEach(exchange -> getTickers(exchange, currencyPairs)
                .forEach(ticker -> allTickers.put(tickerKey(exchange, ticker.getCurrencyPair()), ticker)));

        currencyPairs.forEach(currencyPair -> {
            LOGGER.debug("Computing trade opportunities...");
            exchanges.forEach(longExchange -> exchanges.forEach(shortExchange -> {
                // bail out if both exchanges are the same
                if (longExchange == shortExchange) {
                    return;
                }

                // if the "short" exchange doesn't support margin, bail out
                if (!((Boolean)shortExchange.getExchangeSpecification().getExchangeSpecificParametersItem("margin"))) {
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

                BigDecimal spreadIn = computeSpreadIn(longTicker, shortTicker);
                BigDecimal spreadOut = computeSpreadOut(longTicker, shortTicker);

                if (!inMarket && spreadIn.compareTo(tradingConfiguration.getEntrySpread()) > 0) {
                    LOGGER.info("***** ENTRY *****");
                    LOGGER.info("Entry opportunity {}/{} {}: {}",
                            longExchange.getExchangeSpecification().getExchangeName(),
                            shortExchange.getExchangeSpecification().getExchangeName(),
                            currencyPair,
                            spreadIn);

                    BigDecimal longFees = getExchangeFee(longExchange, currencyPair);
                    BigDecimal shortFees = getExchangeFee(shortExchange, currencyPair);

                    LOGGER.info("{} (long) fee: ${}", longExchange.getExchangeSpecification().getExchangeName(), longFees);
                    LOGGER.info("{} (short) fee: ${}", shortExchange.getExchangeSpecification().getExchangeName(), shortFees);

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

                        LOGGER.info("Trade amount would be: {}{}", Currency.USD.getSymbol(), maxExposure);
                        LOGGER.info("Exit spread target would be: {}", exitTarget);
                        LOGGER.info("Long entry would be: {} {} @ {} = {}{}",
                                currencyPair,
                                longVolume,
                                longLimitPrice,
                                Currency.USD.getSymbol(),
                                longVolume.multiply(longLimitPrice));
                        LOGGER.info("Short entry would be: {} {} @ {} = {}{}",
                                currencyPair,
                                shortVolume,
                                shortLimitPrice,
                                Currency.USD.getSymbol(),
                                shortVolume.multiply(shortLimitPrice));

                        inMarket = true;
                        activeCurrencyPair = currencyPair;
                        activeExitTarget = exitTarget;
                        activeLongExchange = longExchange;
                        activeShortExchange = shortExchange;
                        activeLongVolume = longVolume;
                        activeShortVolume = shortVolume;
                        activeLongEntry = longLimitPrice;
                        activeShortEntry = shortLimitPrice;
                    } else {
                        LOGGER.warn("Will not trade: exposure could not be computed");
                    }
                } else if (inMarket
                        && currencyPair.equals(activeCurrencyPair)
                        && longExchange.equals(activeLongExchange)
                        && shortExchange.equals(activeShortExchange)
                        && spreadOut.compareTo(activeExitTarget) < 0) {

                    LOGGER.info("***** EXIT *****");
                    LOGGER.info("Exit opportunity {} {}/{}: {}",
                            currencyPair,
                            longExchange.getExchangeSpecification().getExchangeName(),
                            shortExchange.getExchangeSpecification().getExchangeName(),
                            spreadOut);
                    LOGGER.info("Long close would be: {} {} @ {} = {}{}",
                            currencyPair,
                            activeLongVolume,
                            longTicker.getBid(),
                            Currency.USD.getSymbol(),
                            activeLongVolume.multiply(longTicker.getBid()));
                    LOGGER.info("Short close would be: {} {} @ {} = {}{}",
                            currencyPair,
                            activeShortVolume,
                            shortTicker.getAsk(),
                            Currency.USD.getSymbol(),
                            activeShortVolume.multiply(shortTicker.getAsk()));

                    BigDecimal longProfit = activeLongVolume.multiply(longTicker.getBid())
                            .subtract(activeLongVolume.multiply(activeLongEntry))
                            .setScale(2, RoundingMode.HALF_EVEN);
                    BigDecimal shortProfit = activeShortVolume.multiply(activeShortEntry)
                            .subtract(activeShortVolume.multiply(shortTicker.getAsk()))
                            .setScale(2, RoundingMode.HALF_EVEN);

                    LOGGER.info("Profit: (long) {}{} + (short) {}{} = {}{}",
                            Currency.USD.getSymbol(),
                            longProfit,
                            Currency.USD.getSymbol(),
                            shortProfit,
                            Currency.USD.getSymbol(),
                            longProfit.add(shortProfit));

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

                String spreadKey = spreadKey(longExchange, shortExchange, currencyPair);

                minSpread.put(spreadKey, spreadIn.min(minSpread.getOrDefault(spreadKey, BigDecimal.valueOf(1))));
                maxSpread.put(spreadKey, spreadIn.max(maxSpread.getOrDefault(spreadKey, BigDecimal.valueOf(-1))));
                minSpread.put(spreadKey, spreadOut.min(minSpread.getOrDefault(spreadKey, BigDecimal.valueOf(1))));
                maxSpread.put(spreadKey, spreadOut.max(maxSpread.getOrDefault(spreadKey, BigDecimal.valueOf(-1))));
            }));
        });
    }

    @Scheduled(initialDelay = 30000, fixedRate = 3600000)
    public void logPeriodicSummary() {
        LOGGER.info("***** SUMMARY *****");

        currencyPairs.forEach(currencyPair -> exchanges.forEach(longExchange -> exchanges.forEach(shortExchange -> {
            if (longExchange == shortExchange) {
                return;
            }

            if (!((Boolean) shortExchange.getExchangeSpecification().getExchangeSpecificParametersItem("margin"))) {
                return;
            }

            String spreadKey = spreadKey(longExchange, shortExchange, currencyPair);

            Ticker longTicker = allTickers.get(tickerKey(longExchange, currencyPair));
            Ticker shortTicker = allTickers.get(tickerKey(shortExchange, currencyPair));

            BigDecimal spreadIn = (longTicker == null || shortTicker == null) ? BigDecimal.ZERO : computeSpreadIn(longTicker, shortTicker);
            BigDecimal spreadOut = (longTicker == null || shortTicker == null) ? BigDecimal.ZERO : computeSpreadOut(longTicker, shortTicker);

            BigDecimal minSpreadAmount = minSpread.get(spreadKey);
            BigDecimal maxSpreadAmount = maxSpread.get(spreadKey);

            if (minSpreadAmount == null || maxSpreadAmount == null) {
                return;
            }

            LOGGER.info("{}/{} {} Min/In/Out/Max Spreads: {}/{}/{}/{}",
                    longExchange.getExchangeSpecification().getExchangeName(),
                    shortExchange.getExchangeSpecification().getExchangeName(),
                    currencyPair,
                    minSpreadAmount,
                    spreadIn,
                    spreadOut,
                    maxSpreadAmount);
        })));

        if (inMarket) {
            LOGGER.info("Active Trade:");
            LOGGER.info("{}/{} exit @ {}",
                    activeLongExchange.getExchangeSpecification().getExchangeName(),
                    activeShortExchange.getExchangeSpecification().getExchangeName(),
                    activeExitTarget);
            LOGGER.info("Long entry {} {} @ {}", activeCurrencyPair, activeLongVolume, activeLongEntry);
            LOGGER.info("Short entry {} {} @ {}", activeCurrencyPair, activeShortVolume, activeShortEntry);
        } else {
            LOGGER.info("No active trades.");
        }
    }

    private static String tickerKey(Exchange exchange, CurrencyPair currencyPair) {
        return String.format("%s:%s",
                exchange.getExchangeSpecification().getExchangeName(),
                currencyPair);
    }

    private static String spreadKey(Exchange longExchange, Exchange shortExchange, CurrencyPair currencyPair) {
        return String.format("%s:%s:%s",
                longExchange.getExchangeSpecification().getExchangeName(),
                shortExchange.getExchangeSpecification().getExchangeName(),
                currencyPair);
    }

    private static BigDecimal getExchangeFee(Exchange exchange, CurrencyPair currencyPair) {
        CurrencyPairMetaData currencyPairMetaData = exchange.getExchangeMetaData().getCurrencyPairs().get(currencyPair);

        if (currencyPairMetaData == null) {
            return (BigDecimal) exchange.getExchangeSpecification().getExchangeSpecificParametersItem("makerFee");
        }

        return currencyPairMetaData.getTradingFee();
    }

    private BigDecimal computeSpreadIn(Ticker longTicker, Ticker shortTicker) {
        BigDecimal longPrice = longTicker.getAsk();
        BigDecimal shortPrice = shortTicker.getBid();

        BigDecimal spread = (shortPrice.subtract(longPrice)).divide(longPrice, BigDecimal.ROUND_HALF_EVEN);

        LOGGER.debug("Spread In: ({} - {})/{} = {}", shortPrice, longPrice, longPrice, spread);

        return spread;
    }

    private BigDecimal computeSpreadOut(Ticker longTicker, Ticker shortTicker) {
        BigDecimal longPrice = longTicker.getBid();
        BigDecimal shortPrice = shortTicker.getAsk();

        BigDecimal spread = (shortPrice.subtract(longPrice)).divide(longPrice, BigDecimal.ROUND_HALF_EVEN);

        LOGGER.debug("Spread Out: ({} - {})/{} = {}", shortPrice, longPrice, longPrice, spread);

        return spread;
    }

    private List<Ticker> getTickers(Exchange exchange, List<CurrencyPair> currencyPairs) {
        MarketDataService marketDataService = exchange.getMarketDataService();

        try {
            CurrencyPairsParam param = () -> currencyPairs;
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
                            return marketDataService.getTicker(currencyPair);
                        } catch (IOException | NullPointerException | ExchangeException ex) {
                            LOGGER.debug("Unable to fetch ticker for {} {}",
                                    exchange.getExchangeSpecification().getExchangeName(),
                                    currencyPair);
                        }

                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (ExchangeException | IOException e) {
            LOGGER.warn("Unable to get ticker for {}: {}", exchange.getExchangeSpecification().getExchangeName(), e.getMessage());
        }

        return Collections.emptyList();
    }

    private BigDecimal getLimitPrice(Exchange exchange, CurrencyPair currencyPair, BigDecimal allowedVolume, Order.OrderType orderType) {
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

        return BigDecimal.ZERO;
    }

    private BigDecimal getMaximumExposure(CurrencyPair currencyPair) {
        if (tradingConfiguration.getFixedExposure() != null) {
            return tradingConfiguration.getFixedExposure();
        } else {
            BigDecimal smallestBalance = null;

            for (Exchange exchange : exchanges) {
                try {
                    BigDecimal balance = getAccountBalance(exchange, currencyPair.counter);

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
}
