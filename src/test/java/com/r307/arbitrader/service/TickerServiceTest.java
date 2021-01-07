package com.r307.arbitrader.service;

import com.r307.arbitrader.ExchangeBuilder;
import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.service.cache.ExchangeFeeCache;
import com.r307.arbitrader.service.event.TickerEventPublisher;
import com.r307.arbitrader.service.model.TickerEvent;
import com.r307.arbitrader.service.model.TradeCombination;
import com.r307.arbitrader.service.ticker.ParallelTickerStrategy;
import com.r307.arbitrader.service.ticker.SingleCallTickerStrategy;
import com.r307.arbitrader.service.ticker.TickerStrategy;
import com.r307.arbitrader.service.ticker.TickerStrategyProvider;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TickerServiceTest {
    private final CurrencyPair CURRENCY_PAIR = CurrencyPair.BTC_USD;

    private List<CurrencyPair> currencyPairs = Collections.singletonList(CurrencyPair.BTC_USD);
    private TickerStrategy singleCallTickerStrategy;
    private TickerStrategy parallelTickerStrategy;
    private TickerService tickerService;
    private ExchangeService exchangeService;
    private ErrorCollectorService errorCollectorService;

    @Mock
    private TickerStrategyProvider tickerStrategyProvider;

    @Mock
    private TickerEventPublisher tickerEventPublisher;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        NotificationConfiguration notificationConfiguration = new NotificationConfiguration();
        TradingConfiguration tradingConfiguration = new TradingConfiguration();

        exchangeService = new ExchangeService(new ExchangeFeeCache(), tickerStrategyProvider);
        tickerService = new TickerService(
            tradingConfiguration,
            exchangeService,
            errorCollectorService);

        errorCollectorService = new ErrorCollectorService();

        singleCallTickerStrategy = new SingleCallTickerStrategy(notificationConfiguration, errorCollectorService, exchangeService, tickerEventPublisher);
        parallelTickerStrategy = new ParallelTickerStrategy(notificationConfiguration, errorCollectorService, exchangeService, tickerEventPublisher);


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

        List<TradeCombination> result = tickerService.getExchangeTradeCombinations();

        assertNotSame(tickerService.tradeCombinations, result);
        assertTrue(result.contains(combination));
    }

    @Test
    public void testFetchTickers() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(singleCallTickerStrategy)
            .withTickers(
                true,
                Collections.singletonList(CurrencyPair.BTC_USD))
            .build();

        tickerService.fetchTickers(exchange, currencyPairs);

        assertTrue(errorCollectorService.isEmpty());
        assertNotNull(tickerService.getTicker(exchange, CurrencyPair.BTC_USD));

        verify(tickerEventPublisher).publishTicker(any(TickerEvent.class));

        verify(exchange.getMarketDataService()).getTickers(any());
        verify(exchange.getMarketDataService(), never()).getTicker(any());
    }

    @Test
    public void testFetchParallelTickers() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(parallelTickerStrategy)
            .withTickers(
                false,
                Collections.singletonList(CurrencyPair.BTC_USD))
            .build();

        tickerService.fetchTickers(exchange, currencyPairs);

        assertTrue(errorCollectorService.isEmpty());
        assertNotNull(tickerService.getTicker(exchange, CurrencyPair.BTC_USD));

        verify(tickerEventPublisher).publishTicker(any(TickerEvent.class));

        verify(exchange.getMarketDataService(), never()).getTickers(any());
        verify(exchange.getMarketDataService(), atLeastOnce()).getTicker(any());
    }

    @Test
    public void testFetchTickersException() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(singleCallTickerStrategy)
            .withTickers(new ExchangeException("Boom!"))
            .build();

        tickerService.fetchTickers(exchange, currencyPairs);

        assertFalse(errorCollectorService.isEmpty());
        assertNull(tickerService.getTicker(exchange, CurrencyPair.BTC_USD));

        verify(tickerEventPublisher, never()).publishTicker(any(TickerEvent.class));

        verify(exchange.getMarketDataService()).getTickers(any());
        verify(exchange.getMarketDataService(), never()).getTicker(any());
    }

    @Test
    public void testPutTicker() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(singleCallTickerStrategy)
            .withHomeCurrency(Currency.USD)
            .build();
        Ticker oldTicker = new Ticker.Builder()
            .bid(new BigDecimal("120.00"))
            .ask(new BigDecimal("123.00"))
            .instrument(CurrencyPair.BTC_USD)
            .timestamp(new Date(1609633979L))
            .build();
        Ticker newTicker = new Ticker.Builder()
            .bid(new BigDecimal("120.00"))
            .ask(new BigDecimal("123.00"))
            .instrument(CurrencyPair.BTC_USD)
            .timestamp(new Date(1609634008L))
            .build();

        tickerService.allTickers.put(
            tickerService.tickerKey(exchange, CurrencyPair.BTC_USD),
            oldTicker);

        tickerService.putTicker(exchange, newTicker);

        assertEquals(newTicker, tickerService.getTicker(exchange, CurrencyPair.BTC_USD));
    }

    @Test
    public void testPutTickerOlderTicker() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(singleCallTickerStrategy)
            .withHomeCurrency(Currency.USD)
            .build();
        Ticker oldTicker = new Ticker.Builder()
            .bid(new BigDecimal("120.00"))
            .ask(new BigDecimal("123.00"))
            .instrument(CurrencyPair.BTC_USD)
            .timestamp(new Date(1609634008L))
            .build();
        Ticker newTicker = new Ticker.Builder()
            .bid(new BigDecimal("120.00"))
            .ask(new BigDecimal("123.00"))
            .instrument(CurrencyPair.BTC_USD)
            .timestamp(new Date(1609633979L))
            .build();

        tickerService.allTickers.put(
            tickerService.tickerKey(exchange, CurrencyPair.BTC_USD),
            oldTicker);

        tickerService.putTicker(exchange, newTicker);

        assertEquals(oldTicker, tickerService.getTicker(exchange, CurrencyPair.BTC_USD));
    }

    @Test
    public void testPutTickerOldNull() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(singleCallTickerStrategy)
            .withHomeCurrency(Currency.USD)
            .build();
        Ticker newTicker = new Ticker.Builder()
            .bid(new BigDecimal("120.00"))
            .ask(new BigDecimal("123.00"))
            .instrument(CurrencyPair.BTC_USD)
            .timestamp(new Date(1609633979L))
            .build();

        tickerService.putTicker(exchange, newTicker);

        assertEquals(newTicker, tickerService.getTicker(exchange, CurrencyPair.BTC_USD));
    }

    @Test
    public void testPutTickerNoOldTimestamp() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(singleCallTickerStrategy)
            .withHomeCurrency(Currency.USD)
            .build();
        Ticker oldTicker = new Ticker.Builder()
            .bid(new BigDecimal("120.00"))
            .ask(new BigDecimal("123.00"))
            .instrument(CurrencyPair.BTC_USD)
            .build();
        Ticker newTicker = new Ticker.Builder()
            .bid(new BigDecimal("120.00"))
            .ask(new BigDecimal("123.00"))
            .instrument(CurrencyPair.BTC_USD)
            .timestamp(new Date(1609633979L))
            .build();

        tickerService.allTickers.put(
            tickerService.tickerKey(exchange, CurrencyPair.BTC_USD),
            oldTicker);

        tickerService.putTicker(exchange, newTicker);

        assertEquals(newTicker, tickerService.getTicker(exchange, CurrencyPair.BTC_USD));
    }

    @Test
    public void testPutTickerNoNewTimestamp() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(singleCallTickerStrategy)
            .withHomeCurrency(Currency.USD)
            .build();
        Ticker oldTicker = new Ticker.Builder()
            .bid(new BigDecimal("120.00"))
            .ask(new BigDecimal("123.00"))
            .instrument(CurrencyPair.BTC_USD)
            .timestamp(new Date(1609634008L))
            .build();
        Ticker newTicker = new Ticker.Builder()
            .bid(new BigDecimal("120.00"))
            .ask(new BigDecimal("123.00"))
            .instrument(CurrencyPair.BTC_USD)
            .build();

        tickerService.allTickers.put(
            tickerService.tickerKey(exchange, CurrencyPair.BTC_USD),
            oldTicker);

        tickerService.putTicker(exchange, newTicker);

        assertEquals(newTicker, tickerService.getTicker(exchange, CurrencyPair.BTC_USD));
    }

    @Test
    public void testPutTickerNoTimestamps() throws IOException {
        Exchange exchange = new ExchangeBuilder("CrazyCoinz", CurrencyPair.BTC_USD)
            .withTickerStrategy(singleCallTickerStrategy)
            .withHomeCurrency(Currency.USD)
            .build();
        Ticker oldTicker = new Ticker.Builder()
            .bid(new BigDecimal("120.00"))
            .ask(new BigDecimal("123.00"))
            .instrument(CurrencyPair.BTC_USD)
            .timestamp(new Date(1609634008L))
            .build();
        Ticker newTicker = new Ticker.Builder()
            .bid(new BigDecimal("120.00"))
            .ask(new BigDecimal("123.00"))
            .instrument(CurrencyPair.BTC_USD)
            .build();

        tickerService.allTickers.put(
            tickerService.tickerKey(exchange, CurrencyPair.BTC_USD),
            oldTicker);

        tickerService.putTicker(exchange, newTicker);

        assertEquals(newTicker, tickerService.getTicker(exchange, CurrencyPair.BTC_USD));
    }
}
