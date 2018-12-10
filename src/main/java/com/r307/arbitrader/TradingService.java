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
import org.knowm.xchange.dto.trade.LimitOrder;
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

            specification.setApiKey(exchangeMetadata.getApiKey());
            specification.setSecretKey(exchangeMetadata.getSecretKey());
            specification.setExchangeSpecificParametersItem("makerFee", exchangeMetadata.getMakerFee());
            specification.setExchangeSpecificParametersItem("takerFee", exchangeMetadata.getTakerFee());

            exchanges.add(ExchangeFactory.INSTANCE.createExchange(specification));
        });

        exchanges.forEach(exchange -> {
            try {
                if (exchange != null) {
                    LOGGER.info("{} balance: {}{}",
                            exchange.getExchangeSpecification().getExchangeName(),
                            Currency.USD.getSymbol(),
                            getAccountBalance(exchange, Currency.USD));
                }
            } catch (IOException e) {
                LOGGER.error("Unable to fetch account balance: ", e);
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
                if (longExchange == shortExchange) {
                    return;
                }

                LOGGER.debug("=> Long/Short: {}/{} {} <=",
                        longExchange.getExchangeSpecification().getExchangeName(),
                        shortExchange.getExchangeSpecification().getExchangeName(),
                        currencyPair);

                Ticker longTicker = allTickers.get(tickerKey(longExchange, currencyPair));
                Ticker shortTicker = allTickers.get(tickerKey(shortExchange, currencyPair));

                if (longTicker == null || shortTicker == null) {
                    return;
                }

                BigDecimal spreadIn = computeSpreadIn(longTicker, shortTicker);
                BigDecimal spreadOut = computeSpreadOut(longTicker, shortTicker);

                if (spreadIn.compareTo(tradingConfiguration.getEntrySpread()) > 0) {
                    if (!inMarket) {
                        LOGGER.warn("Entry opportunity {}/{} {}: {}",
                                longExchange.getExchangeSpecification().getExchangeName(),
                                shortExchange.getExchangeSpecification().getExchangeName(),
                                currencyPair,
                                spreadIn);

                        BigDecimal fees = new BigDecimal(2.0).multiply(
                                ((BigDecimal) longExchange.getExchangeSpecification().getExchangeSpecificParametersItem("makerFee"))
                                        .add((BigDecimal) shortExchange.getExchangeSpecification().getExchangeSpecificParametersItem("makerFee")));
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
                        }
                    }
                } else if (inMarket
                        && currencyPair.equals(activeCurrencyPair)
                        && longExchange.equals(activeLongExchange)
                        && shortExchange.equals(activeShortExchange)
                        && spreadOut.compareTo(activeExitTarget) < 0) {

                    LOGGER.warn("Exit opportunity {} {}/{}: {}",
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

                minSpread.put(spreadKey, spreadIn.min(minSpread.getOrDefault(spreadKey, BigDecimal.ZERO)));
                maxSpread.put(spreadKey, spreadIn.max(maxSpread.getOrDefault(spreadKey, BigDecimal.ZERO)));
                minSpread.put(spreadKey, spreadOut.min(minSpread.getOrDefault(spreadKey, BigDecimal.ZERO)));
                maxSpread.put(spreadKey, spreadOut.max(maxSpread.getOrDefault(spreadKey, BigDecimal.ZERO)));

                if ((!inMarket && spreadIn.compareTo(tradingConfiguration.getEntrySpread()) > 0)
                        || (inMarket && longExchange.equals(activeLongExchange) && shortExchange.equals(activeShortExchange) && spreadOut.compareTo(activeExitTarget) < 0)) {

                    LOGGER.info("{}/{} {}: {}/{} ({}/{})",
                            longExchange.getExchangeSpecification().getExchangeName(),
                            shortExchange.getExchangeSpecification().getExchangeName(),
                            currencyPair,
                            spreadIn, spreadOut,
                            minSpread.get(spreadKey),
                            maxSpread.get(spreadKey));
                }
            }));
        });
    }

    @Scheduled(initialDelay = 30000, fixedRate = 3600000)
    public void logSpreadMinAndMax() {
        currencyPairs.forEach(currencyPair -> exchanges.forEach(longExchange -> exchanges.forEach(shortExchange -> {
            if (longExchange == shortExchange) {
                return;
            }

            String spreadKey = spreadKey(longExchange, shortExchange, currencyPair);

            LOGGER.info("{}/{} {} Min/Max Spreads: {}/{}",
                    longExchange.getExchangeSpecification().getExchangeName(),
                    shortExchange.getExchangeSpecification().getExchangeName(),
                    currencyPair,
                    minSpread.get(spreadKey),
                    maxSpread.get(spreadKey));
        })));
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

        if (marketDataService == null) {
            LOGGER.warn("Market data service is null!");
            return Collections.emptyList();
        }

        try {
            CurrencyPairsParam param = () -> currencyPairs;
            List<Ticker> tickers = marketDataService.getTickers(param);

            tickers.forEach(ticker ->
            LOGGER.debug("{}: {} {}/{}",
                    ticker.getCurrencyPair(),
                    exchange.getExchangeSpecification().getExchangeName(),
                    ticker.getBid(), ticker.getAsk()));

            return tickers;
        } catch (IOException ioe) {
            LOGGER.error("Unexpected IO exception!", ioe);
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
