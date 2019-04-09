package com.r307.arbitrader;

import com.r307.arbitrader.config.ExchangeConfiguration;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;
import static com.r307.arbitrader.service.TradingService.METADATA_KEY;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExchangeBuilder {
    private String name;
    private CurrencyPair currencyPair;
    private Integer bids;
    private Integer asks;
    private List<Balance> balances = new ArrayList<>();
    private TradeService tradeService = null;

    public ExchangeBuilder(String name, CurrencyPair currencyPair) {
        this.name = name;
        this.currencyPair = currencyPair;
    }

    public ExchangeBuilder withOrderBook(int bids, int asks) {
        this.bids = bids;
        this.asks = asks;
        return this;
    }

    public ExchangeBuilder withBalance(Currency currency, BigDecimal amount) {
        Balance balance = new Balance.Builder()
            .currency(currency)
            .available(amount)
            .build();

        balances.add(balance);

        return this;
    }

    public ExchangeBuilder withTradeService() throws IOException {
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

    public Exchange build() throws IOException {
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
                generateOrders(currencyPair, Order.OrderType.ASK),
                generateOrders(currencyPair, Order.OrderType.BID)
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

    private static List<LimitOrder> generateOrders(CurrencyPair currencyPair, Order.OrderType type) {
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
