package com.r307.arbitrader;

import com.r307.arbitrader.config.ExchangeConfiguration;
import com.r307.arbitrader.service.ticker.TickerStrategy;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.Params;
import org.knowm.xchange.service.trade.TradeService;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.r307.arbitrader.DecimalConstants.BTC_SCALE;
import static com.r307.arbitrader.DecimalConstants.USD_SCALE;
import static com.r307.arbitrader.service.TradingService.METADATA_KEY;
import static com.r307.arbitrader.service.TradingService.TICKER_STRATEGY_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExchangeBuilder {
    private String name;
    private CurrencyPair currencyPair;
    private Currency homeCurrency = Currency.USD;
    private Integer bids;
    private Integer asks;
    private List<Balance> balances = new ArrayList<>();
    private ExchangeMetaData exchangeMetaData = null;
    private Exception tickerException = null;
    private TradeService tradeService = null;
    private TickerStrategy tickerStrategy = null;
    private Boolean isGetTickersImplemented = null;
    private List<Ticker> tickers = new ArrayList<>();

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

    public ExchangeBuilder withTickers(boolean isGetTickersImplemented, List<CurrencyPair> currencyPairs) {
        this.isGetTickersImplemented = isGetTickersImplemented;

        currencyPairs.forEach(currencyPair ->
            tickers.add(new Ticker.Builder()
                .currencyPair(currencyPair)
                .open(new BigDecimal(1000.000))
                .last(new BigDecimal(1001.000))
                .bid(new BigDecimal(1001.000))
                .ask(new BigDecimal(1002.000))
                .high(new BigDecimal(1005.00))
                .low(new BigDecimal(1000.00))
                .vwap(new BigDecimal(1000.50))
                .volume(new BigDecimal(500000.00))
                .quoteVolume(new BigDecimal(600000.00))
                .bidSize(new BigDecimal(400.00))
                .askSize(new BigDecimal(600.00))
                .build())
        );

        return this;
    }

    public ExchangeBuilder withTickers(Exception toThrow) {
        this.tickerException = toThrow;

        return this;
    }

    public ExchangeBuilder withTickerStrategy(TickerStrategy tickerStrategy) {
        this.tickerStrategy = tickerStrategy;

        return this;
    }

    public ExchangeBuilder withExchangeMetaData() {
        CurrencyPairMetaData currencyPairMetaData = new CurrencyPairMetaData(
            new BigDecimal(0.0020),
            new BigDecimal(0.0010),
            new BigDecimal(1000.00000000),
            BTC_SCALE,
            null,
            null);
        Map<CurrencyPair, CurrencyPairMetaData> currencyPairMetaDataMap = new HashMap<>();

        currencyPairMetaDataMap.put(currencyPair, currencyPairMetaData);

        CurrencyMetaData baseMetaData = new CurrencyMetaData(BTC_SCALE, BigDecimal.ZERO);
        CurrencyMetaData counterMetaData = new CurrencyMetaData(USD_SCALE, BigDecimal.ZERO);
        Map<Currency, CurrencyMetaData> currencyMetaDataMap = new HashMap<>();

        currencyMetaDataMap.put(currencyPair.base, baseMetaData);
        currencyMetaDataMap.put(currencyPair.counter, counterMetaData);

        exchangeMetaData = new ExchangeMetaData(
            currencyPairMetaDataMap,
            currencyMetaDataMap,
            null,
            null,
            null
        );

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

    public ExchangeBuilder withHomeCurrency(Currency homeCurrency) {
        this.homeCurrency = homeCurrency;

        return this;
    }

    public Exchange build() throws IOException {
        Exchange exchange = mock(Exchange.class);
        ExchangeSpecification specification = mock(ExchangeSpecification.class);
        ExchangeConfiguration metadata = new ExchangeConfiguration();
        MarketDataService marketDataService = mock(MarketDataService.class);

        metadata.setHomeCurrency(homeCurrency);

        when(exchange.getExchangeSpecification()).thenReturn(specification);
        when(specification.getExchangeName()).thenReturn(name);
        when(specification.getExchangeSpecificParametersItem(METADATA_KEY)).thenReturn(metadata);
        when(exchange.getMarketDataService()).thenReturn(marketDataService);

        if (tickerException != null) {
            when(marketDataService.getTicker(any())).thenThrow(tickerException);
            when(marketDataService.getTickers(any(Params.class))).thenThrow(tickerException);
        }

        if (tickers != null && !tickers.isEmpty()) {
            tickers.forEach(ticker -> {
                try {
                    when(marketDataService.getTicker(eq(ticker.getCurrencyPair()))).thenReturn(ticker);
                } catch (IOException e) {
                    // nothing to do here if we couldn't build the mock
                }
            });

            if (isGetTickersImplemented) {
                when(marketDataService.getTickers(any())).thenReturn(tickers);
            } else {
                when(marketDataService.getTickers(any())).thenThrow(new NotYetImplementedForExchangeException());
            }
        }

        if (bids != null || asks != null) {
            OrderBook orderBook = new OrderBook(
                new Date(),
                generateOrders(currencyPair, Order.OrderType.ASK),
                generateOrders(currencyPair, Order.OrderType.BID)
            );

            when(marketDataService.getOrderBook(eq(currencyPair))).thenReturn(orderBook);
        }

        if (!balances.isEmpty()) {
            Wallet wallet = Wallet.Builder.from(balances).build();
            AccountInfo accountInfo = new AccountInfo(wallet);
            AccountService accountService = mock(AccountService.class);

            when(accountService.getAccountInfo()).thenReturn(accountInfo);
            when(exchange.getAccountService()).thenReturn(accountService);
        }

        if (tickerStrategy != null) {
            when(specification.getExchangeSpecificParametersItem(TICKER_STRATEGY_KEY)).thenReturn(tickerStrategy);
        }

        if (exchangeMetaData != null) {
            when(exchange.getExchangeMetaData()).thenReturn(exchangeMetaData);
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
