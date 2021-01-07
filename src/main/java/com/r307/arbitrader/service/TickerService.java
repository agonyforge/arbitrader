package com.r307.arbitrader.service;

import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.service.model.TradeCombination;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    Map<String, Ticker> allTickers = new HashMap<>();
    List<TradeCombination> tradeCombinations = new ArrayList<>();

    @Inject
    public TickerService(
        TradingConfiguration tradingConfiguration,
        ExchangeService exchangeService,
        ErrorCollectorService errorCollectorService) {

        this.tradingConfiguration = tradingConfiguration;
        this.exchangeService = exchangeService;
        this.errorCollectorService = errorCollectorService;
    }

    /**
     * Do an initial fetch of tickers and set up TradeCombination objects to represent valid trade pairs.
     *
     * @param exchanges A list of all the exchanges.
     */
    public void initializeTickers(List<Exchange> exchanges) {
        LOGGER.info("Trading the following exchanges and pairs:");

        exchanges.forEach(longExchange -> exchanges.forEach(shortExchange -> {
            // get the currency pairs common to both exchanges
            Collection<CurrencyPair> currencyPairs = CollectionUtils.intersection(
                exchangeService.getExchangeMetadata(longExchange).getTradingPairs(),
                exchangeService.getExchangeMetadata(shortExchange).getTradingPairs());

            // check each pair to see if it is a valid combination
            currencyPairs.forEach(currencyPair -> {
                if (isInvalidExchangePair(longExchange, shortExchange, currencyPair)) {
                    LOGGER.trace("Invalid exchange pair: {}/{}",
                        longExchange.getExchangeSpecification().getExchangeName(),
                        shortExchange.getExchangeSpecification().getExchangeName());
                    return;
                }

                // valid combinations become a TradeCombination
                final TradeCombination combination = new TradeCombination(longExchange, shortExchange, currencyPair);

                tradeCombinations.add(combination);

                LOGGER.info("{}", combination);
            });
        }));
    }

    /**
     * Fetch tickers for active currency pairs on all exchanges.
     */
    public void refreshTickers() {
        Map<Exchange, Set<CurrencyPair>> queue = new HashMap<>();

        // find the currencies that are actively in use for each exchange
        tradeCombinations.forEach(tradeCombination -> {
            Set<CurrencyPair> longCurrencies = queue.computeIfAbsent(tradeCombination.getLongExchange(), (key) -> new HashSet<>());
            Set<CurrencyPair> shortCurrencies = queue.computeIfAbsent(tradeCombination.getShortExchange(), (key) -> new HashSet<>());

            longCurrencies.add(tradeCombination.getCurrencyPair());
            shortCurrencies.add(tradeCombination.getCurrencyPair());
        });

        // for each exchange, fetch its active currencies
        queue.keySet().parallelStream().forEach(exchange -> {
            List<CurrencyPair> activePairs = new ArrayList<>(queue.get(exchange));

            try {
                fetchTickers(exchange, activePairs);
            } catch (ExchangeException e) {
                LOGGER.warn("Failed to fetch ticker for {}", exchange.getExchangeSpecification().getExchangeName());
            }
        });
    }

    /**
     * Put a new Ticker into the TickerService. This is a convenience method for
     * TickerStrategy implementations to use. This method will silently reject Tickers
     * where both Tickers have a non-null timestamp and the old Ticker's timestamp is
     * newer than the new Ticker's timestamp.
     *
     * If this seems like it breaks encapsulation a little bit, it does. I originally
     * considered making TickerService a TickerEvent listener, but then there would be no
     * guarantee that the TickerService update happened before the trade analysis had
     * already consumed the event. This way we know we have synchronously updated the
     * ticker map before publishing the event that will trigger listeners to consume
     * that data.
     *
     * One alternative we could explore here would be to have two event types. One to
     * represent that we received a new ticker and TickerService should consume it,
     * and another to represent that TickerService has updated and trade analysis should
     * re-analyze it. I think the two solutions would be functionally equivalent - the
     * two events would be a little cleaner but more complicated. This way is simpler
     * to understand and to write, and it provides the same guarantees.
     *
     * @param exchange The Exchange the Ticker was received from.
     * @param ticker The Ticker to update.
     */
    public void putTicker(Exchange exchange, Ticker ticker) {
        allTickers.compute(tickerKey(exchange, (CurrencyPair) ticker.getInstrument()),
            (key, oldTicker) -> {
                if (oldTicker == null
                    || oldTicker.getTimestamp() == null
                    || ticker.getTimestamp() == null
                    || oldTicker.getTimestamp().before(ticker.getTimestamp()) ) {
                    return ticker;
                }
                return oldTicker;
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
     * Return a shuffled list of all the trade combinations. This method returns a new list each time, so it is safe
     * to modify.
     *
     * @return A shuffled list of all the TradeCombinations.
     */
    public List<TradeCombination> getExchangeTradeCombinations() {
        final List<TradeCombination> allResult = new ArrayList<>(tradeCombinations);

        // If everything is always evaluated in the same order, earlier exchange/pair combos have a higher chance of
        // executing trades than ones at the end of the list.
        Collections.shuffle(allResult);

        return allResult;
    }

    /**
     * Fetch tickers from the given exchange using whatever TickerStrategy is configured for the exchange.
     *
     * For "active" strategies (eg. single call, parallel) this will make a REST call to the exchange API, fetch
     * a price and return it. For "passive" strategies (eg. streaming) we receive prices asynchronously and this
     * call will simply fetch the latest price from the TickerStrategy.
     *
     * @param exchange The exchange to fetch prices from.
     * @param currencyPairs The currency pair to fetch prices for.
     */
    // TODO test public API instead of private
    void fetchTickers(Exchange exchange, List<CurrencyPair> currencyPairs) {
        // get the appropriate TickerStrategy to use for this exchange
        TickerStrategy tickerStrategy = (TickerStrategy)exchange.getExchangeSpecification().getExchangeSpecificParametersItem(TICKER_STRATEGY_KEY);

        try {
            // try to get the tickers using the strategy
            tickerStrategy.getTickers(exchange, currencyPairs, this);
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
}
