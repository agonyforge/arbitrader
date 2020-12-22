package com.r307.arbitrader.service;

import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.service.event.TickerEventPublisher;
import com.r307.arbitrader.service.event.TradeAnalysisPublisher;
import com.r307.arbitrader.service.model.TradeCombination;
import com.r307.arbitrader.service.model.event.TradeAnalysisEvent;
import com.r307.arbitrader.service.ticker.TickerStrategy;
import org.apache.commons.collections4.CollectionUtils;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.r307.arbitrader.service.TradingScheduler.TICKER_STRATEGY_KEY;

/**
 * Services related to fetching tickers.
 */
@Component
public class TickerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickerService.class);

    private final TradingConfiguration tradingConfiguration;
    private final ExchangeService exchangeService;
    private final ErrorCollectorService errorCollectorService;
    private final TradeAnalysisPublisher tradeAnalysisPublisher;

    private final Map<String, Ticker> allTickers = new ConcurrentHashMap<>();
    private final Map<Exchange, Set<TradeCombination>> tradeCombinations = new HashMap<>();

    @Inject
    public TickerService(
        TradingConfiguration tradingConfiguration,
        ExchangeService exchangeService,
        ErrorCollectorService errorCollectorService,
        TradeAnalysisPublisher tradeAnalysisPublisher) {

        this.tradingConfiguration = tradingConfiguration;
        this.exchangeService = exchangeService;
        this.errorCollectorService = errorCollectorService;
        this.tradeAnalysisPublisher = tradeAnalysisPublisher;
    }

    /**
     * Do an initial fetch of tickers and set up TradeCombination objects to represent valid trade pairs.
     *
     * @param exchanges A list of all the exchanges.
     */
    public void initializeTickers(List<Exchange> exchanges) {
        LOGGER.info("Fetching all tickers for all exchanges...");

        exchanges
            .forEach(exchange ->
                fetchTickers(exchange, exchangeService.getExchangeMetadata(exchange).getTradingPairs())
            );

        LOGGER.info("Trading the following exchanges and pairs:");

        exchanges.forEach(longExchange -> exchanges.forEach(shortExchange -> {
            final String shortExchangeName = shortExchange.getExchangeSpecification().getExchangeName();
            final String longExchangeName = longExchange.getExchangeSpecification().getExchangeName();

            // get the currency pairs common to both exchanges
            Collection<CurrencyPair> currencyPairs = CollectionUtils.intersection(
                exchangeService.getExchangeMetadata(longExchange).getTradingPairs(),
                exchangeService.getExchangeMetadata(shortExchange).getTradingPairs());

            // check each pair to see if it is a valid combination
            currencyPairs.forEach(currencyPair -> {
                if (isInvalidExchangePair(longExchange, shortExchange, currencyPair)) {
                    LOGGER.debug("Invalid exchange pair: {}/{}", longExchangeName, shortExchangeName);
                    return;
                }

                // valid combinations become a TradeCombination
                final TradeCombination combination = new TradeCombination(longExchange, shortExchange, currencyPair);
                // TODO streamingExchangeTradeCombinations, pollingExchangeTradeCombinations and allExchangeTradeCombinations?
                // TODO can we hide streaming vs. non-streaming so it isn't important to know the difference?
                // TODO does the streaming list have to be a pair where *both* are streaming? what about one streaming and one polling?

                // add streaming combinations into a separate map
                initTradeCombinationMap(shortExchange, longExchange, combination);

                LOGGER.info("{}", combination);
            });
        }));
    }

    private void initTradeCombinationMap(Exchange shortExchange, Exchange longExchange, TradeCombination combination) {

        tradeCombinations.computeIfAbsent(shortExchange, v -> new HashSet<>())
            .add(combination);
        tradeCombinations.computeIfAbsent(longExchange, v -> new HashSet<>())
            .add(combination);
    }

    /**
     * Fetch tickers for active currency pairs on all exchanges.
     */
    public void refreshTickers() {
        final Map<Exchange, Set<CurrencyPair>> queue = new HashMap<>();

        // find the currencies that are actively in use for each exchange
        tradeCombinations.values()
            .stream()
            .flatMap(Collection::stream)
            .forEach(tradeCombination -> {
                queue.computeIfAbsent(tradeCombination.getLongExchange(), v -> new HashSet<>())
                    .add(tradeCombination.getCurrencyPair());
                queue.computeIfAbsent(tradeCombination.getShortExchange(), v -> new HashSet<>())
                    .add(tradeCombination.getCurrencyPair());
            });

        // for each exchange, fetch its active currencies
        queue.keySet().forEach(exchange -> {
            List<CurrencyPair> activePairs = new ArrayList<>(queue.get(exchange));

            try {
                LOGGER.debug("{} fetching tickers for: {}", exchange.getExchangeSpecification().getExchangeName(), activePairs);

                fetchTickers(exchange, activePairs);
            } catch (ExchangeException e) {
                LOGGER.warn("Failed to fetch ticker for {}", exchange.getExchangeSpecification().getExchangeName());
            }
        });
    }

    /**
     * Get a ticker for a currency pair on an exchange. This fetches the last known price and does not actively go out
     * to the exchange to get a fresh price, so it's an inexpensive call to make.
     *
     * @param exchange The exchange to fetch currencies for.
     * @param currencyPair The currency pair to fetch a ticker for.
     * @return The ticker for the given currency pair on the given exchange.
     */
    public Ticker getTicker(Exchange exchange, CurrencyPair currencyPair) {
        return allTickers.get(tickerKey(exchange, currencyPair));
    }

    /**
     * Does this Ticker have all the required fields?
     *
     * @param ticker A Ticker to validate.
     * @return true if the Ticker is missing required fields.
     */
    public boolean isInvalidTicker(Ticker ticker) {
        return ticker == null || ticker.getBid() == null || ticker.getAsk() == null;
    }

    /**
     * Return a set of all trade combinations. This method returns a new set each time, so it is safe
     * to modify.
     *
     * @return A set of all TradeCombinations.
     */
    public Set<TradeCombination> getExchangeTradeCombinations() {
        return tradeCombinations.values()
            .stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    /**
     * Fetch tickers from the given exchange. This method makes a network call to the exchange to get the latest
     * prices, so it is a relatively expensive call.
     *
     * @param exchange The exchange to fetch prices from.
     * @param currencyPairs The currency pair to fetch prices for.
     * @return A list of Tickers from the requested exchange.
     */
    // TODO test public API instead of private
    protected void fetchTickers(Exchange exchange, List<CurrencyPair> currencyPairs) {
        TickerStrategy tickerStrategy = (TickerStrategy)exchange.getExchangeSpecification().getExchangeSpecificParametersItem(TICKER_STRATEGY_KEY);

        try {
            tickerStrategy.fetchTickers(exchange, currencyPairs);
        } catch (RuntimeException re) {
            LOGGER.debug("Unexpected runtime exception: " + re.getMessage(), re);
            errorCollectorService.collect(exchange, re);
        }
    }

    /**
     * Generate a string for an exchange and currency pair suitable for use as a unique key in a Map.
     *
     * @param exchange The Exchange to generate a key for.
     * @param currencyPair The CurrencyPair to generate a key for.
     * @return A String representing the combination of the exchange and currency pair.
     */
    String tickerKey(Exchange exchange, CurrencyPair currencyPair) {
        return String.format("%s:%s",
            exchange.getExchangeSpecification().getExchangeName(),
            exchangeService.convertExchangePair(exchange, currencyPair));
    }

    // determine whether a pair of exchanges is valid for trading
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
        if (tradingConfiguration.getTradeBlacklist().contains(formatTradeCombination(longExchange, shortExchange, currencyPair))) {
            return true;
        }

        // this pair of exchanges is valid for trading!
        return false;
    }

    // format a string representing a pair of exchanges and a currency pair suitable for use as a key in a Map
    private static String formatTradeCombination(Exchange longExchange, Exchange shortExchange, CurrencyPair currencyPair) {
        return String.format("%s:%s:%s",
            longExchange.getExchangeSpecification().getExchangeName(),
            shortExchange.getExchangeSpecification().getExchangeName(),
            currencyPair);
    }

    public void addExchangeTradeCombination(Exchange exchange, TradeCombination tradeCombination) {
        tradeCombinations.computeIfAbsent(exchange, v -> new HashSet<>())
            .add(tradeCombination);
    }

    public void updateTicker(Exchange exchange, Ticker ticker) {
        final String key = tickerKey(exchange, (CurrencyPair) ticker.getInstrument());
        allTickers.put(key, ticker);

        // As we update the ticker we also want to run a trade analysis to find out if we have trade opportunity
        tradeAnalysisPublisher.publishTradeAnalysis(new TradeAnalysisEvent());
    }

    public Map<String, Ticker> getAllTickers() {
        return allTickers;
    }
}
