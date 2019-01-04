package com.r307.arbitrader;

import com.r307.arbitrader.config.ExchangeConfiguration;
import com.r307.arbitrader.config.TradingConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.r307.arbitrader.TradingService.METADATA_KEY;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class TradingServiceTest {
    private static final int SCALE = 6;

    @Mock
    private Exchange exchange;

    @Mock
    private ExchangeSpecification exchangeSpecification;

    @Mock
    private MarketDataService exchangeMarketDataService;

    private CurrencyPair currencyPair = new CurrencyPair("BTC/USD");

    private TradingService tradingService;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        TradingConfiguration configuration = new TradingConfiguration();
        ExchangeConfiguration longMetadata = new ExchangeConfiguration();

        longMetadata.setHomeCurrency(Currency.USD);

        configuration.getExchanges().add(longMetadata);

        when(exchange.getExchangeSpecification()).thenReturn(exchangeSpecification);
        when(exchange.getMarketDataService()).thenReturn(exchangeMarketDataService);

        when(exchangeSpecification.getExchangeSpecificParametersItem(METADATA_KEY)).thenReturn(longMetadata);

        OrderBook exchangeOrderBook = new OrderBook(
                new Date(),
                generateOrders(Order.OrderType.ASK),
                generateOrders(Order.OrderType.BID)
        );

        when(exchangeMarketDataService.getOrderBook(eq(currencyPair))).thenReturn(exchangeOrderBook);

        tradingService = new TradingService(configuration);
    }

    // the best price point has enough volume to fill my order
    @Test
    public void testLimitPriceLongSufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(1.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(exchange, currencyPair, allowedVolume, Order.OrderType.ASK);

        assertEquals(new BigDecimal(100.0000).setScale(SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point has enough volume to fill my order
    @Test
    public void testLimitPriceShortSufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(1.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(exchange, currencyPair, allowedVolume, Order.OrderType.BID);

        assertEquals(new BigDecimal(100.0990).setScale(SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point isn't big enough to fill my order alone, so the price will slip
    @Test
    public void testLimitPriceLongInsufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(11.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(exchange, currencyPair, allowedVolume, Order.OrderType.ASK);

        assertEquals(new BigDecimal(100.0010).setScale(SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point isn't big enough to fill my order alone, so the price will slip
    @Test
    public void testLimitPriceShortInsufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(11.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(exchange, currencyPair, allowedVolume, Order.OrderType.BID);

        assertEquals(new BigDecimal(100.0980).setScale(SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the exchange doesn't have enough volume to fill my gigantic order
    @Test(expected = RuntimeException.class)
    public void testLimitPriceLongInsufficientLiquidity() {
        BigDecimal allowedVolume = new BigDecimal(10001);

        tradingService.getLimitPrice(exchange, currencyPair, allowedVolume, Order.OrderType.ASK);
    }

    // the exchange doesn't have enough volume to fill my gigantic order
    @Test(expected = RuntimeException.class)
    public void testLimitPriceShortInsufficientLiquidity() {
        BigDecimal allowedVolume = new BigDecimal(10001);

        tradingService.getLimitPrice(exchange, currencyPair, allowedVolume, Order.OrderType.BID);
    }

    private List<LimitOrder> generateOrders(Order.OrderType type) {
        List<LimitOrder> orders = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            orders.add(new LimitOrder(
                    type,
                    BigDecimal.TEN,
                    currencyPair,
                    UUID.randomUUID().toString(),
                    new Date(),
                    new BigDecimal(100.0000)
                            .add(new BigDecimal(i * 0.001))
                            .setScale(SCALE, RoundingMode.HALF_EVEN)));
        }

        if (Order.OrderType.BID.equals(type)) {
            Collections.reverse(orders);
        }

        return orders;
    }
}
