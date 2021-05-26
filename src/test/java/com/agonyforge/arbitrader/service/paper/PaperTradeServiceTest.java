package com.agonyforge.arbitrader.service.paper;

import com.agonyforge.arbitrader.BaseTestCase;
import com.agonyforge.arbitrader.config.ExchangeConfiguration;
import com.agonyforge.arbitrader.config.FeeComputation;
import com.agonyforge.arbitrader.config.PaperConfiguration;
import com.agonyforge.arbitrader.exception.MarginNotSupportedException;
import com.agonyforge.arbitrader.service.ExchangeService;
import com.agonyforge.arbitrader.service.TickerService;
import com.agonyforge.arbitrader.service.model.ExchangeFee;
import org.junit.Before;
import org.junit.Test;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.FundsExceededException;

import org.mockito.Mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.agonyforge.arbitrader.DecimalConstants.BTC_SCALE;
import static com.agonyforge.arbitrader.DecimalConstants.USD_SCALE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PaperTradeServiceTest extends BaseTestCase {

    PaperExchange paperExchange;
    PaperTradeService paperTradeService;


    ExchangeConfiguration exchangeConfiguration;

    @Mock
    private TickerService tickerService;

    @Mock
    private ExchangeService exchangeService;

    @Mock
    private Exchange exchange;

    @Before
    public void setUp() {
        PaperConfiguration paperConfiguration = new PaperConfiguration();
        paperConfiguration.setActive(true);
        paperConfiguration.setInitialBalance(new BigDecimal("100"));
        exchangeConfiguration = new ExchangeConfiguration();
        final ExchangeFee exchangeFee = new ExchangeFee(new BigDecimal("0.002"), new BigDecimal("0.001"));

        paperExchange = new PaperExchange(exchange, Currency.USD,tickerService, exchangeService, paperConfiguration);
        paperTradeService = paperExchange.getPaperTradeService();
        paperExchange.getPaperAccountService().putCoin(Currency.BTC, new BigDecimal("10"));

        when(exchange.getExchangeSpecification()).thenReturn(new ExchangeSpecification(PaperExchange.class));
        when(exchangeService.getExchangeMetadata(any(Exchange.class))).thenReturn(exchangeConfiguration);
        when(exchangeService.getExchangeFee(any(Exchange.class),any(CurrencyPair.class),anyBoolean())).thenReturn(exchangeFee);
    }




    @Test
    public void testPlaceAndGetOrder() {
        exchangeConfiguration.setFeeComputation(FeeComputation.SERVER);
        exchangeConfiguration.setMargin(false);

        LimitOrder order = new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("3"))
            .timestamp(new Date())
            .limitPrice(new BigDecimal("20"))
            .orderStatus(Order.OrderStatus.NEW)
            .build();

        String id = paperTradeService.placeLimitOrder(order);
        assertEquals(new BigDecimal("3"), paperTradeService.getOrder(id).stream().findFirst().get().getOriginalAmount());
    }

    @Test
    public void testPlaceOrderFundExceeded() {
        exchangeConfiguration.setFeeComputation(FeeComputation.SERVER);
        exchangeConfiguration.setMargin(false);

        LimitOrder order = new LimitOrder.Builder(Order.OrderType.ASK, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("11"))
            .timestamp(new Date())
            .limitPrice(new BigDecimal("20"))
            .orderStatus(Order.OrderStatus.NEW)
            .build();

        assertThrows(FundsExceededException.class, () -> paperTradeService.placeLimitOrder(order));
    }

    @Test
    public void testPlaceOrderFundExceededMargin() {
        exchangeConfiguration.setFeeComputation(FeeComputation.SERVER);
        exchangeConfiguration.setMargin(true);

        LimitOrder order = new LimitOrder.Builder(Order.OrderType.ASK, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("11"))
            .timestamp(new Date())
            .limitPrice(new BigDecimal("20"))
            .orderStatus(Order.OrderStatus.NEW)
            .build();

        assertThrows(FundsExceededException.class, () -> paperTradeService.placeLimitOrder(order));
    }

    @Test
    public void testPlaceOrderWithLeverage() {
        exchangeConfiguration.setFeeComputation(FeeComputation.SERVER);
        exchangeConfiguration.setMargin(true);

        LimitOrder order = (LimitOrder) new LimitOrder.Builder(Order.OrderType.ASK, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("11"))
            .timestamp(new Date())
            .limitPrice(new BigDecimal("20"))
            .orderStatus(Order.OrderStatus.NEW)
            .leverage("2")
            .build();

        paperTradeService.placeLimitOrder(order);
    }

    @Test
    public void testFillOrderBuyFundExceeded() {
        exchangeConfiguration.setFeeComputation(FeeComputation.SERVER);
        exchangeConfiguration.setMargin(false);

        LimitOrder order = new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("7"))
            .timestamp(new Date())
            .limitPrice(new BigDecimal("20"))
            .orderStatus(Order.OrderStatus.NEW)
            .build();

        assertThrows(FundsExceededException.class, () -> paperTradeService.fillOrder(order, order.getLimitPrice()));
    }

    @Test
    public void testFillOrderSellFundExceeded() {
        exchangeConfiguration.setFeeComputation(FeeComputation.SERVER);
        exchangeConfiguration.setMargin(false);

        LimitOrder order = new LimitOrder.Builder(Order.OrderType.ASK, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("15"))
            .timestamp(new Date())
            .limitPrice(new BigDecimal("20"))
            .orderStatus(Order.OrderStatus.NEW)
            .build();

        assertThrows(FundsExceededException.class, () -> paperTradeService.fillOrder(order, order.getLimitPrice()));
    }

    @Test
    public void testFillOrderBuyFeeComputationClientFundExceeded() {
        exchangeConfiguration.setFeeComputation(FeeComputation.CLIENT);
        exchangeConfiguration.setMargin(false);

        LimitOrder order = new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("15"))
            .timestamp(new Date())
            .limitPrice(new BigDecimal("20"))
            .orderStatus(Order.OrderStatus.NEW)
            .build();

        assertThrows(FundsExceededException.class, () -> paperTradeService.fillOrder(order, order.getLimitPrice()));
    }

    @Test
    public void testFillOrderSellFeeComputationClientFundExceeded() {
        exchangeConfiguration.setFeeComputation(FeeComputation.CLIENT);
        exchangeConfiguration.setMargin(false);

        LimitOrder order = new LimitOrder.Builder(Order.OrderType.ASK, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("10"))
            .timestamp(new Date())
            .limitPrice(new BigDecimal("5"))
            .orderStatus(Order.OrderStatus.NEW)
            .build();

        assertThrows(FundsExceededException.class, () -> paperTradeService.fillOrder(order, order.getLimitPrice()));
    }

    @Test
    public void testFillOrderBuyFeeComputationClient() {
        exchangeConfiguration.setFeeComputation(FeeComputation.CLIENT);
        exchangeConfiguration.setMargin(false);

        LimitOrder order = new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("0.00125048"))
            .timestamp(new Date())
            .limitPrice(new BigDecimal("15993.90"))
            .orderStatus(Order.OrderStatus.NEW)
            .build();

        paperTradeService.fillOrder(order, order.getLimitPrice());
        assertEquals(new BigDecimal("79.99").setScale(USD_SCALE, RoundingMode.HALF_EVEN), paperExchange.getPaperAccountService().getBalance(Currency.USD).setScale(USD_SCALE, RoundingMode.HALF_EVEN));
        assertEquals(new BigDecimal("10.00124798").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), paperExchange.getPaperAccountService().getBalance(Currency.BTC).setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }


    @Test
    public void testFillOrderSellFeeComputationClient() {
        exchangeConfiguration.setFeeComputation(FeeComputation.CLIENT);
        exchangeConfiguration.setMargin(false);

        LimitOrder order = new LimitOrder.Builder(Order.OrderType.ASK, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("0.00124549"))
            .timestamp(new Date())
            .limitPrice(new BigDecimal("16052.89"))
            .orderStatus(Order.OrderStatus.NEW)
            .build();

        paperTradeService.fillOrder(order, order.getLimitPrice());
        assertEquals(new BigDecimal("119.99").setScale(USD_SCALE, RoundingMode.HALF_EVEN), paperExchange.getPaperAccountService().getBalance(Currency.USD).setScale(USD_SCALE, RoundingMode.HALF_EVEN));
        assertEquals(new BigDecimal("9.99875202").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), paperExchange.getPaperAccountService().getBalance(Currency.BTC).setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void testFillOrderBuyFeeComputationServer() {
        exchangeConfiguration.setFeeComputation(FeeComputation.SERVER);
        exchangeConfiguration.setMargin(false);

        LimitOrder order = new LimitOrder.Builder(Order.OrderType.BID, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("5"))
            .timestamp(new Date())
            .limitPrice(new BigDecimal("10"))
            .orderStatus(Order.OrderStatus.NEW)
            .build();

        paperTradeService.fillOrder(order, order.getLimitPrice());

        assertEquals(new BigDecimal("49.90").setScale(USD_SCALE, RoundingMode.HALF_EVEN), paperExchange.getPaperAccountService().getBalance(Currency.USD).setScale(USD_SCALE, RoundingMode.HALF_EVEN));
        assertEquals(new BigDecimal("15").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), paperExchange.getPaperAccountService().getBalance(Currency.BTC).setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }

    @Test
    public void testFillOrderSellFeeComputationServer() {
        exchangeConfiguration.setFeeComputation(FeeComputation.SERVER);
        exchangeConfiguration.setMargin(false);

        LimitOrder order = new LimitOrder.Builder(Order.OrderType.ASK, CurrencyPair.BTC_USD)
            .originalAmount(new BigDecimal("5"))
            .timestamp(new Date())
            .limitPrice(new BigDecimal("10"))
            .orderStatus(Order.OrderStatus.NEW)
            .build();

        paperTradeService.fillOrder(order, order.getLimitPrice());
        assertEquals(new BigDecimal("149.90").setScale(USD_SCALE, RoundingMode.HALF_EVEN), paperExchange.getPaperAccountService().getBalance(Currency.USD).setScale(USD_SCALE, RoundingMode.HALF_EVEN));
        assertEquals(new BigDecimal("5").setScale(BTC_SCALE, RoundingMode.HALF_EVEN), paperExchange.getPaperAccountService().getBalance(Currency.BTC).setScale(BTC_SCALE, RoundingMode.HALF_EVEN));
    }
}
