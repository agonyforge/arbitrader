package com.r307.arbitrader.service;

import com.r307.arbitrader.ExchangeBuilder;
import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.service.model.TradeCombination;
import com.r307.arbitrader.service.ticker.ParallelTickerStrategy;
import com.r307.arbitrader.service.ticker.SingleCallTickerStrategy;
import com.r307.arbitrader.service.ticker.TickerStrategy;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TickerServiceTest {
    private final CurrencyPair CURRENCY_PAIR = CurrencyPair.BTC_USD;

    private List<CurrencyPair> currencyPairs = Collections.singletonList(CurrencyPair.BTC_USD);
    private TickerStrategy singleCallTickerStrategy;
    private TickerStrategy parallelTickerStrategy;

    private ErrorCollectorService errorCollectorService;

    private TickerService tickerService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        NotificationConfiguration notificationConfiguration = new NotificationConfiguration();
        ExchangeService exchangeService = new ExchangeService();
        TradingConfiguration tradingConfiguration = new TradingConfiguration();

        errorCollectorService = new ErrorCollectorService();

        singleCallTickerStrategy = new SingleCallTickerStrategy(notificationConfiguration, errorCollectorService, exchangeService);
        parallelTickerStrategy = new ParallelTickerStrategy(notificationConfiguration, errorCollectorService, exchangeService);

        tickerService = new TickerService(
            tradingConfiguration,
            exchangeService,
            errorCollectorService);
    }

    @Test
    public void testInitializeTickers() throws IOException {
        Exchange exchangeA = new ExchangeBuilder("ExchangeA", CURRENCY_PAIR)
            .withTickers(true, Collections.singletonList(CURRENCY_PAIR))
            .withTickerStrategy(singleCallTickerStrategy)
            .withExchangeMetaData()
            .withMarginSupported(true)
            .build();
        Exchange exchangeB = new ExchangeBuilder("ExchangeB", CURRENCY_PAIR)
            .withTickers(true, Arrays.asList(CURRENCY_PAIR, CurrencyPair.ETH_USD))
            .withTickerStrategy(singleCallTickerStrategy)
            .withExchangeMetaData()
            .withMarginSupported(false)
            .build();
        List<Exchange> exchanges = Arrays.asList(exchangeA, exchangeB);

        tickerService.initializeTickers(exchanges);

        assertEquals(1, tickerService.tradeCombinations.size());
        assertTrue(tickerService.tradeCombinations.contains(new TradeCombination(exchangeB, exchangeA, CURRENCY_PAIR)));
    }

    @Test
    public void testRefreshTickers() throws IOException {
        Exchange exchangeA = new ExchangeBuilder("ExchangeA", CURRENCY_PAIR)
            .withTickers(true, Collections.singletonList(CURRENCY_PAIR))
            .withTickerStrategy(singleCallTickerStrategy)
            .withExchangeMetaData()
            .withMarginSupported(true)
            .build();
        Exchange exchangeB = new ExchangeBuilder("ExchangeB", CURRENCY_PAIR)
            .withTickers(true, Arrays.asList(CURRENCY_PAIR, CurrencyPair.ETH_USD))
            .withTickerStrategy(singleCallTickerStrategy)
            .withExchangeMetaData()
            .withMarginSupported(false)
            .build();

        tickerService.tradeCombinations.add(new TradeCombination(exchangeB, exchangeA, CURRENCY_PAIR));

        tickerService.refreshTickers();

        assertEquals(3, tickerService.allTickers.size());
        assertNotNull(tickerService.tickerKey(exchangeA, CURRENCY_PAIR));
        assertNotNull(tickerService.tickerKey(exchangeB, CURRENCY_PAIR));

        // TODO we get this last one due to inaccurate mocking within getTickers()
        // in a real situation we'd only get the first two
        assertNotNull(tickerService.tickerKey(exchangeB, CurrencyPair.ETH_USD));
    }

    @Test
    public void testGetTicker() throws IOException {
        Exchange exchange = new ExchangeBuilder("BunchaCoins", CURRENCY_PAIR)
            .build();
        Ticker ticker = new Ticker.Builder()
            .currencyPair(CURRENCY_PAIR)
            .build();

        tickerService.allTickers.put(tickerService.tickerKey(exchange, CURRENCY_PAIR), ticker);

        Ticker result = tickerService.getTicker(exchange, CURRENCY_PAIR);

        assertEquals(ticker.getCurrencyPair(), result.getCurrencyPair());
    }

    @Test
    public void testIsInvalidTickerNull() {
        assertTrue(tickerService.isInvalidTicker(null));
    }

    @Test
    public void testIsInvalidTickerEmptyValues() {
        Ticker ticker = new Ticker.Builder()
            .build();

        assertTrue(tickerService.isInvalidTicker(ticker));
    }

    @Test
    public void testIsInvalidTickerMissingBid() {
        Ticker ticker = new Ticker.Builder()
            .ask(new BigDecimal("123.00"))
            .build();

        assertTrue(tickerService.isInvalidTicker(ticker));
    }

    @Test
    public void testIsInvalidTickerMissingAsk() {
        Ticker ticker = new Ticker.Builder()
            .bid(new BigDecimal("123.00"))
            .build();

        assertTrue(tickerService.isInvalidTicker(ticker));
    }

    @Test
    public void testIsInvalidTicker() {
        Ticker ticker = new Ticker.Builder()
            .bid(new BigDecimal("120.00"))
            .ask(new BigDecimal("123.00"))
            .build();

        assertFalse(tickerService.isInvalidTicker(ticker));
    }

    @Test
    public void testGetTradeCombinations() {
        TradeCombination combination = mock(TradeCombination.class);

        tickerService.tradeCombinations.add(combination);

        List<TradeCombination> result = tickerService.getTradeCombinations();

        assertNotSame(tickerService.tradeCombinations, result);
        assertTrue(result.contains(combination));
    }

    @Test
    public void testGetTickers() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(singleCallTickerStrategy)
            .withTickers(
                true,
                Collections.singletonList(CurrencyPair.BTC_USD))
            .build();

        List<Ticker> tickers = tickerService.getTickers(exchange, currencyPairs);

        assertFalse(tickers.isEmpty());
        assertTrue(errorCollectorService.isEmpty());

        verify(exchange.getMarketDataService()).getTickers(any());
        verify(exchange.getMarketDataService(), never()).getTicker(any());
    }

    @Test
    public void testGetParallelTickers() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(parallelTickerStrategy)
            .withTickers(
                false,
                Collections.singletonList(CurrencyPair.BTC_USD))
            .build();

        List<Ticker> tickers = tickerService.getTickers(exchange, currencyPairs);

        assertFalse(tickers.isEmpty());
        assertTrue(errorCollectorService.isEmpty());

        verify(exchange.getMarketDataService(), never()).getTickers(any());
        verify(exchange.getMarketDataService(), atLeastOnce()).getTicker(any());
    }

    @Test
    public void testGetTickersException() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(singleCallTickerStrategy)
            .withTickers(new ExchangeException("Boom!"))
            .build();

        List<Ticker> tickers = tickerService.getTickers(exchange, currencyPairs);

        assertTrue(tickers.isEmpty());
        assertFalse(errorCollectorService.isEmpty());

        verify(exchange.getMarketDataService()).getTickers(any());
        verify(exchange.getMarketDataService(), never()).getTicker(any());
    }
}
