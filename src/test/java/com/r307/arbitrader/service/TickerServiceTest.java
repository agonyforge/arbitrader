package com.r307.arbitrader.service;

import com.r307.arbitrader.ExchangeBuilder;
import com.r307.arbitrader.config.NotificationConfiguration;
import com.r307.arbitrader.config.TradingConfiguration;
import com.r307.arbitrader.service.event.TickerEventPublisher;
import com.r307.arbitrader.service.event.TradeAnalysisPublisher;
import com.r307.arbitrader.service.model.event.TickerEvent;
import com.r307.arbitrader.service.model.TradeCombination;
import com.r307.arbitrader.service.model.event.TradeAnalysisEvent;
import com.r307.arbitrader.service.ticker.ParallelTickerStrategy;
import com.r307.arbitrader.service.ticker.SingleCallTickerStrategy;
import com.r307.arbitrader.service.ticker.TickerStrategy;
import com.r307.arbitrader.service.ticker.TickerStrategyProvider;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TickerServiceTest {
    private final CurrencyPair CURRENCY_PAIR = CurrencyPair.BTC_USD;

    private List<CurrencyPair> currencyPairs = Collections.singletonList(CurrencyPair.BTC_USD);
    private TickerStrategy singleCallTickerStrategy;
    private TickerStrategy parallelTickerStrategy;

    private ErrorCollectorService errorCollectorService;

    private TickerService tickerService;
    private ExchangeService exchangeService;

    @Mock
    private TickerEventPublisher tickerEventPublisher;
    @Mock
    private TradeAnalysisPublisher tradeAnalysisPublisher;

    @Mock
    private TickerStrategyProvider tickerStrategyProvider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        NotificationConfiguration notificationConfiguration = new NotificationConfiguration();
        TradingConfiguration tradingConfiguration = new TradingConfiguration();

        exchangeService = new ExchangeService(new ExchangeFeeCache(), tickerStrategyProvider);
        tickerService = new TickerService(
            tradingConfiguration,
            exchangeService,
            errorCollectorService,
            tradeAnalysisPublisher);

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

        assertEquals(1, tickerService.getExchangeTradeCombinations().size());
        assertTrue(tickerService.getExchangeTradeCombinations().contains(new TradeCombination(exchangeB, exchangeA, CURRENCY_PAIR)));
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

        TradeCombination tradeCombination = new TradeCombination(exchangeB, exchangeA, CURRENCY_PAIR);

        tickerService.addExchangeTradeCombination(exchangeA, tradeCombination);
        tickerService.addExchangeTradeCombination(exchangeB, tradeCombination);

        tickerService.refreshTickers();

        verify(tickerEventPublisher, times(3)).publishTicker(any(TickerEvent.class));

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

        tickerService.getAllTickers().put(tickerService.tickerKey(exchange, CURRENCY_PAIR), ticker);

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
    public void testGetTradeCombinations() throws IOException {
        TradeCombination combination = mock(TradeCombination.class);

        tickerService.addExchangeTradeCombination(new ExchangeBuilder("ExA", null).build(), combination);

        Set<TradeCombination> result = tickerService.getExchangeTradeCombinations();

        assertNotSame(tickerService.getExchangeTradeCombinations(), result);
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

        tickerService.fetchTickers(exchange, currencyPairs);
        verify(tickerEventPublisher, atLeastOnce()).publishTicker(any(TickerEvent.class));

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

        tickerService.fetchTickers(exchange, currencyPairs);
        verify(tickerEventPublisher, atLeastOnce()).publishTicker(any(TickerEvent.class));

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

        tickerService.fetchTickers(exchange, currencyPairs);
        verify(tickerEventPublisher, never()).publishTicker(any(TickerEvent.class));

        assertFalse(errorCollectorService.isEmpty());

        verify(exchange.getMarketDataService()).getTickers(any());
        verify(exchange.getMarketDataService(), never()).getTicker(any());
    }

    @Test
    public void testUpdateTicker() throws IOException {
        assertTrue(tickerService.getAllTickers().isEmpty());

        final CurrencyPair currencyPair = new CurrencyPair("BTC/USD");
        final Exchange exchange = new ExchangeBuilder("Test", currencyPair).build();
        final Ticker ticker = new Ticker.Builder()
            .ask(BigDecimal.valueOf(2000))
            .bid(BigDecimal.valueOf(2000))
            .instrument(currencyPair)
            .build();

        final String key = tickerService.tickerKey(exchange, currencyPair);
        tickerService.updateTicker(exchange, ticker);

        verify(tradeAnalysisPublisher, times(1)).publishTradeAnalysis(any(TradeAnalysisEvent.class));

        final Ticker tickerFromAllTickers = tickerService.getAllTickers().get(key);

        assertFalse(tickerService.getAllTickers().isEmpty());
        assertNotNull(tickerFromAllTickers);
        assertEquals(ticker, tickerFromAllTickers);

        // TODO: assert that a new trade analysis event is triggered
    }
}
