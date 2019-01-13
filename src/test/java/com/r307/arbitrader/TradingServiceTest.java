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
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;
import static com.r307.arbitrader.DecimalConstants.USD_SCALE;
import static com.r307.arbitrader.TradingService.METADATA_KEY;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TradingServiceTest {
    private static final CurrencyPair currencyPair = new CurrencyPair("BTC/USD");

    private Exchange longExchange;
    private Exchange shortExchange;

    private TradingConfiguration configuration;

    private TradingService tradingService;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        configuration = new TradingConfiguration();

        longExchange = new ExchangeBuilder("Long")
                .withTradeService()
                .withOrderBook(100, 100)
                .withBalance(Currency.USD, new BigDecimal(100.00).setScale(USD_SCALE, RoundingMode.HALF_EVEN))
                .build();
        shortExchange = new ExchangeBuilder("Short")
                .withBalance(Currency.USD, new BigDecimal(500.00).setScale(USD_SCALE, RoundingMode.HALF_EVEN))
                .build();

        // This spy right here is a bad code smell, kids! Don't try this at work!
        // Upcoming refactoring will allow me to remove it.
        tradingService = spy(new TradingService(configuration));
    }

    @Test
    public void testGetVolumeForOrder() {
        BigDecimal volume = tradingService.getVolumeForOrder(
                longExchange,
                "orderId",
                new BigDecimal(50.0));

        assertEquals(BigDecimal.TEN, volume);
    }

    @Test(expected = OrderNotFoundException.class)
    public void testGetVolumeForOrderNotFound() {
        tradingService.getVolumeForOrder(
                longExchange,
                "missingOrder",
                new BigDecimal(50.0));
    }

    @Test
    public void testGetVolumeForOrderNotAvailable() {
        BigDecimal volume = tradingService.getVolumeForOrder(
                longExchange,
                "notAvailable",
                new BigDecimal(50.0));

        assertEquals(new BigDecimal(50.0), volume);
    }

    @Test
    public void testGetVolumeForOrderIOException() {
        BigDecimal volume = tradingService.getVolumeForOrder(
                longExchange,
                "ioe",
                new BigDecimal(50.0));

        assertEquals(new BigDecimal(50.0), volume);
    }

    // the best price point has enough volume to fill my order
    @Test
    public void testLimitPriceLongSufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(1.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.ASK);

        assertEquals(new BigDecimal(100.0000).setScale(BTC_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point has enough volume to fill my order
    @Test
    public void testLimitPriceShortSufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(1.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.BID);

        assertEquals(new BigDecimal(100.0990).setScale(BTC_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point isn't big enough to fill my order alone, so the price will slip
    @Test
    public void testLimitPriceLongInsufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(11.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.ASK);

        assertEquals(new BigDecimal(100.0010).setScale(BTC_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the best price point isn't big enough to fill my order alone, so the price will slip
    @Test
    public void testLimitPriceShortInsufficientVolume() {
        BigDecimal allowedVolume = new BigDecimal(11.00);
        BigDecimal limitPrice = tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.BID);

        assertEquals(new BigDecimal(100.0980).setScale(BTC_SCALE, RoundingMode.HALF_EVEN), limitPrice);
    }

    // the exchange doesn't have enough volume to fill my gigantic order
    @Test(expected = RuntimeException.class)
    public void testLimitPriceLongInsufficientLiquidity() {
        BigDecimal allowedVolume = new BigDecimal(10001);

        tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.ASK);
    }

    // the exchange doesn't have enough volume to fill my gigantic order
    @Test(expected = RuntimeException.class)
    public void testLimitPriceShortInsufficientLiquidity() {
        BigDecimal allowedVolume = new BigDecimal(10001);

        tradingService.getLimitPrice(longExchange, currencyPair, allowedVolume, Order.OrderType.BID);
    }

    @Test
    public void testGetMaximumExposureFixedExposure() {
        configuration.setFixedExposure(new BigDecimal(100.00));

        BigDecimal exposure = tradingService.getMaximumExposure(longExchange, shortExchange);

        assertEquals(new BigDecimal(100.00).setScale(USD_SCALE, RoundingMode.HALF_EVEN), exposure);
    }

    // should return 90% of the smallest account balance
    @Test
    public void testGetMaximumExposure() {
        BigDecimal exposure = tradingService.getMaximumExposure(longExchange, shortExchange);

        assertEquals(new BigDecimal(90.00).setScale(USD_SCALE, RoundingMode.HALF_EVEN), exposure);
    }

    @Test
    public void testGetMaximumExposureEmpty() {
        BigDecimal exposure = tradingService.getMaximumExposure();

        assertEquals(new BigDecimal(0.00).setScale(USD_SCALE, RoundingMode.HALF_EVEN), exposure);
    }

    @Test
    public void testGetMaximumExposureException() throws IOException {
        when(tradingService.getAccountBalance(shortExchange, Currency.USD)).thenThrow(new IOException("Boom!"));

        BigDecimal exposure = tradingService.getMaximumExposure();

        // the IOE should not propagate and blow everything up
        assertEquals(new BigDecimal(0.00).setScale(USD_SCALE, RoundingMode.HALF_EVEN), exposure);
    }

    private static class ExchangeBuilder {
        private String name;
        private Integer bids;
        private Integer asks;
        private List<Balance> balances = new ArrayList<>();
        private TradeService tradeService = null;

        ExchangeBuilder(String name) {
            this.name = name;
        }

        ExchangeBuilder withOrderBook(int bids, int asks) {
            this.bids = bids;
            this.asks = asks;
            return this;
        }

        ExchangeBuilder withBalance(Currency currency, BigDecimal amount) {
            Balance balance = new Balance.Builder()
                    .currency(currency)
                    .available(amount)
                    .build();

            balances.add(balance);

            return this;
        }

        ExchangeBuilder withTradeService() throws IOException {
            tradeService = mock(TradeService.class);

            LimitOrder order = new LimitOrder(
                    Order.OrderType.BID,
                    BigDecimal.TEN,
                    currencyPair,
                    UUID.randomUUID().toString(),
                    new Date(),
                    new BigDecimal(100.0000)
                            .add(new BigDecimal(0.001))
                            .setScale(BTC_SCALE, RoundingMode.HALF_EVEN));

            when(tradeService.getOrder(eq("orderId"))).thenReturn(Collections.singleton(order));
            when(tradeService.getOrder(eq("missingOrder"))).thenReturn(Collections.emptyList());
            when(tradeService.getOrder(eq("notAvailable"))).thenThrow(new NotAvailableFromExchangeException("Exchange does not support fetching orders by ID"));
            when(tradeService.getOrder(eq("ioe"))).thenThrow(new IOException("Unable to connect to exchange"));

            return this;
        }

        Exchange build() throws IOException {
            Exchange exchange = mock(Exchange.class);
            ExchangeSpecification specification = mock(ExchangeSpecification.class);
            ExchangeConfiguration metadata = new ExchangeConfiguration();

            metadata.setHomeCurrency(Currency.USD);

            when(exchange.getExchangeSpecification()).thenReturn(specification);
            when(specification.getExchangeName()).thenReturn(name);
            when(specification.getExchangeSpecificParametersItem(METADATA_KEY)).thenReturn(metadata);

            if (bids != null || asks != null) {
                MarketDataService marketDataService = mock(MarketDataService.class);
                OrderBook orderBook = new OrderBook(
                        new Date(),
                        generateOrders(Order.OrderType.ASK),
                        generateOrders(Order.OrderType.BID)
                );

                when(marketDataService.getOrderBook(eq(currencyPair))).thenReturn(orderBook);
                when(exchange.getMarketDataService()).thenReturn(marketDataService);
            }

            if (!balances.isEmpty()) {
                Wallet wallet = new Wallet(balances);
                AccountInfo accountInfo = new AccountInfo(wallet);
                AccountService accountService = mock(AccountService.class);

                when(accountService.getAccountInfo()).thenReturn(accountInfo);
                when(exchange.getAccountService()).thenReturn(accountService);
            }

            if (tradeService != null) {
                when(exchange.getTradeService()).thenReturn(tradeService);
            }

            return exchange;
        }

        private static List<LimitOrder> generateOrders(Order.OrderType type) {
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
}
