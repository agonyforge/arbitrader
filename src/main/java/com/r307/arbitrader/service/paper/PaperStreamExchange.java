package com.r307.arbitrader.service.paper;

import com.r307.arbitrader.config.PaperConfiguration;
import com.r307.arbitrader.service.ExchangeService;
import com.r307.arbitrader.service.TickerService;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;
import io.reactivex.Completable;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import si.mazi.rescu.SynchronizedValueFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

public class PaperStreamExchange extends PaperExchange implements StreamingExchange {
    private final StreamingExchange realExchange;
    private final PaperTradeService tradeService;
    private final PaperAccountService accountService;
    private final MarketDataService marketDataService;

    public PaperStreamExchange(StreamingExchange realExchange, Currency homeCurrency, TickerService tickerService, ExchangeService exchangeService, PaperConfiguration paperConfiguration) {
        super(realExchange, homeCurrency, tickerService, exchangeService, paperConfiguration);
        this.realExchange = realExchange;

        this.tradeService = new PaperTradeService(this, realExchange.getTradeService(), tickerService, exchangeService, paperConfiguration);
        this.accountService = new PaperAccountService(realExchange.getAccountService(), homeCurrency, new BigDecimal(100));

        this.marketDataService = realExchange.getMarketDataService();

    }

    @Override
    public Completable connect(ProductSubscription... args) {
        return realExchange.connect(args);
    }

    @Override
    public Completable disconnect() {
        return realExchange.disconnect();
    }

    @Override
    public boolean isAlive() {
        return realExchange.isAlive();
    }

    @Override
    public StreamingMarketDataService getStreamingMarketDataService() {
        return realExchange.getStreamingMarketDataService();
    }

    @Override
    public void useCompressedMessages(boolean compressedMessages) {
        realExchange.useCompressedMessages(compressedMessages);
    }

    @Override
    public ExchangeSpecification getExchangeSpecification() {
        return realExchange.getExchangeSpecification();
    }

    @Override
    public ExchangeMetaData getExchangeMetaData() {
        return realExchange.getExchangeMetaData();
    }

    @Override
    public List<CurrencyPair> getExchangeSymbols() {
        return realExchange.getExchangeSymbols();
    }

    @Override
    public SynchronizedValueFactory<Long> getNonceFactory() {
        return realExchange.getNonceFactory();
    }

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
        return realExchange.getDefaultExchangeSpecification();
    }

    @Override
    public void applySpecification(ExchangeSpecification exchangeSpecification) {
        realExchange.applySpecification(exchangeSpecification);
    }

    @Override
    public MarketDataService getMarketDataService() {
        return marketDataService;
    }

    @Override
    public TradeService getTradeService() {
        return tradeService;
    }

    @Override
    public AccountService getAccountService() {
        return accountService;
    }

    @Override
    public void remoteInit() throws IOException, ExchangeException {
        realExchange.remoteInit();
    }
}
