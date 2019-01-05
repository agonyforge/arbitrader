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
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;
import static com.r307.arbitrader.TradingService.METADATA_KEY;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class TradingServiceTest {
    @Mock
    private Exchange exchange;

    @Mock
    private ExchangeSpecification exchangeSpecification;

    @Mock
    private MarketDataService exchangeMarketDataService;

    @Mock
    private TradeService exchangeTradeService;

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
        when(exchange.getTradeService()).thenReturn(exchangeTradeService);

        when(exchangeSpecification.getExchangeSpecificParametersItem(METADATA_KEY)).thenReturn(longMetadata);

        OrderBook exchangeOrderBook = new OrderBook(
                new Date(),
                generateOrders(Order.OrderType.ASK),
                generateOrders(Order.OrderType.BID)
        );

        when(exchangeMarketDataService.getOrderBook(eq(currencyPair))).thenReturn(exchangeOrderBook);

        when(exchangeTradeService.getOrder(anyString())).thenReturn(generateOrder());
        when(exchangeTradeService.getOrder(eq("missingOrder"))).thenReturn(Collections.emptyList());
        when(exchangeTradeService.getOrder(eq("notAvailable"))).thenThrow(new NotAvailableFromExchangeException("Exchange does not support fetching orders by ID"));
        when(exchangeTradeService.getOrder(eq("ioe"))).thenThrow(new IOException("Unable to connect to exchange"));

        tradingService = new TradingService(configuration);
    }

    @Test
    public void testGetVolumeForOrder() {
        BigDecimal volume = tradingService.getVolumeForOrder(
                exchange,
                "orderId",
                new BigDecimal(50.0));

        assertEquals(BigDecimal.TEN, volume);
    }

    @Test(expected = OrderNotFoundException.class)
    public void testGetVolumeForOrderNotFound() {
        tradingService.getVolumeForOrder(
                exchange,
                "missingOrder",
                new BigDecimal(50.0));
    }

    @Test
    public void testGetVolumeForOrderNotAvailable() {
        BigDecimal volume = tradingService.getVolumeForOrder(
                exchange,
                "notAvailable",
                new BigDecimal(50.0));

        assertEquals(new BigDecimal(50.0), volume);
    }

    @Test
    public void testGetVolumeForOrderIOException() {
        BigDecimal volume = tradingService.getVolumeForOrder(
                exchange,
                "ioe",
                new BigDecimal(50.0));

        assertEquals(new BigDecimal(50.0), volume);
    }

    // the best price point has enough volume to fill my order
    @Test
    public void testLimitPriceLongSufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(1.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(exchange, currencyPair, allowedVolume, Order.OrderType.ASK);

        assertEquals(new BigDecimal(100.0000).setScale(BTC_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point has enough volume to fill my order
    @Test
    public void testLimitPriceShortSufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(1.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(exchange, currencyPair, allowedVolume, Order.OrderType.BID);

        assertEquals(new BigDecimal(100.0990).setScale(BTC_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point isn't big enough to fill my order alone, so the price will slip
    @Test
    public void testLimitPriceLongInsufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(11.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(exchange, currencyPair, allowedVolume, Order.OrderType.ASK);

        assertEquals(new BigDecimal(100.0010).setScale(BTC_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point isn't big enough to fill my order alone, so the price will slip
    @Test
    public void testLimitPriceShortInsufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(11.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(exchange, currencyPair, allowedVolume, Order.OrderType.BID);

        assertEquals(new BigDecimal(100.0980).setScale(BTC_SCALE, RoundingMode.HALF_EVEN), limitPrice);
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

    private Collection<Order> generateOrder() {
        Order order = new LimitOrder.Builder(Order.OrderType.ASK, currencyPair)
                .cumulativeAmount(BigDecimal.TEN)
                .build();

        return Collections.singletonList(order);
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
                            .setScale(BTC_SCALE, RoundingMode.HALF_EVEN)));
        }

        if (Order.OrderType.BID.equals(type)) {
            Collections.reverse(orders);
        }

        return orders;
    }
}
