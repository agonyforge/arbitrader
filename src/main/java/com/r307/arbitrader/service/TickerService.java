package com.r307.arbitrader.service;

import com.r307.arbitrader.Utils;
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

@Component
public class TickerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickerService.class);

    private final TradingConfiguration tradingConfiguration;
    private final ExchangeService exchangeService;
    private final ErrorCollectorService errorCollectorService;

    Map<String, Ticker> allTickers = new HashMap<>();
    List<TradeCombination> pollingExchangeTradeCombinations = new ArrayList<>();
    List<TradeCombination> streamingExchangeTradeCombinations = new ArrayList<>();

    @Inject
    public TickerService(
        TradingConfiguration tradingConfiguration,
        ExchangeService exchangeService,
        ErrorCollectorService errorCollectorService) {

        this.tradingConfiguration = tradingConfiguration;
        this.exchangeService = exchangeService;
        this.errorCollectorService = errorCollectorService;
    }

    public void initializeTickers(List<Exchange> exchanges) {
        LOGGER.info("Fetching all tickers for all exchanges...");

        allTickers.clear();
        exchanges
            .forEach(exchange -> getTickers(exchange, exchangeService.getExchangeMetadata(exchange).getTradingPairs())
            .forEach(ticker -> allTickers.put(tickerKey(exchange, (CurrencyPair)ticker.getInstrument()), ticker)));

        LOGGER.info("Trading the following exchanges and pairs:");

        exchanges.forEach(longExchange -> exchanges.forEach(shortExchange -> {
            // get the pairs common to both exchanges
            Collection<CurrencyPair> currencyPairs = CollectionUtils.intersection(
                exchangeService.getExchangeMetadata(longExchange).getTradingPairs(),
                exchangeService.getExchangeMetadata(shortExchange).getTradingPairs());

            currencyPairs.forEach(currencyPair -> {
                if (isInvalidExchangePair(longExchange, shortExchange, currencyPair)) {
                    LOGGER.debug("Invalid exchange pair: {}/{}",
                        longExchange.getExchangeSpecification().getExchangeName(),
                        shortExchange.getExchangeSpecification().getExchangeName());
                    return;
                }

                final TradeCombination combination = new TradeCombination(longExchange, shortExchange, currencyPair);

                if (Utils.isStreamingExchange(longExchange) && Utils.isStreamingExchange(shortExchange)) {
                    streamingExchangeTradeCombinations.add(combination);
                }

                // We still want to use streaming exchanges when we are polling
                pollingExchangeTradeCombinations.add(combination);

                LOGGER.info("{}", combination);
            });
        }));
    }

    public void refreshTickers() {
        allTickers.clear();

        Map<Exchange, Set<CurrencyPair>> queue = new HashMap<>();

        // find the currencies that are actively in use for each exchange
        pollingExchangeTradeCombinations.forEach(tradeCombination -> {
            Set<CurrencyPair> longCurrencies = queue.computeIfAbsent(tradeCombination.getLongExchange(), (key) -> new HashSet<>());
            Set<CurrencyPair> shortCurrencies = queue.computeIfAbsent(tradeCombination.getShortExchange(), (key) -> new HashSet<>());

            longCurrencies.add(tradeCombination.getCurrencyPair());
            shortCurrencies.add(tradeCombination.getCurrencyPair());
        });

        // for each exchange, fetch its active currencies
        queue.keySet().forEach(exchange -> {
            List<CurrencyPair> activePairs = new ArrayList<>(queue.get(exchange));

            try {
                LOGGER.debug("{} fetching tickers for: {}", exchange.getExchangeSpecification().getExchangeName(), activePairs);

                getTickers(exchange, activePairs)
                    .forEach(ticker -> allTickers.put(tickerKey(exchange, (CurrencyPair)ticker.getInstrument()), ticker));
            } catch (ExchangeException e) {
                LOGGER.warn("Failed to fetch ticker for {}", exchange.getExchangeSpecification().getExchangeName());
            }
        });
    }

    public Ticker getTicker(Exchange exchange, CurrencyPair currencyPair) {
        return allTickers.get(tickerKey(exchange, currencyPair));
    }

    public boolean isInvalidTicker(Ticker ticker) {
        return ticker == null || ticker.getBid() == null || ticker.getAsk() == null;
    }

    public List<TradeCombination> getPollingExchangeTradeCombinations() {
        final List<TradeCombination> allResult = new ArrayList<>(pollingExchangeTradeCombinations);

        // If everything is always evaluated in the same order, earlier exchange/pair combos have a higher chance of
        // executing trades than ones at the end of the list.
        Collections.shuffle(allResult);

        return allResult;
    }

    public List<TradeCombination> getStreamingExchangeTradeCombinations() {
        final List<TradeCombination> streamingResult = new ArrayList<>(streamingExchangeTradeCombinations);
        Collections.shuffle(streamingResult);

        return streamingResult;
    }

    // TODO test public API instead of private
    List<Ticker> getTickers(Exchange exchange, List<CurrencyPair> currencyPairs) {
        TickerStrategy tickerStrategy = (TickerStrategy)exchange.getExchangeSpecification().getExchangeSpecificParametersItem(TICKER_STRATEGY_KEY);

        try {
            List<Ticker> tickers = tickerStrategy.getTickers(exchange, currencyPairs);

            tickers.forEach(ticker -> LOGGER.debug("Ticker: {} {} {}/{}",
                exchange.getExchangeSpecification().getExchangeName(),
                ticker.getInstrument(),
                ticker.getBid(),
                ticker.getAsk()));

            return tickers;
        } catch (RuntimeException re) {
            LOGGER.debug("Unexpected runtime exception: " + re.getMessage(), re);
            errorCollectorService.collect(exchange, re);
        }

        return Collections.emptyList();
    }

    String tickerKey(Exchange exchange, CurrencyPair currencyPair) {
        return String.format("%s:%s",
            exchange.getExchangeSpecification().getExchangeName(),
            exchangeService.convertExchangePair(exchange, currencyPair));
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
        if (tradingConfiguration.getTradeBlacklist().contains(formatTradeCombination(longExchange, shortExchange, currencyPair))) {
            return true;
        }

        return false;
    }

    private static String formatTradeCombination(Exchange longExchange, Exchange shortExchange, CurrencyPair currencyPair) {
        return String.format("%s:%s:%s",
            longExchange.getExchangeSpecification().getExchangeName(),
            shortExchange.getExchangeSpecification().getExchangeName(),
            currencyPair);
    }
}
